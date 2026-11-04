@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.domain.Period
import com.ppm.momentum.domain.Priority
import com.ppm.momentum.domain.computeStats
import com.ppm.momentum.domain.dailyCompletions
import com.ppm.momentum.domain.focusSeconds
import com.ppm.momentum.domain.monthlyCompletions
import com.ppm.momentum.domain.referenceTime
import com.ppm.momentum.domain.weeklyCompletions
import com.ppm.momentum.domain.weeklyOverdue
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.BarChart
import com.ppm.momentum.ui.components.LineChart
import com.ppm.momentum.ui.components.MetricCard
import com.ppm.momentum.ui.components.PieChart
import com.ppm.momentum.ui.components.PieSlice
import com.ppm.momentum.ui.components.SectionTitle
import com.ppm.momentum.ui.theme.StatusAmber
import com.ppm.momentum.ui.theme.StatusBlue
import com.ppm.momentum.ui.theme.StatusGreen
import com.ppm.momentum.ui.theme.StatusGrey
import com.ppm.momentum.ui.theme.StatusRed
import com.ppm.momentum.ui.theme.color
import com.ppm.momentum.util.endMillis
import com.ppm.momentum.util.format
import com.ppm.momentum.util.startMillis
import com.ppm.momentum.util.toDate
import java.time.LocalDate
import java.util.Locale

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Column {
        SectionTitle(title)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
fun AnalyticsScreen(vm: MainViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today = now.toDate()

    var periodIdx by rememberSaveable { mutableStateOf(Period.WEEK.ordinal) }
    var customStart by rememberSaveable { mutableStateOf(today.minusDays(6).toEpochDay()) }
    var customEnd by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val period = Period.values()[periodIdx]

    val range: LongRange? = if (period == Period.CUSTOM) {
        val a = LocalDate.ofEpochDay(minOf(customStart, customEnd))
        val b = LocalDate.ofEpochDay(maxOf(customStart, customEnd))
        a.startMillis() until b.endMillis()
    } else period.range(today)

    val stats = computeStats(tasks, now, range)
    val inPeriod = if (range == null) tasks else tasks.filter { it.referenceTime() in range }
    val primary = MaterialTheme.colorScheme.primary

    fun pickDate(current: Long, onPicked: (Long) -> Unit) {
        val d = LocalDate.ofEpochDay(current)
        DatePickerDialog(context, { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day).toEpochDay()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Analytics", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.values().forEach { p -> FilterChip(period == p, { periodIdx = p.ordinal }, { Text(p.label) }) }
        }
        if (period == Period.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pickDate(customStart) { customStart = it } }, Modifier.weight(1f)) {
                    Text("From " + LocalDate.ofEpochDay(customStart).format("d MMM yyyy"))
                }
                OutlinedButton({ pickDate(customEnd) { customEnd = it } }, Modifier.weight(1f)) {
                    Text("To " + LocalDate.ofEpochDay(customEnd).format("d MMM yyyy"))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Total", stats.total.toString(), primary, Modifier.weight(1f))
            MetricCard("Completed", stats.completed.toString(), StatusGreen, Modifier.weight(1f))
            MetricCard("Completion", String.format(Locale.getDefault(), "%.0f%%", stats.completionRate), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("On time", stats.onTime.toString(), StatusGreen, Modifier.weight(1f))
            MetricCard("Late", stats.late.toString(), StatusAmber, Modifier.weight(1f))
            MetricCard("Overdue", stats.overdue.toString(), StatusRed, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Incomplete", stats.incomplete.toString(), StatusBlue, Modifier.weight(1f))
            MetricCard("Cancelled", stats.cancelled.toString(), StatusGrey, Modifier.weight(1f))
            MetricCard(
                "Avg. time to finish",
                stats.avgCompletionHours?.let { if (it < 48) String.format(Locale.getDefault(), "%.1fh", it) else String.format(Locale.getDefault(), "%.1fd", it / 24) } ?: "—",
                MaterialTheme.colorScheme.onSurface, Modifier.weight(1f),
            )
        }

        ChartCard("Completed vs incomplete") {
            PieChart(
                listOf(
                    PieSlice("Completed", stats.completed.toFloat(), StatusGreen),
                    PieSlice("Incomplete", stats.incomplete.toFloat(), StatusAmber),
                    PieSlice("Cancelled", stats.cancelled.toFloat(), StatusGrey),
                ),
                String.format(Locale.getDefault(), "%.0f%%", stats.completionRate),
            )
        }
        ChartCard("On time vs late") {
            PieChart(
                listOf(PieSlice("On time", stats.onTime.toFloat(), StatusGreen), PieSlice("Late", stats.late.toFloat(), StatusRed)),
                if (stats.completed == 0) "—" else String.format(Locale.getDefault(), "%.0f%%", stats.onTime * 100.0 / stats.completed),
            )
        }
        ChartCard("Priority distribution") {
            val maxCount = Priority.values().maxOf { p -> inPeriod.count { it.priority == p } }.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Priority.values().forEach { p ->
                    val n = inPeriod.count { it.priority == p }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, Modifier.weight(0.25f), style = MaterialTheme.typography.bodySmall)
                        Box(Modifier.weight(0.65f)) {
                            Box(Modifier.fillMaxWidth(n.toFloat() / maxCount).height(12.dp).background(p.color(), RoundedCornerShape(6.dp)))
                        }
                        Text(n.toString(), Modifier.weight(0.1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("Trend charts show recent windows and are not affected by the period filter.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChartCard("Daily completion (last 14 days)") { BarChart(dailyCompletions(tasks, today, 14), primary) }
        ChartCard("Weekly productivity (last 8 weeks)") { LineChart(weeklyCompletions(tasks, today, 8), MaterialTheme.colorScheme.secondary) }
        ChartCard("Monthly productivity (last 6 months)") { BarChart(monthlyCompletions(tasks, today, 6), StatusBlue) }
        ChartCard("Overdue & late trend (weekly)") { LineChart(weeklyOverdue(tasks, now, today, 8), StatusRed) }

        SectionTitle("Focus time (Pomodoro)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Today", formatDuration(focusSeconds(sessions, Period.TODAY.range(today))), primary, Modifier.weight(1f))
            MetricCard("This week", formatDuration(focusSeconds(sessions, Period.WEEK.range(today))), primary, Modifier.weight(1f))
            MetricCard("This month", formatDuration(focusSeconds(sessions, Period.MONTH.range(today))), primary, Modifier.weight(1f))
        }

        SectionTitle("Task accountability")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tasks postponed: ${stats.postponedTasks}", style = MaterialTheme.typography.bodyMedium)
                Text("Total postponements: ${stats.postponements}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
