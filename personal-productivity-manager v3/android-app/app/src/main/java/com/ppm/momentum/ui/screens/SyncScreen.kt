@file:OptIn(ExperimentalMaterial3Api::class)

package com.ppm.momentum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.BuildConfig
import com.ppm.momentum.auth.GoogleSignIn
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.ui.components.BackTopBar
import kotlinx.coroutines.launch

@Composable
fun SyncScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val status by vm.syncStatus.collectAsStateWithLifecycle()
    val busy by vm.syncBusy.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    LaunchedEffect(prefs != null) { prefs?.let { url = it.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL } } }

    val signedIn = prefs?.authToken?.isNotBlank() == true
    val googleConfigured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    Scaffold(topBar = { BackTopBar("Cloud sync", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Sync is optional and needs your own Momentum server (see the backend folder). Your tasks stay on this phone either way.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                url, { url = it; vm.setServerUrl(it) }, label = { Text("Server URL (https://…)") }, singleLine = true,
                enabled = !signedIn, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            )
            if (signedIn) {
                Text("Signed in as " + (prefs?.accountEmail?.ifBlank { "your Google account" } ?: ""), style = MaterialTheme.typography.titleMedium)
                Button({ vm.syncNow() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Syncing…" else "Sync now") }
                OutlinedButton({ vm.signOutCloud() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val token = GoogleSignIn.getIdToken(ctx, BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                vm.signInWithGoogle(url, token)
                            } catch (e: Exception) {
                                vm.setSyncStatus("Google sign-in failed: " + (e.message ?: "cancelled"))
                            }
                        }
                    },
                    enabled = !busy && googleConfigured && url.startsWith("https://"),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign in with Google") }
                if (!googleConfigured) {
                    Text(
                        "This build has no Google client ID. Set googleWebClientId when building (see docs/GOOGLE_SIGNIN.md).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                if (url.isNotBlank() && !url.startsWith("https://")) {
                    Text("The server URL must start with https://", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Synced: tasks, lists and Pomodoro sessions. Not synced: per-task activity history. " +
                    "Conflicts are resolved by the most recent edit. Your sign-in token is stored in the app's private storage.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
