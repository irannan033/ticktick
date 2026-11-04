import time

import jwt
from fastapi import Depends, HTTPException, Request
from sqlalchemy.orm import Session

from .config import settings
from .db import get_db
from .models import User


def verify_google_token(token: str) -> dict:
    """Validates a Google ID token (signature, expiry, audience = our web client id)."""
    if not settings.google_web_client_id:
        raise HTTPException(503, "Google sign-in is not configured on the server")
    from google.auth.transport import requests as g_requests
    from google.oauth2 import id_token

    try:
        return id_token.verify_oauth2_token(token, g_requests.Request(), settings.google_web_client_id)
    except ValueError as e:
        raise HTTPException(401, f"Invalid Google token: {e}")


def get_verifier():
    """Dependency so tests can swap the verifier without network access."""
    return verify_google_token


def create_token(user_id: str) -> str:
    if not settings.jwt_secret:
        raise HTTPException(503, "JWT_SECRET is not configured on the server")
    now = int(time.time())
    return jwt.encode({"sub": user_id, "iat": now, "exp": now + settings.jwt_days * 86400}, settings.jwt_secret, algorithm="HS256")


def current_user(request: Request, db: Session = Depends(get_db)) -> User:
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        raise HTTPException(401, "Missing bearer token")
    try:
        payload = jwt.decode(header[7:], settings.jwt_secret, algorithms=["HS256"])
    except jwt.PyJWTError:
        raise HTTPException(401, "Invalid or expired token")
    user = db.get(User, payload.get("sub"))
    if user is None:
        raise HTTPException(401, "Unknown user")
    return user
