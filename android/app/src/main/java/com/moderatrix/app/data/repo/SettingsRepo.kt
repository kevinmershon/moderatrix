package com.moderatrix.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moderatrix.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepo(private val context: Context) {
    private val serverUrlKey = stringPreferencesKey("server_base_url")
    private val lastSuccessfulSyncKey = longPreferencesKey("last_successful_sync_epoch_ms")
    private val lastSyncStaleWarningKey = longPreferencesKey("last_sync_stale_warning_epoch_ms")

    val serverBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[serverUrlKey] ?: BuildConfig.SERVER_BASE_URL
    }

    suspend fun getServerBaseUrl(): String = serverBaseUrl.first()

    suspend fun setServerBaseUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[serverUrlKey] = url }
    }

    suspend fun getLastSuccessfulSyncEpochMs(): Long? =
        context.dataStore.data.first()[lastSuccessfulSyncKey]

    suspend fun setLastSuccessfulSyncEpochMs(epochMs: Long) {
        context.dataStore.edit { prefs -> prefs[lastSuccessfulSyncKey] = epochMs }
    }

    suspend fun getLastSyncStaleWarningEpochMs(): Long? =
        context.dataStore.data.first()[lastSyncStaleWarningKey]

    suspend fun setLastSyncStaleWarningEpochMs(epochMs: Long) {
        context.dataStore.edit { prefs -> prefs[lastSyncStaleWarningKey] = epochMs }
    }
}
