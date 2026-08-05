package com.futureclock.app.ui.navigation

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationTest {

    @Before
    fun grantNotificationPermission() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
            )
            .close()
    }

    @Test
    fun primaryDestinationsMoreToolsAndSettingsHavePredictableBackBehavior() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_world)).perform(click())
            onView(withText(R.string.world_subtitle)).check(matches(withText(R.string.world_subtitle)))

            onView(withId(R.id.nav_alarm)).perform(click())
            onView(withText(R.string.alarm_subtitle)).check(matches(withText(R.string.alarm_subtitle)))

            onView(withId(R.id.nav_timer)).perform(click())
            onView(withText(R.string.timer_subtitle)).check(matches(withText(R.string.timer_subtitle)))

            onView(withId(R.id.nav_more)).perform(click())
            onView(withText(R.string.more_subtitle)).check(matches(withText(R.string.more_subtitle)))

            onView(withText(R.string.tab_stopwatch)).perform(click())
            onView(withText(R.string.stopwatch_subtitle))
                .check(matches(withText(R.string.stopwatch_subtitle)))
            onView(withContentDescription(R.string.cd_navigate_back)).perform(click())
            onView(withText(R.string.more_subtitle)).check(matches(withText(R.string.more_subtitle)))

            onView(withText(R.string.tab_settings)).perform(click())
            onView(withText(R.string.settings_subtitle))
                .check(matches(withText(R.string.settings_subtitle)))
            onView(withContentDescription(R.string.cd_navigate_back)).perform(click())
            onView(withText(R.string.more_subtitle)).check(matches(withText(R.string.more_subtitle)))

            onView(withId(R.id.nav_world)).perform(click())
            onView(withContentDescription(R.string.settings_shortcut_description)).perform(click())
            onView(withText(R.string.settings_subtitle))
                .check(matches(withText(R.string.settings_subtitle)))
            pressBack()
            onView(withText(R.string.world_subtitle)).check(matches(withText(R.string.world_subtitle)))
        }
    }

    @Test
    fun selectedPrimaryDestinationSurvivesActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.nav_timer)).perform(click())
            scenario.recreate()
            onView(withId(R.id.nav_timer)).check(matches(isSelected()))
            onView(withText(R.string.timer_subtitle)).check(matches(withText(R.string.timer_subtitle)))
        }
    }
}
