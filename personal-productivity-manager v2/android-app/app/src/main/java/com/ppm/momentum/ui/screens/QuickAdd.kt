package com.ppm.momentum.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import com.ppm.momentum.domain.parseQuickAdd
import com.ppm.momentum.ui.MainViewModel
import com.ppm.momentum.util.formatDateTime

/** One-line natural-language task entry with a live preview of what was understood, plus voice input. */
@Composable
fun QuickAddBar(vm: MainViewModel) {
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val spoken = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) text = spoken
    }
    val preview = remember(text) { if (text.isBlank()) null else parseQuickAdd(text, System.currentTimeMillis()) }

    fun submit() {
        if (text.isNotBlank()) { vm.quickAdd(text); text = "" }
    }

    Column {
        OutlinedTextField(
            text, { text = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Quick add: Call Sam tomorrow 5pm !high") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                Row {
                    IconButton(onClick = {
                        try {
                            speech.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            )
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(ctx, "Voice input is not available on this device", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Filled.Mic, "Voice input") }
                    IconButton(onClick = { submit() }, enabled = text.isNotBlank()) { Icon(Icons.Filled.Add, "Add") }
                }
            },
        )
        preview?.let { q ->
            val parts = buildList {
                q.dueAt?.let { add("due " + formatDateTime(it)) }
                if (q.priority != com.ppm.momentum.domain.Priority.NONE) add(q.priority.label + " priority")
                if (q.important) add("important")
                if (q.urgent) add("urgent")
                q.recurrence?.let { add("repeats " + it.label.lowercase()) }
            }
            if (parts.isNotEmpty()) {
                Text(
                    "\"" + q.title + "\" · " + parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
