from datetime import datetime
from zoneinfo import ZoneInfo

from app.models import User, TaskRow
from app.whatsapp.scheduler import run_due_jobs

TZ = ZoneInfo("Asia/Dhaka")


def ms(y, mo, d, h, mi=0):
    return int(datetime(y, mo, d, h, mi, tzinfo=TZ).timestamp() * 1000)


def make_user(db, **kw):
    u = User(google_sub="s", name="Ann", whatsapp_number="8801712345678", whatsapp_enabled=True, timezone="Asia/Dhaka",
             morning_time="08:00", evening_time="23:00", followup_hours=2, **kw)
    db.add(u)
    db.flush()  # assigns u.id
    db.add(TaskRow(id="t1", user_id=u.id, title="Report", due_at=ms(2026, 11, 4, 15), created_at=1, updated_at=1))
    db.add(TaskRow(id="t2", user_id=u.id, title="Gym", due_at=ms(2026, 11, 4, 18), created_at=1, updated_at=1))
    db.commit()
    return u


def templates(wa):
    return [(p[0], p[1]) for k, _, p in wa.sent if k == "template"]


def test_morning_sent_once_per_day(db_session, wa):
    make_user(db_session)
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 7, 59)) == 0
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 8, 1)) == 1
    name, params = templates(wa)[0]
    assert name == "morning_tasks" and params[0] == "Ann" and "1. Report" in params[1] and "\n" not in params[1]
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 8, 30)) == 0
    assert len(templates(wa)) == 1


def test_followup_respects_interval(db_session, wa):
    make_user(db_session)
    run_due_jobs(db_session, wa, ms(2026, 11, 4, 8, 1))
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 9, 30)) == 0  # < 2h since morning
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 10, 2)) == 1
    assert templates(wa)[-1][0] == "task_followup"
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 11, 0)) == 0


def test_no_followup_when_nothing_pending(db_session, wa):
    make_user(db_session)
    for t in db_session.query(TaskRow).all():
        t.status = "COMPLETED"
    db_session.commit()
    run_due_jobs(db_session, wa, ms(2026, 11, 4, 8, 1))
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 12, 0)) == 0


def test_evening_report_counts(db_session, wa):
    make_user(db_session)
    t = db_session.get(TaskRow, "t1")
    t.status, t.completed_at = "COMPLETED", ms(2026, 11, 4, 14)
    db_session.commit()
    run_due_jobs(db_session, wa, ms(2026, 11, 4, 8, 1))
    run_due_jobs(db_session, wa, ms(2026, 11, 4, 23, 1))
    name, params = templates(wa)[-1]
    assert name == "daily_report" and params[1] == "1 completed, 0 pending, 1 overdue"


def test_late_morning_is_skipped_not_sent(db_session, wa):
    make_user(db_session)
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 14, 0)) == 0  # server was down; 6h late
    assert templates(wa) == []


def test_failures_retry_then_give_up_without_spamming(db_session, wa):
    u = make_user(db_session)
    wa.fail = True
    for minute in (1, 2, 3, 4, 5):
        run_due_jobs(db_session, wa, ms(2026, 11, 4, 8, minute))
    db_session.refresh(u)
    assert u.last_morning_date == "2026-11-04"  # gave up after 3 failures: no endless retry
    wa.fail = False
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 8, 10)) == 0


def test_recovers_after_one_failure(db_session, wa):
    u = make_user(db_session)
    wa.fail = True
    run_due_jobs(db_session, wa, ms(2026, 11, 4, 8, 1))
    wa.fail = False
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 8, 2)) == 1
    db_session.refresh(u)
    assert u.wa_fail_count == 0


def test_disabled_users_get_nothing(db_session, wa):
    u = make_user(db_session)
    u.whatsapp_enabled = False
    db_session.commit()
    assert run_due_jobs(db_session, wa, ms(2026, 11, 4, 8, 1)) == 0
