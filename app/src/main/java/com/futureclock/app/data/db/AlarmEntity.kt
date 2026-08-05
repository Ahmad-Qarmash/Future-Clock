package com.futureclock.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "hour") val hour: Int,
    @ColumnInfo(name = "minute") val minute: Int,
    @ColumnInfo(name = "label") val label: String = "",
    /** Bitmask of ISO days of week (Mon=1, Tue=2, ... Sun=7). 0 = no repeat. */
    @ColumnInfo(name = "days_of_week") val daysOfWeek: Int = 0,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "vibrate") val vibrate: Boolean = true,
    @ColumnInfo(name = "gradual_volume") val gradualVolume: Boolean = true,
    @ColumnInfo(name = "snooze_minutes") val snoozeMinutes: Int = 5,
    @ColumnInfo(name = "sound_uri") val soundUri: String = "",
    @ColumnInfo(name = "difficulty") val difficulty: Int = 0, // 0=off, 1=math easy, 2=math hard, 3=type
    /** IANA timezone ID. Empty only for alarms migrated from v1, which retain device-time behavior. */
    @ColumnInfo(name = "timezone_id") val timeZoneId: String = "",
    /** Snapshot of the selected place. The alarm remains independent from World Clock changes. */
    @ColumnInfo(name = "place_id") val placeId: Long = 0L,
    @ColumnInfo(name = "place_name") val placeName: String = "",
    @ColumnInfo(name = "place_country") val placeCountry: String = "",
    @ColumnInfo(name = "place_flag") val placeFlag: String = "",
    @ColumnInfo(name = "next_trigger_ms") val nextTriggerMs: Long = 0L
)
