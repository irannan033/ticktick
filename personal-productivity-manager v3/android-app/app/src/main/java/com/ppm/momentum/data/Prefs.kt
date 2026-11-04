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
    val notificationsEnabled: Boolean = true,
    val remindAtReminder: Boolean = true,
    val remindAtDue: Boolean = true,
    val overdueRepeat: Boolean = true,
    val overdueIntervalHours: Int = 4,
    val dailySummary: Boolean = true,
    val summaryTime: String = "21:00",
    val lastSummaryDate: String = "",
    val serverUrl: String = "",
    val authToken: String = "",
    val accountEmail: String = "",
    val pullSince: Long = 0,
    val pushSince: Long = 0,
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
        val N_ENABLED = booleanPreferencesKey("n_enabled")
        val N_REMINDER = booleanPreferencesKey("n_reminder")
        val N_DUE = booleanPreferencesKey("n_due")
        val N_OVERDUE = booleanPreferencesKey("n_overdue")
        val N_OVERDUE_H = intPreferencesKey("n_overdue_hours")
        val N_SUMMARY = booleanPreferencesKey("n_summary")
        val N_SUMMARY_TIME = stringPreferencesKey("n_summary_time")
        val N_SUMMARY_LAST = stringPreferencesKey("n_summary_last")
        val S_URL = stringPreferencesKey("s_url")
        val S_TOKEN = stringPreferencesKey("s_token")
        val S_EMAIL = stringPreferencesKey("s_email")
        val S_PULL = androidx.datastore.preferences.core.longPreferencesKey("s_pull")
        val S_PUSH = androidx.datastore.preferences.core.longPreferencesKey("s_push")
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
            notificationsEnabled = p[K.N_ENABLED] ?: true,
            remindAtReminder = p[K.N_REMINDER] ?: true,
            remindAtDue = p[K.N_DUE] ?: true,
            overdueRepeat = p[K.N_OVERDUE] ?: true,
            overdueIntervalHours = p[K.N_OVERDUE_H] ?: 4,
            dailySummary = p[K.N_SUMMARY] ?: true,
            summaryTime = p[K.N_SUMMARY_TIME] ?: "21:00",
            lastSummaryDate = p[K.N_SUMMARY_LAST] ?: "",
            serverUrl = p[K.S_URL] ?: "",
            authToken = p[K.S_TOKEN] ?: "",
            accountEmail = p[K.S_EMAIL] ?: "",
            pullSince = p[K.S_PULL] ?: 0L,
            pushSince = p[K.S_PUSH] ?: 0L,
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

    suspend fun saveNotifications(
        enabled: Boolean, atReminder: Boolean, atDue: Boolean, overdueRepeat: Boolean,
        overdueHours: Int, summary: Boolean, summaryTime: String,
    ) {
        store.edit {
            it[K.N_ENABLED] = enabled; it[K.N_REMINDER] = atReminder; it[K.N_DUE] = atDue
            it[K.N_OVERDUE] = overdueRepeat; it[K.N_OVERDUE_H] = overdueHours
            it[K.N_SUMMARY] = summary; it[K.N_SUMMARY_TIME] = summaryTime
        }
    }

    suspend fun setServerUrl(url: String) { store.edit { it[K.S_URL] = url.trim().trimEnd('/') } }
    suspend fun setAuth(token: String, email: String) { store.edit { it[K.S_TOKEN] = token; it[K.S_EMAIL] = email } }
    suspend fun clearAuth() { store.edit { it[K.S_TOKEN] = ""; it[K.S_EMAIL] = ""; it[K.S_PULL] = 0L; it[K.S_PUSH] = 0L } }
    suspend fun setCursors(pull: Long, push: Long) { store.edit { it[K.S_PULL] = pull; it[K.S_PUSH] = push } }

    suspend fun setLastSummaryDate(date: String) { store.edit { it[K.N_SUMMARY_LAST] = date } }

    suspend fun completeOnboarding(name: String, number: String, morning: String, evening: String, followupHours: Int) {
        store.edit {
            it[K.NAME] = name.trim(); it[K.WA_NUMBER] = number.trim()
            it[K.MORNING] = morning; it[K.EVENING] = evening; it[K.FOLLOWUP] = followupHours
            it[K.ONBOARDED] = true
        }
    }
}
