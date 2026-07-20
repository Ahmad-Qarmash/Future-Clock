package com.futureclock.app.util

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit 4 test helper that prints a brief summary of the test counts
 * across the unit-test suite. Run alongside the other tests.
 */
class TestSummary {

    @Test
    fun printTestCountsSummary() {
        val timeFormat = countTests("com.futureclock.app.util.TimeFormatTest")
        val alarmMath = countTests("com.futureclock.app.util.AlarmMathTest")
        val cityCatalog = countTests("com.futureclock.app.data.tz.CityCatalogTest")
        val total = timeFormat + alarmMath + cityCatalog

        println("==============================================")
        println("[SUMMARY] Future Clock unit-test inventory")
        println("[SUMMARY] TimeFormatTest  : $timeFormat tests")
        println("[SUMMARY] AlarmMathTest   : $alarmMath tests")
        println("[SUMMARY] CityCatalogTest : $cityCatalog tests")
        println("[SUMMARY] TOTAL           : $total tests")
        println("==============================================")

        assertTrue("TimeFormatTest must declare at least one @Test", timeFormat > 0)
        assertTrue("AlarmMathTest must declare at least one @Test", alarmMath > 0)
        assertTrue("CityCatalogTest must declare at least one @Test", cityCatalog > 0)
        assertTrue("Test suite must have a non-zero total", total > 0)
    }

    private fun countTests(fqcn: String): Int = try {
        val cls = Class.forName(fqcn)
        cls.declaredMethods.count { method ->
            method.isAnnotationPresent(org.junit.Test::class.java)
        }
    } catch (e: ClassNotFoundException) {
        println("[SUMMARY] (missing) $fqcn — ${e.message}")
        0
    }
}
