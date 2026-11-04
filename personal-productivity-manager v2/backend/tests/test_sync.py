from tests.conftest import login


def task(id_, title="T", updated=1000, **kw):
    return {"id": id_, "title": title, "createdAt": 1, "updatedAt": updated, **kw}


def test_requires_auth(client):
    assert client.post("/sync", json={}).status_code == 401
    assert client.post("/sync", json={}, headers={"Authorization": "Bearer junk"}).status_code == 401


def test_push_then_pull_roundtrip(client):
    h = login(client)
    r = client.post("/sync", json={"since": 0, "tasks": [task("a", "Write", dueAt=5000, priority="HIGH")]}, headers=h)
    assert r.status_code == 200
    got = {t["id"]: t for t in r.json()["tasks"]}
    assert got["a"]["title"] == "Write" and got["a"]["dueAt"] == 5000 and got["a"]["priority"] == "HIGH"
    # a second device pulling from scratch sees it
    r2 = client.post("/sync", json={"since": 0}, headers=h)
    assert [t["id"] for t in r2.json()["tasks"]] == ["a"]


def test_cursor_only_returns_new_changes(client):
    h = login(client)
    first = client.post("/sync", json={"tasks": [task("a")]}, headers=h).json()
    import time; time.sleep(0.01)
    client.post("/sync", json={"tasks": [task("b")]}, headers=h)
    r = client.post("/sync", json={"since": first["serverTime"] + 1}, headers=h).json()
    assert [t["id"] for t in r["tasks"]] == ["b"]


def test_last_write_wins(client):
    h = login(client)
    client.post("/sync", json={"tasks": [task("a", "new", updated=2000)]}, headers=h)
    r = client.post("/sync", json={"tasks": [task("a", "stale", updated=1500)]}, headers=h).json()
    assert [t for t in r["tasks"] if t["id"] == "a"][0]["title"] == "new"
    r = client.post("/sync", json={"tasks": [task("a", "newer", updated=3000)]}, headers=h).json()
    assert [t for t in r["tasks"] if t["id"] == "a"][0]["title"] == "newer"


def test_delete_is_a_tombstone_that_propagates(client):
    h = login(client)
    client.post("/sync", json={"tasks": [task("a")]}, headers=h)
    r = client.post("/sync", json={"tasks": [task("a", updated=2000, deletedAt=2000)]}, headers=h).json()
    assert r["tasks"][0]["deletedAt"] == 2000


def test_users_are_isolated(client):
    a, b = login(client, "alice"), login(client, "bob")
    client.post("/sync", json={"tasks": [task("a", "alice-secret")]}, headers=a)
    assert client.post("/sync", json={"since": 0}, headers=b).json()["tasks"] == []
    # bob cannot overwrite alice's row by reusing its id
    client.post("/sync", json={"tasks": [task("a", "hijack", updated=99999)]}, headers=b)
    r = client.post("/sync", json={"since": 0}, headers=a).json()
    assert r["tasks"][0]["title"] == "alice-secret"


def test_validation_rejects_bad_enums_and_empty_titles(client):
    h = login(client)
    assert client.post("/sync", json={"tasks": [task("a", priority="URGENT")]}, headers=h).status_code == 422
    assert client.post("/sync", json={"tasks": [task("a", status="OVERDUE")]}, headers=h).status_code == 422
    assert client.post("/sync", json={"tasks": [task("a", title="")]}, headers=h).status_code == 422


def test_sessions_and_categories_sync_and_sessions_are_immutable(client):
    h = login(client)
    s = {"id": "s1", "taskId": "a", "plannedMinutes": 25, "completedSeconds": 1500, "startedAt": 10, "endedAt": 20, "status": "COMPLETED"}
    c = {"id": "c1", "name": "Work", "colorArgb": 123, "updatedAt": 5}
    r = client.post("/sync", json={"sessions": [s], "categories": [c]}, headers=h).json()
    assert r["sessions"][0]["completedSeconds"] == 1500 and r["categories"][0]["name"] == "Work"
    s2 = {**s, "completedSeconds": 1}
    r = client.post("/sync", json={"sessions": [s2]}, headers=h).json()
    assert [x for x in r["sessions"] if x["id"] == "s1"][0]["completedSeconds"] == 1500


def test_settings_validation_and_unique_number(client):
    a, b = login(client, "alice"), login(client, "bob")
    ok = client.put("/me/settings", json={"whatsappNumber": "+880 1712-345678", "whatsappEnabled": True, "timezone": "Asia/Dhaka"}, headers=a)
    assert ok.status_code == 200 and ok.json()["whatsappNumber"] == "8801712345678"
    assert client.put("/me/settings", json={"whatsappNumber": "8801712345678"}, headers=b).status_code == 409
    assert client.put("/me/settings", json={"whatsappNumber": "12"}, headers=b).status_code == 422
    assert client.put("/me/settings", json={"timezone": "Mars/Base"}, headers=b).status_code == 422
    assert client.put("/me/settings", json={"morningTime": "25:00"}, headers=b).status_code == 422
