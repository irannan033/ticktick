# Roadmap

| Phase | Scope | State |
|---|---|---|
| 1 | Core app: tasks, priority, matrix, calendar, details, status tracking, local DB, themes, summary analytics | **Built (v0.1.0)** |
| 2 | Pomodoro (foreground service), local notifications (WorkManager/AlarmManager), charts, Excel/CSV/PDF export, custom date range | **Built (v0.2.0)** — pending device testing |
| 3 | FastAPI backend, PostgreSQL, Firebase Auth / Google Sign-In, sync + offline conflict handling | **Built (v0.3.0)** — needs your accounts to go live |
| 4 | WhatsApp Cloud API: morning list, webhook replies, NLP intent parsing, follow-ups, end-of-day report | **Built (v0.3.0)** — needs your accounts to go live |
| 5 | Desktop/web, recurring tasks, natural-language task creation, Google Calendar, voice | **Partly built**: recurring tasks, quick add, voice, calendar hand-off, web client. Not built: native desktop app (use the web client/PWA), two-way Google Calendar API sync, LLM-based parsing |

Also tracked: calendar drag-and-drop rescheduling, deep-link from notification to the task, exact alarms (needs the SCHEDULE_EXACT_ALARM permission), restoring a running Pomodoro after the process is killed.
