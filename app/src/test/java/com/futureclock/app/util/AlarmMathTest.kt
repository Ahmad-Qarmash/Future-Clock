package com.futureclock.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AlarmMathTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private lateinit var originalDefault: TimeZone
    private lateinit var originalLocale: Locale

    // 2024-01-15 is a Monday.
    private val nowMonday1030Utc: Long = utcTimestamp(2024, Calendar.JANUARY, 15, 10, 30, 0)

    @Before
    fun setUp() {
        originalDefault = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(utc)
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalDefault)
        Locale.setDefault(originalLocale)
    }

    private fun utcTimestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        val c = Calendar.getInstance(utc)
        c.clear()
        c.set(year, month, day, hour, minute, second)
        return c.timeInMillis
    }

    // ---------- nextTrigger ----------

    @Test
    fun nextTrigger_oneShotFutureToday_returnsSameDay() {
        val expected = utcTimestamp(2024, Calendar.JANUARY, 15, 14, 0, 0)
        val actual = AlarmMath.nextTrigger(nowMonday1030Utc, hour = 14, minute = 0, daysOfWeek = 0)
        assertEquals("One-shot alarm for a future time today must fire today", expected, actual)
    }

    @Test
    fun nextTrigger_oneShotPastTime_rollsToNextDay() {
        val expected = utcTimestamp(2024, Calendar.JANUARY, 16, 8, 0, 0)
        val actual = AlarmMath.nextTrigger(nowMonday1030Utc, hour = 8, minute = 0, daysOfWeek = 0)
        assertEquals("One-shot alarm for a past time must roll to next day", expected, actual)
    }

    @Test
    fun nextTrigger_weeklyOnThursday_findsThisThursday() {
        val expected = utcTimestamp(2024, Calendar.JANUARY, 18, 9, 0, 0) // Thursday
        val actual = AlarmMath.nextTrigger(nowMonday1030Utc, hour = 9, minute = 0,
            daysOfWeek = AlarmMath.DOW_THU)
        assertEquals("Weekly alarm on Thursday from Monday 10:30 fires this Thursday 09:00", expected, actual)
    }

    @Test
    fun nextTrigger_weeklyOnTuesdayAndFriday_picksSoonest() {
        val expected = utcTimestamp(2024, Calendar.JANUARY, 16, 9, 0, 0) // Tuesday
        val actual = AlarmMath.nextTrigger(nowMonday1030Utc, hour = 9, minute = 0,
            daysOfWeek = AlarmMath.DOW_TUE or AlarmMath.DOW_FRI)
        assertEquals("Weekly alarm on Tue|Fri from Monday 10:30 must pick Tuesday", expected, actual)
    }

    @Test
    fun nextTrigger_weeklyOnFridayAfterFridayMidday_picksNextFriday() {
        // now = Friday 12:00, alarm at 08:00, only Friday set. Today's 08:00 already past → next Friday.
        val fridayMidday = utcTimestamp(2024, Calendar.JANUARY, 19, 12, 0, 0)
        val expected = utcTimestamp(2024, Calendar.JANUARY, 26, 8, 0, 0) // Next Friday
        val actual = AlarmMath.nextTrigger(fridayMidday, hour = 8, minute = 0, daysOfWeek = AlarmMath.DOW_FRI)
        assertEquals("Weekly Friday alarm past today's 08:00 must roll to next Friday", expected, actual)
    }

    @Test
    fun nextTrigger_weeklyAllWeekdaysSetOnFridayMidday_picksNextMonday() {
        // now = Friday 12:00, alarm at 08:00, mask = Mon..Thu. All of today's bits are already past.
        val fridayMidday = utcTimestamp(2024, Calendar.JANUARY, 19, 12, 0, 0)
        val expected = utcTimestamp(2024, Calendar.JANUARY, 22, 8, 0, 0) // Next Monday
        val actual = AlarmMath.nextTrigger(
            fridayMidday, hour = 8, minute = 0,
            daysOfWeek = AlarmMath.DOW_MON or AlarmMath.DOW_TUE or AlarmMath.DOW_WED or AlarmMath.DOW_THU
        )
        assertEquals("Weekly alarm with only past-this-week days must pick the next week's soonest", expected, actual)
    }

    @Test
    fun nextTrigger_weeklyEveryDayFromMondayLateDay_picksTomorrow() {
        // now = Monday 23:30, alarm at 08:00, every day set. Today's 08:00 is in the past → tomorrow's 08:00.
        val lateMonday = utcTimestamp(2024, Calendar.JANUARY, 15, 23, 30, 0)
        val expected = utcTimestamp(2024, Calendar.JANUARY, 16, 8, 0, 0)
        val actual = AlarmMath.nextTrigger(lateMonday, hour = 8, minute = 0, daysOfWeek = 0x7F)
        assertEquals("Daily alarm with today's 08:00 past must fire tomorrow 08:00", expected, actual)
    }

    // ---------- formatDays ----------

    @Test
    fun formatDays_zero_returnsEmptyString() {
        assertEquals("", AlarmMath.formatDays(0))
    }

    @Test
    fun formatDays_allDaysMask_returnsEveryDay() {
        assertEquals("Every day", AlarmMath.formatDays(0x7F))
    }

    @Test
    fun formatDays_weekdaysOnly_returnsWeekdays() {
        val weekdays = AlarmMath.DOW_MON or AlarmMath.DOW_TUE or AlarmMath.DOW_WED or
            AlarmMath.DOW_THU or AlarmMath.DOW_FRI
        assertEquals("Weekdays", AlarmMath.formatDays(weekdays))
    }

    @Test
    fun formatDays_weekendOnly_returnsWeekend() {
        val weekend = AlarmMath.DOW_SAT or AlarmMath.DOW_SUN
        assertEquals("Weekend", AlarmMath.formatDays(weekend))
    }

    @Test
    fun formatDays_mondayWednesdayFriday_returnsCommaSeparated() {
        val mask = AlarmMath.DOW_MON or AlarmMath.DOW_WED or AlarmMath.DOW_FRI
        assertEquals("Mon, Wed, Fri", AlarmMath.formatDays(mask))
    }

    @Test
    fun formatDays_singleDay_returnsName() {
        assertEquals("Mon", AlarmMath.formatDays(AlarmMath.DOW_MON))
        assertEquals("Sun", AlarmMath.formatDays(AlarmMath.DOW_SUN))
    }

    @Test
    fun formatDays_adjacentDays_returnsInOrder() {
        val mask = AlarmMath.DOW_MON or AlarmMath.DOW_TUE
        assertEquals("Mon, Tue", AlarmMath.formatDays(mask))
    }

    @Test
    fun formatDays_distinctFromWeekdays_allSevenIsNotWeekdays() {
        assertNotEquals("0x7F must not be labeled Weekdays", "Weekdays", AlarmMath.formatDays(0x7F))
    }

    // ---------- hasDay ----------

    @Test
    fun hasDay_bitIsSet_returnsTrue() {
        assertTrue(AlarmMath.hasDay(AlarmMath.DOW_WED, 2)) // bit 2 == DOW_WED
        assertTrue(AlarmMath.hasDay(0x7F, AlarmMath.DOW_MON))
        assertTrue(AlarmMath.hasDay(0x7F, AlarmMath.DOW_SUN))
    }

    @Test
    fun hasDay_bitNotSet_returnsFalse() {
        assertFalse(AlarmMath.hasDay(AlarmMath.DOW_FRI, 0)) // bit 0 (Mon) not set
        assertFalse(AlarmMath.hasDay(0, AlarmMath.DOW_MON))
    }

    @Test
    fun hasDay_zeroMaskShortCircuits_returnsFalse() {
        // The implementation returns false when the mask itself is zero.
        assertFalse("Zero mask must return false", AlarmMath.hasDay(0, 0))
        assertFalse("Zero mask must return false for any bit", AlarmMath.hasDay(0, 6))
    }

    @Test
    fun hasDay_individualBitsForEachDay() {
        val days = listOf(
            AlarmMath.DOW_MON to 0, AlarmMath.DOW_TUE to 1, AlarmMath.DOW_WED to 2,
            AlarmMath.DOW_THU to 3, AlarmMath.DOW_FRI to 4, AlarmMath.DOW_SAT to 5,
            AlarmMath.DOW_SUN to 6
        )
        for ((mask, bit) in days) {
            assertTrue("Bit $bit should be set in ${Integer.toBinaryString(mask)}",
                AlarmMath.hasDay(mask, bit))
        }
    }
}
