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

## Planned (not built)
- Sync: `updatedAt` last-write-wins + `deleted_at` tombstones (see `database/schema.sql`).
- Secrets: WhatsApp/Firebase credentials live only on the backend (`.env`), never in the APK.
- WhatsApp: official Cloud API, approved templates outside the 24h window, idempotent webhook via
  unique `whatsapp_message_id`, confirmation before ambiguous/destructive changes, FCM/local fallback.
