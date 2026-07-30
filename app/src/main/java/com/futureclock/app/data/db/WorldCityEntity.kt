package com.futureclock.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "world_cities")
data class WorldCityEntity(
    @PrimaryKey
    @ColumnInfo(name = "location_id") val locationId: Long,
    val tzId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "country") val country: String,
    @ColumnInfo(name = "flag") val flag: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0
)
