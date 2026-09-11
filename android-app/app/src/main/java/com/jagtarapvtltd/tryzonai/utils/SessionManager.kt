package com.jagtarapvtltd.tryzonai.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.jagtarapvtltd.tryzonai.network.TryOnHistoryItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tryzon_session")

class SessionManager(val context: Context) {
    private val gson = Gson()

    companion object {
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val LOCAL_HISTORY = stringPreferencesKey("local_history")
    }

    val authToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AUTH_TOKEN]
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED] ?: isOnboardingCompletedSync()
    }

    fun isOnboardingCompletedSync(): Boolean {
        return context.getSharedPreferences("tryzon_session_prefs", Context.MODE_PRIVATE)
            .getBoolean("onboarding_completed", false)
    }

    suspend fun saveAuthToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[AUTH_TOKEN] = token
        }
    }

    suspend fun setOnboardingCompleted() {
        context.getSharedPreferences("tryzon_session_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_completed", true).apply()
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(AUTH_TOKEN)
        }
    }

    val localHistory: Flow<List<TryOnHistoryItem>> = context.dataStore.data.map { preferences ->
        val json = preferences[LOCAL_HISTORY]
        if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<TryOnHistoryItem>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addLocalHistoryItem(item: TryOnHistoryItem) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[LOCAL_HISTORY]
            val currentList = if (currentJson.isNullOrEmpty()) {
                mutableListOf<TryOnHistoryItem>()
            } else {
                try {
                    val type = object : TypeToken<List<TryOnHistoryItem>>() {}.type
                    gson.fromJson<List<TryOnHistoryItem>>(currentJson, type).toMutableList()
                } catch (e: Exception) {
                    mutableListOf<TryOnHistoryItem>()
                }
            }
            
            // Check for duplicates
            if (currentList.none { it.id == item.id }) {
                currentList.add(0, item) // Add to top
                // Keep max 20 items locally
                if (currentList.size > 20) {
                    currentList.removeAt(currentList.lastIndex)
                }
                preferences[LOCAL_HISTORY] = gson.toJson(currentList)
            }
        }
    }

    suspend fun removeLocalHistoryItem(id: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[LOCAL_HISTORY]
            if (!currentJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<TryOnHistoryItem>>() {}.type
                    val currentList = gson.fromJson<List<TryOnHistoryItem>>(currentJson, type).toMutableList()
                    currentList.removeAll { it.id == id }
                    preferences[LOCAL_HISTORY] = gson.toJson(currentList)
                    try {
                        com.jagtarapvtltd.tryzonai.utils.GoogleDriveSyncManager.backupHistoryToUserCloud(context, currentList)
                    } catch (_: Exception) {}
                } catch (e: Exception) {
                    // Ignore parsing error
                }
            }
        }
    }
}
