package com.ewbible.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import com.ewbible.app.BuildConfig
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

    private val _lastProvider = MutableStateFlow("Gemini")
    val lastProvider: StateFlow<String> = _lastProvider.asStateFlow()

    private val geminiKey: String
        get() = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
    private val groqKey: String
        get() = try { BuildConfig.GROQ_API_KEY } catch (_: Exception) { "" }
    private val tavilyKey: String
        get() = try { BuildConfig.TAVILY_API_KEY } catch (_: Exception) { "" }

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

    private var remoteGemini = FALLBACK_GEMINI
    private var remoteGroq = FALLBACK_GROQ
    private var remoteVersion = 3

    // FIX 1: 60s was cutting long answers. 120s lets it finish.
    private val STREAM_TIMEOUT_MS = 120000L
    // FIX 2: 2048 tokens + your "2048 chars max" prompt = model stops early.
    // 4096 gives room to finish the sentence.
    private val MAX_TOKENS = 4096

    private var cachedQuery = ""
    private var cachedWeb = ""
    private var cachedAt = 0L
    private val WEB_CACHE_MS = 10 * 60 * 1000L

    suspend fun refreshModels(context: Context) {
        val prefs = context.getSharedPreferences("ew_models", Context.MODE_PRIVATE)
        try {
            val savedVersion = prefs.getInt("version", 3)
            val savedGemini = prefs.getString("gemini", null)
            val savedGroq = prefs.getString("groq", null)
            if (savedGemini != null) {
                val arr = JSONArray(savedGemini)
                if (arr.length() > 0) remoteGemini = (0 until arr.length()).map { arr.getString(it) }
            }
            if (savedGroq != null) {
                val arr = JSONArray(savedGroq)
                if (arr.length() > 0) remoteGroq = (0 until arr.length()).map { arr.getString(it) }
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
                    if (g != null && g.length() > 0) remoteGemini = (0 until g.length()).map { g.getString(it) }
                    if (q != null && q.length() > 0) remoteGroq = (0 until q.length()).map { q.getString(it) }
                    remoteVersion = newVersion
                    prefs.edit()
                        .putInt("version", newVersion)
                        .putString("gemini", JSONArray(remoteGemini).toString())
                        .putString("groq", JSONArray(remoteGroq).toString())
                        .apply()
                }
            }
            conn.disconnect()
        } catch (_: Exception) { }
    }

    private fun needsWebSearch(question: String): Boolean {
        val q = question.lowercase().trim()
        if (q.contains("what does") && q.contains("mean")) return false
        if (q.contains("explain") && (q.contains("verse") || q.contains("chapter"))) return false
        if (q.contains("pray") || q.contains("prayer")) return false
        if (q.contains("who was david") || q.contains("who is david")) return false
        if (q == "hi" || q == "hello" || q == "hey") return false
        val strongTriggers = listOf(
            "pope", "president", "election", "war", "news",
            "latest", "recent", "current", "today", "what happened",
            "job", "jobs", "hiring", "vacancy", "vacancies", "apply",
            "bible", "verse", "church", "pastor", "other topics"
        )
        if (strongTriggers.any { q.contains(it) }) return true
        if (q.length < 30) return false
        val weakTriggers = listOf(
            "who is", "who was", "what is happening",
            "king", "when did", "where is", "how many", "is it true"
        )
        return weakTriggers.any { q.contains(it) }
    }

    private suspend fun getWebContext(question: String): String {
        if (!needsWebSearch(question)) return ""
        val key = question.trim().lowercase()
        if (key == cachedQuery && System.currentTimeMillis() - cachedAt < WEB_CACHE_MS && cachedWeb.isNotBlank()) {
            return cachedWeb
        }
        val result = withTimeoutOrNull(1000L) { searchTavily(question) } ?: ""
        return result
    }

    private suspend fun searchTavily(query: String): String {
        if (tavilyKey.isBlank()) return ""
        return try {
            var conn: HttpURLConnection? = null
            try {
                val searchUrl = URL("https://api.tavily.com/search")
                conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 1000
                    readTimeout = 1200
                    doOutput = true
                }
                val body = JSONObject()
                    .put("api_key", tavilyKey)
                    .put("query", query)
                    .put("search_depth", "basic")
                    .put("max_results", 2)
                    .put("include_answer", false)
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) return ""
                val text = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(text)
                val sb = StringBuilder()
                val results = json.optJSONArray("results")
                if (results != null) {
                    for (i in 0 until minOf(results.length(), 2)) {
                        val r = results.getJSONObject(i)
                        val title = r.optString("title")
                        val resultUrl = r.optString("url")
                        val content = r.optString("content")
                        if (content.isNotBlank()) sb.append("$title: ${content.take(350)}\nLink: $resultUrl\n\n")
                    }
                }
                val out = sb.toString().take(1200)
                if (out.isNotBlank()) {
                    cachedQuery = query.trim().lowercase()
                    cachedWeb = out
                    cachedAt = System.currentTimeMillis()
                }
                out
            } finally {
                runCatching { conn?.disconnect() }
            }
        } catch (e: Exception) { "" }
    }

    // FIX 3: NO MORE RACE. Sequential = no truncation.
    // Try Gemini 1 -> if it streams to completion, done.
    // Only if it fails with ZERO chunks, try next model / Groq.
    // Once a model starts streaming, we COMMIT to it and let it finish.
    fun askStreaming(
        question: String,
        currentRef: String,
        history: List<MessageEntity>,
        image: Bitmap?,
        bibleContext: String,
        translation: String = "NKJV"
    ): Flow<String> = flow {
        val webContext = if (image == null) getWebContext(question) else ""
        val basePrompt = buildPrompt(question, currentRef, history, bibleContext, translation, webContext)

        val errors = mutableListOf<String>()

        // Helper: try one model, stream it. Returns true if completed.
        suspend fun tryOne(
            provider: String,
            model: String,
            stream: Flow<String>
        ): Boolean {
            _lastProvider.value = provider
            var gotAnything = false
            try {
                val completed = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                    stream.collect { chunk ->
                        if (chunk.isNotEmpty()) {
                            gotAnything = true
                            emit(chunk) // real streaming, like this chat
                        }
                    }
                    true
                } ?: false

                if (gotAnything && completed) return true
                // If we got chunks but timed out, DO NOT try another model -
                // it would duplicate / mix answers. Just end this attempt
                // as finished. Timeout is now 120s so this is rare.
                if (gotAnything && !completed) return true
                return false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                errors.add("$provider $model: ${e.message}")
                if (provider == "Gemini") CrashReporter.geminiLog("Gemini $model failed: ${e.message}")
                else CrashReporter.groqLog("Groq $model failed: ${e.message}")
                return false
            }
        }

        if (image != null) {
            for (model in remoteGemini) {
                if (tryOne("Gemini", model, tryGeminiStreaming(model, basePrompt, image))) return@flow
            }
            emit("Could not read the image. Please try again - Gemini is busy right now.")
            return@flow
        }

        // 1. Gemini first
        for (model in remoteGemini) {
            if (tryOne("Gemini", model, tryGeminiStreaming(model, basePrompt, null))) return@flow
        }
        // 2. Only if ALL Gemini failed with 0 chunks, try Groq
        if (groqKey.isNotBlank()) {
            for (model in remoteGroq) {
                if (tryOne("Groq", model, tryGroqStreaming(model, basePrompt))) return@flow
            }
        }

        if (errors.isNotEmpty() && errors.all { it.contains("429") }) {
            emit("Free daily limit reached for today. Please try again tomorrow.")
        } else {
            emit("All AI models are busy right now. Please try again in a minute.")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun askQuestion(prompt: String, image: Bitmap? = null): Result<String> {
        val webContext = if (image == null) getWebContext(prompt) else ""
        val today = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date())
        val linkRule = "Do NOT use Markdown. Do NOT use bullet points, dashes, asterisks, or numbered lists. Write in clean plain paragraphs only, with a blank line between paragraphs. If you include a link, write it as a plain full URL like https://... Only use links from the provided web search results. Never invent links."
        val fullPrompt = if (webContext.isNotBlank()) {
            "Today is $today. $linkRule WEB SEARCH RESULTS - TRUE FACTS AS OF $today. YOU MUST USE THEM. Do NOT say cutoff/2024:\n${webContext}\n\nUser question: $prompt"
        } else {
            "Today is $today. $linkRule User question: $prompt"
        }
        val sb = StringBuilder()
        return try {
            var lastErr: Exception? = null
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
            if (image != null) return Result.failure(Exception("Could not read the image. Please try again - Gemini is busy right now."))
            if (groqKey.isNotBlank()) {
                for (model in remoteGroq) {
                    _lastProvider.value = "Groq"
                    try {
                        sb.clear()
                        val completed = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                            tryGroqStreaming(model, fullPrompt).collect { sb.append(it) }
                            true
                        } ?: false
                        if (sb.isNotBlank() && completed) return Result.success(cleanMarkdown(sb.toString()).trim())
                        if (sb.isNotBlank() && !completed) continue
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { lastErr = e; sb.clear() }
                }
            }
            Result.failure(lastErr ?: Exception("All AI models are busy right now."))
        } catch (e: CancellationException) { throw e }
    }

    private fun buildPrompt(
        question: String,
        currentRef: String,
        history: List<MessageEntity>,
        bibleContext: String,
        translation: String,
        webContext: String = ""
    ): String {
        val sb = StringBuilder()
        val today = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date())
        sb.append("You are a Bible assistant. Today is $today. Answer clearly, warmly, and biblically. Use $translation when quoting Scripture. Current reading: $currentRef.\n")
        // FIX 4: Removed "around 2048 characters max" - that was telling the model to cut off.
        sb.append("Do NOT use Markdown. Do NOT use bullet points, dashes, asterisks, or numbered lists. Write in clean plain paragraphs only, with a blank line between paragraphs. Let the length fit the question - a simple question gets 1 short paragraph, a deeper question gets 2 to 3 spaced paragraphs. Always finish your answer fully. Never stop mid-sentence or mid-verse. If you include a link, write it as a plain full URL like https://... Only use links from the provided web search results. Never invent links.\n\n")
        if (bibleContext.isNotBlank()) sb.append("BIBLE CONTEXT:\n${bibleContext.take(1500)}\n\n")
        if (webContext.isNotBlank()) {
            sb.append("WEB SEARCH RESULTS - TRUE FACTS AS OF $today. YOU MUST USE THEM. Do NOT say your knowledge is outdated, cut off, or up to 2024. Do NOT use old training data for current events. Answer ONLY from these results:\n${webContext.take(1200)}\n\n")
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
            .replace("**", "")
            .replace("__", "")
            .replace("### ", "").replace("## ", "").replace("# ", "").replace("---", "")
            .replace("`", "")
        return cleaned.lines().joinToString("\n") { line ->
            var l = line.trimStart()
            l = l.removePrefix("- ").removePrefix("* ").removePrefix("• ").removePrefix("> ")
            l = l.replace(Regex("^\\d+\\.\\s"), "")
            l = l.replace(Regex("^\\d+\\)\\s"), "")
            l
        }.trim()
    }

    private fun cleanStreamingChunk(text: String): String {
        return text.replace("**", "").replace("__", "").replace("### ", "").replace("## ", "").replace("# ", "").replace("`", "")
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
            if (geminiKey.isBlank()) throw Exception("No Gemini API key")
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$geminiKey")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 3000
                readTimeout = 120000
                doOutput = true
            }
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            val base64 = bitmapToBase64(image)
            if (base64 != null) parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", base64)))
            val genConfig = JSONObject().put("maxOutputTokens", MAX_TOKENS)
            if (model == "gemini-4-hydrogen") {
                genConfig.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
            }
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put("generationConfig", genConfig)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
                throw Exception("Gemini HTTP ${conn.responseCode}: ${err.take(200)}")
            }
            val reader: BufferedReader = conn.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (!l.startsWith("data:")) continue
                val jsonStr = l.removePrefix("data:").trim()
                if (jsonStr.isEmpty() || jsonStr == "[DONE]") continue
                val text = JSONObject(jsonStr)
                    .optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                    ?.optJSONObject(0)?.optString("text")
                if (!text.isNullOrEmpty()) emit(cleanStreamingChunk(text))
            }
        } catch (e: CancellationException) { throw e }
        finally { runCatching { conn?.disconnect() } }
    }.flowOn(Dispatchers.IO)

    private fun tryGroqStreaming(model: String, prompt: String): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            if (groqKey.isBlank()) throw Exception("No Groq API key")
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $groqKey")
                connectTimeout = 3000
                readTimeout = 120000
                doOutput = true
            }
            val body = JSONObject()
                .put("model", model)
                .put("temperature", 0.7)
                .put("max_tokens", MAX_TOKENS)
                .put("stream", true)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "You are a helpful AI. Do NOT use Markdown. Do NOT use bullet points, dashes, asterisks, or numbered lists. Write in clean plain paragraphs only, with a blank line between paragraphs. If you include a link, write it as a plain full URL like https://... Only use links from the provided web search results. Never invent links."))
                    .put(JSONObject().put("role", "user").put("content", prompt)))
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
                throw Exception("Groq HTTP ${conn.responseCode}: ${err.take(200)}")
            }
            val reader: BufferedReader = conn.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                val delta = JSONObject(data)
                    .optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("delta")?.optString("content")
                if (!delta.isNullOrEmpty()) emit(cleanStreamingChunk(delta))
            }
        } catch (e: CancellationException) { throw e }
        finally { runCatching { conn?.disconnect() } }
    }.flowOn(Dispatchers.IO)
}