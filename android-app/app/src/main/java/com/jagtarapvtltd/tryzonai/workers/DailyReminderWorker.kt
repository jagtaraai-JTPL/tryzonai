package com.jagtarapvtltd.tryzonai.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jagtarapvtltd.tryzonai.MainActivity
import com.jagtarapvtltd.tryzonai.R

class DailyReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            sendDailyNotification()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun sendDailyNotification() {
        val channelId = "tryzon_daily_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Try-On Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily reminders to use your free daily try-ons and explore new fashion"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("notification_type", "daily_reminder")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val titles = listOf(
            "🎁 1 Free AI Try-On Pass Waiting For You!",
            "🔥 What should you wear today?",
            "✨ Instant Outfit Makeover Ready!",
            "👗 See yourself in trending styles now!",
            "💃 Free Daily Styling Pass Available!"
        )

        val messages = listOf(
            "Your daily free try-on pass has reset! Tap to upload photo & try new outfits now.",
            "Upload any shirt or dress photo to see how it looks on you in 5 seconds!",
            "Don't let your free daily credits expire today! Tap to open TryZon AI.",
            "Trending fashion outfits are ready for your virtual try-on. Tap to style!",
            "Upgrade your look today! Explore 100+ virtual fitting room outfits."
        )

        val randomIndex = (titles.indices).random()
        val title = titles[randomIndex]
        val message = messages[randomIndex]

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_t)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1001, notification)
    }
}
