# Personal Productivity Manager ("Momentum")

TickTick-inspired personal productivity app: tasks, calendar, Eisenhower matrix, analytics,
and (later phases) Pomodoro, cloud sync and WhatsApp-based task monitoring.

> **Status: v0.3.0** — Android app (phases 1–2), plus the backend, sync, WhatsApp bot, web client, recurring tasks,
> quick-add and voice input (phases 3–5). The parts that need *your accounts* (Google sign-in, WhatsApp/Meta, hosting)
> are implemented and unit-tested but **not exercised against the real services**. See [docs/ROADMAP.md](docs/ROADMAP.md).

## What works (Phases 1–2)

| Area | Status |
|---|---|
| Tasks: create, edit, delete, duplicate, complete, cancel, reopen, reschedule, start | Done |
| Fields: title, description, priority, importance, urgency, list, start, due, reminder time, estimate | Done |
| Auto overdue detection; exact completion timestamp; on-time vs late | Done |
| Per-task activity history, postpone counter | Done |
| Eisenhower matrix (auto-placed, manually movable) | Done |
| Calendar: Day / 3-Day / Week / Month / Year, filters, add-from-date | Done (no drag-and-drop yet; use Reschedule) |
| Dashboard, All / Completed / Overdue / Cancelled lists, search | Done |
| Analytics from real data: summary cards, pie/bar/line charts, custom date range, focus time | Done (Phase 2) |
| Light / Dark / System theme, persisted | Done |
| Profile + WhatsApp preferences (stored locally) | Done (no messages sent yet) |
| Google sign-in + cloud sync (tasks, lists, sessions; last-write-wins; deletions sync) | Built; needs your Google client ID + hosted server |
| Recurring tasks (daily/weekly/monthly), natural-language quick add, voice input, "Add to calendar" hand-off | Done |
| Backend (FastAPI/PostgreSQL), web client at `/app/` | Built + 40 automated tests; deploy with docker compose |
| WhatsApp: morning list, follow-ups, evening report, replies (`done 1`, `postpone`, `add`…) | Built + tested with a fake WhatsApp; needs your Meta setup |
| Pomodoro: presets 15–120 min + custom, pause/resume/stop, task link, breaks, background-safe (foreground service), session history | Done (Phase 2) |
| Notifications: reminder time, due time, repeated overdue alerts, daily summary, finished-session alert | Done (Phase 2) |
| Export: CSV, Excel (.xlsx), PDF report with stats + charts, shared via Android share sheet | Done (Phase 2) |

## Build the APK

Requirements: Android Studio Koala+ (or JDK 17 + Android SDK 34 + Gradle 8.7).

**Option A — Android Studio:** open the `android-app/` folder, let it sync, press Run. Or *Build > Build APK(s)*.

**Option B — GitHub Actions (no local setup):** push to GitHub; the *Android CI* workflow runs unit tests and
builds `app-debug.apk`, available under the run's *Artifacts*. Copy it to your phone and install
(allow "install unknown apps").

**Option C — command line:** `cd android-app && gradle assembleDebug`
(the Gradle wrapper jar is not committed; Android Studio generates it, or run `gradle wrapper --gradle-version 8.7`).

Min Android version: 8.0 (API 26).

## Repository layout

```
android-app/   Kotlin + Jetpack Compose + Room app (MVVM)
backend/       FastAPI + PostgreSQL service, WhatsApp bot, web client (/app)
database/      PostgreSQL schema (generated from the backend models)
docs/          Architecture, roadmap, setup notes
.github/       CI workflow
.env.example   Server-side secrets template (never commit .env)
```

## Testing

Android: `cd android-app && gradle testDebugUnitTest` (24 tests: domain logic, charts, export, quick-add, recurrence).
Backend: `cd backend && pip install -r requirements-dev.txt && python -m pytest -q` (40 tests).
See [docs/TESTING.md](docs/TESTING.md) for the manual test checklist.

## Setting up the cloud features
1. [docs/BACKEND_SETUP.md](docs/BACKEND_SETUP.md) — deploy the server.
2. [docs/GOOGLE_SIGNIN.md](docs/GOOGLE_SIGNIN.md) — create the Google client IDs and build the app with them.
3. [docs/WHATSAPP_SETUP.md](docs/WHATSAPP_SETUP.md) — Meta app, number, approved templates, webhook.
