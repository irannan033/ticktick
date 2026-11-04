@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ppm.momentum.data.CategoryEntity
import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.domain.Priority
import com.ppm.momentum.domain.TaskFilter
import com.ppm.momentum.domain.TaskStatus
import com.ppm.momentum.domain.Timeliness
import com.ppm.momentum.domain.effectiveStatus
import com.ppm.momentum.domain.timeliness
import com.ppm.momentum.ui.theme.StatusAmber
import com.ppm.momentum.ui.theme.StatusGreen
import com.ppm.momentum.ui.theme.StatusRed
import com.ppm.momentum.ui.theme.color
import com.ppm.momentum.util.formatDateTime
import com.ppm.momentum.util.formatTime
import com.ppm.momentum.util.friendlyDue
import com.ppm.momentum.util.toDateTime
import com.ppm.momentum.util.toMillis
import java.time.LocalDateTime

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            text, color = color, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun TaskCard(
    task: TaskEntity,
    category: CategoryEntity?,
    now: Long,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = task.effectiveStatus(now)
    val done = task.status == TaskStatus.COMPLETED
    val cancelled = task.status == TaskStatus.CANCELLED
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = done,
                onCheckedChange = { onToggleDone() },
                enabled = !cancelled,
                colors = CheckboxDefaults.colors(checkedColor = StatusGreen, uncheckedColor = task.priority.color()),
            )
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done || cancelled) TextDecoration.LineThrough else null,
                    color = if (done || cancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val due = task.dueAt
                    val completedAt = task.completedAt
                    if (done && completedAt != null) {
                        Text("Done " + friendlyDue(completedAt, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (due != null) {
                        Text(
                            friendlyDue(due, now), style = MaterialTheme.typography.bodySmall,
                            color = if (status == TaskStatus.OVERDUE) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (category != null) {
                        Box(Modifier.size(8.dp).background(Color(category.colorArgb), CircleShape))
                        Text(category.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            when (task.timeliness()) {
                Timeliness.ON_TIME -> StatusChip("On time", StatusGreen)
                Timeliness.LATE -> StatusChip("Late", StatusAmber)
                Timeliness.NOT_APPLICABLE -> StatusChip(status.label, status.color(primary))
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = modifier.clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = actions,
    )
}

@Composable
fun FilterBar(
    filter: TaskFilter,
    categories: List<CategoryEntity>,
    onChange: (TaskFilter) -> Unit,
    showStatus: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter.priority == null, { onChange(filter.copy(priority = null)) }, { Text("Any priority") })
            Priority.values().filter { it != Priority.NONE }.forEach { p ->
                FilterChip(filter.priority == p, { onChange(filter.copy(priority = if (filter.priority == p) null else p)) }, { Text(p.label) })
            }
        }
        if (showStatus) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter.status == null, { onChange(filter.copy(status = null)) }, { Text("Any status") })
                TaskStatus.values().forEach { s ->
                    FilterChip(filter.status == s, { onChange(filter.copy(status = if (filter.status == s) null else s)) }, { Text(s.label) })
                }
            }
        }
        if (categories.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter.categoryId == null, { onChange(filter.copy(categoryId = null)) }, { Text("Any list") })
                categories.forEach { c ->
                    FilterChip(filter.categoryId == c.id, { onChange(filter.copy(categoryId = if (filter.categoryId == c.id) null else c.id)) }, { Text(c.name) })
                }
            }
        }
    }
}

fun pickDateTime(context: Context, initial: Long?, onPicked: (Long) -> Unit) {
    val c = (initial ?: System.currentTimeMillis()).toDateTime()
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, min ->
            onPicked(LocalDateTime.of(y, m + 1, d, h, min).toMillis())
        }, c.hour, c.minute, true).show()
    }, c.year, c.monthValue - 1, c.dayOfMonth).show()
}

@Composable
private fun FieldRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit, onClear: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
            .clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        if (onClear != null) {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, "Clear") }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun DateTimeField(label: String, value: Long?, defaultMillis: Long?, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    FieldRow(
        icon = Icons.Filled.Event, label = label,
        value = if (value != null) formatDateTime(value) else "Not set",
        onClick = { pickDateTime(context, value ?: defaultMillis) { onChange(it) } },
        onClear = if (value != null) ({ onChange(null) }) else null,
    )
}

/** Time-of-day field storing "HH:mm". */
@Composable
fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val parts = value.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    FieldRow(
        icon = Icons.Filled.Schedule, label = label, value = value,
        onClick = {
            TimePickerDialog(context, { _, hh, mm -> onChange("%02d:%02d".format(hh, mm)) }, h, m, true).show()
        },
        onClear = null,
    )
}
