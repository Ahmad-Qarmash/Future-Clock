package com.futureclock.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.futureclock.app.data.tz.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldClockWidgetPersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearWidgetPreferences() {
        context.getSharedPreferences(WorldClockWidget.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun widgetInstancesPersistIndependentCompletePlaceRecords() {
        val losAngeles = City(
            5_368_361,
            "Los Angeles",
            "United States",
            "US",
            "🇺🇸",
            "California",
            "America/Los_Angeles",
            3_898_747
        )
        val tokyo = City(
            1_850_147,
            "Tokyo",
            "Japan",
            "JP",
            "🇯🇵",
            "Tokyo",
            "Asia/Tokyo",
            8_336_599
        )

        WorldClockWidget.saveCities(context, 101, listOf(losAngeles))
        WorldClockWidget.saveCities(context, 202, listOf(tokyo))

        assertEquals(listOf(losAngeles), WorldClockWidget.loadCities(context, 101))
        assertEquals(listOf(tokyo), WorldClockWidget.loadCities(context, 202))
    }

    @Test
    fun corruptOrMissingWidgetDataFallsBackWithoutCatalogAccess() {
        context.getSharedPreferences(WorldClockWidget.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("widget_303", "[not-json")
            .commit()

        val fallback = WorldClockWidget.loadCities(context, 303)
        assertEquals(3, fallback.size)
        assertTrue(fallback.all { it.name.isNotBlank() && it.tzId.isNotBlank() })
        assertEquals(3, WorldClockWidget.loadCities(context, 404).size)
    }
}
