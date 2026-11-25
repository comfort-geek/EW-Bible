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

    private val PROXY_BASE = "https://exportdefaultasyncfetchrequestenvconsturl.comfortcha34.workers.dev/"
    private val PROXY_HEADER = "X-EW-App"
    private val PROXY_VALUE = "ew-bible-v1"

    private val REMOTE_MODELS_URL =
        "https://raw.githubusercontent.com/comfort-geek/EW-Bible/main/models.json"

    private val FALLBACK_GEMINI = listOf("gemini-4-hydrogen", "gemini-3.8-flash", "gemini-3.5-flash-lite")
    private val FALLBACK_GROQ = listOf("openai/gpt-oss-120b", "qwen/qwen3.8-27b", "openai/gpt-oss-20b")
    private val FALLBACK_CLOUDFLARE = listOf("@cf/meta/llama-3.3-70b-instruct", "@cf/meta/llama-3-8b-instruct")

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

    private companion object {
        const val STYLE_RULE = "You are a helpful assistant. Answer naturally like a knowledgeable friend. Do not mention what model you are. Do not say 'as an AI'. Match your length to the question: short question = short answer, deep question = deeper answer. Use paragraphs, headings or lists only if they help clarity — do not force them. Do not force Bible verses, prayer, or a Sources section unless the question actually needs it."

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
            "price of", "exchange rate", "weather",
            "tell me about", "biography", "history of",
            "what is", "what are", "who are", "facts about", "define", "definition"
        )

        val GENERAL_TOPICS = listOf(
            "coding", "python", "java", "javascript", "programming", "developer",
            "computer", "laptop", "phone", "android", "app", "website",
            "cooking", "recipe", "football", "soccer", "music", "movie",
            "school", "exam", "business", "money", "job", "car", "house"
        )

        val BREAKDOWN_PHRASES = listOf(
            "break down", "breakdown", "break it down", "verse by verse",
            "in portions", "in parts", "deep study", "deep dive", "go deep",
            "explain this passage", "explain this chapter"
        )
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

    private fun detectContext(
        question: String,
        currentRef: String = "",
        history: List<MessageEntity> = emptyList()
    ): ContextType {
        val q = question.lowercase().trim()
        if (q in listOf("hi", "hello", "hey", "amen", "ok", "okay", "thanks", "thank you")) return ContextType.GENERAL

        var bibleScore = 0.0
        var breakdownScore = 0.0
        var newsScore = 0.0
        var generalScore = 1.0

        val hasBibleRef = BIBLE_REF_REGEX.containsMatchIn(q) || NUMBERED_BOOK_REGEX.containsMatchIn(q)
        if (hasBibleRef) bibleScore += 10

        if (q.contains("in the bible") || q.contains("bible says") || q.contains("according to the bible") || q.contains("from scripture")) bibleScore += 10

        val bibleWordCount = BIBLE_WORDS.count { q.contains(it) }
        bibleScore += minOf(bibleWordCount * 2.0, 6.0)

        val hasBreakdownPhrase = BREAKDOWN_PHRASES.any { q.contains(it) }
        val hasStepByStep = q.contains("step by step")
        if (hasBreakdownPhrase) {
            if (hasBibleRef || bibleWordCount > 0) breakdownScore += 9
            else if (q.contains("this") || q.contains("that") || q.contains("passage") || q.contains("chapter")) breakdownScore += 4
            else breakdownScore += 0.5
        }
        if (hasStepByStep) {
            if (hasBibleRef || bibleWordCount > 0) breakdownScore += 8
            else generalScore += 3
        }

        val hasMeaning = listOf("mean", "explain", "teach", "lesson", "understand", "interpret").any { q.contains(it) }
        val isReferring = listOf("this", "that", "it", "verse", "chapter", "passage").any { q.contains(it) }
        if (hasMeaning && isReferring) {
            if (hasBibleRef || bibleWordCount > 0) bibleScore += 4
            else generalScore += 2
        }
        if (q.contains("teach me") && !q.contains("bible") && !q.contains("verse")) {
            generalScore += 3
        }

        val generalTopicCount = GENERAL_TOPICS.count { q.contains(it) }
        if (generalTopicCount > 0) {
            generalScore += generalTopicCount * 4.0
            bibleScore -= generalTopicCount * 5.0
            breakdownScore -= generalTopicCount * 5.0
        }
        if (q.contains("how do i start") || q.contains("how to start") || q.contains("learn")) {
            if (bibleWordCount == 0 && !hasBibleRef) generalScore += 4
        }

        val factualCount = FACTUAL_INTENT.count { q.contains(it) }
        newsScore += factualCount * 3.0
        if (q.contains("malawi") && (q.contains("election") || q.contains("news") || q.contains("president") || q.contains("job"))) newsScore += 6

        val ref = currentRef.lowercase().trim()
        val isInBibleView = ref.isNotBlank() && ref != "home" && !ref.contains("chat")
        if (isInBibleView && q.length < 80 && (hasMeaning || q.contains("today") || q.contains("apply") || q.contains("for me"))) {
            bibleScore += 1.5
        }
        if (history.isNotEmpty()) {
            val lastTwo = history.takeLast(2).joinToString(" ") { it.text.lowercase() }
            val wasBible = lastTwo.contains("verse") || lastTwo.contains("bible") || lastTwo.contains("psalm") || lastTwo.contains("scripture")
            val wasNews = lastTwo.contains("election") || lastTwo.contains("news") || lastTwo.contains("president")
            if (wasBible && isReferring && hasMeaning) bibleScore += 1.5
            if (wasNews && (q.contains("it") || q.contains("that"))) newsScore += 1.5
        }

        if (breakdownScore >= 8 && breakdownScore > bibleScore && breakdownScore > newsScore && breakdownScore > generalScore) return ContextType.BREAKDOWN
        if (bibleScore >= 4 && bibleScore >= newsScore && bibleScore >= generalScore) return ContextType.BIBLE
        if (newsScore >= 6 && newsScore >= generalScore) return ContextType.NEWS
        return ContextType.GENERAL
    }

    // CHANGED: NEWS + GENERAL get Tavily, BIBLE + BREAKDOWN do NOT
    private fun needsWebSearch(question: String, currentRef: String = "", history: List<MessageEntity> = emptyList()): Boolean {
        val context = detectContext(question, currentRef, history)
        if (context == ContextType.NEWS) return true
        if (context == ContextType.GENERAL) {
            val q = question.lowercase().trim()
            if (q in listOf("hi", "hello", "hey", "amen", "ok", "okay", "thanks", "thank you")) return false
            if (q.length < 10) return false
            return true
        }
        return false
    }

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
                        sb.append("SOURCE:\n")
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
                // FIX: If we already emitted words, do NOT retry with next model.
                // Old code returned false here, so next model restarted from word 1
                // and ViewModel appended it -> "old 2 words" + "new full answer" flash.
                if (gotAnything) return true
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
            "The user explicitly asked for a detailed breakdown. You may organise it into 3 to 5 natural sections if that helps. For each section, give the verse, then meaning, then application. Finish fully."
        } else {
            "Let the length fit the question. Simple question = short answer. Complex question = fuller answer. Always finish fully, never stop mid-sentence."
        }

        val contextRule = when (context) {
            ContextType.BIBLE -> "This looks like a Bible question. Answer from Scripture, quote $translation where helpful, and end with a practical application if natural. Do not force news or a Sources section."
            ContextType.BREAKDOWN -> "This looks like a Bible breakdown request. Go verse by verse / section by section in a natural way."
            ContextType.NEWS -> "This looks like a factual / news question. Answer ONLY from the WEB SEARCH RESULTS below. If the results do not contain the answer, say you don't have verified info and don't guess names, dates or results. If results are provided, list Sources at the end as a plain list using ONLY the titles and URLs given, like:\n**Sources**\n[Title](URL)\n[Title](URL)\nDo NOT number them. Do NOT write Source 1, Source 2, 1. or 1). Never invent a URL, title or date."
            ContextType.GENERAL -> "This looks like a general question. Answer helpfully and directly. If WEB SEARCH RESULTS are provided below, use them for facts. Do not force Bible verses. Only list Sources if results were provided, as a plain list like [Title](URL) — do NOT number them, do NOT write Source 1, Source 2."
        }

        val sb = StringBuilder()
        sb.append("Today is $today. Use $translation when quoting Scripture. Current reading: $currentRef.\n")
        sb.append("$STYLE_RULE\n")
        sb.append("$contextRule\n$lengthRule\n\n")
        if (bibleContext.isNotBlank() && (context == ContextType.BIBLE || context == ContextType.BREAKDOWN)) sb.append("BIBLE CONTEXT:\n${bibleContext.take(1000)}\n\n")
        if (webContext.isNotBlank()) {
            sb.append("WEB SEARCH RESULTS (true facts as of $today):\n${webContext.take(3000)}\n\n")
        } else if (context == ContextType.NEWS) {
            sb.append("No web results were available. Do not guess. Say you don't have verified info.\n\n")
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

    private fun cleanMarkdown(text: String): String {
        var cleaned = text
            .replace("**Bold Title:**", "")
            .replace("**Bold Title**", "")
            .replace("Bold Title:", "")
            .replace("Bold Title", "")
            .replace("__", "")
            .replace("### ", "").replace("## ", "").replace("# ", "")
            .replace("`", "")
        cleaned = cleaned.replace(Regex("(?m)^\\s*Source\\s*\\d+\\s*[-:.]\\s*"), "")
        cleaned = cleaned.replace(Regex("(?m)^\\s*\\d+\\.\\s*(?=\\[)"), "")
        cleaned = cleaned.replace(Regex("(?m)^\\s*\\d+\\)\\s*(?=\\[)"), "")
        return cleaned.trim()
    }

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
                .put(JSONObject().put("role", "system").put("content", "You are the EW Bible assistant."))
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
                .put(JSONObject().put("role", "system").put("content", "You are the EW Bible assistant."))
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