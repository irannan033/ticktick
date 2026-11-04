# Testing

## Automated
`cd android-app && gradle testDebugUnitTest` (also runs in CI). Covers domain logic, chart series, CSV and export rows (24 tests, incl. quick-add and recurrence). Backend: `cd backend && python -m pytest -q` (40 tests: sync, auth isolation, WhatsApp, scheduler).
UI and database behaviour have no automated tests yet (Phase 2: Room in-memory DAO tests, Compose UI tests).

## Manual checklist (Phase 1)
1. First launch -> Welcome -> setup (name required; bad WhatsApp number rejected) -> Home.
2. Add task with past due time -> appears under Overdue; Home overdue count increments.
3. Complete that task -> detail shows "Completed late" and a completion timestamp; history shows the entry.
4. Complete a task before its deadline -> "Completed on time".
5. Reopen a completed task -> status Upcoming, history keeps earlier entries.
6. Reschedule to a later time -> "Postponed 1 time(s)" in details.
7. Toggle Important/Urgent in details -> task moves between Matrix quadrants; also via the swap icon in Matrix.
8. Calendar: switch Day/3 days/Week/Month/Year; tap a day, add a task from it; apply priority/status/list filters.
9. Settings -> theme Light/Dark/System; force-close app and reopen -> theme persists, tasks persist.
10. Delete a list in Settings -> its tasks keep existing with no list.
11. Analytics: numbers change as you complete/cancel tasks; switch period chips.

## Manual checklist (Phase 2)
12. Upgrade test: install v0.1.0 with some tasks, then install v0.2.0 over it. Tasks must still be there. If the app crashes on
    launch after upgrading, uninstall and reinstall (and report it; it means the DB migration needs a fix).
13. Allow notifications when prompted. Create a task due in 3 minutes with reminder "At due time" -> alert appears around then.
14. Leave a task overdue; within ~15-30 min you get "Overdue task". Notification settings control the repeat interval.
15. Focus: start 1-minute custom session, lock the screen, wait -> "Focus session complete" arrives; a session row is added.
16. Focus: Pause/Resume/Stop from the app and from the notification buttons. Stop after >=1 min records a "(stopped)" session.
17. Link a task to a session -> task detail lists the session and focused minutes.
18. Analytics: pies/bars/lines change after completing tasks; Custom range works; focus-time cards match the Focus tab.
19. Settings > Export report: generate CSV, Excel and PDF; open each from the share sheet (Excel opens with 15 columns; PDF has
    stats, charts and a task table).
20. Reboot the phone, then check a future reminder still fires (alarms are re-created on boot).

## Manual checklist (v0.3.0)
21. Upgrade from v0.2.0: tasks/lists/sessions survive (DB v2 -> v3). Crash on launch after upgrading = migration bug; report it.
22. Home quick add: type `Call Sam tomorrow 5pm !high every week` -> preview line shows due/priority/repeat; Add creates it.
23. Tap the mic, speak a task -> text appears in the box (needs a speech service on the phone).
24. Complete a weekly recurring task -> a new copy appears one week later; the old one is Completed.
25. Task detail -> calendar icon opens your calendar app with the event pre-filled.
26. (Needs server + Google IDs) Settings > Cloud sync: sign in, Sync now. Create a task on the web client `/app/`, sync the app -> it appears; delete on one side -> disappears on the other.
27. (Needs Meta setup) Save your WhatsApp number in the app; at the morning time you receive the template; reply `list`, `done 1`, `cancel 2` then `yes`.
