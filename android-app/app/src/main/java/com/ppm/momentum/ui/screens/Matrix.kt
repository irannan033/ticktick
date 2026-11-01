@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.domain.Quadrant
import com.ppm.momentum.domain.isActive
import com.ppm.momentum.domain.quadrant
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.theme.color
import com.ppm.momentum.util.friendlyDue
import androidx.compose.material3.FloatingActionButton

@Composable
fun MatrixScreen(vm: MainViewModel, onOpenTask: (String) -> Unit, onAdd: () -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val active = tasks.filter { it.isActive }
    val grouped = active.groupBy { it.quadrant }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Eisenhower matrix", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuadrantCard(Quadrant.DO_FIRST, grouped[Quadrant.DO_FIRST].orEmpty(), now, Modifier.weight(1f), vm, onOpenTask)
                QuadrantCard(Quadrant.SCHEDULE, grouped[Quadrant.SCHEDULE].orEmpty(), now, Modifier.weight(1f), vm, onOpenTask)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuadrantCard(Quadrant.DELEGATE, grouped[Quadrant.DELEGATE].orEmpty(), now, Modifier.weight(1f), vm, onOpenTask)
                QuadrantCard(Quadrant.ELIMINATE, grouped[Quadrant.ELIMINATE].orEmpty(), now, Modifier.weight(1f), vm, onOpenTask)
            }
        }
        FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Filled.Add, "Add task")
        }
    }
}

@Composable
private fun QuadrantCard(
    q: Quadrant, tasks: List<TaskEntity>, now: Long, modifier: Modifier, vm: MainViewModel, onOpenTask: (String) -> Unit,
) {
    val color = q.color()
    Card(
        modifier, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text("${q.title} · ${tasks.size}", style = MaterialTheme.typography.titleMedium, color = color)
                Text(q.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tasks.isEmpty()) {
                Text("Empty", Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(tasks, key = { it.id }) { t -> MatrixRow(t, now, vm, onOpenTask) }
                }
            }
        }
    }
}

@Composable
private fun MatrixRow(t: TaskEntity, now: Long, vm: MainViewModel, onOpenTask: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { onOpenTask(t.id) }.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(t.priority.color(), RoundedCornerShape(50)))
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            t.dueAt?.let { Text(friendlyDue(it, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.SwapVert, "Move to another quadrant") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Quadrant.values().filter { it != t.quadrant }.forEach { target ->
                    DropdownMenuItem(
                        text = { Text("Move to " + target.title) },
                        onClick = { menu = false; vm.setMatrix(t.id, target.important, target.urgent) },
                    )
                }
            }
        }
    }
}
