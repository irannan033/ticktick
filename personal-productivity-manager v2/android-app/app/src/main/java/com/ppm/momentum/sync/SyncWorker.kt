package com.ppm.momentum.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ppm.momentum.MomentumApp

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        when ((applicationContext as MomentumApp).container.sync.sync()) {
            is SyncResult.Error -> Result.retry()
            else -> Result.success()
        }
}
