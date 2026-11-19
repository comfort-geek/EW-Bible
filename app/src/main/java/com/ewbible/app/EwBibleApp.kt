package com.ewbible.app

import android.app.Application

class EwBibleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.init(this)
        CrashReporter.breadcrumb(this, "EwBibleApp.onCreate")
    }
}