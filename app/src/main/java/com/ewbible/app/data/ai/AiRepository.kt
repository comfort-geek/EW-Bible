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

    // --- DO NOT CHANGE - YOUR CURRENT MODELS ONLY ---
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

    // --- Provider health ---
    private data class FailState(var count: Int = 0, var blockedUntil: Long = 0L)
    private val failMap = mutableMapOf<String, FailState>()
    private fun isBlocked(model: String): Boolean {
        val s = failMap[model] ?: return false
        if (System.currentTimeMillis() > s.blockedUntil) { s.count = 0; s.blockedUntil = 0L; return false }
        return true
    }
    private fun markFail(model: String, is429: Boolean) {
        val s = failMap.getOrPut(model) { FailState() }
        s.count++
        if (is429 || s.count >= 3) s.blockedUntil = System.currentTimeMillis() + 5 * 60 * 1000L
    }
    private fun markOk(model: String) { failMap.remove(model) }

    // --- Model data - YOUR models only ---
    private data class ModelInfo(
        val provider: String,
        val model: String,
        val strength: Int,
        val speed: Int,
        val bestForBible: Boolean,
        val bestForBreakdown: Boolean,
        val bestForNews: Boolean,
        val supportsImage: Boolean,
        val maxTokens: Int,
        val contextWindow: Int,
        val isReasoning: Boolean
    )

    private fun modelInfo(provider: String, model: String): ModelInfo {
        return when (model) {
            "gemini-4-hydrogen" -> ModelInfo(provider, model, 10, 7, true, true, true, true, 8192, 1000000, true)
            "gemini-3.8-flash" -> ModelInfo(provider, model, 8, 9, true, true, true, true, 4096, 1000000, false)
            "gemini-3.5-flash-lite" -> ModelInfo(provider, model, 6, 10, false, false, true, true, 4096, 1000000, false)
            "openai/gpt-oss-120b" -> ModelInfo(provider, model, 10, 6, true, true, true, false, 4096, 131072, true)
            "qwen/qwen3.8-27b" -> ModelInfo(provider, model, 8, 8, true, true, false, false, 4096, 131072, false)
            "openai/gpt-oss-20b" -> ModelInfo(provider, model, 6, 10, false, false, true, false, 4096, 131072, false)
            "@cf/meta/llama-3.3-70b-instruct" -> ModelInfo(provider, model, 8, 7, true, true, false, false, 4096, 131072, false)
            "@cf/meta/llama-3-8b-instruct" -> ModelInfo(provider, model, 5, 10, false, false, true, false, 4096, 131072, false)
            else -> ModelInfo(provider, model, 5, 5, false, false, false, false, 4096, 32000, false)
        }
    }

    // --- AUTO-PICK: 3 providers ---
    private fun autoPickProviders(context: ContextType, isBreakdown: Boolean, hasImage: Boolean): List<String> {
        if (hasImage) return listOf("Gemini")
        return when {
            isBreakdown || context == ContextType.BIBLE -> listOf("Groq", "Gemini", "Cloudflare")
            context == ContextType.NEWS -> listOf("Groq", "Cloudflare", "Gemini")
            else -> listOf("Groq", "Cloudflare", "Gemini")
        }
    }

    private fun orderedModels(provider: String, list: List<String>, context: ContextType, isBreakdown: Boolean, questionLen: Int = 0): List<String> {
        val usable = list.filter { !isBlocked(it) }
        if (usable.isEmpty()) return emptyList()
        val infos = usable.map { modelInfo(provider, it) }
        return when {
            isBreakdown || context == ContextType.BIBLE -> infos.sortedWith(
                compareByDescending<ModelInfo> { it.strength }.thenByDescending { it.contextWindow }
            ).map { it.model }
            context == ContextType.NEWS -> infos.sortedWith(
                compareByDescending<ModelInfo> { it.speed }.thenByDescending { if (it.bestForNews) 1 else 0 }
            ).map { it.model }
            questionLen > 300 -> infos.sortedByDescending { it.contextWindow }.map { it.model }
            else -> infos.sortedByDescending { it.speed }.map { it.model }
        }
    }

    private companion object {
        const val STYLE_RULE = "You are a helpful assistant. Answer naturally like a knowledgeable friend. Do not mention what model you are. Do not say 'as an AI'. Match your length to the question: short question = short answer, deep question = deeper answer. Use paragraphs, headings or lists only if they help clarity — do not force them. Do not force Bible verses, prayer, or a Sources section unless the question actually needs it."

        val BIBLE_BOOKS = listOf(
            "genesis", "exodus", "leviticus", "numbers", "deuteronomy",
            "joshua", "judges", "ruth", "samuel", "kings", "chronicles",
            "ezra", "nehemiah", "esther", "job", "psalm", "proverb",
            "ecclesiastes", "song of solomon", "song of songs", "isaiah", "jeremiah",
            "lamentations", "ezekiel", "daniel", "hosea", "joel", "amos",
            "obadiah", "jonah", "micah", "nahum", "habakkuk", "zephaniah",
            "haggai", "zechariah", "malachi",
            "matthew", "mark", "luke", "john", "acts", "romans",
            "corinthians", "galatians", "ephesians", "philippians",
            "colossians", "thessalonians", "timothy", "titus", "philemon",
            "hebrews", "james", "peter", "jude", "revelation"
        )

        val BIBLE_REF_REGEX = Regex("\\b(${BIBLE_BOOKS.joinToString("|")})s?\\s+\\d+")
        val NUMBERED_BOOK_REGEX = Regex("\\b[12]\\s+(samuel|kings|chronicles|corinthians|thessalonians|timothy|peter|john|cor)\\s+\\d+")
        val SHORT_BOOK_REGEX = Regex("\\b(1\\s?cor|2\\s?cor|1\\s?john|2\\s?john|3\\s?john|1\\s?pet|2\\s?pet|1\\s?tim|2\\s?tim|phil|col|gal|eph|rom|heb|jas|jud)\\s+\\d+")
        val FULL_REF_REGEX = Regex("\\b(${BIBLE_BOOKS.joinToString("|")})s?\\s+\\d+\\s*:\\s*\\d+(\\s*-\\s*\\d+)?", RegexOption.IGNORE_CASE)
        val CHAPTER_ONLY_REGEX = Regex("\\b(${BIBLE_BOOKS.joinToString("|")})s?\\s+\\d+\\b", RegexOption.IGNORE_CASE)

        val BIBLE_WORDS = setOf(
            "verse", "verses", "chapter", "bible", "scripture", "psalm", "psalms", "proverb", "proverbs",
            "jesus", "christ", "god", "lord", "church", "pastor", "pastors", "prophet", "prophecy",
            "david", "moses", "apostle", "apostles", "gospel", "sin", "faith",
            "salvation", "holy spirit", "baptism", "baptise", "heaven", "hell",
            "abraham", "isaac", "jacob", "joseph", "samuel", "elijah", "elisha",
            "peter", "paul", "john", "mary", "solomon", "noah", "adam", "eve",
            "disciple", "kingdom", "prayer", "pray", "worship", "sermon", "parable",
            "covenant", "grace", "mercy", "repent", "forgiveness", "anointing",
            "tithe", "offering", "fasting", "temptation", "blessing", "miracle"
        )

        val FACTUAL_INTENT = listOf(
            "who is", "who won", "who was", "who became", "who appointed", "who is the",
            "what happened", "what is happening", "latest", "recent", "current",
            "today", "yesterday", "this week", "this year", "this month",
            "when did", "when will", "how many", "how much",
            "election", "elections", "results", "president", "minister", "government", "parliament",
            "job", "jobs", "hiring", "vacancy", "vacancies", "apply", "salary", "interview",
            "news", "pope", "war", "football", "soccer", "score", "scores",
            "price of", "exchange rate", "weather", "fuel price", "maize price",
            "tell me about", "biography", "history of",
            "what is", "what are", "who are", "facts about", "define", "definition",
            "did malawi", "malawi won", "malawi lost"
        )

        val GENERAL_TOPICS = listOf(
            "coding", "python", "java", "javascript", "kotlin", "programming", "developer",
            "computer", "laptop", "phone", "android", "app", "website", "software",
            "cooking", "recipe", "football", "soccer", "music", "movie", "movies",
            "school", "exam", "business", "money", "job", "car", "house", "farming",
            "health", "doctor", "medicine", "study", "university", "college"
        )

        val BREAKDOWN_PHRASES = listOf(
            "break down", "breakdown", "break it down", "verse by verse",
            "in portions", "in parts", "deep study", "deep dive", "go deep",
            "explain this passage", "explain this chapter", "explain this verse",
            "line by line", "step by step breakdown", "analyse this", "analyze this",
            "what does this mean", "meaning of this verse", "unpack this",
            "explain daniel", "explain psalm", "explain john", "explain matthew"
        )

        // PRAYER_PHRASES REMOVED - no prayer auto-boost
        val DEVOTIONAL_PHRASES = listOf("devotion", "daily verse", "verse of the day", "morning devotion", "devotional", "verse for today")
        val GREETINGS = setOf("hi", "hello", "hey", "amen", "ok", "okay", "thanks", "thank you", "good morning", "good evening", "good afternoon", "yo", "hie", "hello there")
    }

    private fun cleanQuery(q: String): String {
        var t = q.trim().replace(Regex("\\s+"), " ")
        t = t.replace(Regex("(?i)(daniel|psalm|john|matthew|genesis|romans|isaiah)(\\d)"), "$1 $2")
        return t
    }

    private fun extractRefs(question: String): List<String> {
        val found = mutableListOf<String>()
        FULL_REF_REGEX.findAll(question).forEach { found.add(it.value.trim()) }
        if (found.isEmpty()) CHAPTER_ONLY_REGEX.findAll(question).forEach { found.add(it.value.trim()) }
        return found.distinct().take(3)
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
                    val qq = json.optJSONArray("groq")
                    val c = json.optJSONArray("cloudflare")
                    if (g != null && g.length() > 0) remoteGemini = (0 until g.length()).map { g.getString(it) }
                    if (qq != null && qq.length() > 0) remoteGroq = (0 until qq.length()).map { qq.getString(it) }
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

    private fun detectContext(question: String, currentRef: String = "", history: List<MessageEntity> = emptyList()): ContextType {
        val raw = cleanQuery(question)
        val q = raw.lowercase().trim()
        if (q in GREETINGS) return ContextType.GENERAL
        if (q.length <= 2) return ContextType.GENERAL

        var bibleScore = 0.0
        var breakdownScore = 0.0
        var newsScore = 0.0
        var generalScore = 1.0

        val hasBibleRef = BIBLE_REF_REGEX.containsMatchIn(q) || NUMBERED_BOOK_REGEX.containsMatchIn(q) || SHORT_BOOK_REGEX.containsMatchIn(q) || FULL_REF_REGEX.containsMatchIn(q)
        if (hasBibleRef) bibleScore += 10
        if (FULL_REF_REGEX.containsMatchIn(q)) bibleScore += 2

        if (q.contains("in the bible") || q.contains("bible says") || q.contains("according to the bible") || q.contains("from scripture") || q.contains("bible verse")) bibleScore += 10
        if (q.contains("what does the bible say") || q.contains("bible teach")) bibleScore += 5
        // PRAYER boost REMOVED
        if (DEVOTIONAL_PHRASES.any { q.contains(it) }) bibleScore += 7

        val bibleWordCount = BIBLE_WORDS.count { q.contains(it) }
        bibleScore += minOf(bibleWordCount * 2.0, 8.0)

        val hasBreakdownPhrase = BREAKDOWN_PHRASES.any { q.contains(it) }
        val hasStepByStep = q.contains("step by step")
        if (hasBreakdownPhrase) {
            if (hasBibleRef || bibleWordCount > 0) breakdownScore += 9
            else if (q.contains("this") || q.contains("that") || q.contains("passage") || q.contains("chapter") || q.contains("verse")) breakdownScore += 4
            else breakdownScore += 0.5
        }
        if (hasStepByStep) {
            if (hasBibleRef || bibleWordCount > 0) breakdownScore += 8
            else generalScore += 3
        }
        if (hasBibleRef && q.contains("explain") && q.length > 15) breakdownScore += 3
        if (FULL_REF_REGEX.containsMatchIn(q) && q.contains("-") && q.contains("explain")) breakdownScore += 2

        val hasMeaning = listOf("mean", "means", "meaning", "explain", "teach", "lesson", "understand", "interpret", "interpretation").any { q.contains(it) }
        val isReferring = listOf("this", "that", "it", "verse", "chapter", "passage", "scripture").any { q.contains(it) }
        if (hasMeaning && isReferring) {
            if (hasBibleRef || bibleWordCount > 0) bibleScore += 4
            else generalScore += 2
        }
        if (q.contains("teach me") && !q.contains("bible") && !q.contains("verse")) generalScore += 3

        val generalTopicCount = GENERAL_TOPICS.count { q.contains(it) }
        if (generalTopicCount > 0) {
            if (!hasBibleRef && bibleWordCount == 0) {
                generalScore += generalTopicCount * 4.0
                bibleScore -= generalTopicCount * 5.0
                breakdownScore -= generalTopicCount * 5.0
            } else {
                generalScore += generalTopicCount * 1.0
            }
        }
        if (q.contains("how do i start") || q.contains("how to start") || q.contains("learn")) {
            if (bibleWordCount == 0 && !hasBibleRef) generalScore += 4
        }

        val factualCount = FACTUAL_INTENT.count { q.contains(it) }
        newsScore += factualCount * 3.0
        if (q.contains("malawi")) {
            if (q.contains("election") || q.contains("news") || q.contains("president") || q.contains("job") || q.contains("government") || q.contains("minister")) newsScore += 6
            if (q.contains("weather") || q.contains("fuel") || q.contains("price")) newsScore += 4
        }
        if ((q.contains("who is") || q.contains("who won") || q.contains("election")) && q.contains("malawi")) newsScore += 3

        val ref = currentRef.lowercase().trim()
        val isInBibleView = ref.isNotBlank() && ref != "home" && !ref.contains("chat")
        if (isInBibleView && q.length < 80 && (hasMeaning || q.contains("today") || q.contains("apply") || q.contains("for me") || q.contains("mean"))) {
            bibleScore += 1.5
        }
        if (history.isNotEmpty()) {
            val lastTwo = history.takeLast(2).joinToString(" ") { it.text.lowercase() }
            val wasBible = lastTwo.contains("verse") || lastTwo.contains("bible") || lastTwo.contains("psalm") || lastTwo.contains("scripture") || lastTwo.contains("daniel") || lastTwo.contains("jesus")
            val wasNews = lastTwo.contains("election") || lastTwo.contains("news") || lastTwo.contains("president") || lastTwo.contains("malawi")
            val wasGeneral = lastTwo.contains("python") || lastTwo.contains("coding") || lastTwo.contains("recipe")
            if (wasBible && isReferring && hasMeaning) bibleScore += 1.5
            if (wasNews && (q.contains("it") || q.contains("that") || q.contains("them"))) newsScore += 1.5
            if (wasGeneral && !hasBibleRef) generalScore += 1.0
            if (q.matches(Regex(".*(verse|chapter)\\s*\\d+.*")) && wasBible) bibleScore += 3
            if (q.matches(Regex("^(and|what about|what of|next).*verse.*")) && wasBible) bibleScore += 4
        }

        if (breakdownScore >= 8 && breakdownScore > bibleScore && breakdownScore > newsScore && breakdownScore > generalScore) return ContextType.BREAKDOWN
        if (bibleScore >= 4 && bibleScore >= newsScore && bibleScore >= generalScore) return ContextType.BIBLE
        if (newsScore >= 6 && newsScore >= generalScore) return ContextType.NEWS
        return ContextType.GENERAL
    }

    private fun needsWebSearch(question: String, currentRef: String = "", history: List<MessageEntity> = emptyList()): Boolean {
        val context = detectContext(question, currentRef, history)
        if (context == ContextType.NEWS) return true
        if (context == ContextType.GENERAL) {
            val q = question.lowercase().trim()
            if (q in GREETINGS) return false
            if (q.length < 10) return false
            if (q.contains("how to") && !q.contains("malawi") && !q.contains("news")) return false
            return true
        }
        return false
    }

    private suspend fun getWebContext(question: String, currentRef: String = "", history: List<MessageEntity> = emptyList()): String {
        if (!needsWebSearch(question, currentRef, history)) return ""
        val key = cleanQuery(question).trim().lowercase()
        if (key == cachedQuery && System.currentTimeMillis() - cachedAt < WEB_CACHE_MS && cachedWeb.isNotBlank()) return cachedWeb
        val lower = question.lowercase()
        val timeRange = when {
            lower.contains("today") || lower.contains("yesterday") || lower.contains("latest") || lower.contains("breaking") -> "day"
            lower.contains("news") || lower.contains("election") -> "week"
            lower.contains("history") || lower.contains("biography") -> "year"
            else -> "month"
        }
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
                val body = JSONObject().put("query", query).put("search_depth", "basic").put("max_results", 5).put("include_answer", true).put("time_range", timeRange).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) return ""
                val text = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(text)
                val sb = StringBuilder()
                val answer = json.optString("answer")
                if (answer.isNotBlank()) sb.append("TAVILY ANSWER: $answer\n\n")
                val results = json.optJSONArray("results")
                val seenUrls = mutableSetOf<String>()
                val seenTitles = mutableSetOf<String>()
                if (results != null) {
                    for (i in 0 until minOf(results.length(), 5)) {
                        val r = results.getJSONObject(i)
                        val url = r.optString("url")
                        val title = r.optString("title")
                        if (url.isBlank() || !seenUrls.add(url)) continue
                        if (title.isNotBlank() && !seenTitles.add(title.lowercase())) continue
                        sb.append("SOURCE:\nTitle: $title\nURL: $url\nContent: ${r.optString("content").take(600)}\nPublished: ${r.optString("published_date")}\n\n")
                        if (sb.length > 3000) break
                    }
                }
                val out = sb.toString().take(3500)
                if (out.isNotBlank()) {
                    cachedQuery = cleanQuery(query).trim().lowercase()
                    cachedWeb = out
                    cachedAt = System.currentTimeMillis()
                }
                out
            } finally { runCatching { conn?.disconnect() } }
        } catch (_: Exception) { "" }
    }

    fun askStreaming(question: String, currentRef: String, history: List<MessageEntity>, image: Bitmap?, bibleContext: String, translation: String = "NKJV"): Flow<String> = flow {
        val cleanedQ = cleanQuery(question)
        val context = detectContext(cleanedQ, currentRef, history)
        val webContext = if (image == null) getWebContext(cleanedQ, currentRef, history) else ""
        val isBreakdown = context == ContextType.BREAKDOWN
        val basePrompt = buildPrompt(cleanedQ, currentRef, history, bibleContext, translation, webContext, isBreakdown, context)
        val errors = mutableListOf<String>()

        suspend fun tryOne(provider: String, model: String, stream: Flow<String>): Boolean {
            _lastProvider.value = provider
            var gotAnything = false
            try {
                withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                    stream.collect { chunk -> if (chunk.isNotEmpty()) { gotAnything = true; emit(chunk) } }
                    true
                } ?: false
                if (gotAnything) { markOk(model); return true }
                return false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (gotAnything) return true
                val is429 = e.message?.contains("429") == true
                markFail(model, is429)
                errors.add("$provider $model: ${e.message}")
                if (provider == "Gemini") CrashReporter.geminiLog("Gemini $model failed: ${e.message}")
                else CrashReporter.groqLog("$provider $model failed: ${e.message}")
                return false
            }
        }

        val providers = autoPickProviders(context, isBreakdown, image != null)
        for (provider in providers) {
            val list = when (provider) { "Groq" -> remoteGroq; "Cloudflare" -> remoteCloudflare; "Gemini" -> remoteGemini; else -> emptyList() }
            for (model in orderedModels(provider, list, context, isBreakdown, cleanedQ.length)) {
                val stream = when (provider) {
                    "Groq" -> tryGroqStreaming(model, basePrompt, isBreakdown)
                    "Cloudflare" -> tryCloudflareStreaming(model, basePrompt, isBreakdown)
                    else -> tryGeminiStreaming(model, basePrompt, image)
                }
                if (tryOne(provider, model, stream)) return@flow
            }
        }
        if (image != null && errors.isEmpty()) emit("Could not read the image. Please try again.")
        else if (errors.isNotEmpty() && errors.all { it.contains("429") }) emit("Free daily limit reached for today. Please try again tomorrow.")
        else emit("I am busy right now. Please try again in a minute.")
    }.flowOn(Dispatchers.IO)

    suspend fun askQuestion(prompt: String, image: Bitmap? = null): Result<String> {
        val cleaned = cleanQuery(prompt)
        val context = detectContext(cleaned)
        val webContext = if (image == null) getWebContext(cleaned) else ""
        val isBreakdown = context == ContextType.BREAKDOWN
        val fullPrompt = buildPrompt(cleaned, "", emptyList(), "", "NKJV", webContext, isBreakdown, context)
        val sb = StringBuilder()
        return try {
            var lastErr: Exception? = null
            val providers = autoPickProviders(context, isBreakdown, image != null)
            for (provider in providers) {
                val list = when (provider) { "Groq" -> remoteGroq; "Cloudflare" -> remoteCloudflare; "Gemini" -> remoteGemini; else -> emptyList() }
                for (model in orderedModels(provider, list, context, isBreakdown, cleaned.length)) {
                    _lastProvider.value = provider
                    try {
                        sb.clear()
                        val completed = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                            val stream = when (provider) {
                                "Groq" -> tryGroqStreaming(model, fullPrompt, isBreakdown)
                                "Cloudflare" -> tryCloudflareStreaming(model, fullPrompt, isBreakdown)
                                else -> tryGeminiStreaming(model, fullPrompt, image)
                            }
                            stream.collect { sb.append(it) }
                            true
                        } ?: false
                        if (sb.isNotBlank() && completed) return Result.success(cleanMarkdown(sb.toString()).trim())
                        if (sb.isNotBlank() && !completed) continue
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { lastErr = e; sb.clear(); markFail(model, e.message?.contains("429") == true) }
                }
            }
            if (image != null) Result.failure(Exception("Could not read the image. Please try again."))
            else Result.failure(lastErr ?: Exception("I am busy right now. Please try again in a minute."))
        } catch (e: CancellationException) { throw e }
    }

    private fun buildPrompt(question: String, currentRef: String, history: List<MessageEntity>, bibleContext: String, translation: String, webContext: String = "", isBreakdown: Boolean = false, context: ContextType = ContextType.GENERAL): String {
        val today = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date())
        val refs = extractRefs(question)
        val refsLine = if (refs.isNotEmpty()) "User is asking about these exact references: ${refs.joinToString(", ")}. Focus on them. Do not quote a different verse as if it is this one." else ""
        val lengthRule = if (isBreakdown) "The user explicitly asked for a detailed breakdown. Organise it into 3 to 5 natural sections if that helps. For each section, give the verse(s), then meaning, then application. Use **bold** only for section titles and key points. Finish fully, never stop mid-sentence."
        else "Let the length fit the question. Simple question = short answer. Complex question = fuller answer. Always finish fully, never stop mid-sentence. Use **bold** sparingly for key points only."
        val contextRule = when (context) {
            ContextType.BIBLE -> "This looks like a Bible question. Answer from Scripture, quote $translation where helpful, and end with a practical application if natural. Do not force news or a Sources section. Never wrap a verse quote in single * - use plain quotes \"...\". Never invent a verse number or wording."
            ContextType.BREAKDOWN -> "This looks like a Bible breakdown request. Go verse by verse / section by section in a natural way. Quote each verse in plain \"...\" quotes, no single * around it. Do not skip verses in the range."
            ContextType.NEWS -> "This looks like a factual / news question. Answer ONLY from the WEB SEARCH RESULTS below. If the results do not contain the answer, say you don't have verified info and don't guess names, dates or results. If results are provided, list Sources at the end as a plain list using ONLY the titles and URLs given, like:\n**Sources**\n[Title](URL)\n[Title](URL)\nDo NOT number them. Do NOT write Source 1, Source 2, 1. or 1). Never invent a URL, title or date."
            ContextType.GENERAL -> "This looks like a general question. Answer helpfully and directly. If WEB SEARCH RESULTS are provided below, use them for facts. Do not force Bible verses. Only list Sources if results were provided, as a plain list like [Title](URL) — do NOT number them, do NOT write Source 1, Source 2. Never wrap quotes in single *."
        }
        val sb = StringBuilder()
        sb.append("Today is $today. Use $translation when quoting Scripture. Current reading: $currentRef.\n")
        if (refsLine.isNotBlank()) sb.append("$refsLine\n")
        sb.append("$STYLE_RULE\n")
        sb.append("$contextRule\n$lengthRule\n\n")
        if (bibleContext.isNotBlank() && (context == ContextType.BIBLE || context == ContextType.BREAKDOWN)) sb.append("BIBLE CONTEXT:\n${bibleContext.take(1500)}\n\n")
        if (webContext.isNotBlank()) sb.append("WEB SEARCH RESULTS (true facts as of $today):\n${webContext.take(3500)}\n\n")
        else if (context == ContextType.NEWS) sb.append("No web results were available. Do not guess. Say you don't have verified info.\n\n")
        if (history.isNotEmpty()) {
            sb.append("CONVERSATION HISTORY (last 4):\n")
            history.takeLast(4).forEach { sb.append(if (it.isUser) "User: " else "Assistant: ").append(it.text.take(500)).append("\n") }
            sb.append("\n")
        }
        sb.append("User question: $question")
        return sb.toString()
    }

    private fun cleanMarkdown(text: String): String {
        var t = text.replace("**Bold Title:**", "").replace("**Bold Title**", "").replace("Bold Title:", "").replace("Bold Title", "")
        val bolds = mutableListOf<String>()
        t = Regex("\\*\\*(.+?)\\*\\*").replace(t) { m -> bolds.add(m.groupValues[1]); "\u0000BOLD${bolds.size - 1}\u0000" }
        t = t.replace("*", "")
        t = t.replace(Regex("_(.+?)_"), "$1")
        t = t.replace("`", "")
        t = t.replace("__", "")
        bolds.forEachIndexed { i, b -> t = t.replace("\u0000BOLD$i\u0000", "**$b**") }
        t = t.replace(Regex("(?m)^#{1,6}\\s*"), "")
        t = t.replace(Regex("(?m)^---\\s*$"), "")
        t = t.replace(Regex("(?m)^\\s*Source\\s*\\d+\\s*[-:.]\\s*"), "")
        t = t.replace(Regex("(?m)^\\s*\\d+\\.\\s*(?=\\[)"), "")
        t = t.replace(Regex("(?m)^\\s*\\d+\\)\\s*(?=\\[)"), "")
        return t.trim()
    }

    private fun cleanStreamingChunk(text: String): String {
        var t = text.replace("**Bold Title:**", "").replace("**Bold Title**", "").replace("Bold Title:", "").replace("Bold Title", "")
        t = t.replace("### ", "").replace("## ", "").replace("# ", "")
        t = t.replace("`", "")
        return t
    }

    private fun bitmapToBase64(image: Bitmap?): String? {
        if (image == null) return null
        return try {
            val scaled = if (image.width > 1024) {
                val ratio = 1024f / image.width
                Bitmap.createScaledBitmap(image, 1024, (image.height * ratio).toInt(), true)
            } else image
            val bytes = ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 70, out); out.toByteArray() }
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    private fun tryGeminiStreaming(model: String, prompt: String, image: Bitmap?): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(PROXY_BASE + "gemini?model=$model")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; setRequestProperty("Content-Type", "application/json"); setRequestProperty(PROXY_HEADER, PROXY_VALUE); connectTimeout = 3000; readTimeout = 120000; doOutput = true
            }
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            val base64 = bitmapToBase64(image)
            if (base64 != null) parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", base64)))
            val info = modelInfo("Gemini", model)
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts))).put("generationConfig", JSONObject().put("maxOutputTokens", info.maxTokens).put("temperature", 0.7)).toString()
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
        } finally { runCatching { conn?.disconnect() } }
    }

    private fun tryGroqStreaming(model: String, prompt: String, isBreakdown: Boolean): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(PROXY_BASE + "groq")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; setRequestProperty("Content-Type", "application/json"); setRequestProperty(PROXY_HEADER, PROXY_VALUE); connectTimeout = 3000; readTimeout = 120000; doOutput = true
            }
            val messages = JSONArray().put(JSONObject().put("role", "system").put("content", "You are the EW Bible assistant.")).put(JSONObject().put("role", "user").put("content", prompt))
            val info = modelInfo("Groq", model)
            val body = JSONObject().put("model", model).put("messages", messages).put("stream", true).put("max_tokens", info.maxTokens).put("temperature", 0.7).toString()
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
        } finally { runCatching { conn?.disconnect() } }
    }

    private fun tryCloudflareStreaming(model: String, prompt: String, isBreakdown: Boolean): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(PROXY_BASE + "cloudflare")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; setRequestProperty("Content-Type", "application/json"); setRequestProperty(PROXY_HEADER, PROXY_VALUE); connectTimeout = 3000; readTimeout = 120000; doOutput = true
            }
            val messages = JSONArray().put(JSONObject().put("role", "system").put("content", "You are the EW Bible assistant.")).put(JSONObject().put("role", "user").put("content", prompt))
            val info = modelInfo("Cloudflare", model)
            val body = JSONObject().put("model", model).put("messages", messages).put("stream", true).put("max_tokens", info.maxTokens).toString()
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
                        if (choices != null && choices.length() > 0) content = choices.getJSONObject(0).optJSONObject("delta")?.optString("content") ?: ""
                    }
                    if (content.isNotEmpty()) emit(cleanStreamingChunk(content))
                } catch (_: Exception) { }
            }
        } finally { runCatching { conn?.disconnect() } }
    }
}