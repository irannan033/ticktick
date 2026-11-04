"""WhatsApp conversation logic. Every state change goes through tasks_service (same rules as the app)."""
import json
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import TaskRow, User, WhatsAppMessage
from ..tasks_service import (active_tasks, cancel_task, complete_task, create_task, is_overdue, now_ms, postpone_task,
                             tz_of)
from .client import WhatsAppClient, WhatsAppError
from .parser import Intent, parse

HELP = (
    "Momentum commands:\n"
    "• list — show pending tasks\n"
    "• done 1,3 — complete tasks by number (or: done <title>)\n"
    "• postpone 2 to tomorrow 5pm\n"
    "• cancel 2 (asks to confirm)\n"
    "• add Buy milk tomorrow 6pm"
)


def digits(number: str) -> str:
    return "".join(ch for ch in (number or "") if ch.isdigit())


def fmt_due(ms: int | None, tz) -> str:
    return "" if ms is None else datetime.fromtimestamp(ms / 1000, tz).strftime("%a %d %b %H:%M")


def numbered(tasks: list[TaskRow], user: User, now: int, sep: str = "\n") -> str:
    tz = tz_of(user)
    parts = []
    for i, t in enumerate(tasks, 1):
        extra = f" — due {fmt_due(t.due_at, tz)}" if t.due_at else ""
        flag = " (overdue)" if is_overdue(t, now) else ""
        parts.append(f"{i}. {t.title}{extra}{flag}")
    return sep.join(parts)


def set_list(user: User, tasks: list[TaskRow]) -> None:
    user.last_list = json.dumps([t.id for t in tasks])


def log_message(db: Session, user: User | None, direction: str, content: str, status: str,
                wa_id: str | None = None, retries: int = 0) -> None:
    db.add(WhatsAppMessage(user_id=user.id if user else None, direction=direction, content=content[:4000],
                           status=status, wa_message_id=wa_id, retry_count=retries, timestamp=now_ms()))


def reply(db: Session, client: WhatsAppClient, user: User, to: str, text: str) -> None:
    try:
        wa_id = client.send_text(to, text)
        log_message(db, user, "OUT", text, "sent", wa_id)
    except WhatsAppError as e:
        log_message(db, user, "OUT", f"{text}\n[send failed: {e}]", "failed", None, 1)


def _resolve(db: Session, user: User, intent: Intent) -> tuple[list[TaskRow], str | None]:
    """Maps numbers / a title fragment to tasks. Returns (tasks, error_or_clarification)."""
    if intent.numbers:
        ids = json.loads(user.last_list or "[]")
        tasks, bad = [], []
        for n in intent.numbers:
            if 1 <= n <= len(ids):
                t = db.get(TaskRow, ids[n - 1])
                if t and t.user_id == user.id and t.deleted_at is None:
                    tasks.append(t)
                    continue
            bad.append(n)
        if bad:
            return [], f"I don't have a task number {', '.join(map(str, bad))}. Send 'list' to see the current numbers."
        return tasks, None
    frag = intent.text.lower().strip()
    pool = active_tasks(db, user)
    exact = [t for t in pool if t.title.lower() == frag]
    matches = exact or [t for t in pool if frag in t.title.lower()]
    if not matches:
        return [], f"I couldn't find a pending task matching \"{intent.text}\". Send 'list' to see your tasks."
    if len(matches) > 1:
        set_list(user, matches)
        return [], "More than one task matches:\n" + numbered(matches, user, now_ms()) + "\nReply with the number, e.g. 'done 1'."
    return matches, None


def _titles(tasks: list[TaskRow]) -> str:
    return ", ".join(f"\"{t.title}\"" for t in tasks)


def _execute(db: Session, user: User, kind: str, tasks: list[TaskRow], when_ms: int | None = None) -> str:
    tz = tz_of(user)
    if kind == "DONE":
        done, notes = [], []
        for t in tasks:
            if t.status == "COMPLETED":
                continue
            nxt = complete_task(db, user, t)
            done.append(t)
            if nxt:
                notes.append(f"Next \"{nxt.title}\": {fmt_due(nxt.due_at, tz)}")
        if not done:
            return "Those tasks are already completed."
        db.flush()  # autoflush is off; make the new status visible to the count below
        left = len(active_tasks(db, user))
        return "Done: " + _titles(done) + ("\n" + "\n".join(notes) if notes else "") + f"\n{left} pending."
    if kind == "CANCEL":
        for t in tasks:
            cancel_task(db, user, t)
        return "Cancelled: " + _titles(tasks)
    if kind == "POSTPONE":
        for t in tasks:
            postpone_task(db, user, t, when_ms)
        return f"Rescheduled {_titles(tasks)} to {fmt_due(when_ms, tz)}."
    return "Nothing to do."


def handle_incoming(db: Session, client: WhatsAppClient, wa_id: str, from_number: str, text: str, now: int | None = None) -> None:
    now = now or now_ms()
    if wa_id and db.scalar(select(WhatsAppMessage.id).where(WhatsAppMessage.wa_message_id == wa_id)):
        return  # duplicate webhook delivery: already processed
    number = digits(from_number)
    user = db.scalar(select(User).where(User.whatsapp_number == number))
    if user is None or not user.whatsapp_enabled:
        log_message(db, None, "IN", text, "received", wa_id)
        db.commit()
        try:  # polite, non-revealing answer
            client.send_text(number, "This number isn't linked to a Momentum account. Add it in the app: Settings > WhatsApp reminders.")
        except WhatsAppError:
            pass
        return

    user.last_inbound_at = now
    log_message(db, user, "IN", text, "received", wa_id)
    tz = tz_of(user)
    intent = parse(text, datetime.fromtimestamp(now / 1000, tz))

    # Pending confirmation (destructive actions)
    if user.pending_action:
        pending = json.loads(user.pending_action)
        user.pending_action = None
        if intent.kind == "YES":
            tasks = [t for t in (db.get(TaskRow, i) for i in pending["ids"]) if t and t.user_id == user.id and t.deleted_at is None]
            reply(db, client, user, number, _execute(db, user, pending["type"], tasks) if tasks else "Those tasks no longer exist.")
            db.commit()
            return
        if intent.kind == "NO":
            reply(db, client, user, number, "Okay, nothing was changed.")
            db.commit()
            return
        # any other message: confirmation dropped, process the new command normally
    elif intent.kind in ("YES", "NO"):
        reply(db, client, user, number, "There's nothing waiting for confirmation. Send 'help' for commands.")
        db.commit()
        return

    if intent.kind == "HELP" or intent.kind == "UNKNOWN":
        reply(db, client, user, number, HELP if intent.kind == "HELP" else "I didn't understand that.\n" + HELP)
    elif intent.kind == "LIST":
        tasks = active_tasks(db, user)[:15]
        set_list(user, tasks)
        reply(db, client, user, number, ("Your pending tasks:\n" + numbered(tasks, user, now)) if tasks else "No pending tasks. 🎉")
    elif intent.kind == "ADD":
        t = create_task(db, user, intent.text, intent.when_ms)
        due = f" — due {fmt_due(t.due_at, tz)}" if t.due_at else ""
        reply(db, client, user, number, f"Added: \"{t.title}\"{due}")
    else:  # DONE / CANCEL / POSTPONE
        tasks, msg = _resolve(db, user, intent)
        if msg:
            reply(db, client, user, number, msg)
        elif intent.kind == "POSTPONE" and intent.when_ms is None:
            reply(db, client, user, number, "When should I move it to? Example: 'postpone 2 to tomorrow 5pm'.")
        elif intent.kind == "CANCEL":
            user.pending_action = json.dumps({"type": "CANCEL", "ids": [t.id for t in tasks]})
            reply(db, client, user, number, f"Cancel {_titles(tasks)}? Reply YES to confirm or NO to keep.")
        else:
            reply(db, client, user, number, _execute(db, user, intent.kind, tasks, intent.when_ms))
    db.commit()
