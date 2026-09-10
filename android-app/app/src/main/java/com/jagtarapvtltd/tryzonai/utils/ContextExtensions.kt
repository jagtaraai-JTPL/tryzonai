package com.jagtarapvtltd.tryzonai.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Safely traverses the ContextWrapper hierarchy to find the host Activity.
 * Prevents ClassCastException when LocalContext.current is a ContextThemeWrapper in Jetpack Compose.
 */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Extension helper to check if an Activity is safe for UI/Dialog/Ad operations.
 */
fun Activity?.isAlive(): Boolean {
    return this != null && !isFinishing && !isDestroyed
}
