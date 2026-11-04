@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.domain.Period
import com.ppm.momentum.domain.PomodoroStatus
import com.ppm.momentum.domain.focusSeconds
import com.ppm.momentum.domain.isActive
import com.ppm.momentum.focus.FocusPhase
import com.ppm.momentum.focus.PomodoroService
import com.ppm.momentum.focus.TimerStatus
import com.ppm.momentum.focus.formatClock
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.MetricCard
import com.ppm.momentum.ui.components.SectionTitle
import com.ppm.momentum.util.formatDateTime
import com.ppm.momentum.util.toDate

private val PRESETS = listOf(15, 25, 30, 45, 60, 90, 120)

@Composable
fun FocusScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val ts by PomodoroService.state.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val today = now.toDate()

    var minutes by rememberSaveable { mutableStateOf(25) }
    var customText by rememberSaveable { mutableStateOf("") }
    var taskId by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    val idle = ts.status == TimerStatus.IDLE
    val shownSeconds = if (idle) minutes * 60 else ts.remainingSec
    val progress = if (idle) 1f else ts.remainingSec.toFloat() / ts.totalSec.coerceAtLeast(1)
    val linked = tasks.firstOrNull { it.id == (if (idle) taskId else ts.taskId) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Focus", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())

        if (ts.justFinished) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Session complete", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    val long = ts.completedFocus > 0 && ts.completedFocus % 4 == 0
                    Text(
                        if (long) "That was your 4th session — a long break is recommended." else "Take a short break, or start another session.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    TextButton({ PomodoroService.dismissFinished() }) { Text("Dismiss") }
                }
            }
        }

        Text(
            if (idle) "Ready" else ts.phase.label + if (ts.status == TimerStatus.PAUSED) " · paused" else "",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        )
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(240.dp), strokeWidth = 12.dp)
            Text(formatClock(shownSeconds), fontSize = 52.sp, style = MaterialTheme.typography.headlineMedium)
        }
        if (linked != null) Text("Task: " + linked.title, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

        when (ts.status) {
            TimerStatus.IDLE -> Button(
                { PomodoroService.start(ctx, minutes, FocusPhase.FOCUS, taskId) },
                Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Start focus · $minutes min") }
            TimerStatus.RUNNING -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({ PomodoroService.pause(ctx) }, Modifier.weight(1f).height(52.dp)) { Text("Pause") }
                OutlinedButton({ PomodoroService.reset(ctx) }, Modifier.weight(1f).height(52.dp)) { Text("Stop") }
            }
            TimerStatus.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({ PomodoroService.resume(ctx) }, Modifier.weight(1f).height(52.dp)) { Text("Resume") }
                OutlinedButton({ PomodoroService.reset(ctx) }, Modifier.weight(1f).height(52.dp)) { Text("Stop") }
            }
        }

        if (idle) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { m -> FilterChip(minutes == m, { minutes = m; customText = "" }, { Text("$m") }) }
            }
            OutlinedTextField(
                customText,
                { v ->
                    customText = v.filter { it.isDigit() }.take(3)
                    customText.toIntOrNull()?.let { if (it in 1..180) minutes = it }
                },
                label = { Text("Custom minutes (1–180)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton({ menu = true }, Modifier.fillMaxWidth()) { Text(linked?.title ?: "Link a task (optional)") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("No task") }, onClick = { taskId = null; menu = false })
                    tasks.filter { it.isActive }.forEach { t ->
                        DropdownMenuItem(text = { Text(t.title, maxLines = 1) }, onClick = { taskId = t.id; menu = false })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ PomodoroService.start(ctx, 5, FocusPhase.SHORT_BREAK, null) }, Modifier.weight(1f)) { Text("Short break 5m") }
                OutlinedButton({ PomodoroService.start(ctx, 15, FocusPhase.LONG_BREAK, null) }, Modifier.weight(1f)) { Text("Long break 15m") }
            }
        }

        Column(Modifier.fillMaxWidth()) {
            SectionTitle("Focus time")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Today", formatDuration(focusSeconds(sessions, Period.TODAY.range(today))), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                MetricCard("This week", formatDuration(focusSeconds(sessions, Period.WEEK.range(today))), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                MetricCard("Sessions today", sessions.count { it.startedAt.toDate() == today }.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
            SectionTitle("Recent sessions")
            if (sessions.isEmpty()) {
                Text("No sessions yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            sessions.take(5).forEach { s ->
                val title = tasks.firstOrNull { it.id == s.taskId }?.title ?: "No task"
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(formatDateTime(s.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        formatDuration(s.completedSeconds.toLong()) + if (s.status == PomodoroStatus.ABANDONED) " (stopped)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
