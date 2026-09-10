package com.jagtarapvtltd.tryzonai.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsHelper {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
        }
    }

    fun setUserId(userId: String?) {
        try {
            firebaseAnalytics?.setUserId(userId)
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsHelper", "Failed to set userId: $userId", e)
        }
    }

    fun setUserProperty(name: String, value: String?) {
        try {
            firebaseAnalytics?.setUserProperty(name, value)
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsHelper", "Failed to set user property: $name = $value", e)
        }
    }

    fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            firebaseAnalytics?.logEvent(eventName, params)
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsHelper", "Failed to log event: $eventName", e)
        }
    }

    fun logAffiliateClick(productId: Long, store: String, brand: String? = null) {
        val bundle = Bundle().apply {
            putLong("product_id", productId)
            putString("store", store)
            brand?.let { putString("brand", it) }
        }
        logEvent("affiliate_product_click", bundle)
    }
}
