package com.jagtarapvtltd.tryzonai.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

class AppOpenAdManager(private val myApplication: Application) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var currentActivity: Activity? = null
    private var loadTime: Long = 0

    private val AD_UNIT_ID = if (com.jagtarapvtltd.tryzonai.BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/9257395921" // Google Official App Open Test Ad Unit ID
    } else {
        "ca-app-pub-5657564830162310/3843069281" // Production App Open Ad Unit ID
    }

    init {
        myApplication.registerActivityLifecycleCallbacks(this)
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } catch (e: Exception) {
            Log.e("AppOpenAdManager", "Failed to register ProcessLifecycleOwner observer: ${e.message}")
        }
    }

    fun fetchAd() {
        if (isAdAvailable() || isLoadingAd) {
            return
        }

        // AppOpenAd.load MUST be invoked on the Main Thread (UI Thread)
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                fetchAd()
            }
            return
        }

        isLoadingAd = true
        try {
            val request = AdRequest.Builder().build()
            AppOpenAd.load(
                myApplication,
                AD_UNIT_ID,
                request,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        isLoadingAd = false
                        loadTime = Date().time
                        Log.d("AppOpenAdManager", "App Open Ad loaded successfully.")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        isLoadingAd = false
                        Log.d("AppOpenAdManager", "Failed to load App Open Ad: ${loadAdError.message}")
                    }
                }
            )
        } catch (e: Exception) {
            isLoadingAd = false
            Log.e("AppOpenAdManager", "Exception during AppOpenAd.load: ${e.message}", e)
        }
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference: Long = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    fun showAdIfAvailable(activity: Activity) {
        showAdIfAvailable(activity, object : OnShowAdCompleteListener {
            override fun onShowAdComplete() {}
        })
    }

    private var lastShowTime: Long = 0
    private val COOLDOWN_MILLIS: Long = 300_000L // 5 Minutes Cooldown between App Open Ads

    fun showAdIfAvailable(activity: Activity, onShowAdCompleteListener: OnShowAdCompleteListener) {
        if (!activity.isAlive()) {
            Log.d("AppOpenAdManager", "Activity is not alive. Skipping App Open Ad.")
            onShowAdCompleteListener.onShowAdComplete()
            return
        }

        if (isShowingAd) {
            Log.d("AppOpenAdManager", "The app open ad is already showing.")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastShowTime < COOLDOWN_MILLIS) {
            Log.d("AppOpenAdManager", "Skipping App Open Ad due to 5-minute cooldown.")
            onShowAdCompleteListener.onShowAdComplete()
            return
        }

        if (!isAdAvailable()) {
            Log.d("AppOpenAdManager", "The app open ad is not ready yet.")
            onShowAdCompleteListener.onShowAdComplete()
            fetchAd()
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                Log.d("AppOpenAdManager", "App Open Ad dismissed.")
                onShowAdCompleteListener.onShowAdComplete()
                fetchAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAd = false
                Log.d("AppOpenAdManager", "App Open Ad failed to show: ${adError.message}")
                onShowAdCompleteListener.onShowAdComplete()
                fetchAd()
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                lastShowTime = System.currentTimeMillis()
                Log.d("AppOpenAdManager", "App Open Ad showed fullscreen content.")
            }
        }
        isShowingAd = true
        try {
            appOpenAd?.show(activity)
        } catch (e: Exception) {
            Log.e("AppOpenAdManager", "Error showing App Open Ad: ${e.message}", e)
            appOpenAd = null
            isShowingAd = false
            onShowAdCompleteListener.onShowAdComplete()
            fetchAd()
        }
    }

    // App Open Ads are completely disabled per user preference for smooth UX
    override fun onStart(owner: LifecycleOwner) {
        Log.d("AppOpenAdManager", "App Open Ads are disabled for seamless UX.")
        return
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        if (!isShowingAd) {
            currentActivity = activity
        }
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        // Note: Do NOT trigger AppOpenAd onActivityResumed so Google Auth sheet / System dialogs are NEVER interrupted!
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }
}
