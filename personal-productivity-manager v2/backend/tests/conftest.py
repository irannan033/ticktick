import hashlib
import hmac
import json

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app import auth
from app.config import settings
from app.db import Base, get_db
from app.main import app
from app.whatsapp.client import WhatsAppClient, WhatsAppError, get_client


class FakeWA(WhatsAppClient):
    def __init__(self):
        self.sent = []  # (kind, to, payload)
        self.fail = False
        self._n = 0

    def _next(self):
        self._n += 1
        return f"wamid.{self._n}"

    def send_text(self, to, body):
        if self.fail:
            raise WhatsAppError("boom")
        self.sent.append(("text", to, body))
        return self._next()

    def send_template(self, to, name, params):
        if self.fail:
            raise WhatsAppError("boom")
        self.sent.append(("template", to, (name, params)))
        return self._next()


@pytest.fixture()
def db_session():
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    with Session() as s:
        yield s


@pytest.fixture()
def wa():
    return FakeWA()


@pytest.fixture()
def client(db_session, wa, monkeypatch):
    monkeypatch.setattr(settings, "jwt_secret", "test-secret")
    monkeypatch.setattr(settings, "whatsapp_app_secret", "app-secret")
    monkeypatch.setattr(settings, "whatsapp_verify_token", "verify-me")

    def fake_db():
        yield db_session

    # "token-<sub>" verifies as Google subject <sub>
    app.dependency_overrides[get_db] = fake_db
    app.dependency_overrides[get_client] = lambda: wa
    app.dependency_overrides[auth.get_verifier] = lambda: (lambda tok: {"sub": tok.removeprefix("token-"), "email": f"{tok}@x.com", "name": "Tester"})
    yield TestClient(app)
    app.dependency_overrides.clear()


def login(client, sub="alice"):
    r = client.post("/auth/google", json={"idToken": f"token-{sub}"})
    assert r.status_code == 200, r.text
    return {"Authorization": "Bearer " + r.json()["token"]}


def sign(body: dict) -> tuple[bytes, dict]:
    raw = json.dumps(body).encode()
    sig = "sha256=" + hmac.new(b"app-secret", raw, hashlib.sha256).hexdigest()
    return raw, {"X-Hub-Signature-256": sig, "Content-Type": "application/json"}


def wa_payload(msg_id, sender, text):
    return {"entry": [{"changes": [{"value": {"messages": [{"id": msg_id, "from": sender, "type": "text", "text": {"body": text}}]}}]}]}
