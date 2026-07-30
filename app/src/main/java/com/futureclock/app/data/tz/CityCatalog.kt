package com.futureclock.app.data.tz

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.system.Os
import android.util.Log
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream

data class City(
    val id: Long,
    val name: String,
    val country: String,
    val countryCode: String,
    val flag: String,
    val admin1: String,
    val tzId: String,
    val population: Long
) {
    val areaLabel: String
        get() = listOf(admin1, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

data class Country(
    val code: String,
    val name: String,
    val flag: String,
    val placeCount: Int
)

internal object CitySearch {
    fun matchExpression(query: String): String = query.trim()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotBlank() }
        .take(8)
        .joinToString(" AND ") { "$it*" }
}

/** Indexed, offline catalog of 235,000+ cities, towns, villages, and administrative seats. */
class CityCatalog private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val databaseFile = File(appContext.noBackupFilesDir, DATABASE_NAME)

    @Volatile
    private var database: SQLiteDatabase? = null
    @Volatile
    private var countryCache: List<Country>? = null

    /** Starts installation/validation on a caller-selected background dispatcher. */
    fun prepare() {
        readableDatabase()
    }

    fun featured(limit: Int = DEFAULT_LIMIT): List<City> = queryPlaces(
        """
        SELECT id, name, country, country_code, flag, admin1, timezone_id, population
        FROM places
        ORDER BY population DESC, name COLLATE NOCASE
        LIMIT ?
        """.trimIndent(),
        arrayOf(limit.coerceIn(1, MAX_LIMIT).toString())
    )

    fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        countryCode: String? = null
    ): List<City> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return if (countryCode == null) featured(limit) else citiesForCountry(countryCode, limit)
        }
        val match = CitySearch.matchExpression(trimmed)
        if (match.isBlank()) return emptyList()

        val countryClause = if (countryCode == null) "" else "AND p.country_code = ?"
        val args = buildList {
            add(match)
            if (countryCode != null) add(countryCode)
            add(trimmed)
            add("$trimmed%")
            add(limit.coerceIn(1, MAX_LIMIT).toString())
        }.toTypedArray()
        return queryPlaces(
            """
            SELECT p.id, p.name, p.country, p.country_code, p.flag, p.admin1,
                   p.timezone_id, p.population
            FROM places_fts
            JOIN places p ON p.id = places_fts.docid
            WHERE places_fts MATCH ?
            $countryClause
            ORDER BY
                CASE
                    WHEN lower(p.name) = lower(?) THEN 0
                    WHEN lower(p.name) LIKE lower(?) THEN 1
                    ELSE 2
                END,
                p.population DESC,
                p.name COLLATE NOCASE,
                p.id
            LIMIT ?
            """.trimIndent(),
            args
        )
    }

    fun countries(query: String = ""): List<Country> {
        val all = countryCache ?: synchronized(this) {
            countryCache ?: loadCountries().also { countryCache = it }
        }
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return all
        return all.filter {
            normalize(it.name).contains(normalizedQuery) ||
                normalize(it.code).contains(normalizedQuery)
        }
    }

    fun citiesForCountry(countryCode: String, limit: Int = DEFAULT_LIMIT): List<City> = queryPlaces(
        """
        SELECT id, name, country, country_code, flag, admin1, timezone_id, population
        FROM places
        WHERE country_code = ?
        ORDER BY population DESC, name COLLATE NOCASE, id
        LIMIT ?
        """.trimIndent(),
        arrayOf(countryCode, limit.coerceIn(1, MAX_LIMIT).toString())
    )

    fun findById(placeId: Long): City? = queryPlaces(
        """
        SELECT id, name, country, country_code, flag, admin1, timezone_id, population
        FROM places
        WHERE id = ?
        LIMIT 1
        """.trimIndent(),
        arrayOf(placeId.toString())
    ).firstOrNull()

    fun findByTimeZone(timeZoneId: String): City? = queryPlaces(
        """
        SELECT id, name, country, country_code, flag, admin1, timezone_id, population
        FROM places
        WHERE timezone_id = ?
        ORDER BY population DESC
        LIMIT 1
        """.trimIndent(),
        arrayOf(timeZoneId)
    ).firstOrNull()

    fun placeCount(): Int {
        readableDatabase().rawQuery(
            "SELECT value FROM metadata WHERE key = 'place_count'",
            emptyArray()
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull() ?: 0 else 0
        }
    }

    fun timeZoneIds(): List<String> {
        val ids = ArrayList<String>()
        readableDatabase().rawQuery(
            "SELECT DISTINCT timezone_id FROM places ORDER BY timezone_id",
            emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getString(0)
        }
        return ids
    }

    private fun queryPlaces(sql: String, args: Array<String>): List<City> {
        val cities = ArrayList<City>()
        readableDatabase().rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                cities += City(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    country = cursor.getString(2),
                    countryCode = cursor.getString(3),
                    flag = cursor.getString(4),
                    admin1 = cursor.getString(5),
                    tzId = cursor.getString(6),
                    population = cursor.getLong(7)
                )
            }
        }
        return cities
    }

    private fun loadCountries(): List<Country> {
        val result = ArrayList<Country>()
        readableDatabase().rawQuery(
            """
            SELECT country_code, country, flag, COUNT(*)
            FROM places
            GROUP BY country_code, country, flag
            ORDER BY country COLLATE NOCASE, country_code
            """.trimIndent(),
            emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Country(
                    code = cursor.getString(0),
                    name = cursor.getString(1),
                    flag = cursor.getString(2),
                    placeCount = cursor.getInt(3)
                )
            }
        }
        return result
    }

    private fun readableDatabase(): SQLiteDatabase {
        database?.let { if (it.isOpen) return it }
        return synchronized(this) {
            database?.takeIf { it.isOpen } ?: run {
                installDatabaseIfNeeded()
                openValidatedDatabase().also { database = it }
            }
        }
    }

    private fun installDatabaseIfNeeded() {
        val installedVersion = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_INSTALLED_VERSION, 0)
        if (
            installedVersion == CATALOG_VERSION &&
            databaseFile.isFile &&
            databaseFile.length() > MIN_DATABASE_BYTES &&
            isDatabaseCurrent(databaseFile)
        ) return

        check(databaseFile.parentFile?.mkdirs() != false || databaseFile.parentFile?.isDirectory == true) {
            "Unable to create the place catalog directory"
        }
        val temporary = File(databaseFile.parentFile, "${databaseFile.name}.tmp")
        if (temporary.exists()) check(temporary.delete()) { "Unable to remove a partial place catalog" }
        try {
            appContext.assets.open(ASSET_NAME).use { compressed ->
                GZIPInputStream(compressed).use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
            }
            check(temporary.length() > MIN_DATABASE_BYTES) { "Place catalog asset is incomplete" }
            validateNewDatabase(temporary)

            // rename(2) atomically replaces the old disposable catalog on the same filesystem.
            // User alarms and world-clock selections live in Room's separate future_clock.db.
            Os.rename(temporary.absolutePath, databaseFile.absolutePath)
            deleteSidecar(databaseFile, "-journal")
            deleteSidecar(databaseFile, "-wal")
            deleteSidecar(databaseFile, "-shm")
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_INSTALLED_VERSION, CATALOG_VERSION)
                .apply()
        } catch (error: Exception) {
            deleteSidecar(temporary, "-journal")
            deleteSidecar(temporary, "-wal")
            deleteSidecar(temporary, "-shm")
            if (temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "Unable to remove failed catalog installation ${temporary.name}")
            }
            throw error
        }
    }

    private fun openValidatedDatabase(): SQLiteDatabase {
        return try {
            openReadOnly(databaseFile).also(::validateOpenDatabase)
        } catch (_: RuntimeException) {
            database?.close()
            database = null
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_INSTALLED_VERSION)
                .apply()
            installDatabaseIfNeeded()
            openReadOnly(databaseFile).also(::validateOpenDatabase)
        }
    }

    private fun isDatabaseCurrent(file: File): Boolean = runCatching {
        openReadOnly(file).use(::validateOpenDatabase)
    }.isSuccess

    private fun validateOpenDatabase(db: SQLiteDatabase) {
        check(db.version == CATALOG_VERSION) {
            "Unsupported place catalog version ${db.version}"
        }
        db.rawQuery(
            """
            SELECT
                (SELECT value FROM metadata WHERE key = 'catalog_version'),
                (SELECT value FROM metadata WHERE key = 'place_count'),
                EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'places'),
                EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'places_fts'),
                EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = 'places_country_rank_idx')
            """.trimIndent(),
            emptyArray()
        ).use { cursor ->
            check(
                cursor.moveToFirst() &&
                    cursor.getString(0).toIntOrNull() == CATALOG_VERSION &&
                    (cursor.getString(1).toIntOrNull() ?: 0) >= MIN_PLACE_COUNT &&
                    cursor.getInt(2) == 1 &&
                    cursor.getInt(3) == 1 &&
                    cursor.getInt(4) == 1
            ) {
                "Place catalog schema or metadata is missing"
            }
        }
    }

    private fun validateNewDatabase(file: File) {
        // Android vendors may implement FTS integrity checks through the documented
        // INSERT INTO places_fts(places_fts) VALUES('integrity-check') command. That
        // command is logically read-only but requires a writable SQLite handle on
        // Samsung devices. This is the private temporary copy, never the installed DB.
        openForValidation(file).use { db ->
            validateOpenDatabase(db)
            db.rawQuery("PRAGMA quick_check", emptyArray()).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                    "Place catalog integrity check failed"
                }
            }
        }
        deleteSidecar(file, "-journal")
        deleteSidecar(file, "-wal")
        deleteSidecar(file, "-shm")
    }

    private fun deleteSidecar(file: File, suffix: String) {
        val sidecar = File(file.absolutePath + suffix)
        if (sidecar.exists() && !sidecar.delete()) {
            Log.w(TAG, "Unable to remove stale place catalog sidecar ${sidecar.name}")
        }
    }

    private fun openReadOnly(file: File): SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )

    private fun openForValidation(file: File): SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )

    private fun normalize(value: String): String = Normalizer.normalize(
        value.trim().lowercase(Locale.ROOT),
        Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    internal fun closeForTest() {
        synchronized(this) {
            database?.close()
            database = null
            countryCache = null
        }
    }

    companion object {
        // Do not use .gz: Android's asset merger transparently expands that extension and
        // changes its packaged name. .dbz keeps the deterministic gzip bytes intact.
        private const val ASSET_NAME = "places-v2.sqlite.dbz"
        private const val DATABASE_NAME = "places-v1.sqlite"
        private const val MIN_DATABASE_BYTES = 1_000_000L
        private const val MIN_PLACE_COUNT = 200_000
        private const val DEFAULT_LIMIT = 250
        private const val MAX_LIMIT = 500
        private const val CATALOG_VERSION = 2
        private const val PREFS_NAME = "place_catalog_install"
        private const val KEY_INSTALLED_VERSION = "installed_version"
        private const val TAG = "CityCatalog"

        @Volatile
        private var INSTANCE: CityCatalog? = null

        fun get(context: Context): CityCatalog = INSTANCE ?: synchronized(this) {
            INSTANCE ?: CityCatalog(context).also { INSTANCE = it }
        }

        internal fun resetForTest() {
            synchronized(this) {
                INSTANCE?.closeForTest()
                INSTANCE = null
            }
        }
    }
}
