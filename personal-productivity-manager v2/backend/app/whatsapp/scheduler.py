"""Business-initiated WhatsApp messages (morning list, follow-ups, evening report).
These MUST be pre-approved templates (Meta rule outside the 24h window). run_due_jobs is idempotent per day
and safe to call every minute."""
import asyncio
import logging
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import settings
from ..db import SessionLocal
from ..models import TaskRow, User
from ..tasks_service import active_tasks, is_overdue, now_ms, tz_of
from .client import WhatsAppClient, WhatsAppError, get_client
from .service import log_message, numbered, set_list

log = logging.getLogger("scheduler")
LATE_GRACE_MIN = 240  # don't send "good morning" 5 hours late after downtime
MAX_FAILS = 3


def _minutes(hhmm: str) -> int:
    h, m = hhmm.split(":")
    return int(h) * 60 + int(m)


def _template(db: Session, client: WhatsAppClient, user: User, name: str, params: list[str], content: str) -> bool:
    try:
        wa_id = client.send_template(user.whatsapp_number, name, params)
        log_message(db, user, "OUT", content, "sent", wa_id)
        user.wa_fail_count = 0
        return True
    except WhatsAppError as e:
        user.wa_fail_count += 1
        log_message(db, user, "OUT", f"{content}\n[send failed: {e}]", "failed", None, user.wa_fail_count)
        log.warning("WhatsApp send failed for %s (%s/%s): %s", user.id, user.wa_fail_count, MAX_FAILS, e)
        return False


def _inline(tasks: list[TaskRow], user: User, now: int) -> str:
    # Template parameters cannot contain newlines, so entries are joined with a separator.
    return numbered(tasks[:10], user, now, sep="  |  ")[:900]


def run_due_jobs(db: Session, client: WhatsAppClient, now: int | None = None) -> int:
    now = now or now_ms()
    sent = 0
    users = db.scalars(select(User).where(User.whatsapp_enabled.is_(True), User.whatsapp_number.is_not(None))).all()
    for u in users:
        local = datetime.fromtimestamp(now / 1000, tz_of(u))
        today, minute = local.strftime("%Y-%m-%d"), local.hour * 60 + local.minute
        tasks = active_tasks(db, u)
        todays = [t for t in tasks if is_overdue(t, now) or (t.due_at and datetime.fromtimestamp(t.due_at / 1000, tz_of(u)).strftime("%Y-%m-%d") == today)]
        name = u.name or "there"

        # morning list
        m = _minutes(u.morning_time)
        if u.last_morning_date != today and minute >= m:
            if minute - m > LATE_GRACE_MIN:
                u.last_morning_date, u.last_followup_at = today, now  # too late: skip, and wait a full interval before any follow-up
            else:
                body = _inline(todays, u, now) if todays else "No tasks scheduled today"
                if _template(db, client, u, settings.whatsapp_template_morning, [name, body], f"Morning: {body}") or u.wa_fail_count >= MAX_FAILS:
                    set_list(u, todays[:10])
                    u.last_morning_date, u.last_followup_at = today, now
                    u.wa_fail_count = 0
                    sent += 1

        # follow-ups while tasks are still pending
        e = _minutes(u.evening_time)
        if (u.last_morning_date == today and minute < e and todays
                and now - u.last_followup_at >= u.followup_hours * 3_600_000):
            body = _inline(todays, u, now)
            if _template(db, client, u, settings.whatsapp_template_followup, [name, body], f"Follow-up: {body}") or u.wa_fail_count >= MAX_FAILS:
                set_list(u, todays[:10])
                u.last_followup_at = now
                u.wa_fail_count = 0
                sent += 1

        # evening report
        if u.last_evening_date != today and minute >= e:
            if minute - e > LATE_GRACE_MIN:
                u.last_evening_date = today
            else:
                all_today = db.scalars(select(TaskRow).where(TaskRow.user_id == u.id, TaskRow.deleted_at.is_(None))).all()
                done = sum(1 for t in all_today if t.status == "COMPLETED" and t.completed_at
                           and datetime.fromtimestamp(t.completed_at / 1000, tz_of(u)).strftime("%Y-%m-%d") == today)
                pending = sum(1 for t in todays if not is_overdue(t, now))
                overdue = sum(1 for t in tasks if is_overdue(t, now))
                body = f"{done} completed, {pending} pending, {overdue} overdue"
                if _template(db, client, u, settings.whatsapp_template_evening, [name, body], f"Evening report: {body}") or u.wa_fail_count >= MAX_FAILS:
                    u.last_evening_date = today
                    u.wa_fail_count = 0
                    sent += 1
    db.commit()
    return sent


async def scheduler_loop(interval_s: int = 60) -> None:
    client = get_client()
    while True:
        try:
            def tick():
                with SessionLocal() as db:
                    run_due_jobs(db, client)
            await asyncio.to_thread(tick)
        except Exception:  # never let the loop die
            log.exception("scheduler tick failed")
        await asyncio.sleep(interval_s)
