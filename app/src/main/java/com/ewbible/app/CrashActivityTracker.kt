package com.ewbible.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle

class CrashActivityTracker(context: Context) : Application.ActivityLifecycleCallbacks {

    // ✅ FIXED: hold Application context only - never an Activity
    private val appContext: Context = context.applicationContext

    // ✅ FIXED: reporter can never crash the app it's watching
    private fun safeBreadcrumb(msg: String) {
        try {
            CrashReporter.breadcrumb(appContext, msg)
        } catch (_: Exception) {
            // Never let crash-reporting cause a crash
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        safeBreadcrumb("Activity CREATED: ${activity.localClassName}")
    }
    override fun onActivityStarted(activity: Activity) {
        safeBreadcrumb("Activity STARTED: ${activity.localClassName}")
    }
    override fun onActivityResumed(activity: Activity) {
        safeBreadcrumb("Activity RESUMED: ${activity.localClassName}")
    }
    override fun onActivityPaused(activity: Activity) {
        safeBreadcrumb("Activity PAUSED: ${activity.localClassName}")
    }
    override fun onActivityStopped(activity: Activity) {
        safeBreadcrumb("Activity STOPPED: ${activity.localClassName}")
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        safeBreadcrumb("Activity DESTROYED: ${activity.localClassName}")
    }
}