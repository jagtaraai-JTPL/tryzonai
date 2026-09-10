package com.jagtarapvtltd.tryzonai.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private var rewardedAd: RewardedAd? = null
    private val adUnitId = if (com.jagtarapvtltd.tryzonai.BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/5224354917" // Official Google Android Rewarded Test Ad Unit ID
    } else {
        "ca-app-pub-5657564830162310/7742661163" // Production Rewarded Ad Unit ID
    }
    @Volatile private var isLoading = false

    companion object {
        @Volatile
        private var INSTANCE: AdManager? = null

        fun getInstance(context: Context): AdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        loadRewardedAd()
    }

    fun loadRewardedAd(onLoadedCallback: ((Boolean) -> Unit)? = null) {
        if (rewardedAd != null) {
            onLoadedCallback?.invoke(true)
            return
        }
        if (isLoading) {
            return
        }
        isLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            appContext,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdManager", "Rewarded Ad failed to load: ${adError.message} (code ${adError.code})")
                    rewardedAd = null
                    isLoading = false
                    onLoadedCallback?.invoke(false)
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdManager", "Rewarded Ad successfully loaded into memory!")
                    rewardedAd = ad
                    isLoading = false
                    onLoadedCallback?.invoke(true)
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onAdDismissed: () -> Unit,
        onRewardEarned: () -> Unit,
        onAdFailedToLoad: (() -> Unit)? = null
    ) {
        if (!activity.isAlive()) {
            Log.d("AdManager", "Activity is not alive. Skipping ad.")
            onAdFailedToLoad?.invoke() ?: onAdDismissed()
            return
        }

        if (rewardedAd != null) {
            presentAd(activity, onAdDismissed, onRewardEarned, onAdFailedToLoad)
        } else {
            Log.d("AdManager", "Rewarded Ad not preloaded yet. Attempting on-demand load...")
            var hasHandled = false
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (!hasHandled) {
                    hasHandled = true
                    Log.d("AdManager", "Ad loading timed out.")
                    onAdFailedToLoad?.invoke() ?: onAdDismissed()
                }
            }

            handler.postDelayed(timeoutRunnable, 3500)

            loadRewardedAd { success ->
                handler.removeCallbacks(timeoutRunnable)
                if (!hasHandled) {
                    hasHandled = true
                    if (success && rewardedAd != null && activity.isAlive()) {
                        presentAd(activity, onAdDismissed, onRewardEarned, onAdFailedToLoad)
                    } else {
                        Log.d("AdManager", "On-demand ad load failed.")
                        onAdFailedToLoad?.invoke() ?: onAdDismissed()
                    }
                }
            }
        }
    }

    private fun presentAd(
        activity: Activity,
        onAdDismissed: () -> Unit,
        onRewardEarned: () -> Unit,
        onAdFailedToLoad: (() -> Unit)? = null
    ) {
        val ad = rewardedAd ?: run {
            onAdFailedToLoad?.invoke() ?: onAdDismissed()
            return
        }
        
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AdManager", "Rewarded Ad dismissed by user.")
                rewardedAd = null
                loadRewardedAd() // Pre-load next ad for continuous playback
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e("AdManager", "Rewarded Ad failed to show: ${adError.message}")
                rewardedAd = null
                loadRewardedAd()
                onAdFailedToLoad?.invoke() ?: onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d("AdManager", "Rewarded Ad shown full screen!")
            }
        }

        try {
            ad.show(activity) { rewardItem ->
                Log.d("AdManager", "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewardEarned()
            }
        } catch (e: Exception) {
            Log.e("AdManager", "Exception showing rewarded ad: ${e.message}", e)
            rewardedAd = null
            loadRewardedAd()
            onAdFailedToLoad?.invoke() ?: onAdDismissed()
        }
    }
}
