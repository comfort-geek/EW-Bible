package com.ewbible.app

import android.content.Context
import java.io.File

object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"

    fun init(context: Context) {
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = StringBuilder()
                trace.append(throwable.toString()).append("\n")
                throwable.stackTrace.forEach { trace.append("    at ").append(it.toString()).append("\n") }
                var cause = throwable.cause
                while (cause != null) {
                    trace.append("Caused by: ").append(cause.toString()).append("\n")
                    cause.stackTrace.forEach { trace.append("    at ").append(it.toString()).append("\n") }
                    cause = cause.cause
                }
                context.filesDir.resolve(FILE_NAME).writeText(trace.toString())
            } catch (_: Exception) { }
            oldHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getLastCrash(context: Context): String? {
        val file = context.filesDir.resolve(FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        try { context.filesDir.resolve(FILE_NAME).delete() } catch (_: Exception) { }
    }
}