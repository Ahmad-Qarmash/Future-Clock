package com.futureclock.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-preservation-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration2To3PreservesAlarmsAndWorldCities() {
        context.deleteDatabase(databaseName)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { db ->
            db.execSQL(
                """
                CREATE TABLE alarms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    hour INTEGER NOT NULL,
                    minute INTEGER NOT NULL,
                    label TEXT NOT NULL,
                    days_of_week INTEGER NOT NULL,
                    enabled INTEGER NOT NULL,
                    vibrate INTEGER NOT NULL,
                    gradual_volume INTEGER NOT NULL,
                    snooze_minutes INTEGER NOT NULL,
                    sound_uri TEXT NOT NULL,
                    difficulty INTEGER NOT NULL,
                    next_trigger_ms INTEGER NOT NULL,
                    timezone_id TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE world_cities (
                    tzId TEXT NOT NULL PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    country TEXT NOT NULL,
                    flag TEXT NOT NULL,
                    sort_order INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO alarms VALUES
                (7, 6, 45, 'Preserved', 31, 1, 1, 1, 10, '', 0, 123456, 'Asia/Jerusalem')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO world_cities VALUES
                ('America/New_York', 'New York', 'United States', '🇺🇸', 2)
                """.trimIndent()
            )
            db.version = 2
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        runBlocking {
            val alarm = room.alarmDao().getById(7)
            val cities = room.worldCityDao().getAll()
            assertEquals("Preserved", alarm?.label)
            assertEquals("Asia/Jerusalem", alarm?.timeZoneId)
            assertEquals(1, cities.size)
            assertEquals("America/New_York", cities.single().tzId)
            assertTrue(cities.single().locationId < 0)
        }
        room.close()
    }

    @Test
    fun migration1To3AddsTimezoneAndPreservesAllUserData() {
        context.deleteDatabase(databaseName)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { db ->
            db.execSQL(
                """
                CREATE TABLE alarms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    hour INTEGER NOT NULL,
                    minute INTEGER NOT NULL,
                    label TEXT NOT NULL,
                    days_of_week INTEGER NOT NULL,
                    enabled INTEGER NOT NULL,
                    vibrate INTEGER NOT NULL,
                    gradual_volume INTEGER NOT NULL,
                    snooze_minutes INTEGER NOT NULL,
                    sound_uri TEXT NOT NULL,
                    difficulty INTEGER NOT NULL,
                    next_trigger_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE world_cities (
                    tzId TEXT NOT NULL PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    country TEXT NOT NULL,
                    flag TEXT NOT NULL,
                    sort_order INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO alarms VALUES
                (9, 20, 0, 'California', 0, 1, 1, 0, 5, '', 0, 987654)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO world_cities VALUES
                ('America/Los_Angeles', 'Los Angeles', 'United States', '🇺🇸', 0)
                """.trimIndent()
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        runBlocking {
            val alarm = room.alarmDao().getById(9)
            val city = room.worldCityDao().getAll().single()
            assertEquals("California", alarm?.label)
            assertEquals("", alarm?.timeZoneId)
            assertEquals("America/Los_Angeles", city.tzId)
            assertTrue(city.locationId < 0)
        }
        room.openHelper.readableDatabase.query(
            "SELECT identity_hash FROM room_master_table WHERE id = 42"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("b240940c7255397951d7351342c9ab5c", cursor.getString(0))
        }
        room.close()
    }
}
