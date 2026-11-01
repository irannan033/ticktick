package com.ppm.momentum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ppm.momentum.ui.components.EmptyState

/**
 * Placeholder tab. The Pomodoro timer is Phase 2: it needs a foreground service so the
 * countdown survives screen-off, a session table, and notification channels.
 */
@Composable
fun FocusScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        EmptyState(
            Icons.Filled.Timer, "Focus timer — coming in Phase 2",
            "Pomodoro sessions linked to tasks, with background-safe timing and completion alerts, are the next milestone.",
        )
    }
}
