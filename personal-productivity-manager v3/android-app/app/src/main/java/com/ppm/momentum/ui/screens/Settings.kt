@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.data.ThemeMode
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.BackTopBar
import com.ppm.momentum.ui.components.SectionTitle
import com.ppm.momentum.ui.components.TimeField

private val CATEGORY_COLORS = listOf(0xFF4F5BD5, 0xFF0F9D8A, 0xFFF5A524, 0xFFE5484D, 0xFF3B82F6, 0xFFA855F7)

@Composable
private fun NavRow(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp),
    ) {
        Text(
            title, style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { content() }
    }
}

@Composable
fun SettingsScreen(
    vm: MainViewModel, onBack: () -> Unit, onProfile: () -> Unit, onWhatsApp: () -> Unit, onOpenList: (ListKind) -> Unit,
    onNotifications: () -> Unit, onExport: () -> Unit, onSync: () -> Unit,
) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    var newCategory by remember { mutableStateOf("") }

    Scaffold(topBar = { BackTopBar("Settings", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Appearance")
            SettingsCard {
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { m ->
                        FilterChip(prefs?.themeMode == m, { vm.setTheme(m) }, { Text(m.label) })
                    }
                }
            }

            SectionTitle("Account")
            SettingsCard {
                NavRow("Profile", prefs?.name?.ifBlank { "Not set" } ?: "", onClick = onProfile)
                NavRow("WhatsApp reminders", if (prefs?.whatsappEnabled == true) "Enabled (needs backend — Phase 4)" else "Configure number & schedule", onClick = onWhatsApp)
            }

            SectionTitle("Task views")
            SettingsCard {
                NavRow("All tasks", "Search and filter everything") { onOpenList(ListKind.ALL) }
                NavRow("Completed tasks", "With on-time / late result") { onOpenList(ListKind.COMPLETED) }
                NavRow("Overdue tasks", "Past deadline and still open") { onOpenList(ListKind.OVERDUE) }
                NavRow("Cancelled tasks", "Reopen if plans change") { onOpenList(ListKind.CANCELLED) }
            }

            SectionTitle("Lists")
            SettingsCard {
                cats.forEach { c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("●  ", color = Color(c.colorArgb))
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton({ vm.deleteCategory(c.id) }) { Icon(Icons.Filled.Close, "Delete list") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newCategory, { newCategory = it }, placeholder = { Text("New list name") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        if (newCategory.isNotBlank()) {
                            vm.addCategory(newCategory, CATEGORY_COLORS[cats.size % CATEGORY_COLORS.size])
                            newCategory = ""
                        }
                    }) { Icon(Icons.Filled.Add, "Add list") }
                }
            }

            SectionTitle("Notifications & data")
            SettingsCard {
                NavRow("Notification settings", "Reminders, overdue alerts, daily summary", onClick = onNotifications)
                NavRow("Export report", "Excel, CSV or PDF", onClick = onExport)
                NavRow("Cloud sync", "Google sign-in, backup and multi-device sync", onClick = onSync)
            }
            Text("Momentum 0.3.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProfileScreen(vm: MainViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(prefs != null) { prefs?.let { name = it.name; email = it.email } }

    Scaffold(topBar = { BackTopBar("Profile", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it; saved = false }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                email, { email = it; saved = false }, label = { Text("Email (optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { if (name.isNotBlank()) { vm.saveProfile(name, email); saved = true } },
                enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saved) "Saved" else "Save") }
            Text(
                "Google account: not connected. Sign-in and cloud backup arrive in Phase 3.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun WhatsAppSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var number by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    var morning by remember { mutableStateOf("08:00") }
    var evening by remember { mutableStateOf("23:00") }
    var hours by remember { mutableStateOf(2) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(prefs != null) {
        prefs?.let { number = it.whatsappNumber; enabled = it.whatsappEnabled; morning = it.morningTime; evening = it.eveningTime; hours = it.followupHours }
    }
    val numberOk = number.isBlank() || WHATSAPP_NUMBER_REGEX.matches(number.trim())

    Scaffold(topBar = { BackTopBar("WhatsApp reminders", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    "WhatsApp messages are sent by your own backend through the official WhatsApp Cloud API, so this only works after " +
                        "you sign in under Settings > Cloud sync and the server is set up. Saving here also pushes these preferences to the server. " +
                        "No WhatsApp credentials are ever stored in this app.",
                    Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            OutlinedTextField(
                number, { number = it; saved = false }, label = { Text("WhatsApp number (+country code)") }, singleLine = true,
                isError = !numberOk, supportingText = { if (!numberOk) Text("Use international format, e.g. +8801712345678") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable WhatsApp monitoring", Modifier.weight(1f))
                Switch(enabled, { enabled = it; saved = false })
            }
            TimeField("First daily message", morning) { morning = it; saved = false }
            TimeField("End-of-day report", evening) { evening = it; saved = false }
            Text("Follow-up interval", style = MaterialTheme.typography.titleMedium)
            IntervalChips(hours) { hours = it; saved = false }
            Button(
                onClick = { vm.saveWhatsApp(number, enabled && number.isNotBlank(), morning, evening, hours); saved = true },
                enabled = numberOk, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saved) "Saved" else "Save") }
        }
    }
}
