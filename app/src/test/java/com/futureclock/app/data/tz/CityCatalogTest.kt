package com.futureclock.app.data.tz

import org.junit.Assert.assertEquals
import org.junit.Test

class CityCatalogTest {

    @Test
    fun searchExpressionSupportsMultipleWordsAndPrefixes() {
        assertEquals("new* AND york*", CitySearch.matchExpression("  new york  "))
    }

    @Test
    fun searchExpressionSupportsUnicodePlaceNames() {
        assertEquals("São* AND Paulo*", CitySearch.matchExpression("São Paulo"))
    }

    @Test
    fun searchExpressionIgnoresPunctuation() {
        assertEquals("st* AND john* AND s*", CitySearch.matchExpression("st. john's"))
    }

    @Test
    fun areaLabelIncludesRegionWhenAvailable() {
        val city = City(1, "Springfield", "United States", "US", "🇺🇸", "Illinois", "America/Chicago", 0)
        assertEquals("Illinois, United States", city.areaLabel)
    }

    @Test
    fun areaLabelDoesNotRepeatCountry() {
        val city = City(1, "Singapore", "Singapore", "SG", "🇸🇬", "Singapore", "Asia/Singapore", 0)
        assertEquals("Singapore", city.areaLabel)
    }
}
