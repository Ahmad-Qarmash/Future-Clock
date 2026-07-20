package com.futureclock.app.data.tz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CityCatalogTest {

    @Test
    fun all_isNotEmpty() {
        assertTrue("CityCatalog.ALL must contain at least one city", CityCatalog.ALL.isNotEmpty())
    }

    @Test
    fun search_emptyString_returnsAll() {
        assertEquals("Empty query must return ALL", CityCatalog.ALL, CityCatalog.search(""))
    }

    @Test
    fun search_blankString_returnsAll() {
        assertEquals("Blank query must return ALL", CityCatalog.ALL, CityCatalog.search("   "))
    }

    @Test
    fun search_newYork_findsNewYork() {
        val results = CityCatalog.search("new york")
        assertTrue("Search for 'new york' must return at least one result", results.isNotEmpty())
        assertTrue(
            "Search for 'new york' must include New York: $results",
            results.any { it.name == "New York" }
        )
    }

    @Test
    fun search_isCaseInsensitive() {
        val lower = CityCatalog.search("london")
        val upper = CityCatalog.search("LONDON")
        val mixed = CityCatalog.search("LoNdOn")
        assertEquals("Case must not affect search results", lower, upper)
        assertEquals("Case must not affect search results", lower, mixed)
        assertTrue(lower.any { it.name == "London" })
    }

    @Test
    fun search_matchesByCountry() {
        val results = CityCatalog.search("germany")
        assertTrue("Country search for 'germany' must find at least Berlin", results.isNotEmpty())
        assertTrue(results.any { it.country == "Germany" })
    }

    @Test
    fun search_matchesByTzId() {
        val results = CityCatalog.search("Asia/Tokyo")
        assertTrue("tzId search must find Tokyo", results.isNotEmpty())
        assertTrue(results.any { it.tzId == "Asia/Tokyo" })
    }

    @Test
    fun search_nonexistent_returnsEmpty() {
        val results = CityCatalog.search("xyzznonexistent12345")
        assertTrue("Nonexistent query must return empty list", results.isEmpty())
    }

    @Test
    fun allCities_haveNonBlankName() {
        val blankNames = CityCatalog.ALL.filter { it.name.isBlank() }
        assertTrue("All cities must have a non-blank name; found: $blankNames", blankNames.isEmpty())
    }

    @Test
    fun allCities_haveNonBlankCountry() {
        val blankCountries = CityCatalog.ALL.filter { it.country.isBlank() }
        assertTrue("All cities must have a non-blank country; found: $blankCountries", blankCountries.isEmpty())
    }

    @Test
    fun allCities_haveNonBlankTzId() {
        val blankTzIds = CityCatalog.ALL.filter { it.tzId.isBlank() }
        assertTrue("All cities must have a non-blank tzId; found: $blankTzIds", blankTzIds.isEmpty())
    }

    @Test
    fun allCities_haveUniqueNames() {
        val names = CityCatalog.ALL.map { it.name }
        val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue("City names should be unique within the catalog: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun allTzIds_areParseableByTimeZone() {
        val invalid = CityCatalog.ALL
            .map { it.tzId to TimeZone.getTimeZone(it.tzId) }
            .filter { (id, zone) -> zone.id != id }
        assertTrue("All tzIds must round-trip through TimeZone.getTimeZone, fallbacks found: $invalid", invalid.isEmpty())
    }

    @Test
    fun allTzIds_produceNonNullTimeZones() {
        for (city in CityCatalog.ALL) {
            val zone = TimeZone.getTimeZone(city.tzId)
            assertNotNull("TimeZone must not be null for ${city.tzId}", zone)
            assertFalse(
                "TimeZone id should not be blank for ${city.tzId}",
                zone.id.isBlank()
            )
        }
    }

    @Test
    fun wellKnownCities_arePresent() {
        val byName = CityCatalog.ALL.associateBy { it.name }
        listOf("New York", "London", "Tokyo", "Sydney", "UTC").forEach { name ->
            assertTrue("Catalog must include $name", byName.containsKey(name))
        }
    }

    @Test
    fun utcCity_usesUtcTimezone() {
        val utc = CityCatalog.ALL.firstOrNull { it.name == "UTC" }
        assertNotNull("UTC city must be present", utc)
        assertEquals("UTC", utc!!.tzId)
        val zone = TimeZone.getTimeZone(utc.tzId)
        assertEquals("UTC zone must have zero raw offset", 0, zone.rawOffset)
    }
}
