package com.ewbible.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"
    private const val HISTORY_FILE = "crash_history.txt"
    private const val BREADCRUMB_FILE = "breadcrumbs.txt"
    private const val STATE_FILE = "last_state.txt"
    private const val MAX_BREADCRUMBS = 100
    private const val MAX_HISTORY = 10

    @Volatile var lastState: String = "unknown"
    @Volatile var lastBook: String = ""
    @Volatile var lastChapter: Int = -1
    @Volatile var lastTab: String = ""
    @Volatile var lastVerse: Int = -1

    // Use this as your global coroutine handler everywhere
    val coroutineHandler = CoroutineExceptionHandler { _, throwable ->
        // We don't have Context here, so store it
        appContext?.let { recordNonFatal(it, "Coroutine", throwable) }
    }

    @PublishedApi
    @Volatile internal var appContext: Context? = null
    @Volatile private var isInstalled = false

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

    fun breadcrumb(message: String) {
        appContext?.let { breadcrumb(it, message) }
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

    fun setState(book: String, chapter: Int, tab: String, verse: Int = -1) {
        appContext?.let { setState(it, book, chapter, tab, verse) }
    }

    // Call ONCE from Application.onCreate, before anything else
    fun init(context: Context) {
        if (isInstalled) return
        isInstalled = true
        appContext = context.applicationContext

        try {
            val stateFile = context.filesDir.resolve(STATE_FILE)
            if (stateFile.exists()) lastState = stateFile.readText()
        } catch (_: Exception) { }

        breadcrumb(context, "CrashReporter.init - app started")

        // 1. Catch ALL uncaught exceptions, any thread, including main
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(context, thread, throwable, isFatal = true)
                context.filesDir.resolve(FILE_NAME).writeText(report)
                appendHistory(context, report)
            } catch (_: Exception) { }
            // Must call old handler or app will hang instead of crashing
            oldHandler?.uncaughtException(thread, throwable)
            // If no old handler, kill so system shows crash dialog
            if (oldHandler == null) {
                Process.killProcess(Process.myPid())
            }
        }

        // 2. Catch exceptions in Application lifecycle
        if (context is Application) {
            context.registerActivityLifecycleCallbacks(
                CrashActivityTracker(context)
            )
        }

        // 3. Logcat watcher - so even if stack trace is empty we have logs
        Thread {
            try {
                val process = Runtime.getRuntime().exec("logcat -v time -d")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val lines = mutableListOf<String>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lines.add(line!!)
                    if (lines.size > 500) lines.removeAt(0)
                }
                context.filesDir.resolve("last_logcat.txt").writeText(lines.joinToString("\n"))
            } catch (_: Exception) { }
        }.start()
    }

    // Wrap ANY risky block: DB open, asset copy, parsing, etc.
    // Usage: CrashReporter.safe("openDb") { Room.databaseBuilder(...) }
    inline fun <T> safe(tag: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: Throwable) {
            appContext?.let { recordNonFatal(it, tag, e) }
            breadcrumb("SAFE-CAUGHT [$tag]: ${e.message}")
            Log.e("CrashReporter", "safe[$tag]", e)
            null
        }
    }

    suspend fun <T> safeSuspend(tag: String, block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Throwable) {
            appContext?.let { recordNonFatal(it, tag, e) }
            breadcrumb("SAFE-CAUGHT [$tag]: ${e.message}")
            Log.e("CrashReporter", "safeSuspend[$tag]", e)
            null
        }
    }

    fun recordNonFatal(context: Context, tag: String, throwable: Throwable) {
        try {
            breadcrumb(context, "NON-FATAL: $tag - ${throwable.message}")
            val report = buildReport(context, Thread.currentThread(), throwable, isFatal = false, tag = tag)
            context.filesDir.resolve("last_nonfatal.txt").writeText(report)
            appendHistory(context, report)
            Log.e("CrashReporter", "nonfatal[$tag]", throwable)
        } catch (_: Exception) { }
    }

    fun recordNonFatal(tag: String, throwable: Throwable) {
        appContext?.let { recordNonFatal(it, tag, throwable) }
    }

    fun getLastCrash(context: Context): String? {
        val file = context.filesDir.resolve(FILE_NAME)
        return if (file.exists() && file.readText().isNotBlank()) file.readText() else null
    }

    fun getLastNonFatal(context: Context): String? {
        val file = context.filesDir.resolve("last_nonfatal.txt")
        return if (file.exists() && file.readText().isNotBlank()) file.readText() else null
    }

    fun getHistory(context: Context): String? {
        val file = context.filesDir.resolve(HISTORY_FILE)
        return if (file.exists()) file.readText() else null
    }

    fun getLogcat(context: Context): String? {
        val file = context.filesDir.resolve("last_logcat.txt")
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        try { context.filesDir.resolve(FILE_NAME).delete() } catch (_: Exception) { }
    }

    fun clearAll(context: Context) {
        listOf(FILE_NAME, HISTORY_FILE, "last_nonfatal.txt", BREADCRUMB_FILE, STATE_FILE, "last_logcat.txt").forEach {
            try { context.filesDir.resolve(it).delete() } catch (_: Exception) { }
        }
        lastState = "unknown"
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
            if (file.exists() && file.readText().isNotBlank()) sb.append(file.readText()).append("\n")
            else sb.append("(none)\n")
        } catch (_: Exception) { sb.append("(could not read breadcrumbs)\n") }
        sb.append("\n")

        sb.append("=== STACK TRACE ===\n")
        appendThrowable(sb, throwable, 0)
        sb.append("\n")

        sb.append("=== ALL THREADS (to find deadlock / ANR) ===\n")
        try {
            Thread.getAllStackTraces().forEach { (t, stack) ->
                sb.append("Thread: ${t.name} state=${t.state}\n")
                stack.take(15).forEach { sb.append("    at $it\n") }
            }
        } catch (e: Exception) { sb.append("Threads unavailable: ${e.message}\n") }
        sb.append("\n")

        sb.append("=== LAST LOGCAT (last 200 lines) ===\n")
        try {
            val process = Runtime.getRuntime().exec("logcat -v time -d -t 200")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLines().forEach { sb.append(it).append("\n") }
        } catch (e: Exception) { sb.append("Logcat unavailable: ${e.message}\n") }

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