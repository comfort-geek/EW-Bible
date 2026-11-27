package com.ewbible.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ewbible.app.CrashReporter
import com.ewbible.app.data.database.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    private val PROXY_BASE = "https://ewbible.comfortcha34.workers.dev/"
    private val PROXY_HEADER = "X-EW-App"
    private val PROXY_VALUE = "ew-bible-v1"
    private val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val REMOTE_MODELS_URL =
        "https://raw.githubusercontent.com/comfort-geek/EW-Bible/main/models.json"

    private val FALLBACK_GEMINI = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-4-hydrogen")
    private val FALLBACK_GROQ = listOf("openai/gpt-oss-20b", "qwen/qwen3.8-27b", "openai/gpt-oss-120b")
    private val FALLBACK_CLOUDFLARE = listOf("@cf/meta/llama-3-8b-instruct", "@cf/meta/llama-3.3-70b-instruct")

    private var remoteGemini = FALLBACK_GEMINI
    private var remoteGroq = FALLBACK_GROQ
    private var remoteCloudflare = FALLBACK_CLOUDFLARE
    private var remoteVersion = 3

    private var lastWorkingGemini: String? = null
    private var lastWorkingGroq: String? = null
    private var lastWorkingCloudflare: String? = null
    private var lastWorkingLoaded = false
    private var appContext: Context? = null

    private fun orderedModels(models: List<String>, lastWorking: String?): List<String> {
        if (lastWorking == null || !models.contains(lastWorking)) return models
        return listOf(lastWorking) + models.filter { it != lastWorking }
    }

    private fun rememberWorkingModel(provider: String, model: String) {
        var changed = false
        when (provider) {
            "Gemini" -> if (lastWorkingGemini != model) { lastWorkingGemini = model; changed = true }
            "Groq" -> if (lastWorkingGroq != model) { lastWorkingGroq = model; changed = true }
            "Cloudflare" -> if (lastWorkingCloudflare != model) { lastWorkingCloudflare = model; changed = true }
        }
        if (changed) appContext?.let { saveModelsMemory(it) }
    }

    private fun loadModelsMemory(prefs: android.content.SharedPreferences) {
        if (lastWorkingLoaded) return
        lastWorkingLoaded = true
        try {
            lastWorkingGemini = prefs.getString("last_working_gemini", null)
            lastWorkingGroq = prefs.getString("last_working_groq", null)
            lastWorkingCloudflare = prefs.getString("last_working_cloudflare", null)
        } catch (_: Exception) { }
    }

    private fun saveModelsMemory(context: Context) {
        try {
            val prefs = context.getSharedPreferences("ew_models", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_working_gemini", lastWorkingGemini)
                .putString("last_working_groq", lastWorkingGroq)
                .putString("last_working_cloudflare", lastWorkingCloudflare)
                .apply()
        } catch (_: Exception) { }
    }

    private val STREAM_TIMEOUT_MS = 90000L
    private val MAX_TOKENS = 4096
    private val SEARCH_TIMEOUT_MS = 8000L

    private var cachedQuery = ""
    private var cachedWeb = ""
    private var cachedAt = 0L
    private val WEB_CACHE_MS = 10 * 60 * 1000L

    private companion object {
        const val STYLE_RULE = "You are Ask Anything - a warm, smart friend. Talk naturally like a person. Write in well structured points, not one long block paragraph. Each point has a bold title, then a medium paragraph under it explaining it. Leave a blank line between points. Do not mention what model you are. Do not say 'as an AI'. Match your length to the question: short question = short answer, deep question = deeper answer. Use paragraphs, headings or bullet lists only if they help clarity - do not force them. Do not force Bible verses, prayer, or a Sources section unless the question actually needs it. NEVER use tables. NEVER use the | character. NEVER use --- for a table. Use a bullet list instead of a table. If the user is just chatting, just talk back. If the user is talking about the Bible, a verse, or prayer, stay warm and simple, explain it in the flow of the story, then ask a question back. If the user asks about something that could have changed recently, use the WEB SEARCH RESULTS and do not guess."
        const val SYSTEM_MSG = STYLE_RULE

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

        val FACTUAL_INTENT = listOf(
            "who won", "who became", "who appointed",
            "what happened", "what is happening", "latest", "recent", "current",
            "today", "yesterday", "this week", "this year",
            "when did", "when will", "how many", "how much",
            "election", "results", "president", "minister", "government",
            "job", "jobs", "hiring", "vacancy", "vacancies", "apply", "salary",
            "news", "pope", "war", "football", "soccer", "score",
            "price of", "exchange rate", "weather"
        )

        val BREAKDOWN_PHRASES = listOf(
            "break down", "breakdown", "break it down", "verse by verse",
            "in portions", "in parts", "deep study", "deep dive", "go deep",
            "explain this passage", "explain this chapter"
        )

        val WORLD_REGIONS = listOf(
            "america", "africa", "asia", "europe", "oceania", "australia",
            "north america", "south america",
            "usa", "united states", "uk", "england", "france", "germany",
            "china", "india", "japan", "korea", "russia", "ukraine",
            "israel", "palestine", "iran", "iraq", "syria", "turkey",
            "egypt", "nigeria", "kenya", "south africa", "ghana",
            "malawi", "zambia", "zimbabwe", "tanzania", "mozambique",
            "canada", "mexico", "brazil", "argentina", "italy", "spain",
            "netherlands", "poland", "sweden", "norway", "denmark",
            "pakistan", "bangladesh", "indonesia", "thailand", "vietnam",
            "philippines", "saudi", "uae", "dubai", "qatar", "ethiopia",
            "congo", "sudan", "somalia", "uganda", "rwanda", "botswana", "namibia", "angola", "madagascar"
        )
    }

    suspend fun refreshModels(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        val prefs = context.getSharedPreferences("ew_models", Context.MODE_PRIVATE)
        loadModelsMemory(prefs)
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
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
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

    private enum class ContextType { BIBLE, BREAKDOWN, NEWS, GENERAL, MIXED }
    private enum class ActionType { NONE, RECOMMEND, COMPARE, SUMMARIZE, TRANSLATE, LIST, EXPLAIN, TEACH }
    private data class Decision(
        val type: ContextType,
        val intents: List<ContextType>,
        val action: ActionType = ActionType.NONE,
        val confidence: Double = 1.0
    )

    private fun detectAction(q: String): ActionType {
        val s = q.lowercase().trim()
        if (s.contains("recommend") || s.contains("suggest") || s.contains("what should i do") ||
            s.contains("what do you recommend") || s.contains("advice") ||
            s.contains("how do i fix") || s.contains("how to fix") ||
            s.contains("how do i solve") || s.contains("how to solve") ||
            s.contains("how to get") || s.contains("how can i get") ||
            s.contains("give me ideas") || s.contains("ideas to") ||
            s.contains("help me decide") || s.contains("which one should") ||
            s.contains("best way to") || s.contains("best option")
        ) return ActionType.RECOMMEND
        if (s.contains("compare") || s.contains("difference between") || s.contains("vs ") || s.contains("versus")) return ActionType.COMPARE
        if (s.contains("summar") || s.contains("shorten") || s.contains("make it short") || s.contains("in short") || s.contains("tl;dr")) return ActionType.SUMMARIZE
        if (s.startsWith("translate") || s.contains("translate this") || s.contains("translate to") || s.contains("in chichewa") || s.contains("in english")) return ActionType.TRANSLATE
        if (s.contains("list") || s.contains("give me 5") || s.contains("give me 10") || s.contains("top 5") || s.contains("top 10")) return ActionType.LIST
        if (s.contains("break down") || s.contains("breakdown") || s.contains("verse by verse")) return ActionType.EXPLAIN
        if (s.contains("teach me") || s.contains("explain")) return ActionType.TEACH
        return ActionType.NONE
    }

    private fun isWorldNewsQuery(q: String): Boolean {
        val s = q.lowercase()
        return WORLD_REGIONS.any { s.contains(it) } || s.contains("news") || s.contains("headlines") || s.contains("happening") || s.contains("latest") || s.contains("update")
    }

    private fun shouldUseCurrentRef(question: String, currentRef: String): Boolean {
        if (currentRef.isBlank()) return false
        val q = question.lowercase().trim()
        return q.contains("this verse") || q.contains("this chapter") ||
               q.contains("this passage") || q.contains("this psalm") ||
               q.contains("this scripture") || q.contains("this book") ||
               q.contains("explain this") || q.contains("summarize this") ||
               q.contains("what does this mean") || q.contains("what does it mean") ||
               q.contains("explain it") || q.contains("meaning of this") ||
               q == "explain" || q == "explain?" || q == "summarize" ||
               q == "what does this mean?" || q == "what is this about?"
    }

    private fun decide(
        question: String,
        currentRef: String = "",
        history: List<MessageEntity> = emptyList()
    ): Decision {
        val q = question.lowercase().trim()
        if (q in listOf("hi", "hello", "hey", "amen", "ok", "okay", "thanks", "thank you"))
            return Decision(ContextType.GENERAL, listOf(ContextType.GENERAL), ActionType.NONE, 1.0)
        val action = detectAction(q)
        val d = decideWithoutAction(question, currentRef, history)
        return d.copy(action = action)
    }

    private fun decideWithoutAction(
        question: String,
        currentRef: String = "",
        history: List<MessageEntity> = emptyList()
    ): Decision {
        val q = question.lowercase().trim()
        val action = detectAction(q)
        val hasBibleRef = BIBLE_REF_REGEX.containsMatchIn(q) || NUMBERED_BOOK_REGEX.containsMatchIn(q)
        val hasBreakdown = BREAKDOWN_PHRASES.any { q.contains(it) }

        if (hasBibleRef && hasBreakdown) return Decision(ContextType.BREAKDOWN, listOf(ContextType.BREAKDOWN), action)
        if (hasBibleRef) return Decision(ContextType.BIBLE, listOf(ContextType.BIBLE), action)
        if (isWorldNewsQuery(q)) return Decision(ContextType.NEWS, listOf(ContextType.NEWS), action)
        if (q.contains("bible") || q.contains("scripture") || q.contains("verse") || q.contains("psalm") || q.contains("in the bible") || q.contains("bible says"))
            return Decision(ContextType.BIBLE, listOf(ContextType.BIBLE), action)
        if (!shouldUseCurrentRef(question, currentRef) && FACTUAL_INTENT.any { q.contains(it) })
            return Decision(ContextType.NEWS, listOf(ContextType.NEWS), action)
        if (shouldUseCurrentRef(question, currentRef)) {
            return if (hasBreakdown) Decision(ContextType.BREAKDOWN, listOf(ContextType.BREAKDOWN), action)
            else Decision(ContextType.BIBLE, listOf(ContextType.BIBLE), action)
        }
        val parts = q.split(Regex("\\s+and\\s+|\\s+also\\s+|\\s+plus\\s+"))
            .map { it.trim() }.filter { it.length > 3 }
        if (parts.size > 1) {
            val types = parts.map { decideWithoutAction(it, "", emptyList()).type }.distinct()
            if (types.size > 1) return Decision(ContextType.MIXED, types, action)
        }
        return Decision(ContextType.GENERAL, listOf(ContextType.GENERAL), action)
    }

    private fun needsWebSearch(question: String, currentRef: String = "", history: List<MessageEntity> = emptyList()): Boolean {
        val q = question.lowercase().trim()
        if (q in listOf("hi", "hello", "hey", "amen", "ok", "okay", "thanks", "thank you")) return false
        if (isWorldNewsQuery(q)) return true
        val decision = decide(question, currentRef, history)
        if (decision.type == ContextType.BIBLE || decision.type == ContextType.BREAKDOWN) return false
        // UNLOCKED: for NEWS, MIXED and GENERAL, always try web
        return true
    }

    private fun chooseSearchDepth(question: String): String { return "auto" }

    private suspend fun getWebContext(question: String, currentRef: String = "", history: List<MessageEntity> = emptyList()): String {
        if (!needsWebSearch(question, currentRef, history)) return ""
        val key = question.trim().lowercase()
        if (key == cachedQuery && System.currentTimeMillis() - cachedAt < WEB_CACHE_MS && cachedWeb.isNotBlank()) {
            Log.d("EW_SEARCH", "CACHE HIT query=$question")
            return cachedWeb
        }
        val lastUser = history.lastOrNull { it.isUser }?.text ?: ""
        val searchContext = if (lastUser.isNotBlank() && lastUser.lowercase() != key) lastUser else ""
        val result = withTimeoutOrNull(SEARCH_TIMEOUT_MS) { searchWorker(question, searchContext, lastUser) } ?: ""
        if (result.isBlank()) Log.d("EW_SEARCH", "EMPTY result query=$question")
        return result
    }

    private suspend fun searchWorker(query: String, context: String = "", lastQuestion: String = ""): String {
        var conn: HttpURLConnection? = null
        try {
            val searchUrl = URL(PROXY_BASE + "search")
            conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                // BROWSER HEADERS - proven in Pydroid, STATUS 200
                // DO NOT add Accept-Encoding - it returns gzip/br garbage
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Origin", "https://ewbible.comfortcha34.workers.dev")
                setRequestProperty("Referer", "https://ewbible.comfortcha34.workers.dev/")
                setRequestProperty("Sec-Fetch-Dest", "empty")
                setRequestProperty("Sec-Fetch-Mode", "cors")
                setRequestProperty("Sec-Fetch-Site", "same-origin")
                setRequestProperty("Sec-Ch-Ua", "\"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\", \"Not-A.Brand\";v=\"99\"")
                setRequestProperty("Sec-Ch-Ua-Mobile", "?1")
                setRequestProperty("Sec-Ch-Ua-Platform", "\"Android\"")
                setRequestProperty(PROXY_HEADER, PROXY_VALUE)
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 2500
                readTimeout = SEARCH_TIMEOUT_MS.toInt()
                doOutput = true
                instanceFollowRedirects = true
                useCaches = false
            }
            val body = JSONObject()
                .put("query", query)
                .put("context", context)
                .put("last_question", lastQuestion)
                .put("search_depth", chooseSearchDepth(query))
                .put("let_rag_choose_depth", true)
                .put("depth", "auto")
                .put("default_depth", "basic")
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                Log.d("EW_SEARCH", "Worker FAILED code=$code query=$query err=${err.take(300)}")
                return ""
            }
            val text = conn.inputStream.bufferedReader().readText()
            if (text.isBlank()) return ""
            val json = JSONObject(text)
            val depthPicked = json.optString("search_depth", "").ifBlank { json.optString("depth", "basic") }.ifBlank { "basic" }.lowercase()
            val providerUsed = json.optString("provider", "").ifBlank { json.optString("search_provider", "unknown") }.ifBlank { "unknown" }.lowercase()
            val safeDepth = if (depthPicked == "advanced") "advanced" else "basic"
            val headerLine = "[SEARCH: depth=$safeDepth | provider=$providerUsed]"
            Log.d("EW_SEARCH", "$headerLine query=$query len=${text.length}")
            // Worker returns rag_context like in Pydroid test: "Use the following... [1] Title: ... URL: ..."
            val rag = json.optString("rag_context")
            if (rag.isNotBlank()) {
                val out = "$headerLine\n${rag.take(3500)}"
                cachedQuery = query.trim().lowercase()
                cachedWeb = out
                cachedAt = System.currentTimeMillis()
                return out
            }
            // Fallback: only results[] without rag_context
            val sb = StringBuilder()
            sb.append("$headerLine\n")
            val answer = json.optString("answer")
            if (answer.isNotBlank()) sb.append("TAVILY ANSWER: $answer\n\n")
            val results = json.optJSONArray("results")
            if (results != null) {
                for (i in 0 until minOf(results.length(), 5)) {
                    val r = results.getJSONObject(i)
                    val title = r.optString("title")
                    val url = r.optString("url")
                    val content = r.optString("content")
                    val published = r.optString("published_date")
                    if (title.isBlank() && url.isBlank()) continue
                    sb.append("SOURCE:\n")
                    sb.append("Title: $title\n")
                    sb.append("URL: $url\n")
                    sb.append("Content: ${content.take(500)}\n")
                    sb.append("Published: $published\n\n")
                }
            }
            val out = sb.toString().take(3500)
            if (out.isNotBlank() && out != "$headerLine\n") {
                cachedQuery = query.trim().lowercase()
                cachedWeb = out
                cachedAt = System.currentTimeMillis()
            }
            return out
        } catch (e: Exception) {
            Log.d("EW_SEARCH", "searchWorker exception query=$query err=${e.message}")
            return ""
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    fun askStreaming(
        question: String,
        currentRef: String,
        history: List<MessageEntity>,
        image: Bitmap?,
        bibleContext: String,
        translation: String = "NKJV"
    ): Flow<String> = flow {
        val decision = decide(question, currentRef, history)
        val context = decision.type
        val webContext = if (image == null) getWebContext(question, currentRef, history) else ""
        val isBreakdown = context == ContextType.BREAKDOWN
        val basePrompt = buildPrompt(question, currentRef, history, bibleContext, translation, webContext, isBreakdown, context, decision.action)
        val errors = mutableListOf<String>()
        val groqModels = orderedModels(remoteGroq, lastWorkingGroq)
        val cfModels = orderedModels(remoteCloudflare, lastWorkingCloudflare)
        val geminiModels = orderedModels(remoteGemini, lastWorkingGemini)
        suspend fun tryOne(provider: String, model: String, stream: Flow<String>): Boolean {
            var gotAnything = false
            try {
                withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                    stream.collect { chunk -> if (chunk.isNotEmpty()) { gotAnything = true; emit(chunk) } }
                }
                if (gotAnything) { rememberWorkingModel(provider, model); return true }
                return false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (gotAnything) { rememberWorkingModel(provider, model); return true }
                errors.add("$provider $model: ${e.message}")
                if (provider == "Gemini") CrashReporter.geminiLog("Gemini $model failed: ${e.message}")
                else CrashReporter.groqLog("$provider $model failed: ${e.message}")
                return false
            }
        }
        if (image != null) {
            for (model in geminiModels) {
                if (tryOne("Gemini", model, tryGeminiStreaming(model, basePrompt, image, question, isBreakdown))) return@flow
            }
            emit("Could not read the image. Please try again.")
            return@flow
        }
        for (model in groqModels) {
            if (tryOne("Groq", model, tryGroqStreaming(model, basePrompt, isBreakdown, question))) return@flow
        }
        for (model in cfModels) {
            if (tryOne("Cloudflare", model, tryCloudflareStreaming(model, basePrompt, isBreakdown, question))) return@flow
        }
        for (model in geminiModels) {
            if (tryOne("Gemini", model, tryGeminiStreaming(model, basePrompt, null, question, isBreakdown))) return@flow
        }
        if (errors.isNotEmpty() && errors.all { it.contains("429") }) {
            emit("Free daily limit reached for today. Please try again tomorrow.")
        } else {
            emit("I am busy right now. Please try again in a minute.")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun askQuestion(prompt: String, image: Bitmap? = null): Result<String> {
        val decision = decide(prompt)
        val context = decision.type
        val webContext = if (image == null) getWebContext(prompt) else ""
        val isBreakdown = context == ContextType.BREAKDOWN
        val fullPrompt = buildPrompt(prompt, "", emptyList(), "", "NKJV", webContext, isBreakdown, context, decision.action)
        val sb = StringBuilder()
        val groqModels = orderedModels(remoteGroq, lastWorkingGroq)
        val cfModels = orderedModels(remoteCloudflare, lastWorkingCloudflare)
        val geminiModels = orderedModels(remoteGemini, lastWorkingGemini)
        suspend fun tryOneSync(provider: String, model: String, stream: Flow<String>): Boolean {
            sb.clear()
            try {
                withTimeoutOrNull(STREAM_TIMEOUT_MS) { stream.collect { sb.append(it) } }
                if (sb.isNotBlank()) { rememberWorkingModel(provider, model); return true }
                return false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (sb.isNotBlank()) { rememberWorkingModel(provider, model); return true }
                sb.clear()
                return false
            }
        }
        return try {
            var lastErr: Exception? = null
            if (image != null) {
                for (model in geminiModels) {
                    try {
                        if (tryOneSync("Gemini", model, tryGeminiStreaming(model, fullPrompt, image, prompt, isBreakdown))) {
                            return Result.success(cleanMarkdown(sb.toString()).trim())
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { lastErr = e }
                }
                return Result.failure(Exception("Could not read the image. Please try again."))
            }
            for (model in groqModels) {
                try {
                    if (tryOneSync("Groq", model, tryGroqStreaming(model, fullPrompt, isBreakdown, prompt))) {
                        return Result.success(cleanMarkdown(sb.toString()).trim())
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { lastErr = e }
            }
            for (model in cfModels) {
                try {
                    if (tryOneSync("Cloudflare", model, tryCloudflareStreaming(model, fullPrompt, isBreakdown, prompt))) {
                        return Result.success(cleanMarkdown(sb.toString()).trim())
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { lastErr = e }
            }
            for (model in geminiModels) {
                try {
                    if (tryOneSync("Gemini", model, tryGeminiStreaming(model, fullPrompt, null, prompt, isBreakdown))) {
                        return Result.success(cleanMarkdown(sb.toString()).trim())
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { lastErr = e }
            }
            Result.failure(lastErr ?: Exception("I am busy right now. Please try again in a minute."))
        } catch (e: CancellationException) { throw e }
    }

    fun persistModelsMemory(context: Context) { saveModelsMemory(context) }

    private fun maxTokensFor(question: String, isBreakdown: Boolean): Int {
        if (isBreakdown) return MAX_TOKENS
        return when {
            question.length < 20 -> 2048
            question.length < 60 -> 2048
            question.length < 150 -> 3072
            else -> MAX_TOKENS
        }
    }

    private fun buildPrompt(
        question: String,
        currentRef: String,
        history: List<MessageEntity>,
        bibleContext: String,
        translation: String,
        webContext: String = "",
        isBreakdown: Boolean = false,
        context: ContextType = ContextType.GENERAL,
        action: ActionType = ActionType.NONE
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
            ContextType.NEWS -> "This looks like a factual / news question. Use the WEB SEARCH RESULTS below if they are provided. If no results are provided, still answer helpfully from your general knowledge and say this may have changed, check for latest update. Do not refuse. If results are provided, end with EXACTLY this format, max 3, short titles only:\n**Sources**\n- [Short Title](URL)\n- [Short Title](URL)\nDo NOT number them. Do NOT write Source 1, Source 2, 1. or 1). Never invent a URL, title or date. Use ONLY titles and URLs from the results."
            ContextType.GENERAL -> "This looks like a general question. Answer helpfully and directly. If WEB SEARCH RESULTS are provided below, use them for facts. Do not force Bible verses. If results were provided, end with EXACTLY this format, max 3, short titles only:\n**Sources**\n- [Short Title](URL)\n- [Short Title](URL)\nIf no results were provided, do NOT add Sources. Do NOT number them. Never invent a URL. Use ONLY titles and URLs from the results."
            ContextType.MIXED -> "This question has MULTIPLE different parts. Answer EACH part separately under its own heading, e.g. **Part 1**, **Part 2**. For the Bible part, use Scripture. For the news/factual part, use ONLY WEB SEARCH RESULTS if provided, otherwise answer from general knowledge and note it may have changed. At the very end, if web results were used, add EXACTLY:\n**Sources**\n- [Short Title](URL)\nMax 3. Do not force Bible verses into the non-Bible parts, and do not force Sources into the Bible part."
        }
        val actionRule = when (action) {
            ActionType.RECOMMEND -> "ACTION: RECOMMEND. Recommend the user on some ideas to get things right and solved. Give 3 to 5 practical ideas, explain briefly why each one works, then end with 1 clear best pick and the first step to take. Keep it practical, not theoretical."
            ActionType.COMPARE -> "ACTION: COMPARE. Show similarities and differences clearly, side by side if helpful. End with a short conclusion."
            ActionType.SUMMARIZE -> "ACTION: SUMMARIZE. Keep it very short. Give only the main points, no long explanation."
            ActionType.TRANSLATE -> "ACTION: TRANSLATE. Only translate the text given. Do not add explanation unless asked."
            ActionType.LIST -> "ACTION: LIST. Answer as a clean list. No long paragraphs."
            ActionType.EXPLAIN, ActionType.TEACH -> "ACTION: TEACH / EXPLAIN. Explain clearly step by step, simple language, with an example if helpful."
            ActionType.NONE -> ""
        }
        val sb = StringBuilder()
        if (context == ContextType.BIBLE || context == ContextType.BREAKDOWN) {
            sb.append("Today is $today. Use $translation when quoting Scripture. Current reading: $currentRef.\n")
        } else {
            sb.append("Today is $today.\n")
        }
        sb.append("$STYLE_RULE\n")
        sb.append("$contextRule\n$lengthRule\n")
        if (actionRule.isNotBlank()) sb.append("$actionRule\n")
        sb.append("\n")
        if (bibleContext.isNotBlank() && (context == ContextType.BIBLE || context == ContextType.BREAKDOWN)) sb.append("BIBLE CONTEXT:\n${bibleContext.take(4000)}\n\n")
        if (webContext.isNotBlank()) {
            sb.append("WEB SEARCH RESULTS (true facts as of $today):\n${webContext.take(3500)}\n\n")
        } else if (context == ContextType.NEWS) {
            sb.append("No web results were available. Answer from your general knowledge and note it may not be the very latest.\n\n")
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
            .replace(Regex("(?m)^\\s*>\\s*"), "")
            .replace(">", "")
            .replace("|", "")
            .replace("---", "")
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
            .replace(Regex("(?m)^\\s*>\\s*"), "")
            .replace(">", "")
            .replace("|", "")
            .replace("---", "")
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

    private fun tryGeminiStreaming(model: String, prompt: String, image: Bitmap?, question: String = "", isBreakdown: Boolean = false): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(PROXY_BASE + "gemini?model=$model")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json, text/event-stream, */*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Origin", "https://ewbible.comfortcha34.workers.dev")
                setRequestProperty("Referer", "https://ewbible.comfortcha34.workers.dev/")
                setRequestProperty("Sec-Fetch-Mode", "cors")
                setRequestProperty(PROXY_HEADER, PROXY_VALUE)
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 2000
                readTimeout = 90000
                doOutput = true
            }
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            val base64 = bitmapToBase64(image)
            if (base64 != null) {
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", base64)))
            }
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokensFor(question, isBreakdown)).put("temperature", 0.7))
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

    private fun tryGroqStreaming(model: String, prompt: String, isBreakdown: Boolean, question: String = ""): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(PROXY_BASE + "groq")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json, text/event-stream, */*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Origin", "https://ewbible.comfortcha34.workers.dev")
                setRequestProperty("Referer", "https://ewbible.comfortcha34.workers.dev/")
                setRequestProperty("Sec-Fetch-Mode", "cors")
                setRequestProperty(PROXY_HEADER, PROXY_VALUE)
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 2000
                readTimeout = 90000
                doOutput = true
            }
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM_MSG))
                .put(JSONObject().put("role", "user").put("content", prompt))
            val body = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("stream", true)
                .put("max_tokens", maxTokensFor(question, isBreakdown))
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

    private fun tryCloudflareStreaming(model: String, prompt: String, isBreakdown: Boolean, question: String = ""): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(PROXY_BASE + "cloudflare")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json, text/event-stream, */*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Origin", "https://ewbible.comfortcha34.workers.dev")
                setRequestProperty("Referer", "https://ewbible.comfortcha34.workers.dev/")
                setRequestProperty("Sec-Fetch-Mode", "cors")
                setRequestProperty(PROXY_HEADER, PROXY_VALUE)
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 2000
                readTimeout = 90000
                doOutput = true
            }
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM_MSG))
                .put(JSONObject().put("role", "user").put("content", prompt))
            val body = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("stream", true)
                .put("max_tokens", maxTokensFor(question, isBreakdown))
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