package com.futureclock.app.data.tz

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CityCatalogInstallationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseFile get() = File(context.noBackupFilesDir, "places-v1.sqlite")
    private val temporaryFile get() = File(context.noBackupFilesDir, "places-v1.sqlite.tmp")

    @After
    fun closeCatalog() {
        CityCatalog.resetForTest()
    }

    @Test
    fun freshInstallAndProcessRestartExposeCatalog() {
        CityCatalog.resetForTest()
        databaseFile.delete()
        context.getSharedPreferences("place_catalog_install", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        assertTrue(CityCatalog.get(context).placeCount() >= 200_000)
        assertTrue(databaseFile.length() > 1_000_000)
        assertFalse("Temporary catalog must be removed after installation", temporaryFile.exists())
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { assertEquals(2, it.version) }

        CityCatalog.resetForTest()
        assertTrue(CityCatalog.get(context).search("São Paulo").isNotEmpty())
    }

    @Test
    fun partialInstalledFileIsSafelyReplaced() {
        CityCatalog.resetForTest()
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeBytes(ByteArray(256) { 0x41 })
        context.getSharedPreferences("place_catalog_install", Context.MODE_PRIVATE)
            .edit()
            .putInt("installed_version", 2)
            .commit()

        assertTrue(CityCatalog.get(context).placeCount() >= 200_000)
        assertTrue(databaseFile.length() > 1_000_000)
    }

    @Test
    fun everyCatalogTimezoneResolvesOnAndroid() {
        val available = TimeZone.getAvailableIDs().toHashSet()
        val catalogIds = CityCatalog.get(context).timeZoneIds()
        assertEquals(emptyList<String>(), catalogIds.filterNot(available::contains))
    }

    @Test
    fun indexedSearchCoversAliasesDiacriticsPunctuationAndCountryFilters() {
        val catalog = CityCatalog.get(context)
        val searches = listOf(
            "  new YORK  ",
            "NYC",
            "Sao Paulo",
            "São Paulo",
            "st. john's",
            "America New York",
            "United States"
        )
        searches.forEach { query ->
            val results = catalog.search(query)
            assertFalse("No results for '$query'", results.isEmpty())
            assertEquals(
                "Duplicate IDs for '$query'",
                results.map(City::id).distinct().size,
                results.size
            )
        }

        val first = catalog.search("Los Ang", countryCode = "US")
        val second = catalog.search("Los Ang", countryCode = "US")
        assertEquals(first.map(City::id), second.map(City::id))
        assertTrue(first.all { it.countryCode == "US" })
        assertTrue(catalog.search("place-that-does-not-exist-847291").isEmpty())
        assertEquals(250, catalog.search("").size)
    }
}
