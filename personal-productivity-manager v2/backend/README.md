# Momentum backend (FastAPI + PostgreSQL)

Provides: Google sign-in verification + JWT, two-way sync (`POST /sync`), per-user settings, the WhatsApp Cloud API
webhook + scheduler, and a small web client at `/app/`.

```
POST /auth/google      exchange a Google ID token for an API token
GET  /me, PUT /me/settings
POST /sync             push local changes, pull server changes (last-write-wins, tombstones)
GET/POST /webhook/whatsapp   Meta webhook (signature-verified)
GET  /health, /config
```

## Run tests
```
pip install -r requirements-dev.txt
python -m pytest -q          # 40 tests, no network or accounts needed
```

## Run locally
```
cp ../.env.example ../.env   # fill JWT_SECRET (and the Google/WhatsApp values you have)
uvicorn app.main:app --reload
```
Production: `docker compose up -d --build` from the repo root (see ../docs/BACKEND_SETUP.md).

Notes: tables are created with `create_all` on startup. Before changing columns in a later release, adopt Alembic.
Run ONE api replica when `ENABLE_SCHEDULER=true`, otherwise scheduled WhatsApp messages are sent twice.
