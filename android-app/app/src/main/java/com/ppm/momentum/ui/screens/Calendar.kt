@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.data.CategoryEntity
import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.domain.TaskFilter
import com.ppm.momentum.domain.effectiveStatus
import com.ppm.momentum.domain.scheduleDate
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.FilterBar
import com.ppm.momentum.ui.theme.color
import com.ppm.momentum.util.defaultDueFor
import com.ppm.momentum.util.format
import com.ppm.momentum.util.toDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import com.ppm.momentum.ui.components.TaskCard

private enum class CalView(val label: String) {
    DAY("Day"), THREE("3 days"), WEEK("Week"), MONTH("Month"), YEAR("Year")
}

@Composable
fun CalendarScreen(vm: MainViewModel, onOpenTask: (String) -> Unit, onAdd: (Long) -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    var viewIdx by rememberSaveable { mutableStateOf(CalView.MONTH.ordinal) }
    var anchorDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var filter by remember { mutableStateOf(TaskFilter()) }

    val view = CalView.values()[viewIdx]
    val anchor = LocalDate.ofEpochDay(anchorDay)
    val today = now.toDate()

    val byDate: Map<LocalDate, List<TaskEntity>> = tasks
        .filter { filter.matches(it, now) }
        .filter { it.scheduleDate() != null }
        .sortedBy { it.dueAt ?: it.startAt ?: 0L }
        .groupBy { it.scheduleDate()!! }

    fun step(dir: Long) {
        val next = when (view) {
            CalView.DAY -> anchor.plusDays(dir)
            CalView.THREE -> anchor.plusDays(3 * dir)
            CalView.WEEK -> anchor.plusDays(7 * dir)
            CalView.MONTH -> anchor.plusMonths(dir)
            CalView.YEAR -> anchor.plusYears(dir)
        }
        anchorDay = next.toEpochDay()
    }

    val title = when (view) {
        CalView.DAY -> anchor.format("EEEE, d MMM yyyy")
        CalView.THREE -> anchor.format("d MMM") + " – " + anchor.plusDays(2).format("d MMM")
        CalView.WEEK -> weekStart(anchor).format("d MMM") + " – " + weekStart(anchor).plusDays(6).format("d MMM")
        CalView.MONTH -> anchor.format("MMMM yyyy")
        CalView.YEAR -> anchor.format("yyyy")
    }

    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ step(-1) }) { Icon(Icons.Filled.ChevronLeft, "Previous") }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1)
            IconButton({ step(1) }) { Icon(Icons.Filled.ChevronRight, "Next") }
            TextButton({ anchorDay = today.toEpochDay() }) { Text("Today") }
            IconButton({ showFilters = !showFilters }) { Icon(Icons.Filled.FilterList, "Filters") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalView.values().forEach { v ->
                FilterChip(view == v, { viewIdx = v.ordinal }, { Text(v.label) })
            }
        }
        if (showFilters) {
            Spacer(Modifier.height(6.dp))
            FilterBar(filter, cats, { filter = it })
        }
        Spacer(Modifier.height(8.dp))

        when (view) {
            CalView.DAY -> Agenda(listOf(anchor), byDate, cats, now, today, onOpenTask, onAdd, vm)
            CalView.THREE -> Agenda((0L until 3L).map { anchor.plusDays(it) }, byDate, cats, now, today, onOpenTask, onAdd, vm)
            CalView.WEEK -> Agenda((0L until 7L).map { weekStart(anchor).plusDays(it) }, byDate, cats, now, today, onOpenTask, onAdd, vm)
            CalView.MONTH -> MonthView(anchor, today, byDate, cats, now, onSelect = { anchorDay = it.toEpochDay() }, onOpenTask, onAdd, vm)
            CalView.YEAR -> YearView(anchor, today, byDate) { month ->
                anchorDay = (if (month == YearMonth.from(today)) today else month.atDay(1)).toEpochDay()
                viewIdx = CalView.MONTH.ordinal
            }
        }
    }
}

private fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, count: Int, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val isToday = date == today
        Text(
            (if (isToday) "Today · " else "") + date.format("EEE, d MMM"),
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (count > 0) Text("$count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onAdd) { Icon(Icons.Filled.Add, "Add task on this day") }
    }
}

@Composable
private fun Agenda(
    days: List<LocalDate>, byDate: Map<LocalDate, List<TaskEntity>>, cats: List<CategoryEntity>, now: Long, today: LocalDate,
    onOpenTask: (String) -> Unit, onAdd: (Long) -> Unit, vm: MainViewModel,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            val list = byDate[day].orEmpty()
            item(key = "h$day") { DayHeader(day, today, list.size) { onAdd(defaultDueFor(day, now)) } }
            if (list.isEmpty()) {
                item(key = "e$day") {
                    Text("No tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(list, key = { "$day-${it.id}" }) { t ->
                    TaskCard(t, cats.firstOrNull { it.id == t.categoryId }, now, { onOpenTask(t.id) }, { vm.toggleDone(t) })
                }
            }
        }
    }
}

@Composable
private fun MonthView(
    anchor: LocalDate, today: LocalDate, byDate: Map<LocalDate, List<TaskEntity>>, cats: List<CategoryEntity>, now: Long,
    onSelect: (LocalDate) -> Unit, onOpenTask: (String) -> Unit, onAdd: (Long) -> Unit, vm: MainViewModel,
) {
    val month = YearMonth.from(anchor)
    val offset = month.atDay(1).dayOfWeek.value - 1
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    val selectedTasks = byDate[anchor].orEmpty()
    val primary = MaterialTheme.colorScheme.primary

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val dayNum = r * 7 + c - offset + 1
                            if (dayNum in 1..month.lengthOfMonth()) {
                                val date = month.atDay(dayNum)
                                DayCell(date, date == anchor, date == today, byDate[date].orEmpty(), now, primary, Modifier.weight(1f)) { onSelect(date) }
                            } else {
                                Spacer(Modifier.weight(1f).height(52.dp))
                            }
                        }
                    }
                }
            }
        }
        item { DayHeader(anchor, today, selectedTasks.size) { onAdd(defaultDueFor(anchor, now)) } }
        if (selectedTasks.isEmpty()) {
            item { Text("No tasks on this day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(selectedTasks, key = { it.id }) { t ->
                TaskCard(t, cats.firstOrNull { it.id == t.categoryId }, now, { onOpenTask(t.id) }, { vm.toggleDone(t) })
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate, selected: Boolean, isToday: Boolean, tasks: List<TaskEntity>, now: Long, primary: Color,
    modifier: Modifier, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    Column(
        modifier.height(52.dp).padding(2.dp).clip(shape).background(bg).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) primary else MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(8.dp).padding(top = 2.dp)) {
            tasks.take(3).forEach { t -> Box(Modifier.size(5.dp).background(t.effectiveStatus(now).color(primary), CircleShape)) }
        }
    }
}

@Composable
private fun YearView(anchor: LocalDate, today: LocalDate, byDate: Map<LocalDate, List<TaskEntity>>, onPickMonth: (YearMonth) -> Unit) {
    val months = (1..12).map { YearMonth.of(anchor.year, it) }
    LazyVerticalGrid(
        GridCells.Fixed(3), contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(months) { m ->
            val inMonth = byDate.filterKeys { YearMonth.from(it) == m }.values.flatten()
            val done = inMonth.count { it.status == com.ppm.momentum.domain.TaskStatus.COMPLETED }
            val isCurrent = m == YearMonth.from(today)
            Card(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPickMonth(m) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(m.atDay(1).format("MMM"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (inMonth.isEmpty()) "No tasks" else "${inMonth.size} tasks",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (inMonth.isNotEmpty()) Text("$done done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
