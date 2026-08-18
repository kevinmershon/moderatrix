package com.moderatrix.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        ActivityEntryEntity::class,
        VitalsEntryEntity::class,
        CategoryEntity::class,
        ActivityDefEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityEntryDao(): ActivityEntryDao
    abstract fun vitalsEntryDao(): VitalsEntryDao
    abstract fun configDao(): ConfigDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // Adds per-activity morning/noon/night applicability, defaulting to available in all
        // three so existing activities behave exactly as before until edited.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activities ADD COLUMN availableMorning INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE activities ADD COLUMN availableNoon INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE activities ADD COLUMN availableNight INTEGER NOT NULL DEFAULT 1")
            }
        }

        // Adds drag-reorder sortOrder to categories and activities, defaulting to 0 (existing
        // rows are re-ranked to a stable order the first time the config screen loads).
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE activities ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moderatrix.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
    }
}
