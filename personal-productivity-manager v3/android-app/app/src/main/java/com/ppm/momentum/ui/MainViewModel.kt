package com.ppm.momentum.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ppm.momentum.AppContainer
import com.ppm.momentum.data.PrefsRepository
import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.data.TaskRepository
import com.ppm.momentum.data.ThemeMode
import com.ppm.momentum.data.UserPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repo: TaskRepository,
    private val prefsRepo: PrefsRepository,
    private val syncManager: com.ppm.momentum.sync.SyncManager,
) : ViewModel() {

    private val lazily = SharingStarted.WhileSubscribed(5_000)

    val tasks = repo.allTasks.stateIn(viewModelScope, lazily, emptyList())
    val categories = repo.categories.stateIn(viewModelScope, lazily, emptyList())
    val sessions = repo.sessions.stateIn(viewModelScope, lazily, emptyList())
    val prefs: StateFlow<UserPrefs?> = prefsRepo.prefs.stateIn(viewModelScope, lazily, null)

    /** Ticks so "overdue" status refreshes while the app is open. */
    val now: StateFlow<Long> = flow {
        while (true) { emit(System.currentTimeMillis()); delay(30_000) }
    }.stateIn(viewModelScope, lazily, System.currentTimeMillis())

    fun task(id: String) = repo.observeTask(id)
    fun activity(id: String) = repo.observeActivity(id)
    fun sessionsFor(id: String) = repo.observeSessions(id)
    suspend fun getTask(id: String): TaskEntity? = repo.getTask(id)

    fun save(task: TaskEntity, onDone: () -> Unit = {}) = viewModelScope.launch { repo.save(task); onDone() }
    fun complete(id: String) = viewModelScope.launch { repo.complete(id) }
    fun reopen(id: String) = viewModelScope.launch { repo.reopen(id) }
    fun cancel(id: String) = viewModelScope.launch { repo.cancel(id) }
    fun start(id: String) = viewModelScope.launch { repo.start(id) }
    fun reschedule(id: String, due: Long) = viewModelScope.launch { repo.reschedule(id, due) }
    fun setMatrix(id: String, important: Boolean, urgent: Boolean) = viewModelScope.launch { repo.setMatrix(id, important, urgent) }
    fun duplicate(id: String, onDone: (String?) -> Unit = {}) = viewModelScope.launch { onDone(repo.duplicate(id)) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun toggleDone(t: TaskEntity) { if (t.status == com.ppm.momentum.domain.TaskStatus.COMPLETED) reopen(t.id) else complete(t.id) }

    // ---- quick add (natural language) ----
    fun quickAdd(text: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val q = com.ppm.momentum.domain.parseQuickAdd(text, now)
        if (q.title.isNotBlank()) {
            repo.save(
                TaskEntity(
                    title = q.title, priority = q.priority, important = q.important, urgent = q.urgent,
                    dueAt = q.dueAt, recurrence = q.recurrence, createdAt = now, updatedAt = now,
                )
            )
        }
    }

    // ---- cloud sync ----
    val syncStatus = MutableStateFlow("")
    val syncBusy = MutableStateFlow(false)
    fun setSyncStatus(text: String) { syncStatus.value = text }

    private fun describe(r: com.ppm.momentum.sync.SyncResult) = when (r) {
        is com.ppm.momentum.sync.SyncResult.Success -> "Synced: ${r.pushed} sent, ${r.pulled} received"
        com.ppm.momentum.sync.SyncResult.NotSignedIn -> "Not signed in"
        is com.ppm.momentum.sync.SyncResult.Error -> r.message
    }

    fun syncNow() = viewModelScope.launch {
        syncBusy.value = true
        syncStatus.value = describe(syncManager.sync())
        syncBusy.value = false
    }

    fun signInWithGoogle(serverUrl: String, idToken: String) = viewModelScope.launch {
        syncBusy.value = true
        syncStatus.value = describe(syncManager.signInWithGoogleToken(serverUrl, idToken))
        syncBusy.value = false
    }

    fun signOutCloud() = viewModelScope.launch { syncManager.signOut(); syncStatus.value = "Signed out" }
    fun setServerUrl(url: String) = viewModelScope.launch { prefsRepo.setServerUrl(url) }

    fun addCategory(name: String, colorArgb: Long) = viewModelScope.launch { repo.addCategory(name, colorArgb) }
    fun deleteCategory(id: String) = viewModelScope.launch { repo.deleteCategory(id) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefsRepo.setTheme(mode) }
    fun saveProfile(name: String, email: String) = viewModelScope.launch { prefsRepo.saveProfile(name, email) }
    fun saveWhatsApp(number: String, enabled: Boolean, morning: String, evening: String, hours: Int) =
        viewModelScope.launch { prefsRepo.saveWhatsApp(number, enabled, morning, evening, hours); syncManager.sync() }
    fun saveNotifications(
        enabled: Boolean, atReminder: Boolean, atDue: Boolean, overdueRepeat: Boolean,
        overdueHours: Int, summary: Boolean, summaryTime: String,
    ) = viewModelScope.launch {
        prefsRepo.saveNotifications(enabled, atReminder, atDue, overdueRepeat, overdueHours, summary, summaryTime)
        repo.resyncAll()
    }
    fun completeOnboarding(name: String, number: String, morning: String, evening: String, hours: Int) =
        viewModelScope.launch { prefsRepo.completeOnboarding(name, number, morning, evening, hours) }

    companion object {
        fun factory(c: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(c.repository, c.prefs, c.sync) as T
        }
    }
}
