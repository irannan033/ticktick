package com.ppm.momentum.focus

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ppm.momentum.MomentumApp
import com.ppm.momentum.R
import com.ppm.momentum.data.PomodoroEntity
import com.ppm.momentum.domain.PomodoroStatus
import com.ppm.momentum.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class FocusPhase(val label: String) { FOCUS("Focus"), SHORT_BREAK("Short break"), LONG_BREAK("Long break") }
enum class TimerStatus { IDLE, RUNNING, PAUSED }

data class TimerState(
    val phase: FocusPhase = FocusPhase.FOCUS,
    val status: TimerStatus = TimerStatus.IDLE,
    val totalSec: Int = 25 * 60,
    val remainingSec: Int = 25 * 60,
    val taskId: String? = null,
    val completedFocus: Int = 0,
    val justFinished: Boolean = false,
)

fun formatClock(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)

/**
 * Foreground service so the countdown keeps running with the screen off. Remaining time is derived from
 * SystemClock.elapsedRealtime(), so it stays correct even if the process is throttled.
 * Timer state lives in memory: if Android kills the process the running session is lost.
 */
class PomodoroService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var endElapsed = 0L
    private var startedAtWall = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(
                intent.getIntExtra(EXTRA_MINUTES, 25),
                FocusPhase.valueOf(intent.getStringExtra(EXTRA_PHASE) ?: FocusPhase.FOCUS.name),
                intent.getStringExtra(EXTRA_TASK),
            )
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_RESET -> reset()
        }
        return START_NOT_STICKY
    }

    private fun begin(minutes: Int, phase: FocusPhase, taskId: String?) {
        job?.cancel()
        val total = minutes.coerceIn(1, 180) * 60
        startedAtWall = System.currentTimeMillis()
        endElapsed = SystemClock.elapsedRealtime() + total * 1000L
        _state.update { TimerState(phase, TimerStatus.RUNNING, total, total, taskId, it.completedFocus) }
        startForegroundCompat()
        acquireWakeLock(total * 1000L + 60_000L)
        runLoop()
    }

    private fun runLoop() {
        job = scope.launch {
            while (isActive) {
                val rem = ((endElapsed - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                _state.update { it.copy(remainingSec = rem) }
                refreshNotification()
                if (rem <= 0) { finish(); break }
                delay(500)
            }
        }
    }

    private fun pause() {
        if (_state.value.status != TimerStatus.RUNNING) return
        job?.cancel()
        val rem = ((endElapsed - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
        _state.update { it.copy(status = TimerStatus.PAUSED, remainingSec = rem) }
        releaseWakeLock()
        refreshNotification()
    }

    private fun resume() {
        val s = _state.value
        if (s.status != TimerStatus.PAUSED) return
        endElapsed = SystemClock.elapsedRealtime() + s.remainingSec * 1000L
        _state.update { it.copy(status = TimerStatus.RUNNING) }
        acquireWakeLock(s.remainingSec * 1000L + 60_000L)
        runLoop()
    }

    private fun reset() {
        job?.cancel()
        val s = _state.value
        val elapsed = s.totalSec - s.remainingSec
        if (s.phase == FocusPhase.FOCUS && elapsed >= 60) record(s, elapsed, PomodoroStatus.ABANDONED)
        _state.update { TimerState(s.phase, TimerStatus.IDLE, s.totalSec, s.totalSec, s.taskId, it.completedFocus) }
        shutDown()
    }

    private fun finish() {
        val s = _state.value
        if (s.phase == FocusPhase.FOCUS) record(s, s.totalSec, PomodoroStatus.COMPLETED)
        _state.update {
            it.copy(
                status = TimerStatus.IDLE, remainingSec = it.totalSec, justFinished = true,
                completedFocus = if (s.phase == FocusPhase.FOCUS) it.completedFocus + 1 else it.completedFocus,
            )
        }
        Notifier.show(
            this, NOTIF_DONE, Notifier.CH_FOCUS_DONE,
            if (s.phase == FocusPhase.FOCUS) "Focus session complete" else "Break is over",
            if (s.phase == FocusPhase.FOCUS) "Nice work. Time for a break." else "Ready for the next focus session?",
        )
        shutDown()
    }

    private fun record(s: TimerState, seconds: Int, status: PomodoroStatus) {
        val c = (application as MomentumApp).container
        val started = startedAtWall
        CoroutineScope(Dispatchers.IO).launch {
            c.repository.recordSession(
                PomodoroEntity(
                    taskId = s.taskId, plannedMinutes = s.totalSec / 60, completedSeconds = seconds,
                    startedAt = started, endedAt = System.currentTimeMillis(), status = status,
                )
            )
        }
    }

    private fun shutDown() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- notification ------------------------------------------------------------------------

    private fun action(label: String, action: String, code: Int): NotificationCompat.Action {
        val pi = PendingIntent.getService(
            this, code, Intent(this, PomodoroService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action(0, label, pi)
    }

    private fun buildNotification(): Notification {
        val s = _state.value
        val paused = s.status == TimerStatus.PAUSED
        val b = NotificationCompat.Builder(this, Notifier.CH_FOCUS_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.phase.label + if (paused) " (paused)" else "")
            .setContentText(formatClock(s.remainingSec) + " remaining")
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(Notifier.openAppIntent(this))
        if (paused) b.addAction(action("Resume", ACTION_RESUME, 1)) else b.addAction(action("Pause", ACTION_PAUSE, 2))
        b.addAction(action("Stop", ACTION_RESET, 3))
        return b.build()
    }

    private fun startForegroundCompat() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_RUNNING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_RUNNING, n)
    }

    private fun refreshNotification() {
        if (!Notifier.canPost(this)) return
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_RUNNING, buildNotification())
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "momentum:pomodoro").apply { acquire(timeoutMs) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "momentum.START"
        private const val ACTION_PAUSE = "momentum.PAUSE"
        private const val ACTION_RESUME = "momentum.RESUME"
        private const val ACTION_RESET = "momentum.RESET"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_PHASE = "phase"
        private const val EXTRA_TASK = "task"
        private const val NOTIF_RUNNING = 4001
        private const val NOTIF_DONE = 4002

        private val _state = MutableStateFlow(TimerState())
        val state: StateFlow<TimerState> = _state

        fun start(ctx: Context, minutes: Int, phase: FocusPhase, taskId: String?) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, PomodoroService::class.java).setAction(ACTION_START)
                    .putExtra(EXTRA_MINUTES, minutes).putExtra(EXTRA_PHASE, phase.name).putExtra(EXTRA_TASK, taskId),
            )
        }

        private fun send(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, PomodoroService::class.java).setAction(action))
        }

        fun pause(ctx: Context) = send(ctx, ACTION_PAUSE)
        fun resume(ctx: Context) = send(ctx, ACTION_RESUME)
        fun reset(ctx: Context) = send(ctx, ACTION_RESET)
        fun dismissFinished() { _state.update { it.copy(justFinished = false) } }
    }
}
