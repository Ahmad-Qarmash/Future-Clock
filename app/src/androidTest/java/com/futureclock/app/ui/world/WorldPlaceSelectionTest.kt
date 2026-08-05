package com.futureclock.app.ui.world

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldPlaceSelectionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearWorldCities() {
        runBlocking(Dispatchers.IO) {
            (context.applicationContext as FutureClockApp).database.clearAllTables()
        }
    }

    @Test
    fun countryAndPlaceSelectionPersistsAWorldClock() {
        val scenario = ActivityScenario.launchActivityForResult<WorldPickerActivity>(
            Intent(context, WorldPickerActivity::class.java)
        )

        onView(withId(R.id.search)).perform(replaceText("Israel"), closeSoftKeyboard())
        waitForPickerResults()
        eventually {
            onView(withId(R.id.recycler)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Israel")),
                    click()
                )
            )
        }
        onView(withId(R.id.search)).perform(replaceText("Nazareth"), closeSoftKeyboard())
        waitForPickerResults()
        eventually {
            onView(withId(R.id.recycler)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Nazareth")),
                    click()
                )
            )
        }

        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        val cities = runBlocking(Dispatchers.IO) {
            (context.applicationContext as FutureClockApp).database.worldCityDao().getAll()
        }
        assertEquals(1, cities.size)
        assertEquals("Nazareth", cities.single().displayName)
        assertEquals("Asia/Jerusalem", cities.single().tzId)
        assertTrue(cities.single().locationId > 0)
        scenario.close()
    }

    private fun waitForPickerResults() {
        eventually {
            onView(withId(R.id.progress))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.recycler)).check(matches(isEnabled()))
        }
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
