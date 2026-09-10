package com.jagtarapvtltd.tryzonai.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jagtarapvtltd.tryzonai.MainActivity
import com.jagtarapvtltd.tryzonai.R
import com.jagtarapvtltd.tryzonai.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.random.Random

class DailyStyleWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    // 🇮🇳 India (IN) Hinglish + Hindi Fashion Discovery Lines
    private val indianDailyStyleMessages = listOf(
        Pair("Aaj ka look tumne try kiya? ✨", "AI ne aaj ka trending look tumhare liye create kiya hai. Tap to see your new style!"),
        Pair("Your next favorite outfit is ready! 👗", "We picked a trending outfit that suits your style. Tap to see how it looks on you!"),
        Pair("Aaj kuch naya try karne ka mood hai? 😎", "Your personalized AI outfit drop is ready. See your new look now!"),
        Pair("New day. New look. New you. ✨", "Explore today's exclusive AI virtual try-on look right now."),
        Pair("Tum par ye style kaisa lagega? 👀", "See yourself in today's trending outfit. Tap to open!"),
        Pair("Your AI stylist has a new idea for you. 🤖✨", "Check out the new outfit recommendation created just for your body photo."),
        Pair("Aaj ka trending style tumhare liye ready hai. 🔥", "Tap to view today's AI fashion look in full screen!"),
        Pair("Kabhi socha tha ye outfit tum par kaisa lagega?", "See today's virtual fitting result right now!"),
        Pair("Your daily style inspiration is here. 💫", "A brand new look tailored for you is ready. Tap to view!"),
        Pair("Aaj apne wardrobe se bahar niklo. 🔥", "Try on today's fresh trending outfit on your body photo."),
        Pair("One outfit can change your whole vibe. ✨", "See how today's look transforms your fashion confidence."),
        Pair("Aaj ka fashion experiment shuru karein? 👗", "Your daily AI try-on is complete. Tap to see your look!"),
        Pair("Trending today → Try it on yourself. 👕", "See how the top trending outfit looks on YOU right now."),
        Pair("Your next look is just one tap away. ✨", "Open TryZon AI to view your freshly generated outfit look."),
        Pair("AI ne tumhare liye kuch stylish choose kiya hai. 👑", "Tap to reveal today's personalized fashion outfit."),
        Pair("Ready to see yourself in a new style? 👀", "Your daily AI virtual try-on look is ready to view."),
        Pair("Aaj ka look miss mat karna. 🔥", "Today's top trending look is ready on your photo."),
        Pair("What if this was your perfect outfit? ✨", "See how this stunning outfit fits you today."),
        Pair("Your style. Your choice. AI's suggestion. 💫", "Discover today's personalized AI fashion pick."),
        Pair("Aaj ka TryZon look unlock karo. 👗", "Tap to view your daily generated virtual try-on result."),
        Pair("New trend detected. Tum par kaisa lagega? 👀", "See today's viral outfit on your body photo right now."),
        Pair("Fashion changes daily. Your style can too. 💫", "Check out your new look generated for today."),
        Pair("Aaj AI Stylist ne tumhe surprise diya hai. 🎁", "Tap to unwrap today's personalized AI outfit look!"),
        Pair("See yourself differently today. ✨", "A fresh new perspective on your style is just one tap away."),
        Pair("Your daily virtual fitting is ready. 👗", "Tap to open your virtual fitting room result now."),
        Pair("Aaj ka outfit tumhare liye specially selected hai. ❤️", "See how this curated outfit suits your style."),
        Pair("Don't just imagine the outfit. Try it on. ✨", "Your daily AI try-on result is waiting for you."),
        Pair("Your look of the day is waiting. 👀", "See how today's trending outfit looks on your photo."),
        Pair("Aaj ek naya version of you discover karo. 🔥", "Tap to view today's AI virtual try-on creation."),
        Pair("Ready for today's style drop? 🔥", "Your daily AI fashion look has arrived. Tap to view!"),
        Pair("Kabhi-kabhi bas ek naya look confidence badha deta hai. ✨", "Boost your day with today's stylish AI look!"),
        Pair("Jo style tum imagine karte ho, aaj use khud par dekho. 👗", "Your dream outfit is ready on your body photo."),
        Pair("Confidence starts with how you see yourself. ❤️", "See yourself in today's bold new fashion look."),
        Pair("Aaj khud ko ek naye style mein dekho. ✨", "Discover a fresh new look created by AI Stylist."),
        Pair("You don't need a new wardrobe. Maybe just a new idea. 💫", "Get inspired by today's AI fashion recommendation."),
        Pair("Your style has more possibilities than you think. 👀", "Explore today's AI virtual try-on outfit now."),
        Pair("Aaj apne comfort zone se ek outfit bahar try karo. 🔥", "Step into a fresh new style with today's AI look."),
        Pair("The right outfit can change the way you feel. ✨", "Tap to view today's uplifting fashion try-on.")
    )

    // 📅 Dedicated Monday to Sunday Daily Messages
    private val weeklyDayMessages = mapOf(
        "Monday" to Pair("Monday Mood: Start your week with confidence! ⚡", "AI ne aaj Monday ka fresh look tumhare liye create kiya hai. Tap to view!"),
        "Tuesday" to Pair("Trendy Tuesday Drop! 👗", "Today's top viral outfit is ready on your photo. See your Tuesday look!"),
        "Wednesday" to Pair("Wednesday Glow-Up is here! ✨", "Mid-week motivation: See how today's outfit elevates your vibe."),
        "Thursday" to Pair("Thursday Style Upgrade! 🔥", "Step out of your fashion comfort zone with today's AI look."),
        "Friday" to Pair("Friday Night Vibe Check! 🥂", "Weekend shuru hone wala hai! See your stylish Friday outfit."),
        "Saturday" to Pair("Saturday Glamour Special! 🌟", "Your weekend special AI virtual try-on look is ready to view."),
        "Sunday" to Pair("Sunday Chill & Chic Look! ☀️", "Relaxed & stylish Sunday fashion drop tailored for you.")
    )

    // 🌍 US / Global International Fashion Discovery Lines
    private val internationalDailyStyleMessages = listOf(
        Pair("Your daily style drop is ready! ✨", "Our AI Stylist created a fresh trending look for your body photo. Tap to view!"),
        Pair("Ready to see yourself in a new style? 👗", "A brand new trending outfit has been fitted on your photo. Open now!"),
        Pair("New day. New style. New vibe. ✨", "Explore today's exclusive AI virtual try-on look right now."),
        Pair("Your AI Stylist has a new recommendation 🤖✨", "Check out the curated outfit selected specially for your style profile."),
        Pair("One outfit can change your whole look 💫", "See how today's trending outfit transforms your look instantly."),
        Pair("Today's viral trend → Fitted on YOU 👕", "See how the top trending outfit looks on your photo right now."),
        Pair("Your daily virtual fitting is complete 👗", "Tap to open your personalized AI virtual try-on result."),
        Pair("What if this was your next favorite outfit? ✨", "Discover today's AI virtual try-on creation in full resolution."),
        Pair("Fresh style inspiration tailored for you 👑", "Your daily AI outfit drop is ready. Tap to view!"),
        Pair("Confidence starts with how you see yourself ❤️", "See yourself in today's bold new fashion look."),
        Pair("Stepping out of your comfort zone? 🔥", "Try on today's fresh trending outfit on your body photo."),
        Pair("Your look of the day is waiting 👀", "Tap to view your daily generated virtual try-on result now."),
        Pair("Don't just imagine the look. See it on you ✨", "Your daily AI virtual try-on is ready. Tap to view!"),
        Pair("Discover a new version of your style today 💫", "Get inspired by today's AI fashion recommendation.")
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Ensure RetrofitClient session header X-Session-ID is initialized for background worker
        RetrofitClient.initSession(context)

        // 0. Day 1 Fresh Install Protection: Auto-trigger ONLY starts from Day 2 (24+ hours after install)
        val installTime = try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        val isDay1 = (System.currentTimeMillis() - installTime) < 24 * 60 * 60 * 1000L
        val isTestRun = inputData.getString("test_day") != null

        if (isDay1 && !isTestRun) {
            android.util.Log.d("DailyStyleWorker", "Skipping auto-trigger on Day 1 (Fresh Install). User's manual 1st free try-on is preserved.")
            return@withContext Result.success()
        }

        val prefs = context.getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
        val dailyStyleEnabled = prefs.getBoolean("daily_style_enabled", true)
        
        if (!dailyStyleEnabled) {
            return@withContext Result.success()
        }

        val userGender = prefs.getString("user_gender", "Women") ?: "Women"

        // 1. Get or Fallback User Model Photo (Guaranteed Non-Null)
        val userPhotoFile = resolveUserPhotoFile(prefs, userGender)
            ?: return@withContext Result.failure()

        // 2. Get or Fallback Trending Garment File (Guaranteed Non-Null)
        val garmentFile = resolveGarmentFile(prefs, userGender)
            ?: return@withContext Result.failure()

        return@withContext try {
            val personPart = MultipartBody.Part.createFormData(
                "person_image", userPhotoFile.name, userPhotoFile.asRequestBody("image/*".toMediaTypeOrNull())
            )
            val garmentPart = MultipartBody.Part.createFormData(
                "garment_image", garmentFile.name, garmentFile.asRequestBody("image/*".toMediaTypeOrNull())
            )

            val submission = RetrofitClient.apiService.submitTryOn(personPart, garmentPart, null, "true")
            val sessionId = submission.session_id

            var isComplete = false
            var retryCount = 0
            val maxRetries = 120

            while (!isComplete && retryCount < maxRetries) {
                val statusResponse = RetrofitClient.apiService.getTryOnStatus(sessionId)
                when (statusResponse.status) {
                    "done" -> {
                        isComplete = true
                        val resultUrl = statusResponse.result_url ?: ""
                        
                        prefs.edit()
                            .putString("daily_style_result_url", resultUrl)
                            .putString("daily_style_date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date()))
                            .apply()

                        sendDailyStyleNotification(resultUrl, sessionId.toString())
                        return@withContext Result.success()
                    }
                    "failed" -> {
                        return@withContext Result.failure()
                    }
                    else -> {
                        delay(2500)
                        retryCount++
                    }
                }
            }
            Result.retry()
        } catch (e: Exception) {
            android.util.Log.e("DailyStyleWorker", "Failed to generate daily style: ${e.message}")
            Result.retry()
        }
    }

    private fun resolveUserPhotoFile(prefs: android.content.SharedPreferences, gender: String): File? {
        // Priority 1: User's explicit saved photo
        val savedPath = prefs.getString("saved_model_photo_path", null)
            ?: prefs.getString("last_person_image_path", null)
            
        if (!savedPath.isNullOrEmpty()) {
            val file = File(savedPath)
            if (file.exists() && file.length() > 0) return file
        }

        // Priority 2: Latest saved photo in user_models directory
        val modelsDir = File(context.filesDir, "user_models")
        if (modelsDir.exists()) {
            val latest = modelsDir.listFiles()?.filter { it.length() > 0 }?.maxByOrNull { it.lastModified() }
            if (latest != null && latest.exists()) return latest
        }

        // Priority 3: Legacy persisted user photo
        val legacy = File(context.filesDir, "persisted_user_photo.jpg")
        if (legacy.exists() && legacy.length() > 0) return legacy

        // Priority 4: Auto-extract default Drawable sample photo as fallback JPEG
        return try {
            val drawableRes = if (gender.equals("Women", ignoreCase = true)) {
                R.drawable.sample_model_female
            } else {
                R.drawable.sample_model_male
            }
            val tempFile = File(context.cacheDir, "daily_style_person_fallback.jpg")
            val bitmap = BitmapFactory.decodeResource(context.resources, drawableRes)
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            tempFile
        } catch (e: Exception) {
            android.util.Log.e("DailyStyleWorker", "Failed to resolve person photo fallback: ${e.message}")
            null
        }
    }

    private suspend fun resolveGarmentFile(prefs: android.content.SharedPreferences, gender: String): File? {
        // Priority 1: Try fetching fresh trending outfit from backend API
        try {
            val trendingResp = RetrofitClient.apiService.getTrendingOutfit(gender)
            val imgUrl = trendingResp.image_url
            if (imgUrl.isNotEmpty()) {
                val fullUrl = if (imgUrl.startsWith("http")) imgUrl else "https://api.tryzonai.com$imgUrl"
                val tempFile = File(context.cacheDir, "daily_style_garment_trend.jpg")
                val stream = URL(fullUrl).openStream()
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    FileOutputStream(tempFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    if (tempFile.exists() && tempFile.length() > 0) return tempFile
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("DailyStyleWorker", "Failed to download trending outfit API garment: ${e.message}")
        }

        // Priority 2: Saved last garment image
        val lastGarment = prefs.getString("last_garment_image_path", null)
        if (!lastGarment.isNullOrEmpty()) {
            val file = File(lastGarment)
            if (file.exists() && file.length() > 0) return file
        }

        // Priority 3: Fallback Sample Garment Drawable
        return try {
            val drawableRes = R.drawable.sample_model_male
            val tempFile = File(context.cacheDir, "daily_style_garment_fallback.jpg")
            val bitmap = BitmapFactory.decodeResource(context.resources, drawableRes)
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            tempFile
        } catch (e: Exception) {
            android.util.Log.e("DailyStyleWorker", "Failed to resolve garment fallback: ${e.message}")
            null
        }
    }

    private fun sendDailyStyleNotification(resultUrl: String, sessionId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tryzon_daily_style_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily AI Style Looks",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily personalized AI fashion looks generated just for you"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Detect device country (IN for India vs US/GB/Global International)
        val countryCode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0].country
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale.country
            }
        } catch (e: Exception) {
            Locale.getDefault().country
        }

        val isIndia = countryCode.equals("IN", ignoreCase = true)
        val activeMessageList = if (isIndia) indianDailyStyleMessages else internationalDailyStyleMessages
        val testDay = try { inputData.getString("test_day") } catch (e: Exception) { null }
        val currentDay = testDay ?: SimpleDateFormat("EEEE", Locale.US).format(java.util.Date())
        
        val randomQuote = activeMessageList[Random.nextInt(activeMessageList.size)]
        val dayHeader = when (currentDay) {
            "Monday" -> "Monday Mood ⚡"
            "Tuesday" -> "Trendy Tuesday 👗"
            "Wednesday" -> "Wednesday Glow ✨"
            "Thursday" -> "Thursday Upgrade 🔥"
            "Friday" -> "Friday Vibe 🥂"
            "Saturday" -> "Saturday Glamour 🌟"
            "Sunday" -> "Sunday Chic ☀️"
            else -> "Daily Style 💫"
        }
        val selectedMessage = Pair("$dayHeader: ${randomQuote.first}", randomQuote.second)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notification_type", "daily_style")
            putExtra("daily_style_url", resultUrl)
            putExtra("session_id", sessionId)
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            7701,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Download result bitmap for Rich BigPictureStyle Notification preview
        val resultBitmap: Bitmap? = try {
            val fullUrl = if (resultUrl.startsWith("http")) resultUrl else "https://api.tryzonai.com$resultUrl"
            val connection = URL(fullUrl).openConnection()
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doInput = true
            connection.connect()
            val input = connection.getInputStream()
            val raw = BitmapFactory.decodeStream(input)
            if (raw != null) formatNotificationBitmap(raw) else null
        } catch (e: Exception) {
            android.util.Log.w("DailyStyleWorker", "Failed to download notification preview bitmap: ${e.message}")
            null
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_t)
            .setContentTitle(selectedMessage.first)
            .setContentText(selectedMessage.second)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (resultBitmap != null) {
            builder.setLargeIcon(resultBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(resultBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setBigContentTitle(selectedMessage.first)
                    .setSummaryText(selectedMessage.second)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(selectedMessage.second))
        }

        notificationManager.notify(7701, builder.build())
    }

    private fun formatNotificationBitmap(original: Bitmap): Bitmap {
        return try {
            val targetWidth = 1024
            val targetHeight = 512
            val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(resultBitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
            
            // 1. Fill background completely to guarantee ZERO side black bars anywhere
            val bgScale = Math.max(targetWidth.toFloat() / original.width, targetHeight.toFloat() / original.height)
            val bgW = (original.width * bgScale).toInt()
            val bgH = (original.height * bgScale).toInt()
            val bgScaled = Bitmap.createScaledBitmap(original, bgW, bgH, true)
            val bgX = (targetWidth - bgW) / 2f
            val bgY = (targetHeight - bgH) / 2f
            
            canvas.drawBitmap(bgScaled, bgX, bgY, paint)

            // Ambient dark glass overlay behind foreground for contrast
            val overlayPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(130, 18, 18, 24)
            }
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), overlayPaint)

            // 2. Foreground: Fit upper body (Face + Shoulders + Full Shirt/Dress Outfit)
            val fgScale = targetHeight.toFloat() / (original.height * 0.72f)
            val fgW = (original.width * fgScale).toInt()
            val fgH = (original.height * fgScale).toInt()
            val fgScaled = Bitmap.createScaledBitmap(original, fgW, fgH, true)

            val fgX = (targetWidth - fgW) / 2f
            val fgY = 0f // Align top so head is at top and clothes flow down to chest/waist

            canvas.drawBitmap(fgScaled, fgX, fgY, paint)

            resultBitmap
        } catch (e: Exception) {
            original
        }
    }
}
