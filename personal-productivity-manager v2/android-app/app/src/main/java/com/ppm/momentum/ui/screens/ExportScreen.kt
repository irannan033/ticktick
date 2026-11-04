@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.domain.Period
import com.ppm.momentum.export.ExportFormat
import com.ppm.momentum.export.Exporter
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.BackTopBar
import com.ppm.momentum.ui.components.SectionTitle
import com.ppm.momentum.util.endMillis
import com.ppm.momentum.util.format
import com.ppm.momentum.util.startMillis
import com.ppm.momentum.util.toDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val SCOPES = listOf(
    "All tasks" to Period.ALL, "Today" to Period.TODAY, "This week" to Period.WEEK,
    "This month" to Period.MONTH, "This year" to Period.YEAR, "Custom range" to Period.CUSTOM,
)

@Composable
fun ExportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val today = now.toDate()

    var scopeIdx by remember { mutableStateOf(0) }
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var from by remember { mutableStateOf(today.minusDays(29).toEpochDay()) }
    var to by remember { mutableStateOf(today.toEpochDay()) }
    var busy by remember { mutableStateOf(false) }

    val (scopeLabel, period) = SCOPES[scopeIdx]

    fun pick(current: Long, onPicked: (Long) -> Unit) {
        val d = LocalDate.ofEpochDay(current)
        DatePickerDialog(ctx, { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day).toEpochDay()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    Scaffold(topBar = { BackTopBar("Export report", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Period")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SCOPES.forEachIndexed { i, (label, _) -> FilterChip(scopeIdx == i, { scopeIdx = i }, { Text(label) }) }
            }
            if (period == Period.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ pick(from) { from = it } }, Modifier.weight(1f)) { Text("From " + LocalDate.ofEpochDay(from).format("d MMM yyyy")) }
                    OutlinedButton({ pick(to) { to = it } }, Modifier.weight(1f)) { Text("To " + LocalDate.ofEpochDay(to).format("d MMM yyyy")) }
                }
            }
            SectionTitle("Format")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.values().forEach { f -> FilterChip(format == f, { format = f }, { Text(f.label) }) }
            }
            Text(
                "Includes title, description, priority, importance, urgency, list, start, deadline, reminder, status, " +
                    "completion time, on-time/late, postponements and Pomodoro focus time. PDF adds summary statistics and charts.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val range: LongRange? = if (period == Period.CUSTOM) {
                                LocalDate.ofEpochDay(minOf(from, to)).startMillis() until LocalDate.ofEpochDay(maxOf(from, to)).endMillis()
                            } else period.range(today)
                            val label = if (period == Period.CUSTOM) {
                                LocalDate.ofEpochDay(minOf(from, to)).format("d MMM yyyy") + " – " + LocalDate.ofEpochDay(maxOf(from, to)).format("d MMM yyyy")
                            } else scopeLabel + " · generated " + today.format("d MMM yyyy")
                            val file = withContext(Dispatchers.IO) {
                                Exporter.create(ctx, format, label, tasks, cats, sessions, now, range)
                            }
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).setType(format.mime)
                                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            ctx.startActivity(Intent.createChooser(send, "Share report"))
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Export failed: " + (e.message ?: "unknown error"), Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (busy) "Generating…" else "Export & share") }
        }
    }
}
