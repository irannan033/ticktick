package com.ppm.momentum.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ppm.momentum.MomentumApp
import com.ppm.momentum.domain.Period
import com.ppm.momentum.domain.computeStats
import com.ppm.momentum.domain.isOverdue
import com.ppm.momentum.util.toDate
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * Runs about every 15 minutes (WorkManager's minimum). Handles:
 *  - repeated reminders for overdue tasks
 *  - the daily summary once the configured time has passed
 * Exact-time task reminders are handled separately by ReminderScheduler alarms.
 */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as MomentumApp).container
        val p = c.prefs.prefs.first()
        if (!p.notificationsEnabled) return Result.success()
        val tasks = c.repository.allTasks.first()
        val now = System.currentTimeMillis()

        if (p.overdueRepeat) {
            val store = applicationContext.getSharedPreferences("overdue_notified", Context.MODE_PRIVATE)
            val ed = store.edit()
            val gap = p.overdueIntervalHours * 3_600_000L
            tasks.filter { it.isOverdue(now) }.forEach { t ->
                if (now - store.getLong(t.id, 0L) >= gap) {
                    Notifier.show(applicationContext, t.id.hashCode(), Notifier.CH_REMINDERS, "Overdue task", t.title)
                    ed.putLong(t.id, now)
                }
            }
            ed.apply()
        }

        if (p.dailySummary) {
            val today = now.toDate()
            val at = runCatching { LocalTime.parse(p.summaryTime) }.getOrDefault(LocalTime.of(21, 0))
            if (p.lastSummaryDate != today.toString() && !LocalTime.now().isBefore(at)) {
                val s = computeStats(tasks, now, Period.TODAY.range(today))
                Notifier.show(
                    applicationContext, 7001, Notifier.CH_SUMMARY, "Today's summary",
                    "${s.completed} completed · ${s.pending} pending · ${s.overdue} overdue",
                )
                c.prefs.setLastSummaryDate(today.toString())
            }
        }
        return Result.success()
    }
}
