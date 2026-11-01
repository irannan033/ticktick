@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.TimeField

val WHATSAPP_NUMBER_REGEX = Regex("^\\+[1-9]\\d{7,14}$")

@Composable
fun OnboardingFlow(vm: MainViewModel) {
    var step by rememberSaveable { mutableStateOf(0) }
    if (step == 0) WelcomeStep(onContinue = { step = 1 }) else SetupStep(vm)
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Momentum", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Plan your day, prioritise with the Eisenhower matrix, and track what you actually get done.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))
        // Google Sign-In needs a Firebase project (google-services.json + web client ID).
        // It is wired in Phase 3 together with cloud sync; see docs/GOOGLE_SIGNIN.md.
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Continue with Google (Phase 3)")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Continue on this device")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Your tasks are stored locally on this phone. Cloud backup arrives with Google sign-in.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupStep(vm: MainViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var morning by rememberSaveable { mutableStateOf("08:00") }
    var evening by rememberSaveable { mutableStateOf("23:00") }
    var hours by rememberSaveable { mutableStateOf(2) }
    var tried by rememberSaveable { mutableStateOf(false) }

    val nameOk = name.isNotBlank()
    val numberOk = number.isBlank() || WHATSAPP_NUMBER_REGEX.matches(number.trim())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Set up your profile", style = MaterialTheme.typography.headlineMedium)
        Text(
            "WhatsApp reminders start working in Phase 4 once the backend is connected. These preferences are saved now so nothing needs re-entering later.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            name, { name = it }, label = { Text("Your name") }, singleLine = true,
            isError = tried && !nameOk, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            number, { number = it }, label = { Text("WhatsApp number (e.g. +8801712345678)") }, singleLine = true,
            isError = tried && !numberOk,
            supportingText = { if (tried && !numberOk) Text("Use international format: + country code, then number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        TimeField("First daily message", morning) { morning = it }
        TimeField("End-of-day report", evening) { evening = it }
        Text("Follow-up interval", style = MaterialTheme.typography.titleMedium)
        IntervalChips(hours) { hours = it }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                tried = true
                if (nameOk && numberOk) vm.completeOnboarding(name, number, morning, evening, hours)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Get started") }
    }
}

@Composable
fun IntervalChips(selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3, 4, 6).forEach { h ->
            FilterChip(selected == h, { onSelect(h) }, { Text("${h}h") })
        }
    }
}
