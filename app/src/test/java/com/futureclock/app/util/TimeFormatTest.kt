package com.futureclock.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class TimeFormatTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private lateinit var originalDefault: TimeZone
    private lateinit var originalLocale: Locale

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

    // ---------- formatTime(zone, use24h, showSeconds) ----------

    @Test
    fun formatTime_24hWithSeconds_usesHmsPattern() {
        val s = TimeFormat.formatTime(utc, use24h = true, showSeconds = true)
        assertNotNull("Formatted time must not be null", s)
        assertEquals("24h with seconds should be 8 chars: HH:mm:ss", 8, s.length)
        assertEquals("Two colons expected", 2, s.count { it == ':' })
        assertTrue("All chars should be digits or colons: $s", s.all { it.isDigit() || it == ':' })
    }

    @Test
    fun formatTime_24hNoSeconds_usesHmPattern() {
        val s = TimeFormat.formatTime(utc, use24h = true, showSeconds = false)
        assertEquals("24h no seconds should be 5 chars: HH:mm", 5, s.length)
        assertEquals("One colon expected", 1, s.count { it == ':' })
        assertTrue("All chars should be digits or colons: $s", s.all { it.isDigit() || it == ':' })
    }

    @Test
    fun formatTime_12hWithSeconds_usesHms12hPattern() {
        val s = TimeFormat.formatTime(utc, use24h = false, showSeconds = true)
        assertTrue("Must end with AM or PM: $s", s.endsWith("AM") || s.endsWith("PM"))
        assertEquals("Two colons expected", 2, s.count { it == ':' })
        assertTrue("12h with seconds should be at least 10 chars", s.length >= 10)
    }

    @Test
    fun formatTime_12hNoSeconds_usesHm12hPattern() {
        val s = TimeFormat.formatTime(utc, use24h = false, showSeconds = false)
        assertTrue("Must end with AM or PM: $s", s.endsWith("AM") || s.endsWith("PM"))
        assertEquals("One colon expected", 1, s.count { it == ':' })
    }

    // ---------- formatTime(zone, use24h, hour, minute) ----------

    @Test
    fun formatTime_24hExplicitHourMinute_afternoon() {
        assertEquals("14:30", TimeFormat.formatTime(utc, use24h = true, hour = 14, minute = 30))
    }

    @Test
    fun formatTime_24hExplicitHourMinute_midnight() {
        assertEquals("00:00", TimeFormat.formatTime(utc, use24h = true, hour = 0, minute = 0))
    }

    @Test
    fun formatTime_24hExplicitHourMinute_endOfDay() {
        assertEquals("23:59", TimeFormat.formatTime(utc, use24h = true, hour = 23, minute = 59))
    }

    @Test
    fun formatTime_24hExplicitHourMinute_padsSingleDigits() {
        assertEquals("09:05", TimeFormat.formatTime(utc, use24h = true, hour = 9, minute = 5))
    }

    @Test
    fun formatTime_12hExplicitHourMinute_midnightIs12AM() {
        assertEquals("12:00 AM", TimeFormat.formatTime(utc, use24h = false, hour = 0, minute = 0))
    }

    @Test
    fun formatTime_12hExplicitHourMinute_noonIs12PM() {
        assertEquals("12:00 PM", TimeFormat.formatTime(utc, use24h = false, hour = 12, minute = 0))
    }

    @Test
    fun formatTime_12hExplicitHourMinute_afternoonUsesPM() {
        assertEquals("3:30 PM", TimeFormat.formatTime(utc, use24h = false, hour = 15, minute = 30))
    }

    @Test
    fun formatTime_12hExplicitHourMinute_morningUsesAM() {
        assertEquals("9:05 AM", TimeFormat.formatTime(utc, use24h = false, hour = 9, minute = 5))
    }

    // ---------- formatTime(use24h, hour, minute) — default zone overload ----------

    @Test
    fun formatTime_defaultZone_24hPadsSingleDigits() {
        assertEquals("09:05", TimeFormat.formatTime(use24h = true, hour = 9, minute = 5))
    }

    @Test
    fun formatTime_defaultZone_12hContainsAmOrPm() {
        val s = TimeFormat.formatTime(use24h = false, hour = 15, minute = 30)
        assertTrue("12h default-zone output should contain 3:30: $s", s.contains("3:30"))
        assertTrue("12h default-zone output should end with PM: $s", s.endsWith("PM"))
    }

    // ---------- formatDate(zone) ----------

    @Test
    fun formatDate_utcZone_isNonEmpty() {
        val s = TimeFormat.formatDate(utc)
        assertNotNull(s)
        assertTrue("formatDate output should not be blank: '$s'", s.isNotBlank())
    }

    @Test
    fun formatDate_utcZone_hasTwoCommasAndAYear() {
        val s = TimeFormat.formatDate(utc)
        assertEquals("Pattern 'EEEE, MMMM d, yyyy' has two commas: $s", 2, s.count { it == ',' })
        assertTrue("formatDate should contain a 4-digit year: $s", Regex("""\b\d{4}\b""").containsMatchIn(s))
    }

    // ---------- formatShortDate(zone) ----------

    @Test
    fun formatShortDate_utcZone_isNonEmpty() {
        val s = TimeFormat.formatShortDate(utc)
        assertNotNull(s)
        assertTrue("formatShortDate should not be blank: '$s'", s.isNotBlank())
    }

    @Test
    fun formatShortDate_utcZone_omitsYearAndCommas() {
        val s = TimeFormat.formatShortDate(utc)
        assertEquals("'MMM d' has no commas: $s", 0, s.count { it == ',' })
        assertTrue("formatShortDate should not contain a 4-digit year: $s",
            !Regex("""\b\d{4}\b""").containsMatchIn(s))
    }

    // ---------- formatDay(zone) ----------

    @Test
    fun formatDay_utcZone_isUppercaseWeekdayName() {
        val s = TimeFormat.formatDay(utc)
        val validDays = setOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        assertTrue("formatDay must be an uppercase weekday name, got '$s'", s in validDays)
    }

    // ---------- formatOffset(zone) ----------

    @Test
    fun formatOffset_utc_returnsZero() {
        assertEquals("UTC+00:00", TimeFormat.formatOffset(utc))
    }

    @Test
    fun formatOffset_tokyo_returnsPositive9() {
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        assertEquals("UTC+09:00", TimeFormat.formatOffset(tokyo))
    }

    @Test
    fun formatOffset_newYork_startsWithUtcSign() {
        val ny = TimeZone.getTimeZone("America/New_York")
        val s = TimeFormat.formatOffset(ny)
        assertTrue("New York offset must start with 'UTC': $s", s.startsWith("UTC"))
        assertTrue("New York offset should be negative or zero: $s",
            s.startsWith("UTC-") || s.startsWith("UTC+00"))
    }

    @Test
    fun formatOffset_matchesPattern() {
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        val s = TimeFormat.formatOffset(tokyo)
        assertTrue("Offset must match UTC±HH:MM: $s", Regex("""^UTC[+\-]\d{2}:\d{2}$""").matches(s))
    }

    // ---------- dayDelta(zone) ----------

    @Test
    fun dayDelta_sameZoneAsDevice_isZero() {
        assertEquals("dayDelta against the device default zone must be 0", 0, TimeFormat.dayDelta(utc))
    }

    @Test
    fun dayDelta_differentZone_isWithinReasonableRange() {
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        val delta = TimeFormat.dayDelta(tokyo)
        assertTrue("dayDelta should be in [-1, +1] for an adjacent zone: $delta", delta in -1..1)
    }

    // ---------- StopwatchFormat.format ----------

    @Test
    fun stopwatchFormat_zeroWithMillis() {
        assertEquals("00:00.00", StopwatchFormat.format(0L, withMillis = true))
    }

    @Test
    fun stopwatchFormat_zeroWithoutMillis() {
        assertEquals("00:00", StopwatchFormat.format(0L, withMillis = false))
    }

    @Test
    fun stopwatchFormat_underOneSecond_padsCentisecond() {
        assertEquals("00:00.10", StopwatchFormat.format(100L, withMillis = true))
    }

    @Test
    fun stopwatchFormat_oneSecondTwentyThreeHundred() {
        assertEquals("00:01.23", StopwatchFormat.format(1234L, withMillis = true))
    }

    @Test
    fun stopwatchFormat_exactMinute() {
        assertEquals("01:00.00", StopwatchFormat.format(60_000L, withMillis = true))
    }

    @Test
    fun stopwatchFormat_hourBoundary_includesHours() {
        assertEquals("1:01:01.00", StopwatchFormat.format(3_661_000L, withMillis = true))
    }

    @Test
    fun stopwatchFormat_justUnderMinute_noHours() {
        assertEquals("00:59.99", StopwatchFormat.format(59_990L, withMillis = true))
    }

    @Test
    fun stopwatchFormat_withMillisFalse_omitsFraction() {
        assertEquals("01:00", StopwatchFormat.format(60_000L, withMillis = false))
        assertEquals("1:01:01", StopwatchFormat.format(3_661_000L, withMillis = false))
    }

    // ---------- CountdownFormat.format ----------

    @Test
    fun countdownFormat_zero_returnsZeroZero() {
        assertEquals("00:00", CountdownFormat.format(0L))
    }

    @Test
    fun countdownFormat_negative_returnsZeroZero() {
        assertEquals("00:00", CountdownFormat.format(-1000L))
    }

    @Test
    fun countdownFormat_oneSecond_returnsZeroZeroOne() {
        assertEquals("00:01", CountdownFormat.format(1_000L))
    }

    @Test
    fun countdownFormat_exactMinute() {
        assertEquals("01:00", CountdownFormat.format(60_000L))
    }

    @Test
    fun countdownFormat_underOneHour() {
        assertEquals("59:59", CountdownFormat.format(59 * 60_000L + 59_000L))
    }

    @Test
    fun countdownFormat_exactHour_includesHours() {
        assertEquals("1:00:00", CountdownFormat.format(3_600_000L))
    }

    @Test
    fun countdownFormat_hoursMinutesSeconds() {
        assertEquals("1:01:01", CountdownFormat.format(3_661_000L))
    }

    // ---------- CountdownLongFormat.format ----------

    @Test
    fun countdownLongFormat_zero_returnsZeroM() {
        assertEquals("0m", CountdownLongFormat.format(0L))
    }

    @Test
    fun countdownLongFormat_negative_returnsZeroM() {
        assertEquals("0m", CountdownLongFormat.format(-1000L))
    }

    @Test
    fun countdownLongFormat_secondsOnly() {
        assertEquals("45s", CountdownLongFormat.format(45_000L))
    }

    @Test
    fun countdownLongFormat_minutesOnly_padsSeconds() {
        assertEquals("1m 00s", CountdownLongFormat.format(60_000L))
    }

    @Test
    fun countdownLongFormat_minutesAndSeconds() {
        assertEquals("59m 59s", CountdownLongFormat.format(59 * 60_000L + 59_000L))
    }

    @Test
    fun countdownLongFormat_exactHour() {
        assertEquals("1h 0m", CountdownLongFormat.format(3_600_000L))
    }

    @Test
    fun countdownLongFormat_hoursAndMinutes() {
        assertEquals("8h 24m", CountdownLongFormat.format(8L * 3_600_000L + 24L * 60_000L))
    }

    @Test
    fun countdownLongFormat_hoursAndMinutes_showsMinutesNotSeconds() {
        assertEquals("1h 1m", CountdownLongFormat.format(3_661_000L))
        assertNotEquals("Hours branch should not include seconds", "1h 1m 1s", CountdownLongFormat.format(3_661_000L))
    }
}
