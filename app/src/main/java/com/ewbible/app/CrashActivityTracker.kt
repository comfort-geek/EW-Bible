package com.ewbible.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle

class CrashActivityTracker(private val context: Context) : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        CrashReporter.breadcrumb(context, "Activity CREATED: ${activity.localClassName}")
    }
    override fun onActivityStarted(activity: Activity) {
        CrashReporter.breadcrumb(context, "Activity STARTED: ${activity.localClassName}")
    }
    override fun onActivityResumed(activity: Activity) {
        CrashReporter.breadcrumb(context, "Activity RESUMED: ${activity.localClassName}")
    }
    override fun onActivityPaused(activity: Activity) {
        CrashReporter.breadcrumb(context, "Activity PAUSED: ${activity.localClassName}")
    }
    override fun onActivityStopped(activity: Activity) {
        CrashReporter.breadcrumb(context, "Activity STOPPED: ${activity.localClassName}")
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        CrashReporter.breadcrumb(context, "Activity DESTROYED: ${activity.localClassName}")
    }
}