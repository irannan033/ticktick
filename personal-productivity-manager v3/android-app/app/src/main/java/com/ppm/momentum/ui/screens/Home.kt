package com.ppm.momentum.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.data.UserPrefs
import com.ppm.momentum.domain.Period
import com.ppm.momentum.domain.TaskStatus
import com.ppm.momentum.domain.computeStats
import com.ppm.momentum.domain.isActive
import com.ppm.momentum.domain.isOverdue
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.EmptyState
import com.ppm.momentum.ui.components.MetricCard
import com.ppm.momentum.ui.components.SectionTitle
import com.ppm.momentum.ui.components.TaskCard
import com.ppm.momentum.ui.theme.StatusAmber
import com.ppm.momentum.ui.theme.StatusBlue
import com.ppm.momentum.ui.theme.StatusGreen
import com.ppm.momentum.ui.theme.StatusRed
import com.ppm.momentum.util.endMillis
import com.ppm.momentum.util.format
import com.ppm.momentum.util.toDate
import com.ppm.momentum.util.toDateTime

@Composable
fun HomeScreen(
    vm: MainViewModel,
    prefs: UserPrefs,
    onOpenTask: (String) -> Unit,
    onAdd: () -> Unit,
    onTab: (String) -> Unit,
    onOpenList: (ListKind) -> Unit,
    onSettings: () -> Unit,
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val today = now.toDate()

    val todays = tasks.filter { it.status != TaskStatus.CANCELLED && it.dueAt?.toDate() == today }
    val doneToday = todays.count { it.status == TaskStatus.COMPLETED }
    val pendingToday = todays.count { it.isActive && !it.isOverdue(now) }
    val overdue = tasks.filter { it.isOverdue(now) }
    val upcoming = tasks.count { it.isActive && (it.dueAt ?: 0L) >= today.endMillis() }
    val progress = if (todays.isEmpty()) 0f else doneToday.toFloat() / todays.size
    val weekStats = computeStats(tasks, now, Period.WEEK.range(today))

    val hour = now.toDateTime().hour
    val greeting = when { hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
    val listItems = (overdue + todays.filter { !it.isOverdue(now) }).distinctBy { it.id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (prefs.name.isBlank()) greeting else "$greeting, ${prefs.name}",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(today.format("EEEE, d MMMM yyyy"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                }
            }
            item { QuickAddBar(vm) }
            item {
                Card(
                    shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(72.dp), strokeWidth = 7.dp)
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.padding(start = 18.dp)) {
                            Text("Today's progress", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                if (todays.isEmpty()) "Nothing due today" else "$doneToday of ${todays.size} tasks done",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Today", todays.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricCard("Completed", doneToday.toString(), StatusGreen, Modifier.weight(1f), onClick = { onOpenList(ListKind.COMPLETED) })
                    MetricCard("Pending", pendingToday.toString(), StatusAmber, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Overdue", overdue.size.toString(), StatusRed, Modifier.weight(1f), onClick = { onOpenList(ListKind.OVERDUE) })
                    MetricCard("Upcoming", upcoming.toString(), StatusBlue, Modifier.weight(1f), onClick = { onOpenList(ListKind.ALL) })
                    MetricCard("Week rate", "${weekStats.completionRate.toInt()}%", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Shortcut(Icons.Filled.Timer, "Focus", Modifier.weight(1f)) { onTab("focus") }
                    Shortcut(Icons.Filled.CalendarMonth, "Calendar", Modifier.weight(1f)) { onTab("calendar") }
                    Shortcut(Icons.Filled.GridView, "Matrix", Modifier.weight(1f)) { onTab("matrix") }
                    Shortcut(Icons.Filled.CheckCircle, "All tasks", Modifier.weight(1f)) { onOpenList(ListKind.ALL) }
                }
            }
            item { SectionTitle("Today & overdue") }
            if (listItems.isEmpty()) {
                item { EmptyState(Icons.Filled.CheckCircle, "You are all clear", "Tap Add task to plan something.") }
            } else {
                items(listItems, key = { it.id }) { t ->
                    TaskCard(
                        t, cats.firstOrNull { it.id == t.categoryId }, now,
                        onClick = { onOpenTask(t.id) }, onToggleDone = { vm.toggleDone(t) },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add task") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = modifier.clip(shape).clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
