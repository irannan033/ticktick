package com.ppm.momentum

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.ppm.momentum.data.AppDatabase
import com.ppm.momentum.data.MIGRATION_1_2
import com.ppm.momentum.data.MIGRATION_2_3
import com.ppm.momentum.data.PrefsRepository
import com.ppm.momentum.data.TaskRepository
import com.ppm.momentum.notify.Notifier
import com.ppm.momentum.notify.ReminderScheduler
import com.ppm.momentum.notify.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Manual dependency container; swap for Hilt/Koin later if the graph grows. */
class AppContainer(context: Context) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "momentum.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
    val prefs = PrefsRepository(context)
    val scheduler = ReminderScheduler(context, prefs)
    val repository = TaskRepository(db, scheduler)
    val sync = com.ppm.momentum.sync.SyncManager(repository, prefs)

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
        Notifier.ensureChannels(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reminders", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build(),
        )
        // Background sync; it is a no-op until the user signs in under Settings > Cloud sync.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "cloud-sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<com.ppm.momentum.sync.SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }
}
