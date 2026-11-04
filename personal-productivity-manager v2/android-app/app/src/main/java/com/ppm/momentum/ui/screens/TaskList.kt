@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.domain.TaskFilter
import com.ppm.momentum.domain.TaskStatus
import com.ppm.momentum.domain.effectiveStatus
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.BackTopBar
import com.ppm.momentum.ui.components.EmptyState
import com.ppm.momentum.ui.components.FilterBar
import com.ppm.momentum.ui.components.TaskCard

enum class ListKind(val title: String) {
    ALL("All tasks"), COMPLETED("Completed tasks"), OVERDUE("Overdue tasks"), CANCELLED("Cancelled tasks")
}

@Composable
fun TaskListScreen(vm: MainViewModel, kind: ListKind, onBack: () -> Unit, onOpenTask: (String) -> Unit, onAdd: () -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(TaskFilter()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val base = when (kind) {
        ListKind.ALL -> tasks
        ListKind.COMPLETED -> tasks.filter { it.status == TaskStatus.COMPLETED }.sortedByDescending { it.completedAt }
        ListKind.OVERDUE -> tasks.filter { it.effectiveStatus(now) == TaskStatus.OVERDUE }
        ListKind.CANCELLED -> tasks.filter { it.status == TaskStatus.CANCELLED }.sortedByDescending { it.updatedAt }
    }
    val shown = base.filter { filter.matches(it, now) && (query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true)) }

    Scaffold(
        topBar = {
            BackTopBar(kind.title, onBack) {
                IconButton(onClick = { showFilters = !showFilters }) { Icon(Icons.Filled.FilterList, "Filters") }
            }
        },
        floatingActionButton = {
            if (kind == ListKind.ALL) FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add task") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                query, { query = it }, placeholder = { Text("Search tasks") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth(),
            )
            if (showFilters) FilterBar(filter, cats, { filter = it }, showStatus = kind == ListKind.ALL)
            if (shown.isEmpty()) {
                EmptyState(Icons.Filled.CheckCircle, "Nothing here", if (filter.isEmpty && query.isBlank()) null else "Try clearing the filters.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { t ->
                        TaskCard(t, cats.firstOrNull { it.id == t.categoryId }, now, { onOpenTask(t.id) }, { vm.toggleDone(t) })
                    }
                }
            }
        }
    }
}
