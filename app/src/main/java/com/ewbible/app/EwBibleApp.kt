package com.ewbible.app

import android.app.Application

class EwBibleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.init(this)
        CrashReporter.breadcrumb(this, "EwBibleApp.onCreate")
        // REMOVED: CrashReporter.init() already registers CrashActivityTracker
        // Don't register it again here or you'll get double breadcrumbs
    }
}