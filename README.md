# Personal Productivity Manager ("Momentum")

TickTick-inspired personal productivity app: tasks, calendar, Eisenhower matrix, analytics,
and (later phases) Pomodoro, cloud sync and WhatsApp-based task monitoring.

> **Status: Phase 1 (v0.1.0)** — core Android app. Phases 2–5 are not built yet. See [docs/ROADMAP.md](docs/ROADMAP.md).

## What works in Phase 1

| Area | Status |
|---|---|
| Tasks: create, edit, delete, duplicate, complete, cancel, reopen, reschedule, start | Done |
| Fields: title, description, priority, importance, urgency, list, start, due, reminder time, estimate | Done |
| Auto overdue detection; exact completion timestamp; on-time vs late | Done |
| Per-task activity history, postpone counter | Done |
| Eisenhower matrix (auto-placed, manually movable) | Done |
| Calendar: Day / 3-Day / Week / Month / Year, filters, add-from-date | Done (no drag-and-drop yet; use Reschedule) |
| Dashboard, All / Completed / Overdue / Cancelled lists, search | Done |
| Summary analytics from real data (counts, rates, priority split) | Done (charts in Phase 2) |
| Light / Dark / System theme, persisted | Done |
| Profile + WhatsApp preferences (stored locally) | Done (no messages sent yet) |
| Google Sign-In | **Not yet** — needs your Firebase project (Phase 3) |
| Pomodoro, notifications, export, charts | **Phase 2** |
| Backend, sync, WhatsApp | **Phase 3 / 4** |

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
backend/       FastAPI service (Phase 3/4) - placeholder
database/      PostgreSQL target schema for cloud sync
docs/          Architecture, roadmap, setup notes
.github/       CI workflow
.env.example   Server-side secrets template (never commit .env)
```

## Testing

`cd android-app && gradle testDebugUnitTest` — unit tests cover quadrant placement, overdue detection,
on-time/late logic, statistics and filters (`app/src/test/.../LogicTest.kt`).
See [docs/TESTING.md](docs/TESTING.md) for the manual test checklist.
