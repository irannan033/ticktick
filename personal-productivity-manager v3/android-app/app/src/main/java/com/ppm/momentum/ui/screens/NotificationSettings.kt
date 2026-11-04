@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.notify.Notifier
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.BackTopBar
import com.ppm.momentum.ui.components.TimeField

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
fun NotificationSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(true) }
    var atReminder by remember { mutableStateOf(true) }
    var atDue by remember { mutableStateOf(true) }
    var overdue by remember { mutableStateOf(true) }
    var overdueHours by remember { mutableStateOf(4) }
    var summary by remember { mutableStateOf(true) }
    var summaryTime by remember { mutableStateOf("21:00") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(prefs != null) {
        prefs?.let {
            enabled = it.notificationsEnabled; atReminder = it.remindAtReminder; atDue = it.remindAtDue
            overdue = it.overdueRepeat; overdueHours = it.overdueIntervalHours
            summary = it.dailySummary; summaryTime = it.summaryTime
        }
    }

    Scaffold(topBar = { BackTopBar("Notifications", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!Notifier.canPost(ctx)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Notifications are blocked for this app. Enable them in Android Settings > Apps > Momentum > Notifications.",
                        Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            SwitchRow("Enable notifications", enabled) { enabled = it; saved = false }
            SwitchRow("At the task's reminder time", atReminder, enabled) { atReminder = it; saved = false }
            SwitchRow("When a task is due", atDue, enabled) { atDue = it; saved = false }
            SwitchRow("Repeat reminders for overdue tasks", overdue, enabled) { overdue = it; saved = false }
            if (overdue) {
                Text("Repeat every", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4, 8).forEach { h -> FilterChip(overdueHours == h, { overdueHours = h; saved = false }, { Text("${h}h") }) }
                }
            }
            SwitchRow("Daily productivity summary", summary, enabled) { summary = it; saved = false }
            if (summary) TimeField("Send summary at", summaryTime) { summaryTime = it; saved = false }
            Text(
                "Due-time and reminder alerts use Android alarms and can arrive a few minutes late in battery saver. " +
                    "Overdue repeats and the summary are checked about every 15 minutes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { vm.saveNotifications(enabled, atReminder, atDue, overdue, overdueHours, summary, summaryTime); saved = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saved) "Saved" else "Save") }
        }
    }
}
