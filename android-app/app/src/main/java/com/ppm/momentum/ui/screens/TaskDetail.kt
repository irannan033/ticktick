@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.data.ActivityEntity
import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.domain.TaskStatus
import com.ppm.momentum.domain.Timeliness
import com.ppm.momentum.domain.effectiveStatus
import com.ppm.momentum.domain.quadrant
import com.ppm.momentum.domain.timeliness
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.BackTopBar
import com.ppm.momentum.ui.components.SectionTitle
import com.ppm.momentum.ui.components.StatusChip
import com.ppm.momentum.ui.components.pickDateTime
import com.ppm.momentum.ui.theme.StatusAmber
import com.ppm.momentum.ui.theme.StatusGreen
import com.ppm.momentum.ui.theme.color
import com.ppm.momentum.util.formatDateTime
import com.ppm.momentum.util.formatMinutes

@Composable
fun TaskDetailScreen(
    vm: MainViewModel,
    id: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenTask: (String) -> Unit,
) {
    val task by remember(id) { vm.task(id) }.collectAsStateWithLifecycle(initialValue = null)
    val activity by remember(id) { vm.activity(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val cats by vm.categories.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    val t = task
    Scaffold(
        topBar = {
            BackTopBar("Task", onBack) {
                if (t != null) {
                    IconButton(onClick = { onEdit(t.id) }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { vm.duplicate(t.id) { newId -> if (newId != null) onOpenTask(newId) } }) {
                        Icon(Icons.Filled.ContentCopy, "Duplicate")
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        },
    ) { padding ->
        if (t == null) return@Scaffold
        val status = t.effectiveStatus(now)
        val category = cats.firstOrNull { it.id == t.categoryId }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(t.title, style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(status.label, status.color(MaterialTheme.colorScheme.primary))
                StatusChip(t.priority.label + " priority", t.priority.color())
                when (t.timeliness()) {
                    Timeliness.ON_TIME -> StatusChip("Completed on time", StatusGreen)
                    Timeliness.LATE -> StatusChip("Completed late", StatusAmber)
                    Timeliness.NOT_APPLICABLE -> {}
                }
            }

            if (t.description.isNotBlank()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) { Text(t.description, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow("List", category?.name ?: "None")
                    InfoRow("Start", t.startAt?.let { formatDateTime(it) } ?: "Not set")
                    InfoRow("Deadline", t.dueAt?.let { formatDateTime(it) } ?: "Not set")
                    InfoRow("Reminder", t.reminderAt?.let { formatDateTime(it) } ?: "None")
                    InfoRow("Estimated", t.estimatedMinutes?.let { formatMinutes(it) } ?: "Not set")
                    InfoRow("Completed at", t.completedAt?.let { formatDateTime(it) } ?: "—")
                    InfoRow("Postponed", if (t.postponeCount == 0) "Never" else "${t.postponeCount} time(s)")
                    InfoRow("Created", formatDateTime(t.createdAt))
                    InfoRow("Updated", formatDateTime(t.updatedAt))
                }
            }

            SectionTitle("Eisenhower matrix")
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Important", Modifier.weight(1f))
                        Switch(t.important, { vm.setMatrix(t.id, it, t.urgent) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Urgent", Modifier.weight(1f))
                        Switch(t.urgent, { vm.setMatrix(t.id, t.important, it) })
                    }
                    Text(
                        "Quadrant: ${t.quadrant.title} (${t.quadrant.subtitle})",
                        style = MaterialTheme.typography.bodySmall, color = t.quadrant.color(),
                    )
                }
            }

            ActionButtons(t, vm, onReschedule = {
                pickDateTime(context, t.dueAt ?: now) { vm.reschedule(t.id, it) }
            })

            SectionTitle("Pomodoro sessions")
            Text(
                "Focus sessions linked to this task will appear here once the Pomodoro timer ships in Phase 2.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Activity history")
            if (activity.isEmpty()) {
                Text("No activity yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        activity.forEach { ActivityRow(it) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && t != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete task?") },
            text = { Text("This permanently removes the task and its history.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(t.id); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ActionButtons(t: TaskEntity, vm: MainViewModel, onReschedule: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (t.status) {
            TaskStatus.COMPLETED, TaskStatus.CANCELLED ->
                Button({ vm.reopen(t.id) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Replay, null); Spacer(Modifier.width(8.dp)); Text("Reopen task")
                }
            else -> {
                Button({ vm.complete(t.id) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp)); Text("Mark complete")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.status == TaskStatus.UPCOMING) {
                        OutlinedButton({ vm.start(t.id) }, Modifier.weight(1f)) {
                            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Start")
                        }
                    }
                    OutlinedButton(onReschedule, Modifier.weight(1f)) {
                        Icon(Icons.Filled.Event, null); Spacer(Modifier.width(4.dp)); Text("Reschedule")
                    }
                    OutlinedButton({ vm.cancel(t.id) }, Modifier.weight(1f)) {
                        Icon(Icons.Filled.Cancel, null); Spacer(Modifier.width(4.dp)); Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActivityRow(a: ActivityEntity) {
    Column {
        Row {
            Text(a.action, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(formatDateTime(a.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val ps = a.previousStatus
        val ns = a.newStatus
        if (ps != null && ns != null && ps != ns) {
            Text("${ps.label} → ${ns.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!a.note.isNullOrBlank()) {
            Text(a.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
