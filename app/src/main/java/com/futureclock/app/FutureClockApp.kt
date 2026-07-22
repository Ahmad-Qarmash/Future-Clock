package com.futureclock.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.futureclock.app.data.db.AppDatabase
import com.futureclock.app.data.prefs.SettingsRepository
import com.futureclock.app.notification.NotificationChannels
import com.futureclock.app.widget.WidgetUpdateScheduler
import com.futureclock.app.ui.theme.ThemeController
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FutureClockApp : Application() {

    val applicationScope: CoroutineScope by lazy {
        val handler = CoroutineExceptionHandler { _, t ->
            // Log background coroutine failures without crashing the app.
            android.util.Log.w("FutureClockApp", "Background coroutine failed", t)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val settings: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Resolve the persisted appearance before the first Activity is created.
        val savedTheme = runBlocking(Dispatchers.IO) { settings.themeMode.first() }
        ThemeController.apply(savedTheme)

        createNotificationChannels()

        // Start periodic widget updates. The scheduler itself catches
        // SecurityException from missing exact-alarm permission, but wrap it
        // again here as a defensive guard so a startup crash is impossible.
        runCatching { WidgetUpdateScheduler.scheduleNext(this) }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                NotificationChannels.ALARMS,
                getString(R.string.notif_channel_alarms),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_alarms_desc)
                enableVibration(true)
                setBypassDnd(false)
            },
            NotificationChannel(
                NotificationChannels.TIMER,
                getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.timer_channel_description)
                setSound(null, null)
            },
            NotificationChannel(
                NotificationChannels.STOPWATCH,
                getString(R.string.stopwatch_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.stopwatch_channel_description)
                setSound(null, null)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannels(channels)
    }

    companion object {
        @Volatile lateinit var instance: FutureClockApp
            private set
    }
}
