package com.ppm.momentum.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ppm.momentum.MomentumApp
import com.ppm.momentum.domain.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_TASK) ?: return
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = (context.applicationContext as MomentumApp).container
                val t = c.repository.getTask(id)
                val p = c.prefs.prefs.first()
                if (t != null && t.isActive && p.notificationsEnabled) {
                    if (kind == ReminderScheduler.KIND_DUE && p.remindAtDue) {
                        Notifier.show(context, id.hashCode() + 2, Notifier.CH_REMINDERS, "Due now", t.title)
                    } else if (kind == ReminderScheduler.KIND_REMINDER && p.remindAtReminder) {
                        Notifier.show(context, id.hashCode() + 1, Notifier.CH_REMINDERS, "Reminder", t.title)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms are cleared on reboot / app update; re-create them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as MomentumApp).container.repository.resyncAll()
            } finally {
                pending.finish()
            }
        }
    }
}
