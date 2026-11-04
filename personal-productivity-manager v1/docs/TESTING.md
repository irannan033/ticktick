# Testing

## Automated
`cd android-app && gradle testDebugUnitTest` (also runs in CI). Covers domain logic only.
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
