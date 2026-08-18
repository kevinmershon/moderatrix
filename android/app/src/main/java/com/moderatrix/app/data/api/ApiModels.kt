package com.moderatrix.app.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiCategory(val id: String, val name: String)

@JsonClass(generateAdapter = true)
data class ApiActivity(
    val id: String,
    @Json(name = "category_id") val categoryId: String,
    val name: String,
    @Json(name = "target_freq_per_week") val targetFreqPerWeek: Int,
    val archived: Boolean,
    @Json(name = "available_morning") val availableMorning: Boolean = true,
    @Json(name = "available_noon") val availableNoon: Boolean = true,
    @Json(name = "available_night") val availableNight: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ApiConfig(
    val categories: List<ApiCategory>,
    val activities: List<ApiActivity>
)

@JsonClass(generateAdapter = true)
data class ApiActivityEntry(
    val id: String,
    val date: String,
    val period: String, // "morning" | "noon" | "night"
    @Json(name = "activity_id") val activityId: String,
    val done: Boolean,
    @Json(name = "recorded_at_epoch_ms") val recordedAtEpochMs: Long
)

@JsonClass(generateAdapter = true)
data class ApiVitalsEntry(
    val id: String,
    val date: String,
    val period: String,
    val mood: Int?,
    val alertness: Int?,
    val energy: Int?,
    val pain: Int?,
    val satiety: Int?,
    val hydration: Int?,
    val notes: String?,
    @Json(name = "recorded_at_epoch_ms") val recordedAtEpochMs: Long
)

@JsonClass(generateAdapter = true)
data class ApiSyncRequest(
    val activities: List<ApiActivityEntry>,
    val vitals: List<ApiVitalsEntry>
)

@JsonClass(generateAdapter = true)
data class ApiSyncResponse(
    @Json(name = "accepted_activity_ids") val acceptedActivityIds: List<String>,
    @Json(name = "accepted_vitals_ids") val acceptedVitalsIds: List<String>,
    @Json(name = "server_time_epoch_ms") val serverTimeEpochMs: Long
)

@JsonClass(generateAdapter = true)
data class ApiDayLog(
    val date: String,
    val activities: List<ApiActivityEntry>,
    val vitals: List<ApiVitalsEntry>
)

@JsonClass(generateAdapter = true)
data class ApiLastRecorded(
    @Json(name = "last_recorded_epoch_ms") val lastRecordedEpochMs: Long?
)
