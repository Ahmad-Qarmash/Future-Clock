package com.futureclock.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.futureclock.app.R
import com.futureclock.app.ads.AdManager
import com.futureclock.app.ads.ConsentManager
import com.futureclock.app.databinding.ActivityMainBinding
import com.futureclock.app.notification.Actions
import com.futureclock.app.ui.alarm.AlarmFragment
import com.futureclock.app.ui.clock.ClockFragment
import com.futureclock.app.ui.more.MoreFragment
import com.futureclock.app.ui.settings.SettingsFragment
import com.futureclock.app.ui.stopwatch.StopwatchFragment
import com.futureclock.app.ui.timer.TimerFragment
import com.futureclock.app.ui.world.WorldFragment
import com.futureclock.app.ui.common.UiFeedback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tabFragments = mutableMapOf<Int, Fragment>()
    private var pendingWorldCityId: Long? = null

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) UiFeedback.show(binding.root, R.string.perm_notifications_rationale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNav(savedInstanceState)
        ConsentManager.gatherConsent(this) {
            AdManager.initialize(applicationContext)
            setupBannerAd()
        }
        requestPermissionsIfNeeded()
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra(EXTRA_DEFAULT_TAB)) {
            binding.bottomNav.selectedItemId =
                intent.getIntExtra(EXTRA_DEFAULT_TAB, R.id.nav_clock)
        }
        handleDeepLink(intent)
    }

    private fun setupNav(savedInstanceState: Bundle?) {
        binding.bottomNav.setOnItemSelectedListener { item ->
            selectTab(item.itemId)
            true
        }
        val defaultTab = intent.getIntExtra(EXTRA_DEFAULT_TAB, R.id.nav_clock)
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = defaultTab
        }
    }

    private fun selectTab(itemId: Int) {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate(
                null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }
        val tag = "tab_$itemId"
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        val fragment = existing ?: tabFragments[itemId] ?: createFragment(itemId).also { tabFragments[itemId] = it }

        fm.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }

    private fun createFragment(itemId: Int): Fragment = when (itemId) {
        R.id.nav_clock -> ClockFragment()
        R.id.nav_world -> WorldFragment()
        R.id.nav_alarm -> AlarmFragment()
        R.id.nav_timer -> TimerFragment()
        R.id.nav_more -> MoreFragment()
        else -> ClockFragment()
    }

    /** Opens the Settings screen on top of the current tab (accessible from the Clock tab gear). */
    fun openSettings() {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, SettingsFragment(), "tab_settings")
            .addToBackStack(null)
            .commit()
    }

    /** Stopwatch remains one predictable tap inside More and retains normal Back behavior. */
    fun openStopwatchFromMore() {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, StopwatchFragment(), "more_stopwatch")
            .addToBackStack(null)
            .commit()
    }

    /** Opens the launcher-widget settings screen with normal Back navigation. */
    fun openWidgetSettings() {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(
                R.id.fragment_container,
                com.futureclock.app.ui.settings.WidgetSettingsFragment(),
                "widget_settings"
            )
            .addToBackStack(null)
            .commit()
    }

    fun consumePendingWorldCityId(): Long? = pendingWorldCityId.also { pendingWorldCityId = null }

    private fun setupBannerAd() {
        val playServices = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(this)
        if (playServices != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            // No Google Play Services: ads won't work, so hide the slot and keep the app open.
            binding.adContainer.visibility = android.view.View.GONE
            return
        }
        try {
            AdManager.createBanner(this, binding.adContainer)
        } catch (_: Throwable) {
            binding.adContainer.visibility = android.view.View.GONE
        }
    }

    private fun requestPermissionsIfNeeded() {
        // Notifications (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(perm)
            }
        }
        // Exact alarm (API 31+): warn once per launch but never kick the user out
        // of the app. Opening Settings here looked like a crash on first run.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                UiFeedback.show(binding.root, R.string.perm_exact_alarm_rationale)
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
            Actions.ACTION_OPEN_STOPWATCH_TAB -> R.id.nav_more
            else -> return
        }
        if (action == Actions.ACTION_OPEN_WORLD_TAB && intent.hasExtra(EXTRA_WORLD_CITY_ID)) {
            pendingWorldCityId = intent.getLongExtra(EXTRA_WORLD_CITY_ID, NO_WORLD_CITY_ID)
                .takeIf { it != NO_WORLD_CITY_ID }
        }
        binding.bottomNav.selectedItemId = tab
        if (action == Actions.ACTION_OPEN_WORLD_TAB && pendingWorldCityId != null) {
            binding.bottomNav.post {
                (supportFragmentManager.findFragmentByTag("tab_${R.id.nav_world}") as? WorldFragment)
                    ?.focusCity(pendingWorldCityId ?: return@post)
            }
        }
        if (action == Actions.ACTION_OPEN_STOPWATCH_TAB) {
            binding.bottomNav.post { openStopwatchFromMore() }
        }
    }

    override fun onResume() {
        super.onResume()
        AdManager.resumeBanner()
    }

    override fun onPause() {
        AdManager.pauseBanner()
        super.onPause()
    }

    override fun onDestroy() {
        AdManager.destroyBanner()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DEFAULT_TAB = "default_tab"
        const val EXTRA_WORLD_CITY_ID = "world_city_id"
        private const val NO_WORLD_CITY_ID = Long.MIN_VALUE
    }
}
