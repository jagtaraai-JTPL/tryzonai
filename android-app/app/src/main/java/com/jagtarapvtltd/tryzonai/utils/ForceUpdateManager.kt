package com.jagtarapvtltd.tryzonai.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.jagtarapvtltd.tryzonai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ForceUpdateManager(private val context: Context) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    companion object {
        const val UPDATE_REQUEST_CODE = 9001
    }

    /**
     * Checks Google Play In-App Update API for immediate forced update.
     */
    fun checkForPlayStoreUpdate(activity: Activity, onUpdateNotAvailable: () -> Unit = {}) {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            val isUpdateAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val isImmediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

            if (isUpdateAvailable && isImmediateAllowed) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        activity,
                        AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    Log.e("ForceUpdateManager", "Failed to start Google Play update flow: ${e.message}")
                    onUpdateNotAvailable()
                }
            } else {
                onUpdateNotAvailable()
            }
        }.addOnFailureListener { e ->
            Log.e("ForceUpdateManager", "Failed to check Play Store update info: ${e.message}")
            onUpdateNotAvailable()
        }
    }

    /**
     * Resume update if app was in the middle of an immediate update.
     */
    fun checkResumeUpdate(activity: Activity) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        activity,
                        AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    Log.e("ForceUpdateManager", "Failed to resume update flow: ${e.message}")
                }
            }
        }
    }

    /**
     * Queries backend config for remote minimum required version code.
     */
    suspend fun fetchRemoteMinVersion(): RemoteVersionConfig? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://tryzonai.com/api/v1/config/app-version")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                return@withContext RemoteVersionConfig(
                    latestVersionCode = json.optInt("latest_version_code", 73),
                    minRequiredVersionCode = json.optInt("min_required_version_code", 70),
                    forceUpdate = json.optBoolean("force_update", true),
                    title = json.optString("update_title", "🚨 Mandatory Update Required"),
                    message = json.optString("update_message", "A critical update is required to continue using TryZon AI."),
                    playStoreUrl = json.optString("play_store_url", "https://play.google.com/store/apps/details?id=com.jagtarapvtltd.tryzonai")
                )
            }
        } catch (e: Exception) {
            Log.e("ForceUpdateManager", "Error fetching remote min version: ${e.message}")
        }
        return@withContext null
    }

    fun openPlayStorePage() {
        val packageName = context.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}

data class RemoteVersionConfig(
    val latestVersionCode: Int,
    val minRequiredVersionCode: Int,
    val forceUpdate: Boolean,
    val title: String,
    val message: String,
    val playStoreUrl: String
)
