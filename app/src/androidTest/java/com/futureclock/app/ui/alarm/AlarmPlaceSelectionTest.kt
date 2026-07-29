package com.futureclock.app.ui.alarm

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.TimePicker
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.ui.world.WorldPickerActivity
import com.futureclock.app.util.AlarmMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class AlarmPlaceSelectionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun clearUserData() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jerusalem"))
        runBlocking(Dispatchers.IO) {
            (context.applicationContext as FutureClockApp).database.clearAllTables()
        }
    }

    @After
    fun releaseIntents() {
        runCatching { Intents.release() }
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun actualCountryAndCityResultCanBeSavedRecreatedAndReopened() {
        val pickerResult = chooseLosAngelesThroughActualPicker()

        Intents.init()
        intending(hasComponent(WorldPickerActivity::class.java.name)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, pickerResult)
        )
        val editor = ActivityScenario.launch<AlarmEditActivity>(
            Intent(context, AlarmEditActivity::class.java)
        )

        onView(withId(R.id.timezone_picker)).perform(click())
        eventually {
            onView(withId(R.id.timezone_picker))
                .check(matches(withText(containsString("America/Los_Angeles"))))
        }
        editor.onActivity {
            it.findViewById<TimePicker>(R.id.time_picker).apply {
                hour = 20
                minute = 0
            }
        }

        editor.recreate()
        eventually {
            onView(withId(R.id.timezone_picker))
                .check(matches(withText(containsString("America/Los_Angeles"))))
        }
        onView(withId(R.id.btn_save)).perform(scrollTo(), click())

        var saved: AlarmEntity? = null
        eventually {
            saved = runBlocking(Dispatchers.IO) {
                (context.applicationContext as FutureClockApp).database.alarmDao()
                    .getAll()
                    .singleOrNull { it.timeZoneId == "America/Los_Angeles" }
            }
            assertNotNull(saved)
        }
        assertEquals(20, saved!!.hour)
        assertEquals(0, saved!!.minute)
        val california = TimeZone.getTimeZone("America/Los_Angeles")
        val scheduledWallTime = Calendar.getInstance(california).apply {
            timeInMillis = saved!!.nextTriggerMs
        }
        assertEquals(20, scheduledWallTime.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, scheduledWallTime.get(Calendar.MINUTE))

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        val recalculated = AlarmMath.nextTrigger(
            saved!!.nextTriggerMs - 60_000L,
            saved!!.hour,
            saved!!.minute,
            saved!!.daysOfWeek,
            saved!!.timeZoneId
        )
        val afterDeviceZoneChange = Calendar.getInstance(california).apply {
            timeInMillis = recalculated
        }
        assertEquals(20, afterDeviceZoneChange.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, afterDeviceZoneChange.get(Calendar.MINUTE))
        editor.close()

        ActivityScenario.launch<AlarmEditActivity>(
            Intent(context, AlarmEditActivity::class.java).putExtra("alarm_id", saved!!.id)
        ).use {
            eventually {
                onView(withId(R.id.timezone_picker))
                    .check(matches(withText(containsString("America/Los_Angeles"))))
            }
        }
    }

    private fun chooseLosAngelesThroughActualPicker(): Intent {
        val scenario = ActivityScenario.launchActivityForResult<WorldPickerActivity>(
            Intent(context, WorldPickerActivity::class.java)
                .putExtra(WorldPickerActivity.EXTRA_MODE, WorldPickerActivity.MODE_ALARM)
        )

        eventually {
            onView(withId(R.id.search))
                .perform(replaceText("United States"), closeSoftKeyboard())
        }
        eventually {
            onView(withId(R.id.recycler)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText("United States")),
                    click()
                )
            )
        }
        eventually {
            onView(withId(R.id.screen_title))
                .check(matches(withText(containsString("United States"))))
        }
        onView(withId(R.id.search)).perform(replaceText("Los Angeles"), closeSoftKeyboard())
        eventually {
            onView(withId(R.id.recycler)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Los Angeles")),
                    click()
                )
            )
        }

        val result = scenario.result
        assertEquals(Activity.RESULT_OK, result.resultCode)
        val data = requireNotNull(result.resultData)
        assertEquals(
            "America/Los_Angeles",
            data.getStringExtra(WorldPickerActivity.EXTRA_TIMEZONE_ID)
        )
        scenario.close()
        return data
    }

    private fun eventually(assertion: () -> Unit) {
        var lastFailure: Throwable? = null
        repeat(100) {
            try {
                assertion()
                return
            } catch (failure: Throwable) {
                lastFailure = failure
                SystemClock.sleep(200)
            }
        }
        throw lastFailure ?: AssertionError("Condition was not met")
    }
}
