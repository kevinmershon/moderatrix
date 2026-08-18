package com.moderatrix.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Period { MORNING, NOON, NIGHT }

@Entity(tableName = "activity_entries")
data class ActivityEntryEntity(
    @PrimaryKey val id: String,
    val date: String, // yyyy-MM-dd
    val period: Period,
    val activityId: String,
    val done: Boolean,
    val recordedAtEpochMs: Long,
    val synced: Boolean = false
)

@Entity(tableName = "vitals_entries")
data class VitalsEntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val period: Period,
    val mood: Int?,
    val alertness: Int?,
    val energy: Int?,
    val pain: Int?,
    val satiety: Int?,
    val hydration: Int?,
    val notes: String?,
    val recordedAtEpochMs: Long,
    val synced: Boolean = false
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int = 0
)

@Entity(tableName = "activities")
data class ActivityDefEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val targetFreqPerWeek: Int,
    val archived: Boolean,
    val availableMorning: Boolean = true,
    val availableNoon: Boolean = true,
    val availableNight: Boolean = true,
    val sortOrder: Int = 0
) {
    fun isAvailableFor(period: Period): Boolean = when (period) {
        Period.MORNING -> availableMorning
        Period.NOON -> availableNoon
        Period.NIGHT -> availableNight
    }
}
