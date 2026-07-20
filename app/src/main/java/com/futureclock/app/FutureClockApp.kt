package com.futureclock.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.futureclock.app.ads.AdManager
import com.futureclock.app.data.db.AppDatabase
import com.futureclock.app.data.prefs.SettingsRepository
import com.futureclock.app.notification.NotificationChannels
import com.futureclock.app.widget.WidgetUpdateScheduler
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FutureClockApp : Application() {

    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

        createNotificationChannels()

        // Initialize AdMob
        applicationScope.launch {
            try {
                MobileAds.initialize(this@FutureClockApp) {}
                AdManager.initialize(this@FutureClockApp)
            } catch (_: Throwable) { /* ads unavailable in dev */ }
        }

        // Start periodic widget updates
        WidgetUpdateScheduler.scheduleNext(this)
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
