package com.ewbible.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import com.ewbible.app.CrashReporter
import com.ewbible.app.data.database.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiRepository {

    private val _lastProvider = MutableStateFlow("Groq")
    val lastProvider: StateFlow<String> = _lastProvider.asStateFlow()

    // Proxy - keys live in Cloudflare, not in the app
    private val PROXY_BASE = "https://exportdefaultasyncfetchrequestenvconsturl.comfortcha34.workers.dev/"
    private val PROXY_HEADER = "X-EW-App"
    private val PROXY_VALUE = "ew-bible-v1"

    private val REMOTE_MODELS_URL =
        "https://raw.githubusercontent.com/comfort-geek/EW-Bible/main/models.json"

    private val FALLBACK_GEMINI = listOf(
        "gemini-4-hydrogen",
        "gemini-3.8-flash",
        "gemini-3.5-flash-lite"
    )
    private val FALLBACK_GROQ = listOf(
        "openai/gpt-oss-120b",
        "qwen/qwen3.8-27b",
        "openai/gpt-oss-20b"
    )
    private val FALLBACK_CLOUDFLARE = listOf(
        "@cf/meta/llama-3.3-70b-instruct",
        "@cf/meta/llama-3-8b-instruct"
    )

    private var remoteGemini = FALLBACK_GEMINI
    private var remoteGroq = FALLBACK_GROQ
    private var remoteCloudflare = FALLBACK_CLOUDFLARE
    private var remoteVersion = 3

    private val STREAM_TIMEOUT_MS = 120000L
    private val MAX_TOKENS = 4096

    private var cachedQuery = ""
    private var cachedWeb = ""
    private var cachedAt = 0L
    private val WEB_CACHE_MS = 10 * 60 * 1000L

    // ===== FIXED: Bold Title + Sources =====

    private companion object {
        const val STYLE_RULE = "You are the EW Bible assistant. Never mention Meta AI, Groq, Gemini, or what AI model you are. Never say 'as an AI'. Write short, warm, clear. Use short paragraphs, 1 to 3 sentences each. Leave a blank line between every paragraph. When you start a new idea, start the paragraph with a bold heading on its own line, like **Economic Impact**. Never write the words 'Bold Title:' or 'Bold Title'. Do NOT use bullet points, dashes, or numbered lists. For BIBLE and GENERAL answers, do NOT add a Sources section. For NEWS / FACT answers, you MUST end with a Sources section with at least 2 sources."

        val BIBLE_BOOKS = listOf(
            "genesis", "exodus", "leviticus", "numbers", "deuteronomy",
            "joshua", "judges", "ruth", "samuel", "kings", "chronicles",
            "ezra", "nehemiah", "esther", "job", "psalm", "proverb",
            "ecclesiastes", "song of solomon", "isaiah", "jeremiah",
            "lamentations", "ezekiel", "daniel", "hosea", "joel", "amos",
            "obadiah", "jonah", "micah", "nahum", "habakkuk", "zephaniah",
            "haggai", "zechariah", "malachi",
            "matthew", "mark", "luke", "john", "acts", "romans",
            "corinthians", "galatians", "ephesians", "philippians",
            "colossians", "thessalonians", "timothy", "titus", "philemon",
            "hebrews", "james", "peter", "jude", "revelation"
        )

        val BIBLE_REF_REGEX = Regex("\\b(${BIBLE_BOOKS.joinToString("|")})s?\\s+\\d+")
        val NUMBERED_BOOK_REGEX = Regex("\\b[12]\\s+(samuel|kings|chronicles|corinthians|thessalonians|timothy|peter|john)\\s+\\d+")

        val BIBLE_WORDS = setOf(
            "verse", "chapter", "bible", "scripture", "psalm", "proverb",
            "jesus", "god", "lord", "church", "pastor",
            "david", "moses", "apostle", "gospel", "sin", "faith",
            "salvation", "holy spirit", "baptism", "heaven", "hell",
            "abraham", "isaac", "jacob", "joseph", "samuel", "elijah",
            "peter", "paul", "john", "mary", "solomon", "noah"
        )

        val FACTUAL_INTENT = listOf(
            "who is", "who won", "who was", "who became", "who appointed",
            "what happened", "what is happening", "latest", "recent", "current",
            "today", "yesterday", "this week", "this year",
            "when did", "when will", "how many", "how much",
            "election", "results", "president", "minister", "government",
            "job", "jobs", "hiring", "vacancy", "vacancies", "apply", "salary",
            "news", "pope", "war", "football", "soccer", "score",
            "price of", "exchange rate", "weather"
        )

        val NEWS_BREAK_WORDS = listOf("malawi", "election", "news", "job", "president", "what happened", "who won")
    }

    suspend fun refreshModels(context: Context) {
        val prefs = context.getSharedPreferences("ew_models", Context.MODE_PRIVATE)
        try {
            val savedVersion = prefs.getInt("version", 3)
            val savedGemini = prefs.getString("gemini", null)
            val savedGroq = prefs.getString("groq", null)
            val savedCf = prefs.getString("cloudflare", null)
            if (savedGemini != null) {
                val arr = JSONArray(savedGemini)
                if (arr.length() > 0) remoteGemini = (0 until arr.length()).map { arr.getString(it) }
            }
            if (savedGroq != null) {
                val arr = JSONArray(savedGroq)
                if (arr.length() > 0) remoteGroq = (0 until arr.length()).map { arr.getString(it) }
            }
            if (savedCf != null) {
                val arr = JSONArray(savedCf)
                if (arr.length() > 0) remoteCloudflare = (0 until arr.length()).map { arr.getString(it) }
            }
            remoteVersion = savedVersion
        } catch (_: Exception) { }

        try {
            val conn = URL(REMOTE_MODELS_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(text)
                val newVersion = json.optInt("version", 0)
                if (newVersion > remoteVersion) {
                    val g = json.optJSONArray("gemini")
                    val q = json.optJSONArray("groq")
                    val c = json.optJSONArray("cloudflare")
                    if (g != null && g.length() > 0) remoteGemini = (0 until g.length()).map { g.getString(it) }
                    if (q != null && q.length() > 0) remoteGroq = (0 until q.length()).map { q.getString(it) }
                    if (c != null && c.length() > 0) remoteCloudflare = (0 until c.length()).map { c.getString(it) }
                    remoteVersion = newVersion
                    prefs.edit()
                        .putInt("version", newVersion)
                        .putString("gemini", JSONArray(remoteGemini).toString())
                        .putString("groq", JSONArray(remoteGroq).toString())
                        .putString("cloudflare", JSONArray(remoteCloudflare).toString())
                        .apply()
                }
            }
            conn.disconnect()
        } catch (_: Exception) { }
    }

    private enum class ContextType { BIBLE, BREAKDOWN, NEWS, GENERAL }

    private fun isBibleQuestion(q: String): Boolean = BIBLE_WORDS.any { q.contains(it) }

    private fun isBibleReference(q: String): Boolean =
        BIBLE_REF_REGEX.containsMatchIn(q) || NUMBERED_BOOK_REGEX.containsMatchIn(q)

    private fun needsBreakdown(question: String): Boolean {
        val q = question.lowercase().trim()
        return q.contains("break down") || q.contains("breakdown") || q.contains("break it down") ||
            q.contains("verse by verse") || q.contains("portion") || q.contains("in portions") ||
            q.contains("in parts") || q.contains("step by step") ||
            q.contains("explain this passage") || q.contains("explain this chapter") ||
            q.contains("deep study") || q.contains("deep dive") || q.contains("go deep")
    }

    private fun detectContext(
        question: String,
        currentRef: String = "",
        history: List<MessageEntity> = emptyList()
    ): ContextType {
        val q = question.lowercase().trim()
        if (q == "hi" || q == "hello" || q == "hey" || q == "amen") return ContextType.GENERAL
        if (needsBreakdown(q)) return ContextType.BREAKDOWN
        if (isBibleReference(q)) return ContextType.BIBLE
        if (q.contains("in the bible") || q.contains("in bible") || q.contains("according to the bible") ||
            q.contains("bible says") || q.contains("from scripture") || q.contains("in scripture") ||
            q.contains("bible verse") || q.contains("bible passage")) return ContextType.BIBLE

        if ((q.contains("what does") || q.contains("want does") || q.contains("what dose") || q.contains("wht does")) && q.contains("mean")) return ContextType.BIBLE
        if (q.contains("does") && q.contains("mean") && isBibleQuestion(q)) return ContextType.BIBLE
        if (q.contains("meaning of") || q.contains("lesson from") || q.contains("teach me") || q.contains("devotion")) return ContextType.BIBLE

        val isReferring = q.contains("this") || q.contains("that") || q.contains("it") ||
            q.contains("verse") || q.contains("chapter") || q.contains("passage")
        val isMeaningIntent = q.contains("mean") || q.contains("explain") || q.contains("teach") ||
            q.contains("lesson") || q.contains("understand") || q.contains("interpret")
        if (isReferring && isMeaningIntent) return ContextType.BIBLE
        if (q.contains("explain") && (q.contains("verse") || q.contains("chapter") || q.contains("passage") || q.contains("psalm"))) return ContextType.BIBLE

        val ref = currentRef.lowercase().trim()
        val isInBibleView = ref.isNotBlank() && ref != "home" && !ref.contains("chat")
        if (isInBibleView && q.length < 80) {
            if (isMeaningIntent || q.contains("today") || q.contains("now") || q.contains("for me") || q.contains("apply")) {
                if (NEWS_BREAK_WORDS.none { q.contains(it) }) return ContextType.BIBLE
            }
        }

        if (history.isNotEmpty()) {
            val lastTwo = history.takeLast(2).joinToString(" ") { it.text.lowercase() }
            val wasBible = lastTwo.contains("verse") || lastTwo.contains("bible") || lastTwo.contains("psalm") ||
                lastTwo.contains("romans") || lastTwo.contains("scripture") || lastTwo.contains("chapter")
            if (wasBible && NEWS_BREAK_WORDS.none { q.contains(it) }) {
                if (isMeaningIntent || q.contains("it") || q.contains("this") || q.contains("today") || q.contains("for me")) return ContextType.BIBLE
            }
        }

        if (FACTUAL_INTENT.any { q.contains(it) }) return ContextType.NEWS
        if (isBibleQuestion(q)) return ContextType.BIBLE
        return ContextType.GENERAL
    }

    private fun needsWebSearch(question: String, currentRef: String = "", history: List<MessageEntity> = emptyList()): Boolean =
        detectContext(question, currentRef, history) == ContextType.NEWS

    private suspend fun getWebContext(question: String, currentRef: String = "", history: List<MessageEntity> = emptyList()): String {
        if (!needsWebSearch(question, currentRef, history)) return ""
        val key = question.trim().lowercase()
        if (key == cachedQuery && System.currentTimeMillis() - cachedAt < WEB_CACHE_MS && cachedWeb.isNotBlank()) return cachedWeb
        val lower = question.lowercase()
        val timeRange = if (lower.contains("today") || lower.contains("yesterday") || lower.contains("latest") || lower.contains("news")) "week" else "month"
        return withTimeoutOrNull(4000L) { searchTavily(question, timeRange) } ?: ""
    }

    private suspend fun searchTavily(query: String, timeRange: String = "month"): String {
        return try {
            var conn: HttpURLConnection? = null
            try {
                val searchUrl = URL(PROXY_BASE + "search")
                conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty(PROXY_HEADER, PROXY_VALUE)
                    connectTimeout = 3000
                    readTimeout = 4000
                    doOutput = true
                }
                val body = JSONObject()
                    .put("query", query)
                    .put("search_depth", "basic")
                    .put("max_results", 3)
                    .put("include_answer", true)
                    .put("time_range", timeRange)
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) return ""
                val text = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(text)
                val sb = StringBuilder()
                val answer = json.optString("answer")
                if (answer.isNotBlank()) sb.append("TAVILY ANSWER: $answer\n\n")
                val results = json.optJSONArray("results")
                if (results != null) {
                    for (i in 0 until minOf(results.length(), 3)) {
                        val r = results.getJSONObject(i)
                        sb.append("SOURCE ${i + 1}:\n")
                        sb.append("Title: ${r.optString("title")}\n")
                        sb.append("URL: ${r.optString("url")}\n")
                        sb.append("Content: ${r.optString("content").take(500)}\n")
                        sb.append("Published: ${r.optString("published_date")}\n\n")
                    }
                }
                val out = sb.toString().take(3000)
                if (out.isNotBlank()) {
                    cachedQuery = query.trim().lowercase()
                    cachedWeb = out
                    cachedAt = System.currentTimeMillis()
                }
                out
            } finally {
                runCatching { conn?.disconnect() }
            }
        } catch (_: Exception) { "" }
    }

    fun askStreaming(
        question: String,
        currentRef: String,
        history: List<MessageEntity>,
        image: Bitmap?,
        bibleContext: String,
        translation: String = "NKJV"
    ): Flow<String> = flow {
        val context = detectContext(question, currentRef, history)
        val webContext = if (image == null) getWebContext(question, currentRef, history) else ""
        val isBreakdown = context == ContextType.BREAKDOWN
        val basePrompt = buildPrompt(question, currentRef, history, bibleContext, translation, webContext, isBreakdown, context)
        val errors = mutableListOf<String>()

        suspend fun tryOne(provider: String, model: String, stream: Flow<String>): Boolean {
            _lastProvider.value = provider
            var gotAnything = false
            try {
                withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                    stream.collect { chunk ->
                        if (chunk.isNotEmpty()) { gotAnything = true; emit(chunk) }
                    }
                    true
                } ?: false
                if (gotAnything) return true
                return false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                errors.add("$provider $model: ${e.message}")
                if (provider == "Gemini") CrashReporter.geminiLog("Gemini $model failed: ${e.message}")
                else CrashReporter.groqLog("$provider $model failed: ${e.message}")
                return false
            }
        }

        if (image != null) {
            for (model in remoteGemini) {
                if (tryOne("Gemini", model, tryGeminiStreaming(model, basePrompt, image))) return@flow
            }
            emit("Could not read the image. Please try again.")
            return@flow
        }
        // ORDER: Groq -> Cloudflare -> Gemini last
        for (model in remoteGroq) {
            if (tryOne("Groq", model, tryGroqStreaming(model, basePrompt, isBreakdown))) return@flow
        }
        for (model in remoteCloudflare) {
            if (tryOne("Cloudflare", model, tryCloudflareStreaming(model, basePrompt, isBreakdown))) return@flow
        }
        for (model in remoteGemini) {
            if (tryOne("Gemini", model, tryGeminiStreaming(model, basePrompt, null))) return@flow
        }
        if (errors.isNotEmpty() && errors.all { it.contains("429") }) {
            emit("Free daily limit reached for today. Please try again tomorrow.")
        } else {
            emit("I am busy right now. Please try again in a minute.")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun askQuestion(prompt: String, image: Bitmap? = null): Result<String> {
        val context = detectContext(prompt)
        val webContext = if (image == null) getWebContext(prompt) else ""
        val isBreakdown = context == ContextType.BREAKDOWN
        val fullPrompt = buildPrompt(prompt, "", emptyList(), "", "NKJV", webContext, isBreakdown, context)
        val sb = StringBuilder()
        return try {
            var lastErr: Exception? = null
            if (image != null) {
                for (model in remoteGemini) {
                    _lastProvider.value = "Gemini"
                    try {
                        sb.clear()
                        val completed = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                            tryGeminiStreaming(model, fullPrompt, image).collect { sb.append(it) }
                            true
                        } ?: false
                        if (sb.isNotBlank() && completed) return Result.success(cleanMarkdown(sb.toString()).trim())
                        if (sb.isNotBlank() && !completed) continue
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { lastErr = e; sb.clear() }
                }
                return Result.failure(Exception("Could not read the image. Please try again."))
            }
            // ORDER: Groq -> Cloudflare -> Gemini last
            for (model in remoteGroq) {
                _lastProvider.value = "Groq"
                try {
                    sb.clear()
                    val completed = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                        tryGroqStreaming(model, fullPrompt, isBreakdown).collect { sb.append(it) }
                        true
                    } ?: false
                    if (sb.isNotBlank() && completed) return Result.success(cleanMarkdown(sb.toString()).trim())
                    if (sb.isNotBlank() && !completed) continue
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { lastErr = e; sb.clear() }
            }
            for (model in remoteCloudflare) {
                _lastProvider.value = "Cloudflare"
                try {
                    sb.clear()
                    val completed = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                        tryCloudflareStreaming(model, fullPrompt, isBreakdown).collect { sb.append(it) }
                        true
                    } ?: false
                    if (sb.isNotBlank() && completed) return Result.success(cleanMarkdown(sb.toString()).trim())
                    if (sb.isNotBlank() && !completed) continue
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { lastErr = e; sb.clear() }
            }
            for (model in remoteGemini) {
                _lastProvider.value = "Gemini"
                try {
                    sb.clear()
                    val completed = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                        tryGeminiStreaming(model, fullPrompt, null).collect { sb.append(it) }
                        true
                    } ?: false
                    if (sb.isNotBlank() && completed) return Result.success(cleanMarkdown(sb.toString()).trim())
                    if (sb.isNotBlank() && !completed) continue
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { lastErr = e; sb.clear() }
            }
            Result.failure(lastErr ?: Exception("I am busy right now. Please try again in a minute."))
        } catch (e: CancellationException) { throw e }
    }

    private fun buildPrompt(
        question: String,
        currentRef: String,
        history: List<MessageEntity>,
        bibleContext: String,
        translation: String,
        webContext: String = "",
        isBreakdown: Boolean = false,
        context: ContextType = ContextType.GENERAL
    ): String {
        val today = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date())

        val lengthRule = if (isBreakdown) {
            "The user asked for a breakdown. Break the passage or topic into 3 to 5 portions. For EACH portion: Start with 'Portion 1: [Title]' on its own line, then quote the verse/part, then 1 paragraph for meaning, then 1 paragraph for application. Leave a blank line between portions. Always finish fully."
        } else {
            "Do NOT break into portions. Let the length fit the question - a simple question gets 1 short paragraph, a deeper question gets 2 to 3 spaced paragraphs. Always finish your answer fully. Never stop mid-sentence or mid-verse."
        }

        val contextRule = when (context) {
            ContextType.BIBLE -> "CONTEXT: BIBLE. Answer only from Scripture. Quote $translation. If the question is about the Bible, cite the Bible reference instead, like 2 Samuel 18:9-15. Do NOT add news, jobs, or politics. End with application. Do NOT add a Sources section."
            ContextType.BREAKDOWN -> "CONTEXT: BREAKDOWN. Break into 3-5 portions as instructed. Do NOT add a Sources section."
            ContextType.NEWS -> "CONTEXT: NEWS / FACT. Answer ONLY from WEB SEARCH RESULTS. Do NOT quote Bible verses. Do NOT turn it into a sermon. Do NOT add prayer at the end. At the very end, you MUST add a Sources section. Format it exactly like this:\n\n**Sources**\nSource 1 - [Title from SOURCE 1](URL from SOURCE 1)\nSource 2 - [Title from SOURCE 2](URL from SOURCE 2)\n\nUse at least 2 different sources if 2 or more are given. If only 1 source is given, use only that 1. Use ONLY the URLs given in WEB SEARCH RESULTS. Never invent a URL, title, or date."
            ContextType.GENERAL -> "CONTEXT: GENERAL. Answer clearly and helpfully. Do NOT force Bible verses. Do NOT add a Sources section."
        }

        val sb = StringBuilder()
        sb.append("Today is $today. Use $translation when quoting Scripture. Current reading: $currentRef.\n")
        sb.append("$STYLE_RULE\n")
        sb.append("$contextRule\n$lengthRule\n\n")
        if (bibleContext.isNotBlank() && context != ContextType.NEWS) sb.append("BIBLE CONTEXT:\n${bibleContext.take(1000)}\n\n")
        if (webContext.isNotBlank()) {
            sb.append("WEB SEARCH RESULTS - TRUE FACTS AS OF $today. Answer ONLY from these results for factual claims. If the results do NOT contain the answer, say 'Please check your connection and try again.' and do NOT guess names, dates, or election results. Never invent a president, election result, or date:\n${webContext.take(3000)}\n\n")
        } else if (context == ContextType.NEWS) {
            sb.append("No web results were available. Do NOT guess names, dates, or election results. If you do not know the fact, say 'Please check your connection and try again.'\n\n")
        }
        if (history.isNotEmpty()) {
            sb.append("CONVERSATION HISTORY (last 4):\n")
            history.takeLast(4).forEach {
                sb.append(if (it.isUser) "User: " else "Assistant: ").append(it.text.take(400)).append("\n")
            }
            sb.append("\n")
        }
        sb.append("User question: $question")
        return sb.toString()
    }

    // ===== FIX 2: cleanMarkdown - strips **Bold Title:** fully, keeps **bold** + links =====
    private fun cleanMarkdown(text: String): String {
        var cleaned = text
            .replace("**Bold Title:**", "")
            .replace("**Bold Title**", "")
            .replace("Bold Title:", "")
            .replace("Bold Title", "")
            .replace("__", "")
            .replace("### ", "").replace("## ", "").replace("# ", "").replace("---", "")
            .replace("`", "")
        // Keep **bold** and [Title](URL) links - do NOT strip them
        return cleaned.lines().joinToString("\n") { line ->
            var l = line.trimStart()
            if (!l.startsWith("Source ")) {
                l = l.removePrefix("- ").removePrefix("* ").removePrefix("• ").removePrefix("> ")
                l = l.replace(Regex("^\\d+\\.\\s"), "")
                l = l.replace(Regex("^\\d+\\)\\s"), "")
            }
            l
        }.trim()
    }

    // ===== FIX 3: cleanStreamingChunk - same cleaning as cleanMarkdown =====
    private fun cleanStreamingChunk(text: String): String {
        return text
            .replace("**Bold Title:**", "")
            .replace("**Bold Title**", "")
            .replace("Bold Title:", "")
            .replace("Bold Title", "")
            .replace("__", "")
            .replace("### ", "").replace("## ", "").replace("# ", "")
            .replace("`", "")
    }

    private fun bitmapToBase64(image: Bitmap?): String? {
        if (image == null) return null
        return try {
            val scaled = if (image.width > 1024) {
                val ratio = 1024f / image.width
                Bitmap.createScaledBitmap(image, 1024, (image.height * ratio).toInt(), true)
            } else image
            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                out.toByteArray()
            }
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    private fun tryGeminiStreaming(model: String, prompt: String, image: Bitmap?): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(PROXY_BASE + "gemini?model=$model")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty(PROXY_HEADER, PROXY_VALUE)
                connectTimeout = 3000
                readTimeout = 120000
                doOutput = true
            }
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            val base64 = bitmapToBase64(image)
            if (base64 != null) {
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", base64)))
            }
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put("generationConfig", JSONObject().put("maxOutputTokens", MAX_TOKENS).put("temperature", 0.7))
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) throw Exception("Gemini ${conn.responseCode}")
            val reader = BufferedReader(conn.inputStream.bufferedReader())
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (l.isEmpty() || l == "[DONE]") continue
                val data = if (l.startsWith("data:")) l.removePrefix("data:").trim() else l
                if (data.isEmpty() || data == "[DONE]") continue
                try {
                    val json = JSONObject(data)
                    val candidates = json.optJSONArray("candidates") ?: continue
                    if (candidates.length() == 0) continue
                    val content = candidates.getJSONObject(0).optJSONObject("content") ?: continue
                    val p = content.optJSONArray("parts") ?: continue
                    for (i in 0 until p.length()) {
                        val t = p.getJSONObject(i).optString("text")
                        if (t.isNotEmpty()) emit(cleanStreamingChunk(t))
                    }
                } catch (_: Exception) { }
            }
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun tryGroqStreaming(model: String, prompt: String, isBreakdown: Boolean): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(PROXY_BASE + "groq")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty(PROXY_HEADER, PROXY_VALUE)
                connectTimeout = 3000
                readTimeout = 120000
                doOutput = true
            }
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", STYLE_RULE))
                .put(JSONObject().put("role", "user").put("content", prompt))
            val body = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("stream", true)
                .put("max_tokens", MAX_TOKENS)
                .put("temperature", 0.7)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) throw Exception("Groq ${conn.responseCode}")
            val reader = BufferedReader(conn.inputStream.bufferedReader())
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (l.isEmpty() || l == "[DONE]") continue
                val data = if (l.startsWith("data:")) l.removePrefix("data:").trim() else l
                if (data.isEmpty() || data == "[DONE]") continue
                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                    val content = delta?.optString("content") ?: choices.getJSONObject(0).optJSONObject("message")?.optString("content") ?: ""
                    if (content.isNotEmpty()) emit(cleanStreamingChunk(content))
                } catch (_: Exception) { }
            }
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun tryCloudflareStreaming(model: String, prompt: String, isBreakdown: Boolean): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(PROXY_BASE + "cloudflare")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty(PROXY_HEADER, PROXY_VALUE)
                connectTimeout = 3000
                readTimeout = 120000
                doOutput = true
            }
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", STYLE_RULE))
                .put(JSONObject().put("role", "user").put("content", prompt))
            val body = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("stream", true)
                .put("max_tokens", MAX_TOKENS)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) throw Exception("Cloudflare ${conn.responseCode}")
            val reader = BufferedReader(conn.inputStream.bufferedReader())
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (l.isEmpty() || l == "[DONE]") continue
                val data = if (l.startsWith("data:")) l.removePrefix("data:").trim() else l
                if (data.isEmpty() || data == "[DONE]") continue
                try {
                    val json = JSONObject(data)
                    var content = json.optJSONObject("result")?.optString("response") ?: ""
                    if (content.isEmpty()) {
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            content = choices.getJSONObject(0).optJSONObject("delta")?.optString("content") ?: ""
                        }
                    }
                    if (content.isNotEmpty()) emit(cleanStreamingChunk(content))
                } catch (_: Exception) { }
            }
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}