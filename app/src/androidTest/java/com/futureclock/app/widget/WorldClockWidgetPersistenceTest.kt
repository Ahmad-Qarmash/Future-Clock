package com.futureclock.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
    fun widgetInstancesPersistIndependentPageStateOnly() {
        WorldClockWidget.savePageForTest(context, 101, 2)
        WorldClockWidget.savePageForTest(context, 202, 5)

        assertEquals(2, WorldClockWidget.pageForTest(context, 101))
        assertEquals(5, WorldClockWidget.pageForTest(context, 202))
        assertEquals(0, WorldClockWidget.pageForTest(context, 303))
    }
}
