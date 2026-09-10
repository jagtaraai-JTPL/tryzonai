package com.jagtarapvtltd.tryzonai.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jagtarapvtltd.tryzonai.network.RetrofitClient
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class OfflineTryOnWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val userPhotoPath = inputData.getString("userPhotoPath") ?: return Result.failure()
        val garmentPath = inputData.getString("garmentPath") ?: return Result.failure()
        val productId = inputData.getString("productId")

        val userPhotoFile = File(userPhotoPath)
        val garmentFile = File(garmentPath)

        val prefs = context.getSharedPreferences("offline_worker_prefs", Context.MODE_PRIVATE)
        val workerId = id.toString()
        val existingSessionId = prefs.getInt(workerId, -1)

        val sessionId: Int

        if (existingSessionId != -1) {
            sessionId = existingSessionId
        } else {
            if (!userPhotoFile.exists() || !garmentFile.exists()) {
                return Result.failure()
            }

            try {
                val personPart = MultipartBody.Part.createFormData(
                    "person_image", userPhotoFile.name, userPhotoFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                val garmentPart = MultipartBody.Part.createFormData(
                    "garment_image", garmentFile.name, garmentFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                val productIdPart = productId?.toRequestBody("text/plain".toMediaTypeOrNull())

                val submission = RetrofitClient.apiService.submitTryOn(personPart, garmentPart, productIdPart)
                sessionId = submission.session_id
                prefs.edit().putInt(workerId, sessionId).apply()
            } catch (e: Exception) {
                return Result.retry()
            }
        }

        try {
            // Poll for status
            var isComplete = false
            var retryCount = 0
            val maxRetries = 150 // 5 minutes max

            while (!isComplete && retryCount < maxRetries) {
                val statusResponse = RetrofitClient.apiService.getTryOnStatus(sessionId)
                
                when (statusResponse.status) {
                    "done" -> {
                        isComplete = true
                        
                        // Save to history
                        val sessionManager = com.jagtarapvtltd.tryzonai.utils.SessionManager(context)
                        sessionManager.addLocalHistoryItem(
                            com.jagtarapvtltd.tryzonai.network.TryOnHistoryItem(
                                id = sessionId.toString(),
                                product_id = productId,
                                result_url = statusResponse.result_url ?: "",
                                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()),
                                garment_name = "Offline Try-On"
                            )
                        )
                        
                        // Show Notification
                        sendNotification(sessionId.toString(), statusResponse.result_url)
                        
                        // Cleanup temp files & prefs
                        userPhotoFile.delete()
                        garmentFile.delete()
                        prefs.edit().remove(workerId).apply()
                        
                        return Result.success()
                    }
                    "failed" -> {
                        return Result.failure()
                    }
                    else -> {
                        delay(2000)
                        retryCount++
                    }
                }
            }
            return Result.retry()

        } catch (e: Exception) {
            android.util.Log.e("OfflineTryOnWorker", "Worker failed: ${e.message}")
            return Result.retry()
        }
    }

    private fun sendNotification(sessionId: String, resultUrl: String?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tryzon_ai_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TryZon AI Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = android.content.Intent(context, com.jagtarapvtltd.tryzonai.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("deep_link_type", "tryon_complete")
            putExtra("session_id", sessionId)
            putExtra("from_notification", true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, sessionId.hashCode(), intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.jagtarapvtltd.tryzonai.R.drawable.ic_notification_t)
            .setContentTitle("Try-On Ready! 👗")
            .setContentText("Your offline AI Virtual Try-On is now complete. Tap to view.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(sessionId.hashCode(), builder.build())
    }
}
