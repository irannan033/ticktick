import hashlib
import hmac
import json

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import PlainTextResponse
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import settings
from ..db import get_db
from ..models import WhatsAppMessage
from .client import WhatsAppClient, get_client
from .service import handle_incoming

router = APIRouter()


@router.get("/webhook/whatsapp")
def verify(mode: str = Query("", alias="hub.mode"), token: str = Query("", alias="hub.verify_token"),
           challenge: str = Query("", alias="hub.challenge")):
    if mode == "subscribe" and settings.whatsapp_verify_token and hmac.compare_digest(token, settings.whatsapp_verify_token):
        return PlainTextResponse(challenge)
    raise HTTPException(403, "Verification failed")


def _signature_ok(secret: str, body: bytes, header: str) -> bool:
    expected = "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, header or "")


@router.post("/webhook/whatsapp")
async def receive(request: Request, db: Session = Depends(get_db), client: WhatsAppClient = Depends(get_client)):
    if not settings.whatsapp_app_secret:
        raise HTTPException(503, "WHATSAPP_APP_SECRET is not configured")
    raw = await request.body()
    if not _signature_ok(settings.whatsapp_app_secret, raw, request.headers.get("X-Hub-Signature-256", "")):
        raise HTTPException(403, "Bad signature")
    try:
        payload = json.loads(raw)
    except ValueError:
        raise HTTPException(400, "Invalid JSON")

    for entry in payload.get("entry", []):
        for change in entry.get("changes", []):
            value = change.get("value", {})
            for msg in value.get("messages", []):
                if msg.get("type") == "text":
                    handle_incoming(db, client, msg.get("id", ""), msg.get("from", ""), msg.get("text", {}).get("body", ""))
                elif msg.get("from"):
                    try:
                        client.send_text(msg["from"], "I can only read text messages. Send 'help' for commands.")
                    except Exception:
                        pass
            for st in value.get("statuses", []):  # delivery receipts
                row = db.scalar(select(WhatsAppMessage).where(WhatsAppMessage.wa_message_id == st.get("id")))
                if row and st.get("status") in ("sent", "delivered", "read", "failed"):
                    row.status = st["status"]
    db.commit()
    return {"ok": True}  # always 200 once authenticated so Meta does not retry endlessly
