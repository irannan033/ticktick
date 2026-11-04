"""Task rules shared by sync, WhatsApp and the scheduler."""
import calendar
import time
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from sqlalchemy import select
from sqlalchemy.orm import Session

from .models import ActivityRow, TaskRow, User, new_id


def now_ms() -> int:
    return int(time.time() * 1000)


def tz_of(user: User) -> ZoneInfo:
    try:
        return ZoneInfo(user.timezone or "UTC")
    except Exception:
        return ZoneInfo("UTC")


def is_active(t: TaskRow) -> bool:
    return t.deleted_at is None and t.status in ("UPCOMING", "IN_PROGRESS")


def is_overdue(t: TaskRow, now: int) -> bool:
    return is_active(t) and t.due_at is not None and t.due_at < now


def active_tasks(db: Session, user: User) -> list[TaskRow]:
    rows = db.scalars(select(TaskRow).where(TaskRow.user_id == user.id, TaskRow.deleted_at.is_(None),
                                            TaskRow.status.in_(("UPCOMING", "IN_PROGRESS")))).all()
    return sorted(rows, key=lambda t: (t.due_at is None, t.due_at or 0, t.created_at))


def log(db: Session, user: User, task_id: str, action: str, note: str | None = None) -> None:
    db.add(ActivityRow(user_id=user.id, task_id=task_id, action=action, note=note, timestamp=now_ms()))


def next_due(due_ms: int, recurrence: str, tz: ZoneInfo) -> int:
    """Next occurrence keeping the local wall-clock time. Month-end clamps (Jan 31 -> Feb 28)."""
    d = datetime.fromtimestamp(due_ms / 1000, tz)
    if recurrence == "DAILY":
        n = d + timedelta(days=1)
    elif recurrence == "WEEKLY":
        n = d + timedelta(days=7)
    elif recurrence == "MONTHLY":
        y, m = (d.year + (d.month // 12), d.month % 12 + 1)
        n = d.replace(year=y, month=m, day=min(d.day, calendar.monthrange(y, m)[1]))
    else:
        raise ValueError(recurrence)
    return int(n.timestamp() * 1000)


def complete_task(db: Session, user: User, t: TaskRow, now: int | None = None) -> TaskRow | None:
    """Marks done; returns the next occurrence when the task recurs."""
    now = now or now_ms()
    t.status, t.completed_at, t.updated_at, t.server_updated_at = "COMPLETED", now, now, now
    on_time = t.due_at is None or now <= t.due_at
    log(db, user, t.id, "Completed", "Completed on time" if on_time else "Completed late")
    nxt = None
    if t.recurrence and t.due_at:
        delta = next_due(t.due_at, t.recurrence, tz_of(user)) - t.due_at
        nxt = TaskRow(
            id=new_id(), user_id=user.id, title=t.title, description=t.description, priority=t.priority,
            important=t.important, urgent=t.urgent, category_id=t.category_id,
            start_at=t.start_at + delta if t.start_at else None, due_at=t.due_at + delta,
            reminder_at=t.reminder_at + delta if t.reminder_at else None,
            estimated_minutes=t.estimated_minutes, status="UPCOMING", recurrence=t.recurrence,
            created_at=now, updated_at=now, server_updated_at=now,
        )
        db.add(nxt)
        log(db, user, nxt.id, "Created", "Next occurrence of a recurring task")
    return nxt


def cancel_task(db: Session, user: User, t: TaskRow) -> None:
    now = now_ms()
    t.status, t.completed_at, t.updated_at, t.server_updated_at = "CANCELLED", None, now, now
    log(db, user, t.id, "Cancelled", "Cancelled via WhatsApp")


def postpone_task(db: Session, user: User, t: TaskRow, new_due: int) -> None:
    now = now_ms()
    if t.due_at is not None and new_due > t.due_at:
        t.postpone_count += 1
    if t.reminder_at and t.due_at:
        t.reminder_at += new_due - t.due_at
    t.due_at, t.updated_at, t.server_updated_at = new_due, now, now
    log(db, user, t.id, "Rescheduled", "Rescheduled via WhatsApp")


def create_task(db: Session, user: User, title: str, due_at: int | None) -> TaskRow:
    now = now_ms()
    t = TaskRow(id=new_id(), user_id=user.id, title=title[:500], due_at=due_at, created_at=now, updated_at=now, server_updated_at=now)
    db.add(t)
    log(db, user, t.id, "Created", "Created via WhatsApp")
    return t
