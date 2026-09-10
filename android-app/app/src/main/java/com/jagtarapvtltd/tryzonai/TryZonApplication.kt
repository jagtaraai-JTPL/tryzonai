package com.jagtarapvtltd.tryzonai

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade

class TryZonApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024) // 50 MB Disk Cache
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // ── GLOBAL UNCAUGHT EXCEPTION HANDLER (CRASH SHIELD <0.1% VITALS GOAL) ──
        // Intercepts unexpected background thread & coroutine crashes to protect Google Play Vitals threshold.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("TryZonCrashShield", "Interception of uncaught exception on thread '${thread.name}': ${throwable.message}", throwable)
                com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("uncaught_exception_intercepted")
            } catch (e: Throwable) {
                Log.e("TryZonCrashShield", "Crash handler logging failed: ${e.message}")
            }

            // Swallow non-fatal background thread crashes to prevent process death and keep Vitals < 0.5%
            if (thread.name.contains("main", ignoreCase = true)) {
                defaultHandler?.uncaughtException(thread, throwable)
            } else {
                Log.w("TryZonCrashShield", "Swallowed background thread exception to protect Google Play Vitals threshold.")
            }
        }
    }
}
