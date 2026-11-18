package com.ewbible.app

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"
    private const val HISTORY_FILE = "crash_history.txt"
    private const val BREADCRUMB_FILE = "breadcrumbs.txt"
    private const val STATE_FILE = "last_state.txt"
    private const val MAX_BREADCRUMBS = 50
    private const val MAX_HISTORY = 5

    @Volatile var lastState: String = "unknown"
    @Volatile var lastBook: String = ""
    @Volatile var lastChapter: Int = -1
    @Volatile var lastTab: String = ""
    @Volatile var lastVerse: Int = -1

    fun breadcrumb(context: Context, message: String) {
        try {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "[$time] $message"
            val file = context.filesDir.resolve(BREADCRUMB_FILE)
            val old = if (file.exists()) file.readLines() else emptyList()
            val updated = (old + line).takeLast(MAX_BREADCRUMBS)
            file.writeText(updated.joinToString("\n"))
        } catch (_: Exception) { }
    }

    fun setState(context: Context, book: String, chapter: Int, tab: String, verse: Int = -1) {
        lastBook = book
        lastChapter = chapter
        lastTab = tab
        lastVerse = verse
        lastState = "book=$book chapter=$chapter tab=$tab verse=$verse"
        try {
            context.filesDir.resolve(STATE_FILE).writeText(lastState)
        } catch (_: Exception) { }
    }

    fun init(context: Context) {
        try {
            val stateFile = context.filesDir.resolve(STATE_FILE)
            if (stateFile.exists()) lastState = stateFile.readText()
        } catch (_: Exception) { }

        breadcrumb(context, "CrashReporter.init")

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(context, thread, throwable, isFatal = true)
                context.filesDir.resolve(FILE_NAME).writeText(report)
                appendHistory(context, report)
            } catch (_: Exception) { }
            oldHandler?.uncaughtException(thread, throwable)
        }
    }

    fun recordNonFatal(context: Context, tag: String, throwable: Throwable) {
        try {
            breadcrumb(context, "NON-FATAL: $tag - ${throwable.message}")
            val report = buildReport(context, Thread.currentThread(), throwable, isFatal = false, tag = tag)
            context.filesDir.resolve("last_nonfatal.txt").writeText(report)
            appendHistory(context, report)
        } catch (_: Exception) { }
    }

    fun getLastCrash(context: Context): String? {
        val file = context.filesDir.resolve(FILE_NAME)
        return if (file.exists() && file.readText().isNotBlank()) file.readText() else null
    }

    fun getHistory(context: Context): String? {
        val file = context.filesDir.resolve(HISTORY_FILE)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        try { context.filesDir.resolve(FILE_NAME).delete() } catch (_: Exception) { }
    }

    fun clearAll(context: Context) {
        try { context.filesDir.resolve(FILE_NAME).delete() } catch (_: Exception) { }
        try { context.filesDir.resolve(HISTORY_FILE).delete() } catch (_: Exception) { }
        try { context.filesDir.resolve("last_nonfatal.txt").delete() } catch (_: Exception) { }
        try { context.filesDir.resolve(BREADCRUMB_FILE).delete() } catch (_: Exception) { }
        try { context.filesDir.resolve(STATE_FILE).delete() } catch (_: Exception) { }
    }

    private fun appendHistory(context: Context, report: String) {
        try {
            val file = context.filesDir.resolve(HISTORY_FILE)
            val existing = if (file.exists()) file.readText() else ""
            val separator = "\n\n========== CRASH ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ==========\n\n"
            val combined = existing + separator + report
            val parts = combined.split("========== CRASH ")
            val kept = if (parts.size > MAX_HISTORY) parts.takeLast(MAX_HISTORY) else parts
            file.writeText(kept.joinToString("========== CRASH "))
        } catch (_: Exception) { }
    }

    private fun buildReport(context: Context, thread: Thread, throwable: Throwable, isFatal: Boolean, tag: String = ""): String {
        val sb = StringBuilder()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

        sb.append("=== ${if (isFatal) "FATAL CRASH" else "NON-FATAL"} ===\n")
        if (tag.isNotBlank()) sb.append("Tag: $tag\n")
        sb.append("Time: $dateStr\n")
        sb.append("Thread: ${thread.name} (id=${thread.id}, isMain=${thread.name == "main"})\n")
        sb.append("Uptime: ${SystemClock.uptimeMillis()}ms | Elapsed: ${SystemClock.elapsedRealtime()}ms\n\n")

        sb.append("=== APP + DEVICE ===\n")
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val versionCode = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else pkg.versionCode.toLong()
            sb.append("App: ${context.packageName} v${pkg.versionName} ($versionCode)\n")
        } catch (e: Exception) { sb.append("App: unknown (${e.message})\n") }
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
        sb.append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("ABI: ${Build.SUPPORTED_ABIS.joinToString(",")}\n\n")

        sb.append("=== MEMORY ===\n")
        try {
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            sb.append("Total PSS: ${memInfo.totalPss} KB | Dalvik: ${memInfo.dalvikPss} KB | Native: ${memInfo.nativePss} KB\n")
            val runtime = Runtime.getRuntime()
            sb.append("Heap: used=${(runtime.totalMemory() - runtime.freeMemory()) / 1024} KB total=${runtime.totalMemory() / 1024} KB max=${runtime.maxMemory() / 1024} KB\n")
            sb.append("Native heap: allocated=${Debug.getNativeHeapAllocatedSize() / 1024} KB\n")
            sb.append("Process PID: ${Process.myPid()}\n")
        } catch (e: Exception) { sb.append("Memory: unavailable (${e.message})\n") }
        sb.append("\n")

        sb.append("=== LAST APP STATE ===\n")
        sb.append("State: $lastState\n")
        sb.append("Book: $lastBook | Chapter: $lastChapter | Tab: $lastTab | Verse: $lastVerse\n\n")

        sb.append("=== BREADCRUMBS (oldest -> newest) ===\n")
        try {
            val file = context.filesDir.resolve(BREADCRUMB_FILE)
            if (file.exists() && file.readText().isNotBlank()) {
                sb.append(file.readText()).append("\n")
            } else {
                sb.append("(none)\n")
            }
        } catch (_: Exception) { sb.append("(could not read breadcrumbs)\n") }
        sb.append("\n")

        sb.append("=== STACK TRACE ===\n")
        appendThrowable(sb, throwable, 0)
        sb.append("\n")
        return sb.toString()
    }

    private fun appendThrowable(sb: StringBuilder, throwable: Throwable, depth: Int) {
        val indent = "  ".repeat(depth)
        sb.append(indent).append(if (depth == 0) "" else "Caused by: ").append(throwable.toString()).append("\n")
        throwable.stackTrace.forEach { sb.append(indent).append("    at ").append(it.toString()).append("\n") }
        throwable.suppressed.forEach { sup ->
            sb.append(indent).append("    Suppressed: ").append(sup.toString()).append("\n")
        }
        throwable.cause?.let { appendThrowable(sb, it, depth + 1) }
    }
}