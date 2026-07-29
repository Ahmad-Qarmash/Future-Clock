package com.futureclock.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY next_trigger_ms ASC")
    suspend fun getEnabledSortedByNext(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY next_trigger_ms ASC LIMIT 1")
    suspend fun getNextEnabled(): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}

@Dao
interface WorldCityDao {
    @Query("SELECT * FROM world_cities ORDER BY sort_order, display_name")
    fun observeAll(): Flow<List<WorldCityEntity>>

    @Query("SELECT * FROM world_cities ORDER BY sort_order, display_name")
    suspend fun getAll(): List<WorldCityEntity>

    @Query("SELECT * FROM world_cities WHERE location_id = :locationId LIMIT 1")
    suspend fun getByLocationId(locationId: Long): WorldCityEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(city: WorldCityEntity)

    @Update
    suspend fun update(city: WorldCityEntity)

    @Delete
    suspend fun delete(city: WorldCityEntity)

    @Query("DELETE FROM world_cities WHERE location_id = :locationId")
    suspend fun deleteByLocationId(locationId: Long)

    @Query("SELECT COUNT(*) FROM world_cities")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sort_order),0) + 1 FROM world_cities")
    suspend fun nextSortOrder(): Int
}
