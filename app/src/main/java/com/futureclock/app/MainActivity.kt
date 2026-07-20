package com.futureclock.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.futureclock.app.R
import com.futureclock.app.ads.AdManager
import com.futureclock.app.databinding.ActivityMainBinding
import com.futureclock.app.notification.Actions
import com.futureclock.app.ui.alarm.AlarmFragment
import com.futureclock.app.ui.clock.ClockFragment
import com.futureclock.app.ui.settings.SettingsFragment
import com.futureclock.app.ui.stopwatch.StopwatchFragment
import com.futureclock.app.ui.timer.TimerFragment
import com.futureclock.app.ui.world.WorldFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tabFragments = mutableMapOf<Int, Fragment>()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, R.string.perm_notifications_rationale, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNav()
        setupBannerAd()
        requestPermissionsIfNeeded()
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun setupNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            selectTab(item.itemId)
            AdManager.maybeShowInterstitial(this, AdManager.Trigger.TAB_CHANGE)
            true
        }
        val defaultTab = intent.getIntExtra("default_tab", R.id.nav_clock)
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = defaultTab
        }
    }

    private fun selectTab(itemId: Int) {
        val tag = "tab_$itemId"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        val fragment = existing ?: tabFragments[itemId] ?: createFragment(itemId).also { tabFragments[itemId] = it }

        fm.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }

    private fun createFragment(itemId: Int): Fragment = when (itemId) {
        R.id.nav_clock -> ClockFragment()
        R.id.nav_world -> WorldFragment()
        R.id.nav_alarm -> AlarmFragment()
        R.id.nav_timer -> TimerFragment()
        R.id.nav_stopwatch -> StopwatchFragment()
        R.id.nav_settings -> SettingsFragment()
        else -> ClockFragment()
    }

    private fun setupBannerAd() {
        AdManager.createBanner(this, binding.adContainer)
    }

    private fun requestPermissionsIfNeeded() {
        // Notifications (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(perm)
            }
        }
        // Exact alarm (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                Toast.makeText(this, R.string.perm_exact_alarm_rationale, Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                try { startActivity(intent) } catch (_: Throwable) {}
            }
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val action = intent?.action ?: return
        val tab = when (action) {
            Actions.ACTION_OPEN_CLOCK_TAB -> R.id.nav_clock
            Actions.ACTION_OPEN_WORLD_TAB -> R.id.nav_world
            Actions.ACTION_OPEN_ALARM_TAB -> R.id.nav_alarm
            Actions.ACTION_OPEN_TIMER_TAB -> R.id.nav_timer
            Actions.ACTION_OPEN_STOPWATCH_TAB -> R.id.nav_stopwatch
            else -> return
        }
        binding.bottomNav.selectedItemId = tab
    }
}
