@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.domain.Priority
import com.ppm.momentum.domain.quadrantOf
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.BackTopBar
import com.ppm.momentum.ui.components.DateTimeField
import com.ppm.momentum.ui.components.SectionTitle
import com.ppm.momentum.ui.theme.color
import com.ppm.momentum.util.defaultDueFor
import com.ppm.momentum.util.toDate
import java.util.UUID

private val REMINDER_OPTIONS = listOf<Pair<String, Int?>>(
    "None" to null, "At due time" to 0, "30 min before" to 30, "1 hour before" to 60, "1 day before" to 1440,
)

@Composable
fun TaskEditorScreen(vm: MainViewModel, taskId: String?, presetDate: Long?, onClose: () -> Unit) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    var loaded by remember { mutableStateOf(taskId == null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.NONE) }
    var important by remember { mutableStateOf(false) }
    var urgent by remember { mutableStateOf(false) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var dueAt by remember { mutableStateOf(presetDate) }
    var startAt by remember { mutableStateOf<Long?>(null) }
    var reminderOffset by remember { mutableStateOf<Int?>(null) }
    var estimate by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        if (taskId != null) {
            vm.getTask(taskId)?.let { t ->
                title = t.title; description = t.description; priority = t.priority
                important = t.important; urgent = t.urgent; categoryId = t.categoryId
                dueAt = t.dueAt; startAt = t.startAt
                estimate = t.estimatedMinutes?.toString() ?: ""
                val due = t.dueAt; val rem = t.reminderAt
                if (due != null && rem != null) {
                    val off = ((due - rem) / 60_000L).toInt()
                    reminderOffset = REMINDER_OPTIONS.map { it.second }.firstOrNull { it == off }
                }
            }
            loaded = true
        }
    }

    Scaffold(topBar = { BackTopBar(if (taskId == null) "New task" else "Edit task", onClose) }) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                title, { title = it; titleError = false }, label = { Text("Title") }, singleLine = true,
                isError = titleError, supportingText = { if (titleError) Text("A title is required") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                description, { description = it }, label = { Text("Description") },
                minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Priority")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.values().forEach { p ->
                    FilterChip(priority == p, { priority = p }, { Text(p.label) })
                }
            }

            SectionTitle("Eisenhower matrix")
            ToggleRow("Important", important) { important = it }
            ToggleRow("Urgent", urgent) { urgent = it }
            Text(
                "Quadrant: " + quadrantOf(important, urgent).title,
                style = MaterialTheme.typography.bodySmall, color = quadrantOf(important, urgent).color(),
            )

            SectionTitle("List")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(categoryId == null, { categoryId = null }, { Text("None") })
                cats.forEach { c -> FilterChip(categoryId == c.id, { categoryId = c.id }, { Text(c.name) }) }
            }

            SectionTitle("Schedule")
            DateTimeField("Start (optional)", startAt, defaultDueFor(now.toDate(), now)) { startAt = it }
            DateTimeField("Due date & time", dueAt, defaultDueFor(now.toDate(), now)) { dueAt = it }

            SectionTitle("Reminder")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMINDER_OPTIONS.forEach { (label, offset) ->
                    FilterChip(reminderOffset == offset, { reminderOffset = offset }, { Text(label) }, enabled = dueAt != null || offset == null)
                }
            }
            if (dueAt == null) {
                Text("Set a due date to use reminders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                estimate, { v -> estimate = v.filter { it.isDigit() }.take(4) },
                label = { Text("Estimated duration (minutes)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                    } else {
                        val due = dueAt
                        val offset = reminderOffset
                        val t = TaskEntity(
                            id = taskId ?: UUID.randomUUID().toString(),
                            title = title.trim(), description = description.trim(),
                            priority = priority, important = important, urgent = urgent,
                            categoryId = categoryId, startAt = startAt, dueAt = due,
                            reminderAt = if (due != null && offset != null) due - offset * 60_000L else null,
                            estimatedMinutes = estimate.toIntOrNull()?.takeIf { it > 0 },
                            createdAt = now, updatedAt = now,
                        )
                        vm.save(t) { onClose() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (taskId == null) "Create task" else "Save changes") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}
