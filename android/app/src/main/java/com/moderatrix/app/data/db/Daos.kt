package com.moderatrix.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ActivityEntryEntity)

    @Query("SELECT * FROM activity_entries WHERE date = :date ORDER BY recordedAtEpochMs ASC")
    fun observeForDate(date: String): Flow<List<ActivityEntryEntity>>

    @Query("SELECT * FROM activity_entries WHERE date = :date ORDER BY recordedAtEpochMs ASC")
    suspend fun getForDate(date: String): List<ActivityEntryEntity>

    @Query("SELECT * FROM activity_entries WHERE synced = 0 ORDER BY recordedAtEpochMs ASC")
    suspend fun getUnsynced(): List<ActivityEntryEntity>

    @Query("UPDATE activity_entries SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT MAX(recordedAtEpochMs) FROM activity_entries")
    suspend fun lastRecordedEpochMs(): Long?

    @Query("SELECT date, period, activityId FROM activity_entries WHERE date >= :sinceDate")
    suspend fun completionsSince(sinceDate: String): List<ActivityCompletionRow>
}

data class ActivityCompletionRow(
    val date: String,
    val period: Period,
    val activityId: String
)

@Dao
interface VitalsEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: VitalsEntryEntity)

    @Query("SELECT * FROM vitals_entries WHERE date = :date ORDER BY recordedAtEpochMs ASC")
    fun observeForDate(date: String): Flow<List<VitalsEntryEntity>>

    @Query("SELECT * FROM vitals_entries WHERE date = :date ORDER BY recordedAtEpochMs ASC")
    suspend fun getForDate(date: String): List<VitalsEntryEntity>

    @Query("SELECT * FROM vitals_entries WHERE synced = 0 ORDER BY recordedAtEpochMs ASC")
    suspend fun getUnsynced(): List<VitalsEntryEntity>

    @Query("UPDATE vitals_entries SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT MAX(recordedAtEpochMs) FROM vitals_entries")
    suspend fun lastRecordedEpochMs(): Long?
}

@Dao
interface ConfigDao {
    @Transaction
    suspend fun replaceAll(categories: List<CategoryEntity>, activities: List<ActivityDefEntity>) {
        clearCategories()
        clearActivities()
        insertCategories(categories)
        insertActivities(activities)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<ActivityDefEntity>)

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM activities")
    suspend fun clearActivities()

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM activities WHERE archived = 0 ORDER BY sortOrder ASC")
    fun observeActivities(): Flow<List<ActivityDefEntity>>

    @Query("SELECT * FROM activities ORDER BY sortOrder ASC")
    suspend fun getAllActivities(): List<ActivityDefEntity>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(activity: ActivityDefEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivities(activities: List<ActivityDefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)
}
