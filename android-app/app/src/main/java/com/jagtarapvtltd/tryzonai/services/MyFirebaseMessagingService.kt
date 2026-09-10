package com.jagtarapvtltd.tryzonai.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jagtarapvtltd.tryzonai.MainActivity
import com.jagtarapvtltd.tryzonai.R

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Handle Data Payload
        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"]
            val sessionId = remoteMessage.data["session_id"]
            val title = remoteMessage.notification?.title ?: "TryZon AI"
            val body = remoteMessage.notification?.body ?: "Your request is complete!"
            
            sendNotification(title, body, type, sessionId)
        } else {
            // Handle Notification Payload
            remoteMessage.notification?.let {
                sendNotification(it.title ?: "TryZon AI", it.body ?: "", imageUrl = it.imageUrl?.toString())
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token will be handled in MainActivity/AuthViewModel on app start
    }

    private fun sendNotification(title: String, messageBody: String, type: String? = null, sessionId: String? = null, imageUrl: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("deep_link_type", type ?: "tryon_complete")
            putExtra("session_id", sessionId)
            putExtra("from_notification", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        var resultBitmap: android.graphics.Bitmap? = null
        if (!imageUrl.isNullOrEmpty()) {
            try {
                val fullUrl = com.jagtarapvtltd.tryzonai.utils.UrlUtils.getFullUrl(imageUrl)
                val request = okhttp3.Request.Builder().url(fullUrl).build()
                okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body.byteStream().use { stream ->
                            val options = android.graphics.BitmapFactory.Options().apply {
                                inSampleSize = 2
                                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                            }
                            resultBitmap = android.graphics.BitmapFactory.decodeStream(stream, null, options)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TryZonFCM", "Failed to load image: ${e.message}")
            }
        }

        // Decode the large icon from drawable
        val largeIcon = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_tryzon_logo)

        val channelId = "tryzon_notifications"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            // Custom 'T' logo silhouette for the small icon
            .setSmallIcon(R.drawable.ic_notification_t)
            .setLargeIcon(resultBitmap ?: largeIcon)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setColor(android.graphics.Color.parseColor("#FFD700")) // Primary Gold
            // Big text style for expanding long messages
            .setStyle(if (resultBitmap != null) {
                NotificationCompat.BigPictureStyle().bigPicture(resultBitmap).bigLargeIcon(null as android.graphics.Bitmap?)
            } else {
                NotificationCompat.BigTextStyle().bigText(messageBody)
            })
            // Premium vibration pattern
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .setLights(android.graphics.Color.parseColor("#FFD700"), 1000, 1000)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TryZon Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for AI Try-On completions and updates"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#FFD700")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
