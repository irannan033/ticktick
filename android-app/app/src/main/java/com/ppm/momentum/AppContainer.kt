package com.ppm.momentum

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.ppm.momentum.data.AppDatabase
import com.ppm.momentum.data.PrefsRepository
import com.ppm.momentum.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; swap for Hilt/Koin later if the graph grows. */
class AppContainer(context: Context) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "momentum.db").build()
    val repository = TaskRepository(db)
    val prefs = PrefsRepository(context)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repository.seedDefaults() }
    }
}

class MomentumApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
