package com.moderatrix.app.data.repo

import android.content.Context
import android.util.Log
import com.moderatrix.app.data.api.ApiActivity
import com.moderatrix.app.data.api.ApiActivityEntry
import com.moderatrix.app.data.api.ApiCategory
import com.moderatrix.app.data.api.ApiConfig
import com.moderatrix.app.data.api.ApiSyncRequest
import com.moderatrix.app.data.api.ApiVitalsEntry
import com.moderatrix.app.data.api.NetworkModule
import com.moderatrix.app.data.db.ActivityDefEntity
import com.moderatrix.app.data.db.ActivityEntryEntity
import com.moderatrix.app.data.db.AppDatabase
import com.moderatrix.app.data.db.CategoryEntity
import com.moderatrix.app.data.db.Period
import com.moderatrix.app.data.db.VitalsEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

private const val TAG = "ModeratrixRepo"

class ModeratrixRepo(context: Context) {
    private val db = AppDatabase.get(context)
    private val settingsRepo = SettingsRepo(context)
    private val activityDao = db.activityEntryDao()
    private val vitalsDao = db.vitalsEntryDao()
    private val configDao = db.configDao()

    suspend fun ensureSeeded() {
        if (configDao.getAllActivities().isEmpty()) {
            configDao.replaceAll(DefaultConfig.categories, DefaultConfig.activities)
        }
    }

    fun observeCategories(): Flow<List<CategoryEntity>> = configDao.observeCategories()
    fun observeActivities(): Flow<List<ActivityDefEntity>> = configDao.observeActivities()

    suspend fun getAllActivities(): List<ActivityDefEntity> = configDao.getAllActivities()
    suspend fun getAllCategories(): List<CategoryEntity> = configDao.getAllCategories()

    suspend fun upsertActivityDef(activity: ActivityDefEntity) {
        configDao.upsertActivity(activity)
        triggerSyncBestEffort()
    }

    suspend fun upsertCategory(category: CategoryEntity) {
        configDao.upsertCategory(category)
    }

    fun observeActivityEntries(date: LocalDate): Flow<List<ActivityEntryEntity>> =
        activityDao.observeForDate(date.toString())

    fun observeVitalsEntries(date: LocalDate): Flow<List<VitalsEntryEntity>> =
        vitalsDao.observeForDate(date.toString())

    suspend fun getActivityEntries(date: LocalDate): List<ActivityEntryEntity> =
        activityDao.getForDate(date.toString())

    suspend fun getVitalsEntries(date: LocalDate): List<VitalsEntryEntity> =
        vitalsDao.getForDate(date.toString())

    suspend fun recordActivity(date: LocalDate, period: Period, activityId: String, done: Boolean) {
        val entry = ActivityEntryEntity(
            id = UUID.randomUUID().toString(),
            date = date.toString(),
            period = period,
            activityId = activityId,
            done = done,
            recordedAtEpochMs = System.currentTimeMillis(),
            synced = false
        )
        activityDao.upsert(entry)
        triggerSyncBestEffort()
    }

    suspend fun recordVitals(
        date: LocalDate,
        period: Period,
        mood: Int?,
        alertness: Int?,
        energy: Int?,
        pain: Int?,
        satiety: Int?,
        hydration: Int?,
        notes: String?
    ) {
        val entry = VitalsEntryEntity(
            id = UUID.randomUUID().toString(),
            date = date.toString(),
            period = period,
            mood = mood,
            alertness = alertness,
            energy = energy,
            pain = pain,
            satiety = satiety,
            hydration = hydration,
            notes = notes,
            recordedAtEpochMs = System.currentTimeMillis(),
            synced = false
        )
        vitalsDao.upsert(entry)
        triggerSyncBestEffort()
    }

    suspend fun lastRecordedEpochMs(): Long? {
        val a = activityDao.lastRecordedEpochMs()
        val v = vitalsDao.lastRecordedEpochMs()
        return maxOfNullable(a, v)
    }

    private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    /** Fire-and-forget attempt; failures are silently ignored, WorkManager periodic sync will retry. */
    private fun triggerSyncBestEffort() {
        // Actual scheduling is handled by SyncScheduler from the caller (ViewModel/App),
        // kept as a no-op hook here to avoid a hard dependency from data -> work.
    }

    /** Pushes all unsynced local rows to the server. Returns true if fully successful. */
    suspend fun syncNow(): Boolean {
        val baseUrl = settingsRepo.getServerBaseUrl()
        val api = NetworkModule.buildApi(baseUrl)

        return try {
            val unsyncedActivities = activityDao.getUnsynced()
            val unsyncedVitals = vitalsDao.getUnsynced()

            if (unsyncedActivities.isEmpty() && unsyncedVitals.isEmpty()) {
                return true
            }

            val request = ApiSyncRequest(
                activities = unsyncedActivities.map {
                    ApiActivityEntry(
                        id = it.id,
                        date = it.date,
                        period = it.period.name.lowercase(),
                        activityId = it.activityId,
                        done = it.done,
                        recordedAtEpochMs = it.recordedAtEpochMs
                    )
                },
                vitals = unsyncedVitals.map {
                    ApiVitalsEntry(
                        id = it.id,
                        date = it.date,
                        period = it.period.name.lowercase(),
                        mood = it.mood,
                        alertness = it.alertness,
                        energy = it.energy,
                        pain = it.pain,
                        satiety = it.satiety,
                        hydration = it.hydration,
                        notes = it.notes,
                        recordedAtEpochMs = it.recordedAtEpochMs
                    )
                }
            )

            val response = api.sync(request)
            activityDao.markSynced(response.acceptedActivityIds)
            vitalsDao.markSynced(response.acceptedVitalsIds)

            val allActivitiesAccepted = response.acceptedActivityIds.size == unsyncedActivities.size
            val allVitalsAccepted = response.acceptedVitalsIds.size == unsyncedVitals.size
            val success = allActivitiesAccepted && allVitalsAccepted
            if (success) {
                settingsRepo.setLastSuccessfulSyncEpochMs(System.currentTimeMillis())
            }
            success
        } catch (e: Exception) {
            Log.w(TAG, "sync failed: ${e.message}")
            false
        }
    }

    suspend fun lastSuccessfulSyncEpochMs(): Long? = settingsRepo.getLastSuccessfulSyncEpochMs()

    /** Pulls the config from the server and overwrites local config (server is authoritative for config). */
    suspend fun pullConfig(): Boolean {
        val baseUrl = settingsRepo.getServerBaseUrl()
        val api = NetworkModule.buildApi(baseUrl)
        return try {
            val cfg = api.getConfig()
            configDao.replaceAll(
                cfg.categories.map { CategoryEntity(it.id, it.name) },
                cfg.activities.map {
                    ActivityDefEntity(
                        it.id, it.categoryId, it.name, it.targetFreqPerWeek, it.archived,
                        it.availableMorning, it.availableNoon, it.availableNight
                    )
                }
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "pullConfig failed: ${e.message}")
            false
        }
    }

    suspend fun pushConfig(): Boolean {
        val baseUrl = settingsRepo.getServerBaseUrl()
        val api = NetworkModule.buildApi(baseUrl)
        return try {
            val categories = configDao.getAllCategories()
            val activities = configDao.getAllActivities()
            api.putConfig(
                ApiConfig(
                    categories = categories.map { ApiCategory(it.id, it.name) },
                    activities = activities.map {
                        ApiActivity(
                            it.id, it.categoryId, it.name, it.targetFreqPerWeek, it.archived,
                            it.availableMorning, it.availableNoon, it.availableNight
                        )
                    }
                )
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "pushConfig failed: ${e.message}")
            false
        }
    }

    suspend fun isServerReachable(): Boolean {
        val baseUrl = settingsRepo.getServerBaseUrl()
        val api = NetworkModule.buildApi(baseUrl)
        return try {
            api.health() == "ok"
        } catch (e: Exception) {
            false
        }
    }

    fun settings(): SettingsRepo = settingsRepo
}
