import json
from datetime import datetime
from zoneinfo import ZoneInfo

from sqlalchemy import select

from app.models import TaskRow, User, WhatsAppMessage
from app.tasks_service import now_ms
from tests.conftest import login, sign, wa_payload

NUMBER = "8801712345678"


def setup_user(client, db, tz="UTC"):
    h = login(client)
    client.put("/me/settings", json={"whatsappNumber": NUMBER, "whatsappEnabled": True, "timezone": tz}, headers=h)
    return h


def add_tasks(client, h, *titles, **kw):
    now = now_ms()
    client.post("/sync", json={"tasks": [
        {"id": f"t{i}", "title": t, "createdAt": now + i, "updatedAt": now + i, "dueAt": now + 3_600_000 * (i + 1), **kw}
        for i, t in enumerate(titles)]}, headers=h)


def say(client, msg_id, text, sender=NUMBER):
    raw, headers = sign(wa_payload(msg_id, sender, text))
    r = client.post("/webhook/whatsapp", content=raw, headers=headers)
    assert r.status_code == 200, r.text


def last_reply(wa):
    return [p for k, _, p in wa.sent if k == "text"][-1]


def test_webhook_verification(client):
    ok = client.get("/webhook/whatsapp", params={"hub.mode": "subscribe", "hub.verify_token": "verify-me", "hub.challenge": "123"})
    assert ok.status_code == 200 and ok.text == "123"
    assert client.get("/webhook/whatsapp", params={"hub.mode": "subscribe", "hub.verify_token": "nope", "hub.challenge": "1"}).status_code == 403


def test_bad_or_missing_signature_rejected(client, wa):
    raw = json.dumps(wa_payload("m1", NUMBER, "list")).encode()
    assert client.post("/webhook/whatsapp", content=raw, headers={"X-Hub-Signature-256": "sha256=bad"}).status_code == 403
    assert client.post("/webhook/whatsapp", content=raw).status_code == 403
    assert wa.sent == []


def test_list_then_done_by_number(client, db_session, wa):
    h = setup_user(client, db_session)
    add_tasks(client, h, "Write report", "Call Sam", "Buy milk")
    say(client, "m1", "list")
    assert "1. Write report" in last_reply(wa) and "3. Buy milk" in last_reply(wa)
    say(client, "m2", "done 1,3")
    assert "Write report" in last_reply(wa) and "Buy milk" in last_reply(wa) and "1 pending" in last_reply(wa)
    rows = {t.id: t for t in db_session.scalars(select(TaskRow)).all()}
    assert rows["t0"].status == "COMPLETED" and rows["t2"].status == "COMPLETED" and rows["t1"].status == "UPCOMING"
    assert rows["t0"].completed_at is not None


def test_done_is_pulled_by_the_app_on_next_sync(client, db_session, wa):
    h = setup_user(client, db_session)
    add_tasks(client, h, "Write report")
    say(client, "m1", "list")
    say(client, "m2", "done 1")
    pulled = client.post("/sync", json={"since": 0}, headers=h).json()["tasks"]
    assert pulled[0]["status"] == "COMPLETED"


def test_duplicate_webhook_delivery_is_processed_once(client, db_session, wa):
    h = setup_user(client, db_session)
    add_tasks(client, h, "A")
    say(client, "m1", "list")
    n = len(wa.sent)
    say(client, "m1", "list")  # Meta retry of the same message id
    assert len(wa.sent) == n
    assert db_session.scalars(select(WhatsAppMessage).where(WhatsAppMessage.wa_message_id == "m1")).all().__len__() == 1


def test_cancel_requires_confirmation(client, db_session, wa):
    h = setup_user(client, db_session)
    add_tasks(client, h, "A", "B")
    say(client, "m1", "list")
    say(client, "m2", "cancel 2")
    assert "YES" in last_reply(wa)
    assert db_session.get(TaskRow, "t1").status == "UPCOMING"  # nothing changed yet
    say(client, "m3", "no")
    assert db_session.get(TaskRow, "t1").status == "UPCOMING"
    say(client, "m4", "cancel 2")
    say(client, "m5", "yes")
    assert db_session.get(TaskRow, "t1").status == "CANCELLED"


def test_ambiguous_title_asks_instead_of_guessing(client, db_session, wa):
    h = setup_user(client, db_session)
    add_tasks(client, h, "Email Sam", "Email Priya")
    say(client, "m1", "done email")
    assert "More than one" in last_reply(wa)
    assert all(t.status == "UPCOMING" for t in db_session.scalars(select(TaskRow)).all())
    say(client, "m2", "done 2")
    assert db_session.get(TaskRow, "t1").status == "COMPLETED"
    assert db_session.get(TaskRow, "t0").status == "UPCOMING"


def test_done_by_unique_title_fragment_and_unknown_number(client, db_session, wa):
    h = setup_user(client, db_session)
    add_tasks(client, h, "Write quarterly report", "Call Sam")
    say(client, "m1", "done quarterly")
    assert db_session.get(TaskRow, "t0").status == "COMPLETED"
    say(client, "m2", "list")
    say(client, "m3", "done 9")
    assert "don't have a task number 9" in last_reply(wa)


def test_postpone_and_add(client, db_session, wa):
    h = setup_user(client, db_session, tz="Asia/Dhaka")
    add_tasks(client, h, "Report")
    say(client, "m1", "list")
    say(client, "m2", "postpone 1 to tomorrow 5pm")
    t = db_session.get(TaskRow, "t0")
    d = datetime.fromtimestamp(t.due_at / 1000, ZoneInfo("Asia/Dhaka"))
    assert d.hour == 17 and t.postpone_count == 1
    say(client, "m3", "add Buy milk tomorrow 6pm")
    new = [x for x in db_session.scalars(select(TaskRow)).all() if x.title == "Buy milk"][0]
    assert datetime.fromtimestamp(new.due_at / 1000, ZoneInfo("Asia/Dhaka")).hour == 18
    say(client, "m4", "postpone 1")
    assert "When" in last_reply(wa)


def test_recurring_task_spawns_next_occurrence(client, db_session, wa):
    h = setup_user(client, db_session)
    add_tasks(client, h, "Daily standup", recurrence="DAILY")
    say(client, "m1", "list")
    say(client, "m2", "done 1")
    rows = db_session.scalars(select(TaskRow)).all()
    assert len(rows) == 2
    old = db_session.get(TaskRow, "t0")
    nxt = [r for r in rows if r.id != "t0"][0]
    assert nxt.status == "UPCOMING" and nxt.due_at - old.due_at == 86_400_000 and nxt.recurrence == "DAILY"


def test_unlinked_number_gets_generic_reply_and_no_data(client, db_session, wa):
    h = setup_user(client, db_session)
    add_tasks(client, h, "Secret task")
    say(client, "m1", "list", sender="8809999999999")
    assert "isn't linked" in last_reply(wa) and "Secret" not in last_reply(wa)


def test_disabled_user_is_ignored(client, db_session, wa):
    h = setup_user(client, db_session)
    client.put("/me/settings", json={"whatsappEnabled": False}, headers=h)
    say(client, "m1", "list")
    assert "isn't linked" in last_reply(wa)


def test_delivery_status_updates_log(client, db_session, wa):
    h = setup_user(client, db_session)
    say(client, "m1", "help")
    out = db_session.scalars(select(WhatsAppMessage).where(WhatsAppMessage.direction == "OUT")).all()[0]
    body = {"entry": [{"changes": [{"value": {"statuses": [{"id": out.wa_message_id, "status": "delivered"}]}}]}]}
    raw, headers = sign(body)
    client.post("/webhook/whatsapp", content=raw, headers=headers)
    db_session.refresh(out)
    assert out.status == "delivered"


def test_non_text_message_gets_hint(client, wa):
    body = {"entry": [{"changes": [{"value": {"messages": [{"id": "x", "from": NUMBER, "type": "image"}]}}]}]}
    raw, headers = sign(body)
    assert client.post("/webhook/whatsapp", content=raw, headers=headers).status_code == 200
    assert "only read text" in last_reply(wa)
