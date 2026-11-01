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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repo: TaskRepository,
    private val prefsRepo: PrefsRepository,
) : ViewModel() {

    private val lazily = SharingStarted.WhileSubscribed(5_000)

    val tasks = repo.allTasks.stateIn(viewModelScope, lazily, emptyList())
    val categories = repo.categories.stateIn(viewModelScope, lazily, emptyList())
    val prefs: StateFlow<UserPrefs?> = prefsRepo.prefs.stateIn(viewModelScope, lazily, null)

    /** Ticks so "overdue" status refreshes while the app is open. */
    val now: StateFlow<Long> = flow {
        while (true) { emit(System.currentTimeMillis()); delay(30_000) }
    }.stateIn(viewModelScope, lazily, System.currentTimeMillis())

    fun task(id: String) = repo.observeTask(id)
    fun activity(id: String) = repo.observeActivity(id)
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

    fun addCategory(name: String, colorArgb: Long) = viewModelScope.launch { repo.addCategory(name, colorArgb) }
    fun deleteCategory(id: String) = viewModelScope.launch { repo.deleteCategory(id) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefsRepo.setTheme(mode) }
    fun saveProfile(name: String, email: String) = viewModelScope.launch { prefsRepo.saveProfile(name, email) }
    fun saveWhatsApp(number: String, enabled: Boolean, morning: String, evening: String, hours: Int) =
        viewModelScope.launch { prefsRepo.saveWhatsApp(number, enabled, morning, evening, hours) }
    fun completeOnboarding(name: String, number: String, morning: String, evening: String, hours: Int) =
        viewModelScope.launch { prefsRepo.completeOnboarding(name, number, morning, evening, hours) }

    companion object {
        fun factory(c: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(c.repository, c.prefs) as T
        }
    }
}
