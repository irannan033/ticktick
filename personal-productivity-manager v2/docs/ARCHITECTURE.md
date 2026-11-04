# Architecture

```
Android app (Compose UI) -> ViewModel -> Repository -> Room (SQLite)         [Phase 1]
                                                   \-> Sync engine -> Backend API -> PostgreSQL   [Phase 3]
WhatsApp user <-> WhatsApp Cloud API <-> Backend webhook/scheduler -> PostgreSQL                  [Phase 4]
```

## Android layers (`com.ppm.momentum`)
- `domain/` — pure Kotlin: enums, quadrant rules, overdue/timeliness, stats, filters. Unit-tested, no Android imports.
- `data/` — Room entities/DAOs, `TaskRepository` (all writes + activity log in transactions), DataStore prefs.
- `ui/` — Compose screens, `MainViewModel` (single VM for Phase 1), navigation, theme.

## Key decisions
- **UUID string primary keys** so offline-created rows from multiple devices merge without collisions.
- **OVERDUE is derived, never stored**: `status in (UPCOMING, IN_PROGRESS) AND dueAt < now`. It cannot go stale.
- **Importance/urgency are independent booleans**; priority is a separate field. Quadrant is computed.
- **`@Upsert` instead of `REPLACE`**: REPLACE deletes and re-inserts, which would cascade-delete a task's activity log.
- **Every status change is logged** with previous/new status, so reopening preserves history.
- **Statistics are computed from task rows** (`domain/Logic.kt: computeStats`); no stored counters.
- Room schema export is on (`app/schemas/`) to support explicit, backward-compatible migrations.

## Phase 2 additions
- **DB v2** adds `pomodoro_sessions` through an explicit migration (`MIGRATION_1_2`); no data is dropped.
- **Pomodoro**: `PomodoroService` is a foreground service (type `specialUse`). Remaining time is computed from
  `SystemClock.elapsedRealtime()` and a partial wake lock keeps the CPU awake, so the timer survives screen-off.
  State is held in memory: if Android kills the process mid-session, that session is lost.
- **Notifications**: `ReminderScheduler` sets one `setAndAllowWhileIdle` alarm per task reminder/due time (re-created on
  boot by `BootReceiver`); `ReminderWorker` (WorkManager, ~15 min) handles overdue repeats and the daily summary.
- **Export**: dependency-free writers (`ExportData.kt`: CSV with BOM, minimal XLSX via zip; `PdfReport.kt`: Android PdfDocument).
  Files go to the app cache and are shared through `FileProvider`.
- **Charts** are drawn with Compose Canvas (no chart library); series come from pure, unit-tested functions in `domain/Logic.kt`.

## Phase 3-5 additions
- **DB v3** (`MIGRATION_2_3`): `recurrence` and `deletedAt` on tasks, `updatedAt`/`deletedAt` on categories. Deletes are now
  tombstones so they can sync. Default lists use fixed ids so devices don't create duplicates.
- **Sync** (`sync/SyncManager`): push cursor on the device clock, pull cursor on the server clock (robust to a wrong phone clock);
  last-write-wins on `updatedAt`; sessions are immutable; activity history stays local. HTTPS only.
- **Backend** (`backend/app`): users, tasks, categories, sessions, activity, whatsapp_messages. Times are epoch ms.
  Task rules (complete, recurrence, postpone) live in `tasks_service.py` and mirror the app.
- **WhatsApp**: webhook verifies Meta's HMAC signature; the unique `wa_message_id` makes retries idempotent; the parser is
  rule-based and never guesses (ambiguous -> asks; cancel -> asks YES/NO); scheduled messages are templates; the scheduler is
  idempotent per day, skips stale sends after downtime and gives up after 3 failures.
- **Web client** (`backend/web`) is a static page that uses `/sync` like the app.

## Known limitations
- Sign-in token is kept in app-private DataStore (not hardware-backed).
- No refresh tokens / rate limiting on the API; add them before opening it to the public.
- Web client does not spawn the next occurrence of recurring tasks (the app and WhatsApp do).
- Server tables use `create_all`; adopt Alembic before changing columns.
