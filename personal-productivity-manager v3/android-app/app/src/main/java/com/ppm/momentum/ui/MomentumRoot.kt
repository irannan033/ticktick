package com.ppm.momentum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ppm.momentum.data.ThemeMode
import com.ppm.momentum.ui.nav.MainNav
import com.ppm.momentum.ui.screens.OnboardingFlow
import com.ppm.momentum.ui.theme.MomentumTheme

@Composable
fun MomentumRoot(vm: MainViewModel) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val p = prefs
    MomentumTheme(p?.themeMode ?: ThemeMode.SYSTEM) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (p == null) {
                Splash()
            } else if (!p.onboarded) {
                OnboardingFlow(vm)
            } else {
                MainNav(vm, p)
            }
        }
    }
}

@Composable
private fun Splash() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Momentum", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}
