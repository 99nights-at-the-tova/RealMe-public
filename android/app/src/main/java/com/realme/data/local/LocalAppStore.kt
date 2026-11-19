package com.realme.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.realMeDataStore by preferencesDataStore(name = "realme_local")

class LocalAppStore(
    context: Context,
    dataStore: DataStore<Preferences>? = null,
) {
    private val preferencesStore = dataStore ?: context.realMeDataStore
    private val snapshotKey = stringPreferencesKey("app_snapshot")
    private val writeMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val snapshots: Flow<AppSnapshot> = preferencesStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[snapshotKey]?.let { encoded ->
                json.decodeFromString(AppSnapshot.serializer(), encoded)
            } ?: AppSnapshot()
        }

    suspend fun current(): AppSnapshot = snapshots.first()

    suspend fun update(transform: (AppSnapshot) -> AppSnapshot): AppSnapshot = writeMutex.withLock {
        val updated = transform(current())
        preferencesStore.edit { preferences ->
            preferences[snapshotKey] = json.encodeToString(AppSnapshot.serializer(), updated)
        }
        updated
    }
}
