from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from . import auth
from .config import settings
from .db import get_db
from .models import CategoryRow, SessionRow, TaskRow, User
from .schemas import (AuthResponse, CategoryDTO, GoogleLogin, SessionDTO, SettingsUpdate, SyncRequest, SyncResponse,
                      TaskDTO, UserDTO)
from .tasks_service import now_ms

router = APIRouter()


@router.get("/health")
def health():
    return {"ok": True}


@router.get("/config")
def public_config():
    """Non-secret values the web client needs. The Google WEB client id is public by design."""
    return {"googleClientId": settings.google_web_client_id}


@router.post("/auth/google", response_model=AuthResponse)
def google_login(body: GoogleLogin, db: Session = Depends(get_db), verifier=Depends(auth.get_verifier)):
    claims = verifier(body.id_token)
    sub = claims.get("sub")
    if not sub:
        raise HTTPException(401, "Token has no subject")
    user = db.scalar(select(User).where(User.google_sub == sub))
    if user is None:
        user = User(google_sub=sub, name=claims.get("name") or "", email=claims.get("email"), created_at=now_ms())
        db.add(user)
        db.commit()
    return AuthResponse(token=auth.create_token(user.id), user=UserDTO.model_validate(user))


@router.get("/me", response_model=UserDTO)
def me(user: User = Depends(auth.current_user)):
    return user


@router.put("/me/settings", response_model=UserDTO)
def update_settings(body: SettingsUpdate, db: Session = Depends(get_db), user: User = Depends(auth.current_user)):
    data = body.model_dump(exclude_unset=True)
    if "timezone" in data:
        try:
            ZoneInfo(data["timezone"])
        except Exception:
            raise HTTPException(422, "Unknown timezone")
    if "whatsapp_number" in data:
        digits = "".join(ch for ch in (data["whatsapp_number"] or "") if ch.isdigit())
        if digits and not (8 <= len(digits) <= 15):
            raise HTTPException(422, "WhatsApp number must be 8-15 digits in international format")
        other = db.scalar(select(User).where(User.whatsapp_number == digits, User.id != user.id)) if digits else None
        if other:
            raise HTTPException(409, "That WhatsApp number is already linked to another account")
        data["whatsapp_number"] = digits or None
    for k, v in data.items():
        setattr(user, k, v)
    db.commit()
    return user


# ---- sync ---------------------------------------------------------------------------------------

def _upsert(db: Session, model, user: User, dto, now: int, fields: list[str], lww_field: str | None):
    row = db.get(model, dto.id)
    if row is not None and row.user_id != user.id:
        return  # id belongs to another account: ignore
    if row is not None and lww_field is None:
        return  # immutable rows (sessions): first write wins, no re-bump
    if row is not None and getattr(dto, lww_field) <= getattr(row, lww_field):
        return  # server copy is newer or equal: last-write-wins
    if row is None:
        row = model(id=dto.id, user_id=user.id)
        db.add(row)
    for f in fields:
        setattr(row, f, getattr(dto, f))
    row.server_updated_at = now


TASK_FIELDS = ["title", "description", "priority", "important", "urgent", "category_id", "start_at", "due_at",
               "reminder_at", "estimated_minutes", "status", "completed_at", "postpone_count", "recurrence",
               "created_at", "updated_at", "deleted_at"]


@router.post("/sync", response_model=SyncResponse)
def sync(body: SyncRequest, db: Session = Depends(get_db), user: User = Depends(auth.current_user)):
    """Two-way sync: apply client changes (last-write-wins on updatedAt, deletions are tombstones),
    then return everything the server changed since the client's cursor."""
    now = now_ms()
    for t in body.tasks:
        _upsert(db, TaskRow, user, t, now, TASK_FIELDS, "updated_at")
    for c in body.categories:
        _upsert(db, CategoryRow, user, c, now, ["name", "color_argb", "updated_at", "deleted_at"], "updated_at")
    for s in body.sessions:  # immutable once written
        _upsert(db, SessionRow, user, s, now, ["task_id", "planned_minutes", "completed_seconds", "started_at", "ended_at", "status"], None)
    db.commit()

    def changed(model):
        return db.scalars(select(model).where(model.user_id == user.id, model.server_updated_at >= body.since)).all()

    return SyncResponse(
        server_time=now,
        tasks=[TaskDTO.model_validate(r) for r in changed(TaskRow)],
        categories=[CategoryDTO.model_validate(r) for r in changed(CategoryRow)],
        sessions=[SessionDTO.model_validate(r) for r in changed(SessionRow)],
    )
