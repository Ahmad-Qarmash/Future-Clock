package com.futureclock.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, WorldCityEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun worldCityDao(): WorldCityDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "futureclock.db"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /** Keeps existing alarms intact. A blank zone preserves their previous device-local behavior. */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN timezone_id TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Gives every saved place its own identity so locations sharing a timezone can coexist. */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE world_cities_new (
                        location_id INTEGER NOT NULL,
                        tzId TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        country TEXT NOT NULL,
                        flag TEXT NOT NULL,
                        sort_order INTEGER NOT NULL,
                        PRIMARY KEY(location_id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO world_cities_new
                        (location_id, tzId, display_name, country, flag, sort_order)
                    SELECT -rowid, tzId, display_name, country, flag, sort_order
                    FROM world_cities
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE world_cities")
                db.execSQL("ALTER TABLE world_cities_new RENAME TO world_cities")
            }
        }

        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
