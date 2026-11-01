package com.ppm.momentum.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ppm.momentum.data.UserPrefs
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.screens.AnalyticsScreen
import com.ppm.momentum.ui.screens.CalendarScreen
import com.ppm.momentum.ui.screens.FocusScreen
import com.ppm.momentum.ui.screens.HomeScreen
import com.ppm.momentum.ui.screens.ListKind
import com.ppm.momentum.ui.screens.MatrixScreen
import com.ppm.momentum.ui.screens.ProfileScreen
import com.ppm.momentum.ui.screens.SettingsScreen
import com.ppm.momentum.ui.screens.TaskDetailScreen
import com.ppm.momentum.ui.screens.TaskEditorScreen
import com.ppm.momentum.ui.screens.TaskListScreen
import com.ppm.momentum.ui.screens.WhatsAppSettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("calendar", "Calendar", Icons.Filled.CalendarMonth),
    Tab("matrix", "Matrix", Icons.Filled.GridView),
    Tab("focus", "Focus", Icons.Filled.Timer),
    Tab("analytics", "Analytics", Icons.Filled.BarChart),
)

@Composable
fun MainNav(vm: MainViewModel, prefs: UserPrefs) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    fun openTask(id: String) = nav.navigate("detail/$id")
    fun addTask(date: Long? = null) = nav.navigate("editor?date=${date ?: -1L}")
    fun editTask(id: String) = nav.navigate("editor?taskId=$id")
    fun goTab(r: String) = nav.navigate(r) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route, onClick = { goTab(t.route) },
                            icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    vm, prefs,
                    onOpenTask = ::openTask, onAdd = { addTask() }, onTab = ::goTab,
                    onOpenList = { nav.navigate("list/${it.name}") }, onSettings = { nav.navigate("settings") },
                )
            }
            composable("calendar") { CalendarScreen(vm, onOpenTask = ::openTask, onAdd = { addTask(it) }) }
            composable("matrix") { MatrixScreen(vm, onOpenTask = ::openTask, onAdd = { addTask() }) }
            composable("focus") { FocusScreen() }
            composable("analytics") { AnalyticsScreen(vm) }

            composable(
                "editor?taskId={taskId}&date={date}",
                arguments = listOf(
                    navArgument("taskId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { e ->
                val date = e.arguments?.getLong("date") ?: -1L
                TaskEditorScreen(vm, e.arguments?.getString("taskId"), if (date >= 0) date else null, onClose = { nav.popBackStack() })
            }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                TaskDetailScreen(
                    vm, e.arguments?.getString("id") ?: "",
                    onBack = { nav.popBackStack() }, onEdit = ::editTask,
                    onOpenTask = ::openTask,
                )
            }
            composable("list/{kind}", arguments = listOf(navArgument("kind") { type = NavType.StringType })) { e ->
                val kind = runCatching { ListKind.valueOf(e.arguments?.getString("kind") ?: "ALL") }.getOrDefault(ListKind.ALL)
                TaskListScreen(vm, kind, onBack = { nav.popBackStack() }, onOpenTask = ::openTask, onAdd = { addTask() })
            }
            composable("settings") {
                SettingsScreen(
                    vm, onBack = { nav.popBackStack() },
                    onProfile = { nav.navigate("profile") }, onWhatsApp = { nav.navigate("whatsapp") },
                    onOpenList = { nav.navigate("list/${it.name}") },
                )
            }
            composable("profile") { ProfileScreen(vm, onBack = { nav.popBackStack() }) }
            composable("whatsapp") { WhatsAppSettingsScreen(vm, onBack = { nav.popBackStack() }) }
        }
    }
}
