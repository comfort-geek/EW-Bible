package com.ewbible.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ewbible.app.BuildConfig
import com.ewbible.app.CrashReporter
import com.ewbible.app.data.database.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    private val STREAM_TIMEOUT_MS = 60000L
    private val MAX_TOKENS = 2048

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
            "bible", "verse", "church", "pastor"
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
        val result = withTimeoutOrNull(2200L) { searchTavily(question) } ?: ""
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
                    connectTimeout = 2000
                    readTimeout = 2500
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

        if (image != null) {
            for (model in remoteGemini) {
                _lastProvider.value = "Gemini"
                var gotAnything = false
                try {
                    val completed = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                        tryGeminiStreaming(model, basePrompt, image).collect { chunk ->
                            if (chunk.isNotEmpty()) { gotAnything = true; emit(chunk) }
                        }
                        true
                    } ?: false
                    if (gotAnything && completed) return@flow
                    if (gotAnything && !completed) return@flow
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    CrashReporter.geminiLog("Gemini $model failed: ${e.message}")
                }
            }
            emit("Could not read the image. Please try again - Gemini is busy right now.")
            return@flow
        }

        val errors = mutableListOf<String>()

        coroutineScope {
            val channel = Channel<Pair<String, String>>(Channel.UNLIMITED)
            val winner = AtomicReference<String?>(null)
            val winnerFinished = AtomicBoolean(false)
            val winnerGotAnything = AtomicBoolean(false)

            val geminiJob = launch {
                for (model in remoteGemini) {
                    if (winner.get() != null && winner.get() != "Gemini") break
                    try {
                        var gotChunkFromThisModel = false
                        val completed = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                            tryGeminiStreaming(model, basePrompt, null).collect { chunk ->
                                if (chunk.isNotEmpty()) {
                                    gotChunkFromThisModel = true
                                    winnerGotAnything.set(true)
                                    winner.compareAndSet(null, "Gemini")
                                    if (winner.get() == "Gemini") channel.send("Gemini" to chunk)
                                }
                            }
                            true
                        } ?: false
                        if (winner.get() == "Gemini") {
                            if (completed) winnerFinished.set(true)
                            break
                        }
                        if (gotChunkFromThisModel) break
                        if (!completed) continue
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        errors.add("Gemini $model: ${e.message}")
                        CrashReporter.geminiLog("Gemini $model failed: ${e.message}")
                    }
                }
            }

            val groqJob = launch {
                if (groqKey.isBlank()) return@launch
                for (model in remoteGroq) {
                    if (winner.get() != null && winner.get() != "Groq") break
                    try {
                        var gotChunkFromThisModel = false
                        val completed = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                            tryGroqStreaming(model, basePrompt).collect { chunk ->
                                if (chunk.isNotEmpty()) {
                                    gotChunkFromThisModel = true
                                    winnerGotAnything.set(true)
                                    winner.compareAndSet(null, "Groq")
                                    if (winner.get() == "Groq") channel.send("Groq" to chunk)
                                }
                            }
                            true
                        } ?: false
                        if (winner.get() == "Groq") {
                            if (completed) winnerFinished.set(true)
                            break
                        }
                        if (gotChunkFromThisModel) break
                        if (!completed) continue
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        errors.add("Groq $model: ${e.message}")
                        CrashReporter.groqLog("Groq $model failed: ${e.message}")
                    }
                }
            }

            var gotAnything = false
            var loserCancelled = false

            while (true) {
                if (geminiJob.isCompleted && groqJob.isCompleted && channel.isEmpty) break
                if (geminiJob.isCompleted && groqJob.isCompleted) {
                    val drained = channel.tryReceive().getOrNull()
                    if (drained == null) break
                    if (!loserCancelled) {
                        loserCancelled = true
                        _lastProvider.value = drained.first
                    }
                    gotAnything = true
                    emit(drained.second)
                    continue
                }
                val result = withTimeoutOrNull(STREAM_TIMEOUT_MS + 5000L) { channel.receiveCatching() }
                val item = result?.getOrNull() ?: break
                if (!loserCancelled) {
                    loserCancelled = true
                    _lastProvider.value = item.first
                    if (item.first == "Gemini") groqJob.cancel() else geminiJob.cancel()
                }
                gotAnything = true
                emit(item.second)
            }

            channel.close()
            geminiJob.join()
            groqJob.join()

            if (!gotAnything) {
                if (errors.isNotEmpty() && errors.all { it.contains("429") }) {
                    emit("Free daily limit reached for today. Please try again tomorrow.")
                } else {
                    emit("All AI models are busy right now. Please try again in a minute.")
                }
            }
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
        sb.append("You are a helpful Bible assistant. Today is $today. Answer clearly, warmly, and biblically. Use $translation when quoting Scripture. Current reading: $currentRef.\n")
        sb.append("Do NOT use Markdown. Do NOT use bullet points, dashes, asterisks, or numbered lists. Write in clean plain paragraphs only, with a blank line between paragraphs. Let the length fit the question - a simple question gets 1 short paragraph, a deeper question gets 2 to 3 spaced paragraphs. Keep answer complete but not too long - around 2048 characters max. Always finish your answer fully. Never stop mid-sentence or mid-verse. If you include a link, write it as a plain full URL like https://... Only use links from the provided web search results. Never invent links.\n\n")
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

    // FIXED: Now strips bullets, numbers, >, *, - at start of lines
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

    // FIXED: Safe for streaming - only removes inline markdown, never touches line starts mid-chunk
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
                connectTimeout = 5000
                readTimeout = 60000
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
                connectTimeout = 5000
                readTimeout = 60000
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