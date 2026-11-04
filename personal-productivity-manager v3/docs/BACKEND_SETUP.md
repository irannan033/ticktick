# Deploying the backend

You need: a Linux server or PaaS with Docker, a domain, and HTTPS (the app refuses plain http).

1. Clone the repo on the server and `cp .env.example .env`; set `POSTGRES_PASSWORD`, `JWT_SECRET`, `GOOGLE_WEB_CLIENT_ID`.
2. `docker compose up -d --build`  (API on port 8000, PostgreSQL in a volume).
3. Put a reverse proxy with TLS in front (Caddy is simplest: `your.domain { reverse_proxy localhost:8000 }`).
4. Check `https://your.domain/health` returns `{"ok":true}`; the web client is at `https://your.domain/app/`.
5. In the Android app: Settings > Cloud sync > enter the server URL > Sign in with Google.

Back up the `pgdata` volume. To rotate secrets, change `.env` and restart; changing `JWT_SECRET` signs everyone out.
Free tiers that sleep (cold starts) will delay WhatsApp scheduling: use an always-on instance for that feature.
