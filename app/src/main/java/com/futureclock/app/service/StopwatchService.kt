package com.futureclock.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.notification.Actions
import com.futureclock.app.notification.NotificationChannels
import com.futureclock.app.notification.NotificationIds
import com.futureclock.app.util.StopwatchFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StopwatchService : Service() {

    data class Lap(val index: Int, val lapMs: Long, val totalMs: Long)
    data class State(
        val running: Boolean = false,
        val baseElapsed: Long = 0L,
        val lastResumeAt: Long = 0L,
        val laps: List<Lap> = emptyList()
    ) {
        val totalMs: Long
            get() = if (running) baseElapsed + (SystemClock.elapsedRealtime() - lastResumeAt) else baseElapsed
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.ACTION_STOPWATCH_START -> start()
            Actions.ACTION_STOPWATCH_PAUSE -> pause()
            Actions.ACTION_STOPWATCH_RESET -> reset()
            Actions.ACTION_STOPWATCH_LAP -> lap()
        }
        return START_STICKY
    }

    private fun start() {
        if (_state.value.running) return
        _state.value = _state.value.copy(
            running = true,
            lastResumeAt = SystemClock.elapsedRealtime()
        )
        startForegroundIfNeeded()
        startTicking()
    }

    private fun pause() {
        if (!_state.value.running) return
        val now = SystemClock.elapsedRealtime()
        val newBase = _state.value.baseElapsed + (now - _state.value.lastResumeAt)
        _state.value = _state.value.copy(running = false, baseElapsed = newBase, lastResumeAt = now)
        tickJob?.cancel()
        updateNotification()
    }

    private fun reset() {
        _state.value = State()
        tickJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun lap() {
        val now = SystemClock.elapsedRealtime()
        val total = if (_state.value.running)
            _state.value.baseElapsed + (now - _state.value.lastResumeAt)
        else _state.value.baseElapsed
        val laps = _state.value.laps
        val lastTotal = laps.lastOrNull()?.totalMs ?: 0L
        val lapMs = total - lastTotal
        val newLap = Lap(laps.size + 1, lapMs, total)
        _state.value = _state.value.copy(laps = listOf(newLap) + laps)
    }

    private fun startForegroundIfNeeded() {
        val notification = buildNotification()
        startForeground(NotificationIds.STOPWATCH, notification)
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (_state.value.running) {
                withContext(Dispatchers.Main) { updateNotification() }
                delay(100L)
            }
        }
    }

    private fun updateNotification() {
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) {
            nm.notify(NotificationIds.STOPWATCH, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val s = _state.value
        val text = StopwatchFormat.format(s.totalMs)
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { action = Actions.ACTION_OPEN_STOPWATCH_TAB },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val togglePi = pendingIntentFor(
            if (s.running) Actions.ACTION_STOPWATCH_PAUSE else Actions.ACTION_STOPWATCH_START, 1
        )
        val lapPi = pendingIntentFor(Actions.ACTION_STOPWATCH_LAP, 2)
        val resetPi = pendingIntentFor(Actions.ACTION_STOPWATCH_RESET, 3)
        return NotificationCompat.Builder(this, NotificationChannels.STOPWATCH)
            .setSmallIcon(R.drawable.ic_stopwatch)
            .setContentTitle(getString(R.string.tab_stopwatch))
            .setContentText(text)
            .setOngoing(s.running)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(
                if (s.running) R.drawable.ic_pause else R.drawable.ic_play,
                if (s.running) getString(R.string.action_pause) else getString(R.string.action_start),
                togglePi
            )
            .addAction(R.drawable.ic_lap, getString(R.string.action_lap), lapPi)
            .addAction(R.drawable.ic_reset, getString(R.string.action_reset), resetPi)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .build()
    }

    private fun pendingIntentFor(action: String, code: Int): PendingIntent {
        val intent = Intent(this, StopwatchService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state

        fun start(context: Context) {
            context.startService(Intent(context, StopwatchService::class.java).apply {
                action = Actions.ACTION_STOPWATCH_START
            })
        }
        fun pause(context: Context) {
            context.startService(Intent(context, StopwatchService::class.java).apply {
                action = Actions.ACTION_STOPWATCH_PAUSE
            })
        }
        fun reset(context: Context) {
            context.startService(Intent(context, StopwatchService::class.java).apply {
                action = Actions.ACTION_STOPWATCH_RESET
            })
        }
        fun lap(context: Context) {
            context.startService(Intent(context, StopwatchService::class.java).apply {
                action = Actions.ACTION_STOPWATCH_LAP
            })
        }
    }
}
