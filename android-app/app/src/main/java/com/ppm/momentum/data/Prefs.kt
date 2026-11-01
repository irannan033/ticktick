package com.ppm.momentum.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

data class UserPrefs(
    val themeMode: ThemeMode,
    val onboarded: Boolean,
    val name: String,
    val email: String,
    val whatsappNumber: String,
    val whatsappEnabled: Boolean,
    val morningTime: String,
    val eveningTime: String,
    val followupHours: Int,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PrefsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object K {
        val THEME = stringPreferencesKey("theme")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val NAME = stringPreferencesKey("name")
        val EMAIL = stringPreferencesKey("email")
        val WA_NUMBER = stringPreferencesKey("wa_number")
        val WA_ENABLED = booleanPreferencesKey("wa_enabled")
        val MORNING = stringPreferencesKey("morning")
        val EVENING = stringPreferencesKey("evening")
        val FOLLOWUP = intPreferencesKey("followup_hours")
    }

    val prefs: Flow<UserPrefs> = store.data.map { p ->
        UserPrefs(
            themeMode = runCatching { ThemeMode.valueOf(p[K.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            onboarded = p[K.ONBOARDED] ?: false,
            name = p[K.NAME] ?: "",
            email = p[K.EMAIL] ?: "",
            whatsappNumber = p[K.WA_NUMBER] ?: "",
            whatsappEnabled = p[K.WA_ENABLED] ?: false,
            morningTime = p[K.MORNING] ?: "08:00",
            eveningTime = p[K.EVENING] ?: "23:00",
            followupHours = p[K.FOLLOWUP] ?: 2,
        )
    }

    suspend fun setTheme(mode: ThemeMode) { store.edit { it[K.THEME] = mode.name } }

    suspend fun saveProfile(name: String, email: String) {
        store.edit { it[K.NAME] = name.trim(); it[K.EMAIL] = email.trim() }
    }

    suspend fun saveWhatsApp(number: String, enabled: Boolean, morning: String, evening: String, followupHours: Int) {
        store.edit {
            it[K.WA_NUMBER] = number.trim(); it[K.WA_ENABLED] = enabled
            it[K.MORNING] = morning; it[K.EVENING] = evening; it[K.FOLLOWUP] = followupHours
        }
    }

    suspend fun completeOnboarding(name: String, number: String, morning: String, evening: String, followupHours: Int) {
        store.edit {
            it[K.NAME] = name.trim(); it[K.WA_NUMBER] = number.trim()
            it[K.MORNING] = morning; it[K.EVENING] = evening; it[K.FOLLOWUP] = followupHours
            it[K.ONBOARDED] = true
        }
    }
}
