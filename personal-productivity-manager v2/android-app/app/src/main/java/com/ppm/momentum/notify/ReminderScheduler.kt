package com.ppm.momentum.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ppm.momentum.data.PrefsRepository
import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.domain.isActive
import kotlinx.coroutines.flow.first

/**
 * Schedules one alarm per (task, kind). Uses setAndAllowWhileIdle, which needs no special permission
 * but may fire a few minutes late in Doze. Alarms do not survive reboot, so BootReceiver re-syncs them.
 */
class ReminderScheduler(private val context: Context, private val prefs: PrefsRepository) {
    private val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(taskId: String, kind: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("momentum://task/$taskId/$kind"))
            .putExtra(EXTRA_TASK, taskId).putExtra(EXTRA_KIND, kind)
        return PendingIntent.getBroadcast(
            context, (taskId + kind).hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun cancel(taskId: String) {
        am.cancel(pending(taskId, KIND_REMINDER))
        am.cancel(pending(taskId, KIND_DUE))
    }

    suspend fun sync(t: TaskEntity) {
        cancel(t.id)
        val p = prefs.prefs.first()
        if (!p.notificationsEnabled || !t.isActive) return
        val now = System.currentTimeMillis()
        val reminder = t.reminderAt
        val due = t.dueAt
        if (p.remindAtReminder && reminder != null && reminder > now) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder, pending(t.id, KIND_REMINDER))
        }
        if (p.remindAtDue && due != null && due > now) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending(t.id, KIND_DUE))
        }
    }

    companion object {
        const val EXTRA_TASK = "taskId"
        const val EXTRA_KIND = "kind"
        const val KIND_REMINDER = "reminder"
        const val KIND_DUE = "due"
    }
}
