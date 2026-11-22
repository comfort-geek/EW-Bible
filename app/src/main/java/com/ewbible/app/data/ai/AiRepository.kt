package com.ewbible.app.data.ai

import android.graphics.Bitmap
import com.ewbible.app.BuildConfig
import com.ewbible.app.CrashReporter
import com.ewbible.app.data.database.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AiRepository {

    private val _lastProvider = MutableStateFlow("Gemini")
    val lastProvider: StateFlow<String> = _lastProvider.asStateFlow()

    private val geminiKey: String
        get() = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }

    private val groqKey: String
        get() = try { BuildConfig.GROQ_API_KEY } catch (_: Exception) { "" }

    // --- WORKING MODELS - VERIFIED 22 NOV 2026 - FREE WITH EXISTING KEYS ---
    // GOOGLE (2):
    //  1. gemini-3.8-flash - main, best quality, free
    //  2. gemini-3.5-flash-lite - fallback, fastest, highest free limit
    // GROQ (2):
    //  1. openai/gpt-oss-120b - main, best quality, free, TEXT ONLY
    //  2. openai/gpt-oss-20b - fallback, fastest, free, TEXT ONLY
    // DO NOT USE:
    //  gemini-2.5-flash - retired / 404
    //  gemini-3.5-flash (no -lite) - deprecated, routed away
    //  llama-3.3-70b-versatile - Enterprise-only since 16 Aug 2026
    //  qwen/qwen3.8-27b for vision - Preview only, not reliable for production
    private val GEMINI_MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite")
    private val GROQ_MODELS = listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b")

    fun askStreaming(
        question: String,
        currentRef: String,
        history: List<MessageEntity>,
        image: Bitmap?,
        bibleContext: String,
        translation: String = "NKJV"
    ): Flow<String> = flow {
        val fullPrompt = buildPrompt(question, currentRef, history, bibleContext, translation)
        val result = askQuestion(fullPrompt, image)
        val text = result.getOrElse { "Sorry, I couldn't answer that. ${it.message ?: ""}" }
        val chunkSize = 24
        var i = 0
        while (i < text.length) {
            val end = minOf(i + chunkSize, text.length)
            emit(text.substring(i, end))
            i = end
            if (i < text.length) delay(18)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun askQuestion(prompt: String, image: Bitmap? = null): Result<String> {
        val errors = mutableListOf<String>()

        // --- 1. Try Gemini (2 models) - text + image ---
        for (model in GEMINI_MODELS) {
            _lastProvider.value = "Gemini"
            val result = tryGemini(model, prompt, image)
            if (result.isSuccess) {
                val text = result.getOrNull()
                if (!text.isNullOrBlank()) {
                    _lastProvider.value = "Gemini"
                    CrashReporter.geminiLog("Gemini $model success, ${text.length} chars")
                    return Result.success(text.trim())
                }
            }
            val err = result.exceptionOrNull()?.message ?: "Gemini empty"
            errors.add("Gemini $model: $err")
            CrashReporter.geminiLog("Gemini $model failed: $err")
        }

        // --- 2. Fallback to Groq (2 models) - TEXT ONLY ---
        // gpt-oss models do NOT support images. Don't send image to Groq.
        if (image != null) {
            return Result.failure(Exception("Could not read the image. Please try again - Gemini is busy right now."))
        }

        if (groqKey.isNotBlank()) {
            for (model in GROQ_MODELS) {
                _lastProvider.value = "Groq"
                CrashReporter.groqLog("Groq trying $model")
                val result = tryGroq(model, prompt)
                if (result.isSuccess) {
                    val text = result.getOrNull()
                    if (!text.isNullOrBlank()) {
                        _lastProvider.value = "Groq"
                        CrashReporter.groqLog("Groq $model success, ${text.length} chars")
                        return Result.success(text.trim())
                    }
                }
                val err = result.exceptionOrNull()?.message ?: "Groq empty"
                errors.add("Groq $model: $err")
                CrashReporter.groqLog("Groq $model failed: $err")
            }
        } else {
            CrashReporter.groqLog("Groq skipped - no API key")
        }

        // --- 3. All 4 failed ---
        if (errors.all { it.contains("429") }) {
            return Result.failure(Exception("Free daily limit reached for today. Please try again tomorrow - it's free, so we share a daily limit."))
        }
        // Don't show raw JSON to user - show clean message, log full error
        return Result.failure(Exception("All AI models are busy right now. Please try again in a minute."))
    }

    private fun buildPrompt(
        question: String,
        currentRef: String,
        history: List<MessageEntity>,
        bibleContext: String,
        translation: String
    ): String {
        val sb = StringBuilder()
        sb.append("You are a helpful Bible assistant. Answer clearly, warmly, and biblically. Use $translation when quoting Scripture. Current reading: $currentRef.\n\n")
        if (bibleContext.isNotBlank()) {
            sb.append("BIBLE CONTEXT:\n$bibleContext\n\n")
        }
        if (history.isNotEmpty()) {
            sb.append("CONVERSATION HISTORY (last 6):\n")
            history.takeLast(6).forEach {
                sb.append(if (it.isUser) "User: " else "Assistant: ").append(it.text.take(800)).append("\n")
            }
            sb.append("\n")
        }
        sb.append("User question: $question")
        return sb.toString()
    }

    private fun bitmapToBase64(image: Bitmap?): String? {
        if (image == null) return null
        return try {
            val bytes = ByteArrayOutputStream().use { out ->
                image.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    // --- Gemini ---
    private suspend fun tryGemini(model: String, prompt: String, image: Bitmap?): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            if (geminiKey.isBlank()) return@withContext Result.failure(Exception("No Gemini API key"))
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$geminiKey")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30000
                readTimeout = 60000
                doOutput = true
            }
            val parts = JSONArray()
            parts.put(JSONObject().put("text", prompt))
            val base64 = bitmapToBase64(image)
            if (base64 != null) {
                parts.put(
                    JSONObject().put(
                        "inlineData", JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", base64)
                    )
                )
            }
            // NOTE: temperature / top_p / top_k are deprecated on Gemini 3.6+ and ignored.
            // Don't send them - they can cause 400 on new models.
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put("generationConfig", JSONObject().put("maxOutputTokens", 1500))
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val response = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
                CrashReporter.geminiLog("Gemini $model HTTP $code: ${err.take(500)}")
                return@withContext Result.failure(Exception("Gemini HTTP $code: ${err.take(200)}"))
            }
            val text = JSONObject(response)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text")
            if (text.isNullOrBlank()) Result.failure(Exception("Gemini empty response"))
            else Result.success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashReporter.geminiLog("Gemini exception: ${e.message}")
            Result.failure(e)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    // --- Groq - TEXT ONLY ---
    private suspend fun tryGroq(model: String, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            if (groqKey.isBlank()) return@withContext Result.failure(Exception("No Groq API key"))
            CrashReporter.groqLog("Groq using model: $model")

            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $groqKey")
                connectTimeout = 30000
                readTimeout = 60000
                doOutput = true
            }

            val body = JSONObject()
                .put("model", model)
                .put("temperature", 0.7)
                .put("max_tokens", 1500)
                .put(
                    "messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "You are a helpful Bible assistant. Answer clearly, warmly, and biblically."))
                        .put(JSONObject().put("role", "user").put("content", prompt))
                )
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val response = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
                CrashReporter.groqLog("Groq $model HTTP $code: ${err.take(500)}")
                return@withContext Result.failure(Exception("Groq HTTP $code: ${err.take(200)}"))
            }
            val text = JSONObject(response)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")
            if (text.isNullOrBlank()) Result.failure(Exception("Groq empty response"))
            else Result.success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashReporter.groqLog("Groq exception: ${e.message}")
            Result.failure(e)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}