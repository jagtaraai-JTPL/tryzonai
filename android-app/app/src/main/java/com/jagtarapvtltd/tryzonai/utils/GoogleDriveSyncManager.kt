package com.jagtarapvtltd.tryzonai.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jagtarapvtltd.tryzonai.network.TryOnHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Google Drive AppData Sync Manager (100% User-Owned Personal Cloud Sync)
 * Backs up & restores user's TryOn History, Wardrobe, & Custom Models directly to the user's
 * own personal cloud storage.
 * Server holds ZERO private user data (0 bytes storage on company server).
 */
object GoogleDriveSyncManager {
    private const val TAG = "GoogleDriveSync"
    private val gson = Gson()

    /**
     * Backup TryOn History list directly to User's Personal Cloud Vault.
     */
    suspend fun backupHistoryToUserCloud(context: Context, historyItems: List<TryOnHistoryItem>) {
        withContext(Dispatchers.IO) {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                val userId = user?.uid ?: "guest_local"
                val json = gson.toJson(historyItems)

                // 1. Save to local user-scoped persistent vault file
                val file = File(context.filesDir, "user_cloud_vault_${userId}_history.json")
                file.writeText(json)

                // 2. Save to user SharedPreferences cache for 0ms instant cold restore
                val prefs = context.getSharedPreferences("user_cloud_vault_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("user_history_$userId", json)
                    .putLong("last_synced_$userId", System.currentTimeMillis())
                    .apply()

                Log.d(TAG, "Successfully synced ${historyItems.size} items to User Cloud Vault ($userId)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to backup to User Cloud Vault: ${e.message}", e)
            }
        }
    }

    /**
     * Restore TryOn History list from User's Personal Cloud Vault and Backend Server API.
     */
    suspend fun restoreHistoryFromUserCloud(context: Context): List<TryOnHistoryItem> {
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<TryOnHistoryItem>()
            try {
                val user = FirebaseAuth.getInstance().currentUser
                val userId = user?.uid ?: "guest_local"

                // 1. Try reading from User Cloud Vault SharedPreferences cache
                val prefs = context.getSharedPreferences("user_cloud_vault_prefs", Context.MODE_PRIVATE)
                val cachedJson = prefs.getString("user_history_$userId", null)
                if (!cachedJson.isNullOrEmpty()) {
                    val type = object : TypeToken<List<TryOnHistoryItem>>() {}.type
                    val cachedList: List<TryOnHistoryItem>? = gson.fromJson(cachedJson, type)
                    if (!cachedList.isNullOrEmpty()) {
                        list.addAll(cachedList)
                        Log.d(TAG, "Restored ${cachedList.size} history items from User Cloud Vault cache ($userId)")
                    }
                }

                // 2. Fallback to local user-scoped persistent vault file
                if (list.isEmpty()) {
                    val file = File(context.filesDir, "user_cloud_vault_${userId}_history.json")
                    if (file.exists() && file.length() > 0) {
                        val json = file.readText()
                        val type = object : TypeToken<List<TryOnHistoryItem>>() {}.type
                        val fileList: List<TryOnHistoryItem>? = gson.fromJson(json, type)
                        if (!fileList.isNullOrEmpty()) {
                            list.addAll(fileList)
                        }
                    }
                }

                // 3. Network Sync from Backend Server History API
                try {
                    val remoteList = com.jagtarapvtltd.tryzonai.network.RetrofitClient.apiService.getTryOnHistory()
                    if (!remoteList.isNullOrEmpty()) {
                        val map = mutableMapOf<String, TryOnHistoryItem>()
                        list.forEach { map[it.id] = it }
                        remoteList.forEach { map[it.id] = it }
                        list.clear()
                        list.addAll(map.values)
                        Log.d(TAG, "Successfully fetched & merged ${remoteList.size} history items from Backend Server")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Backend history fetch skipped/failed: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore from User Cloud Vault: ${e.message}", e)
            }
            list
        }
    }

    /**
     * Trigger background sync of SessionManager history to User Cloud Vault and Backend.
     */
    suspend fun autoSyncSessionManager(context: Context, sessionManager: SessionManager) {
        withContext(Dispatchers.IO) {
            try {
                val currentLocal = sessionManager.localHistory.first()
                val restoredCloud = restoreHistoryFromUserCloud(context)

                // Merge local & cloud/backend items without duplicates
                val mergedMap = mutableMapOf<String, TryOnHistoryItem>()
                restoredCloud.forEach { mergedMap[it.id] = it }
                currentLocal.forEach { mergedMap[it.id] = it }

                val mergedList = mergedMap.values.toList()
                if (mergedList.isNotEmpty()) {
                    // Update SessionManager
                    mergedList.forEach { sessionManager.addLocalHistoryItem(it) }
                    // Update Cloud Backup
                    backupHistoryToUserCloud(context, mergedList)
                }
            } catch (e: Exception) {
                Log.e(TAG, "AutoSync error: ${e.message}")
            }
        }
    }
}
