package com.futureclock.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.ads.AdManager
import com.futureclock.app.notification.Actions
import com.futureclock.app.notification.Extras
import com.futureclock.app.notification.NotificationChannels
import com.futureclock.app.notification.NotificationIds
import com.futureclock.app.util.CountdownFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TimerService : Service() {

    enum class State { IDLE, RUNNING, PAUSED, FINISHED }
    data class TimerState(
        val state: State = State.IDLE,
        val totalMs: Long = 0L,
        val remainingMs: Long = 0L
    )

    private var countDownTimer: CountDownTimer? = null
    private var finishedShown = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.ACTION_TIMER_START -> {
                val total = intent.getLongExtra(Extras.TIMER_TOTAL_MS, 60_000L)
                startTimer(total)
            }
            Actions.ACTION_TIMER_PAUSE -> pause()
            Actions.ACTION_TIMER_RESET -> reset()
            else -> if (_state.value.state == State.IDLE) startTimer(60_000L)
        }
        return START_STICKY
    }

    private fun startTimer(totalMs: Long) {
        countDownTimer?.cancel()
        val remaining = if (_state.value.state == State.PAUSED) _state.value.remainingMs else totalMs
        finishedShown = false
        startForeground(NotificationIds.TIMER, buildNotification(remaining, getString(R.string.timer_running)))
        countDownTimer = object : CountDownTimer(remaining, 100L) {
            override fun onTick(remaining: Long) {
                _state.value = TimerState(State.RUNNING, totalMs, remaining)
                if (remaining <= 1_000L && !finishedShown) {
                    // We won't mark finished here; onFinish handles it.
                }
                updateNotification(remaining, getString(R.string.timer_running))
            }
            override fun onFinish() {
                _state.value = TimerState(State.FINISHED, totalMs, 0L)
                finishedShown = true
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }.start()
        _state.value = TimerState(State.RUNNING, totalMs, remaining)
    }

    private fun pause() {
        countDownTimer?.cancel()
        _state.value = _state.value.copy(state = State.PAUSED)
        updateNotification(_state.value.remainingMs, getString(R.string.timer_paused))
    }

    private fun reset() {
        countDownTimer?.cancel()
        _state.value = TimerState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(remaining: Long, status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                action = Actions.ACTION_OPEN_TIMER_TAB
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pausePi = pendingIntentFor(Actions.ACTION_TIMER_PAUSE, 1)
        val resetPi = pendingIntentFor(Actions.ACTION_TIMER_RESET, 2)
        return NotificationCompat.Builder(this, NotificationChannels.TIMER)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("$status · ${CountdownFormat.format(remaining)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_pause, getString(R.string.action_pause), pausePi)
            .addAction(R.drawable.ic_reset, getString(R.string.action_reset), resetPi)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun updateNotification(remaining: Long, status: String) {
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) {
            nm.notify(NotificationIds.TIMER, buildNotification(remaining, status))
        }
    }

    private fun pendingIntentFor(action: String, code: Int): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }

    companion object {
        private val _state = MutableStateFlow(TimerState())
        val state: StateFlow<TimerState> = _state

        fun start(context: Context, totalMs: Long) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = Actions.ACTION_TIMER_START
                putExtra(Extras.TIMER_TOTAL_MS, totalMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            context.startService(Intent(context, TimerService::class.java).apply {
                action = Actions.ACTION_TIMER_PAUSE
            })
        }

        fun reset(context: Context) {
            context.startService(Intent(context, TimerService::class.java).apply {
                action = Actions.ACTION_TIMER_RESET
            })
        }
    }
}
