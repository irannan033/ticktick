@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.domain.Period
import com.ppm.momentum.domain.Priority
import com.ppm.momentum.domain.computeStats
import com.ppm.momentum.domain.referenceTime
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.MetricCard
import com.ppm.momentum.ui.components.SectionTitle
import com.ppm.momentum.ui.theme.StatusAmber
import com.ppm.momentum.ui.theme.StatusBlue
import com.ppm.momentum.ui.theme.StatusGreen
import com.ppm.momentum.ui.theme.StatusGrey
import com.ppm.momentum.ui.theme.StatusRed
import com.ppm.momentum.ui.theme.color
import com.ppm.momentum.util.toDate
import java.util.Locale

/**
 * Phase 1 analytics: every number is computed from the real task table.
 * Pie/line/bar charts and custom date ranges arrive in Phase 2.
 */
@Composable
fun AnalyticsScreen(vm: MainViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var periodIdx by rememberSaveable { mutableStateOf(Period.WEEK.ordinal) }
    val period = Period.values()[periodIdx]
    val range = period.range(now.toDate())
    val stats = computeStats(tasks, now, range)
    val inPeriod = if (range == null) tasks else tasks.filter { it.referenceTime() in range }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Analytics", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.values().forEach { p -> FilterChip(period == p, { periodIdx = p.ordinal }, { Text(p.label) }) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Total", stats.total.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
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

        SectionTitle("Priority distribution")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val maxCount = Priority.values().maxOf { p -> inPeriod.count { it.priority == p } }.coerceAtLeast(1)
                Priority.values().forEach { p ->
                    val n = inPeriod.count { it.priority == p }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, Modifier.weight(0.25f), style = MaterialTheme.typography.bodySmall)
                        Box(Modifier.weight(0.65f)) {
                            Box(
                                Modifier.fillMaxWidth(n.toFloat() / maxCount).height(12.dp)
                                    .background(p.color(), RoundedCornerShape(6.dp)),
                            )
                        }
                        Text(n.toString(), Modifier.weight(0.1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        SectionTitle("Task accountability")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tasks postponed: ${stats.postponedTasks}", style = MaterialTheme.typography.bodyMedium)
                Text("Total postponements: ${stats.postponements}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "Charts, trends, Pomodoro focus time and custom date ranges arrive in Phase 2.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
