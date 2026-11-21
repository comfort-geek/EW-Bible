package com.ewbible.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"
    private const val HISTORY_FILE = "crash_history.txt"
    private const val BREADCRUMB_FILE = "breadcrumbs.txt"
    private const val STATE_FILE = "last_state.txt"
    private const val GEMINI_FILE = "gemini_logs.txt"
    private const val GROQ_FILE = "groq_logs.txt"
    private const val NONFATAL_FILE = "last_nonfatal.txt"
    private const val LOGCAT_FILE = "last_logcat.txt"
    private const val MAX_BREADCRUMBS = 100
    private const val MAX_HISTORY = 10
    private const val MAX_GEMINI_LOGS = 200
    private const val MAX_GROQ_LOGS = 200
    private const val MAX_LOGCAT_LINES = 500
    private const val ANR_THRESHOLD_MS = 5000L
    private const val SLOW_DB_MS = 300L
    private const val SLOW_NETWORK_MS = 2000L
    private const val SLOW_OP_MS = 500L
    private const val FLUSH_DELAY_MS = 500L

    @Volatile var lastState: String = "unknown"
    @Volatile var lastBook: String = ""
    @Volatile var lastChapter: Int = -1
    @Volatile var lastTab: String = ""
    @Volatile var lastVerse: Int = -1

    val coroutineHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        appContext?.let { recordNonFatal(it, "Coroutine", throwable) }
            ?: Log.e("CrashReporter", "Coroutine error before init", throwable)
    }

    @PublishedApi
    @Volatile internal var appContext: Context? = null
    @Volatile private var isInstalled = false
    private val anrWatchdogStarted = AtomicBoolean(false)
    private val logcatStarted = AtomicBoolean(false)
    private val lastAnrAt = AtomicLong(0L)

    private val ioExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "CrashReporter-IO").apply { isDaemon = true }
        }

    private val breadcrumbLock = Any()
    private val breadcrumbDeque = ArrayDeque<String>(MAX_BREADCRUMBS)
    private val breadcrumbDirty = AtomicBoolean(false)

    private val geminiLock = Any()
    private val geminiDeque = ArrayDeque<String>(MAX_GEMINI_LOGS)
    private val _geminiLogsFlow = MutableStateFlow<List<String>>(emptyList())
    val geminiLogsFlow: StateFlow<List<String>> = _geminiLogsFlow
    private val geminiDirty = AtomicBoolean(false)

    private val groqLock = Any()
    private val groqDeque = ArrayDeque<String>(MAX_GROQ_LOGS)
    private val _groqLogsFlow = MutableStateFlow<List<String>>(emptyList())
    val groqLogsFlow: StateFlow<List<String>> = _groqLogsFlow
    private val groqDirty = AtomicBoolean(false)

    private val logcatLock = Any()
    private val logcatDeque = ArrayDeque<String>(MAX_LOGCAT_LINES)
    private val logcatDirty = AtomicBoolean(false)

    private fun snapshotBreadcrumbs(): List<String> = synchronized(breadcrumbLock) {
        breadcrumbDeque.toList()
    }
    private fun snapshotGeminiLogs(): List<String> = synchronized(geminiLock) {
        geminiDeque.toList()
    }
    private fun snapshotGroqLogs(): List<String> = synchronized(groqLock) {
        groqDeque.toList()
    }
    private fun snapshotLogcat(): List<String> = synchronized(logcatLock) {
        logcatDeque.toList()
    }

    private fun timeNow(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

    private val secretPatterns = listOf(
        Regex("(?i)(api[_-]?key\\s*[:=]\\s*)[^\\s&\"']+"),
        Regex("(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s\"']+"),
        Regex("(?i)(sk-[A-Za-z0-9_-]{10,})"),
        Regex("(?i)(gsk_[A-Za-z0-9_-]{10,})"),
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    )
    fun redact(message: String): String {
        var out = message
        secretPatterns.forEachIndexed { index, regex ->
            out = if (index == secretPatterns.lastIndex) {
                regex.replace(out, "[email-redacted]")
            } else {
                regex.replace(out, "\$1[redacted]")
            }
        }
        out = Regex("\\b[A-Za-z0-9_\\-]{32,}\\.[A-Za-z0-9_\\-]{10,}").replace(out, "[token-redacted]")
        return out
    }

    private fun scheduleFlush(dirty: AtomicBoolean, fileName: String, snapshot: () -> List<String>) {
        if (!dirty.compareAndSet(false, true)) return
        val ctx = appContext ?: run { dirty.set(false); return }
        ioExecutor.schedule({
            dirty.set(false)
            try {
                ctx.filesDir.resolve(fileName).writeText(snapshot().joinToString("\n"))
            } catch (_: Exception) { }
        }, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    fun breadcrumb(context: Context, message: String) {
        try {
            val line = "[${timeNow()}] ${redact(message)}"
            synchronized(breadcrumbLock) {
                breadcrumbDeque.addLast(line)
                while (breadcrumbDeque.size > MAX_BREADCRUMBS) breadcrumbDeque.removeFirst()
            }
            if (appContext == null) appContext = context.applicationContext
            scheduleFlush(breadcrumbDirty, BREADCRUMB_FILE, ::snapshotBreadcrumbs)
        } catch (_: Exception) { }
    }

    fun breadcrumb(message: String) {
        appContext?.let { breadcrumb(it, message) }
    }

    fun geminiLog(message: String) {
        try {
            val clean = redact(message)
            val line = "[${timeNow()}] $clean"
            Log.d("Gemini", clean)
            synchronized(geminiLock) {
                geminiDeque.addLast(line)
                while (geminiDeque.size > MAX_GEMINI_LOGS) geminiDeque.removeFirst()
                _geminiLogsFlow.value = geminiDeque.toList()
            }
            addBreadcrumbDirect("GEMINI: $clean")
            scheduleFlush(geminiDirty, GEMINI_FILE, ::snapshotGeminiLogs)
        } catch (_: Exception) { }
    }

    fun geminiLog(context: Context, message: String) {
        if (appContext == null) appContext = context.applicationContext
        geminiLog(message)
    }

    fun groqLog(message: String) {
        try {
            val clean = redact(message)
            val line = "[${timeNow()}] $clean"
            Log.d("Groq", clean)
            synchronized(groqLock) {
                groqDeque.addLast(line)
                while (groqDeque.size > MAX_GROQ_LOGS) groqDeque.removeFirst()
                _groqLogsFlow.value = groqDeque.toList()
            }
            addBreadcrumbDirect("GROQ: $clean")
            scheduleFlush(groqDirty, GROQ_FILE, ::snapshotGroqLogs)
        } catch (_: Exception) { }
    }

    fun groqLog(context: Context, message: String) {
        if (appContext == null) appContext = context.applicationContext
        groqLog(message)
    }

    private fun addBreadcrumbDirect(lineRaw: String) {
        val line = "[${timeNow()}] $lineRaw"
        synchronized(breadcrumbLock) {
            breadcrumbDeque.addLast(line)
            while (breadcrumbDeque.size > MAX_BREADCRUMBS) breadcrumbDeque.removeFirst()
        }
        scheduleFlush(breadcrumbDirty, BREADCRUMB_FILE, ::snapshotBreadcrumbs)
    }

    fun aiLog(provider: String, message: String) {
        if (provider.equals("Groq", ignoreCase = true)) groqLog(message) else geminiLog(message)
    }

    fun logPerformance(tag: String, durationMs: Long, thresholdMs: Long = SLOW_OP_MS, extra: String = "") {
        if (durationMs < thresholdMs) return
        val msg = "SLOW [$tag] ${durationMs}ms threshold=${thresholdMs}ms $extra"
        breadcrumb(msg)
        appContext?.let { recordNonFatal(it, "Performance-$tag", RuntimeException(msg)) }
    }

    // FIXED: removed 'inline' - inline cannot access private appContext
    fun <T> timed(tag: String, thresholdMs: Long = SLOW_OP_MS, block: () -> T): T {
        val start = SystemClock.uptimeMillis()
        return try {
            block()
        } finally {
            logPerformance(tag, SystemClock.uptimeMillis() - start, thresholdMs)
        }
    }

    suspend fun <T> timedSuspend(tag: String, thresholdMs: Long = SLOW_OP_MS, block: suspend () -> T): T {
        val start = SystemClock.uptimeMillis()
        return try {
            block()
        } finally {
            logPerformance(tag, SystemClock.uptimeMillis() - start, thresholdMs)
        }
    }

    fun logSlowDb(query: String, durationMs: Long) {
        if (durationMs >= SLOW_DB_MS) logPerformance("DB", durationMs, SLOW_DB_MS, "query=${redact(query)}")
    }

    fun logNetwork(url: String, status: Int, durationMs: Long, error: String? = null) {
        val host = try { java.net.URL(url).host } catch (_: Exception) { "unknown-host" }
        if (error != null || durationMs >= SLOW_NETWORK_MS || status >= 400) {
            val msg = "NETWORK host=$host status=$status ${durationMs}ms error=${redact(error ?: "")}"
            breadcrumb(msg)
            appContext?.let { recordNonFatal(it, "Network", RuntimeException(msg)) }
        }
    }

    fun logUiError(tag: String, throwable: Throwable) {
        recordNonFatal(tag, throwable)
    }

    fun logMemoryWarning() {
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMb = runtime.maxMemory() / 1024 / 1024
        val msg = "MEMORY-WARNING used=${usedMb}MB max=${maxMb}MB"
        breadcrumb(msg)
        checkDiskAndMemory()
    }

    fun checkDiskAndMemory() {
        val ctx = appContext ?: return
        try {
            val freeBytes = ctx.filesDir.freeSpace
            val freeMb = freeBytes / 1024 / 1024
            if (freeMb < 100) {
                val msg = "LOW-DISK free=${freeMb}MB filesDir=${ctx.filesDir.path}"
                breadcrumb(msg)
                recordNonFatal(ctx, "LowDisk", RuntimeException(msg))
            }
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            if (memInfo.totalPss > 350 * 1024) {
                val msg = "HIGH-PSS totalPss=${memInfo.totalPss}KB"
                breadcrumb(msg)
                recordNonFatal(ctx, "HighMemory", RuntimeException(msg))
            }
        } catch (_: Exception) { }
    }

    fun recordANR(mainStack: Array<StackTraceElement>, allStacks: Map<Thread, Array<StackTraceElement>>) {
        val ctx = appContext ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastAnrAt.get() < 30_000) return
        lastAnrAt.set(now)
        ioExecutor.execute {
            try {
                val sb = StringBuilder()
                sb.append("=== ANR (main thread stalled > ${ANR_THRESHOLD_MS}ms) ===\n")
                sb.append("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}\n")
                sb.append("State: $lastState\n")
                sb.append("--- Main thread ---\n")
                mainStack.forEach { sb.append("    at $it\n") }
                sb.append("--- All threads ---\n")
                allStacks.forEach { (t, stack) ->
                    sb.append("Thread: ${t.name} state=${t.state}\n")
                    stack.take(20).forEach { sb.append("    at $it\n") }
                }
                val report = sb.toString()
                ctx.filesDir.resolve(NONFATAL_FILE).writeText(report)
                appendHistory(ctx, report, label = "ANR")
                breadcrumb(ctx, "ANR recorded, main stalled")
            } catch (_: Exception) { }
        }
    }

    fun getGeminiLogs(context: Context): String? {
        val mem = snapshotGeminiLogs()
        if (mem.isNotEmpty()) return mem.joinToString("\n")
        val file = context.filesDir.resolve(GEMINI_FILE)
        return if (file.exists()) file.readText() else null
    }

    fun clearGeminiLogs(context: Context) {
        synchronized(geminiLock) {
            geminiDeque.clear()
            _geminiLogsFlow.value = emptyList()
        }
        ioExecutor.execute {
            try { context.filesDir.resolve(GEMINI_FILE).delete() } catch (_: Exception) { }
        }
    }

    fun getGroqLogs(context: Context): String? {
        val mem = snapshotGroqLogs()
        if (mem.isNotEmpty()) return mem.joinToString("\n")
        val file = context.filesDir.resolve(GROQ_FILE)
        return if (file.exists()) file.readText() else null
    }

    fun clearGroqLogs(context: Context) {
        synchronized(groqLock) {
            groqDeque.clear()
            _groqLogsFlow.value = emptyList()
        }
        ioExecutor.execute {
            try { context.filesDir.resolve(GROQ_FILE).delete() } catch (_: Exception) { }
        }
    }

    fun getAiLogs(context: Context): String {
        val g = getGeminiLogs(context) ?: "(no Gemini calls yet)"
        val q = getGroqLogs(context) ?: "(no Groq calls yet)"
        return "=== GEMINI ===\n$g\n\n=== GROQ ===\n$q"
    }

    fun setState(context: Context, book: String, chapter: Int, tab: String, verse: Int = -1) {
        lastBook = book
        lastChapter = chapter
        lastTab = tab
        lastVerse = verse
        lastState = "book=$book chapter=$chapter tab=$tab verse=$verse"
        if (appContext == null) appContext = context.applicationContext
        val ctx = appContext ?: return
        ioExecutor.execute {
            try { ctx.filesDir.resolve(STATE_FILE).writeText(lastState) } catch (_: Exception) { }
        }
    }

    fun setState(book: String, chapter: Int, tab: String, verse: Int = -1) {
        appContext?.let { setState(it, book, chapter, tab, verse) }
    }

    fun init(context: Context) {
        synchronized(this) {
            if (isInstalled) return
            isInstalled = true
        }
        val appCtx = context.applicationContext
        appContext = appCtx

        try {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites().detectNetwork()
                    .penaltyLog().build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects().detectLeakedClosableObjects()
                    .penaltyLog().build()
            )
        } catch (_: Exception) { }

        ioExecutor.execute {
            try {
                val f = appCtx.filesDir.resolve(BREADCRUMB_FILE)
                if (f.exists()) {
                    val lines = f.readLines().takeLast(MAX_BREADCRUMBS)
                    synchronized(breadcrumbLock) {
                        breadcrumbDeque.clear()
                        breadcrumbDeque.addAll(lines)
                    }
                }
                val g = appCtx.filesDir.resolve(GEMINI_FILE)
                if (g.exists()) {
                    val glines = g.readLines().takeLast(MAX_GEMINI_LOGS)
                    synchronized(geminiLock) {
                        geminiDeque.clear()
                        geminiDeque.addAll(glines)
                        _geminiLogsFlow.value = geminiDeque.toList()
                    }
                }
                val q = appCtx.filesDir.resolve(GROQ_FILE)
                if (q.exists()) {
                    val qlines = q.readLines().takeLast(MAX_GROQ_LOGS)
                    synchronized(groqLock) {
                        groqDeque.clear()
                        groqDeque.addAll(qlines)
                        _groqLogsFlow.value = groqDeque.toList()
                    }
                }
                val stateFile = appCtx.filesDir.resolve(STATE_FILE)
                if (stateFile.exists()) lastState = stateFile.readText()
                val lc = appCtx.filesDir.resolve(LOGCAT_FILE)
                if (lc.exists()) {
                    val llines = lc.readLines().takeLast(MAX_LOGCAT_LINES)
                    synchronized(logcatLock) {
                        logcatDeque.clear()
                        logcatDeque.addAll(llines)
                    }
                }
            } catch (_: Exception) { }
        }

        breadcrumb(appCtx, "CrashReporter.init - app started")

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(appCtx, thread, throwable, isFatal = true)
                appCtx.filesDir.resolve(FILE_NAME).writeText(report)
                appendHistory(appCtx, report, label = "FATAL")
            } catch (_: Exception) { }
            oldHandler?.uncaughtException(thread, throwable)
            if (oldHandler == null) {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }

        if (context is Application) {
            context.registerActivityLifecycleCallbacks(CrashActivityTracker())
        }

        startLogcatCapture()
        startAnrWatchdog()
        ioExecutor.execute { checkDiskAndMemory() }
    }

    // FIXED: java.lang.Process, not android.os.Process
    private fun startLogcatCapture() {
        if (!logcatStarted.compareAndSet(false, true)) return
        ioExecutor.execute {
            var process: java.lang.Process? = null
            try {
                process = Runtime.getRuntime().exec("logcat -v time")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val clean = redact(line!!)
                    synchronized(logcatLock) {
                        logcatDeque.addLast(clean)
                        while (logcatDeque.size > MAX_LOGCAT_LINES) logcatDeque.removeFirst()
                    }
                    scheduleFlush(logcatDirty, LOGCAT_FILE, ::snapshotLogcat)
                }
            } catch (_: Exception) {
            } finally {
                try { process?.destroy() } catch (_: Exception) { }
                logcatStarted.set(false)
            }
        }
    }

    private fun startAnrWatchdog() {
        if (!anrWatchdogStarted.compareAndSet(false, true)) return
        Thread({
            val mainHandler = android.os.Handler(Looper.getMainLooper())
            while (true) {
                try {
                    val responded = AtomicBoolean(false)
                    val postedAt = SystemClock.uptimeMillis()
                    mainHandler.post { responded.set(true) }
                    var waited = 0L
                    while (!responded.get() && waited < ANR_THRESHOLD_MS + 2000) {
                        Thread.sleep(100)
                        waited += 100
                    }
                    if (!responded.get()) {
                        val mainThread = Looper.getMainLooper().thread
                        recordANR(mainThread.stackTrace, Thread.getAllStackTraces())
                        Thread.sleep(10_000)
                    } else {
                        val took = SystemClock.uptimeMillis() - postedAt
                        if (took > 1000) logPerformance("MainThread-Jank", took, 1000)
                        Thread.sleep(1000)
                    }
                } catch (e: InterruptedException) {
                    return@Thread
                } catch (_: Exception) {
                    try { Thread.sleep(2000) } catch (_: InterruptedException) { return@Thread }
                }
            }
        }, "CrashReporter-ANR-Watchdog").apply { isDaemon = true }.start()
    }

    // FIXED: removed 'inline'
    fun <T> safe(tag: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            appContext?.let { recordNonFatal(it, tag, e) }
            breadcrumb("SAFE-CAUGHT [$tag]: ${redact(e.message ?: e.toString())}")
            Log.e("CrashReporter", "safe[$tag]", e)
            null
        }
    }

    suspend fun <T> safeSuspend(tag: String, block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            appContext?.let { recordNonFatal(it, tag, e) }
            breadcrumb("SAFE-CAUGHT [$tag]: ${redact(e.message ?: e.toString())}")
            Log.e("CrashReporter", "safeSuspend[$tag]", e)
            null
        }
    }

    fun recordNonFatal(context: Context, tag: String, throwable: Throwable) {
        if (throwable is CancellationException) return
        val ctx = context.applicationContext
        if (appContext == null) appContext = ctx
        ioExecutor.execute {
            try {
                addBreadcrumbDirect("NON-FATAL: $tag - ${redact(throwable.message ?: throwable.toString())}")
                val report = buildReport(ctx, Thread.currentThread(), throwable, isFatal = false, tag = tag)
                ctx.filesDir.resolve(NONFATAL_FILE).writeText(report)
                appendHistory(ctx, report, label = "NON-FATAL")
                Log.e("CrashReporter", "nonfatal[$tag]", throwable)
            } catch (_: Exception) { }
        }
    }

    fun recordNonFatal(tag: String, throwable: Throwable) {
        if (throwable is CancellationException) return
        val ctx = appContext
        if (ctx != null) recordNonFatal(ctx, tag, throwable)
        else Log.e("CrashReporter", "nonfatal[$tag] before init", throwable)
    }

    fun getLastCrash(context: Context): String? {
        val file = context.filesDir.resolve(FILE_NAME)
        if (!file.exists()) return null
        val text = try { file.readText() } catch (_: Exception) { return null }
        return if (text.isNotBlank()) text else null
    }

    fun getLastNonFatal(context: Context): String? {
        val file = context.filesDir.resolve(NONFATAL_FILE)
        if (!file.exists()) return null
        val text = try { file.readText() } catch (_: Exception) { return null }
        return if (text.isNotBlank()) text else null
    }

    fun getHistory(context: Context): String? {
        val file = context.filesDir.resolve(HISTORY_FILE)
        return if (file.exists()) file.readText() else null
    }

    fun getLogcat(context: Context): String? {
        val mem = snapshotLogcat()
        if (mem.isNotEmpty()) return mem.joinToString("\n")
        val file = context.filesDir.resolve(LOGCAT_FILE)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        try { context.filesDir.resolve(FILE_NAME).delete() } catch (_: Exception) { }
    }

    fun clearAll(context: Context) {
        listOf(FILE_NAME, HISTORY_FILE, NONFATAL_FILE, BREADCRUMB_FILE, STATE_FILE, LOGCAT_FILE, GEMINI_FILE, GROQ_FILE).forEach {
            try { context.filesDir.resolve(it).delete() } catch (_: Exception) { }
        }
        synchronized(breadcrumbLock) { breadcrumbDeque.clear() }
        synchronized(geminiLock) {
            geminiDeque.clear()
            _geminiLogsFlow.value = emptyList()
        }
        synchronized(groqLock) {
            groqDeque.clear()
            _groqLogsFlow.value = emptyList()
        }
        synchronized(logcatLock) { logcatDeque.clear() }
        lastState = "unknown"
        lastBook = ""
        lastChapter = -1
        lastTab = ""
        lastVerse = -1
    }

    private fun appendHistory(context: Context, report: String, label: String = "CRASH") {
        try {
            val file = context.filesDir.resolve(HISTORY_FILE)
            val existing = if (file.exists()) file.readText() else ""
            val separator = "\n\n========== $label ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ==========\n\n"
            val marker = "\n\n========== "
            val parts = if (existing.isEmpty()) emptyList() else existing.split(marker).filter { it.isNotBlank() }
            val rebuilt = parts.map { marker + it }
            val combined = rebuilt + listOf(separator + report)
            val kept = if (combined.size > MAX_HISTORY) combined.takeLast(MAX_HISTORY) else combined
            file.writeText(kept.joinToString(""))
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

        sb.append("=== MEMORY + DISK ===\n")
        try {
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            sb.append("Total PSS: ${memInfo.totalPss} KB | Dalvik: ${memInfo.dalvikPss} KB | Native: ${memInfo.nativePss} KB\n")
            val runtime = Runtime.getRuntime()
            sb.append("Heap: used=${(runtime.totalMemory() - runtime.freeMemory()) / 1024} KB total=${runtime.totalMemory() / 1024} KB max=${runtime.maxMemory() / 1024} KB\n")
            sb.append("Native heap: allocated=${Debug.getNativeHeapAllocatedSize() / 1024} KB\n")
            sb.append("Process PID: ${android.os.Process.myPid()}\n")
            val filesDir = context.filesDir
            sb.append("Disk free: ${filesDir.freeSpace / 1024 / 1024} MB | total: ${filesDir.totalSpace / 1024 / 1024} MB\n")
        } catch (e: Exception) { sb.append("Memory: unavailable (${e.message})\n") }
        sb.append("\n")

        sb.append("=== LAST APP STATE ===\n")
        sb.append("State: $lastState\n")
        sb.append("Book: $lastBook | Chapter: $lastChapter | Tab: $lastTab | Verse: $lastVerse\n\n")

        sb.append("=== BREADCRUMBS (oldest -> newest) ===\n")
        val crumbs = snapshotBreadcrumbs()
        if (crumbs.isNotEmpty()) sb.append(crumbs.joinToString("\n")).append("\n")
        else sb.append("(none)\n")
        sb.append("\n")

        sb.append("=== GEMINI LOGS (last 200) ===\n")
        val gLogs = snapshotGeminiLogs()
        if (gLogs.isNotEmpty()) sb.append(gLogs.joinToString("\n")).append("\n")
        else sb.append("(no Gemini calls yet)\n")
        sb.append("\n")

        sb.append("=== GROQ LOGS (last 200) ===\n")
        val qLogs = snapshotGroqLogs()
        if (qLogs.isNotEmpty()) sb.append(qLogs.joinToString("\n")).append("\n")
        else sb.append("(no Groq calls yet)\n")
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

        sb.append("=== LAST LOGCAT (in-memory ring, last 200) ===\n")
        try {
            val lines = snapshotLogcat().takeLast(200)
            if (lines.isNotEmpty()) lines.forEach { sb.append(it).append("\n") }
            else sb.append("(logcat empty - READ_LOGS not granted on this device)\n")
        } catch (e: Exception) { sb.append("Logcat unavailable: ${e.message}\n") }

        return sb.toString()
    }

    private fun appendThrowable(sb: StringBuilder, throwable: Throwable, depth: Int) {
        val indent = "  ".repeat(depth)
        sb.append(indent).append(if (depth == 0) "" else "Caused by: ").append(redact(throwable.toString())).append("\n")
        throwable.stackTrace.forEach { sb.append(indent).append("    at ").append(it.toString()).append("\n") }
        throwable.suppressed.forEach { sup ->
            sb.append(indent).append("    Suppressed: ").append(redact(sup.toString())).append("\n")
        }
        throwable.cause?.let { appendThrowable(sb, it, depth + 1) }
    }
}