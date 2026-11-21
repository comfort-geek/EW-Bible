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

    // --- Provider tracking for UI: "Gemini" or "Groq" ---
    private val _lastProvider = MutableStateFlow("Gemini")
    val lastProvider: StateFlow<String> = _lastProvider.asStateFlow()

    private val geminiKey: String
        get() = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }

    private val groqKey: String
        get() = try { BuildConfig.GROQ_API_KEY } catch (_: Exception) { "" }

    // --- Public API used by ChatViewModel ---
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
        // Fake streaming - split into chunks so UI animates
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
        // Reset to Gemini for every new question
        _lastProvider.value = "Gemini"

        // --- 1. Try Gemini first ---
        val geminiResult = tryGemini(prompt, image)
        if (geminiResult.isSuccess) {
            val text = geminiResult.getOrNull()
            if (!text.isNullOrBlank()) {
                _lastProvider.value = "Gemini"
                CrashReporter.geminiLog("Gemini success, ${text.length} chars")
                return Result.success(text.trim())
            }
        }
        val geminiError = geminiResult.exceptionOrNull()?.message ?: "Gemini empty"
        CrashReporter.geminiLog("Gemini failed: $geminiError - falling back to Groq")

        // --- 2. Fallback to Groq ---
        if (groqKey.isNotBlank()) {
            // FIXED: don't set provider to Groq until it actually succeeds
            CrashReporter.groqLog("Groq trying - Gemini failed: $geminiError")
            val groqResult = tryGroq(prompt)
            if (groqResult.isSuccess) {
                val text = groqResult.getOrNull()
                if (!text.isNullOrBlank()) {
                    _lastProvider.value = "Groq"
                    CrashReporter.groqLog("Groq success, ${text.length} chars")
                    return Result.success(text.trim())
                }
            }
            val groqError = groqResult.exceptionOrNull()?.message ?: "Groq empty"
            CrashReporter.groqLog("Groq failed: $groqError")
            return Result.failure(Exception("Gemini: $geminiError | Groq: $groqError"))
        }

        CrashReporter.groqLog("Groq skipped - no API key")
        return Result.failure(Exception("Gemini failed: $geminiError"))
    }

    // --- Prompt ---
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
                // FIXED: truncate each message so prompt doesn't blow up tokens
                sb.append(if (it.isUser) "User: " else "Assistant: ").append(it.text.take(800)).append("\n")
            }
            sb.append("\n")
        }
        sb.append("User question: $question")
        return sb.toString()
    }

    // --- Gemini ---
    private suspend fun tryGemini(prompt: String, image: Bitmap?): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            if (geminiKey.isBlank()) return@withContext Result.failure(Exception("No Gemini API key"))
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiKey")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30000
                readTimeout = 60000
                doOutput = true
            }
            val parts = JSONArray()
            parts.put(JSONObject().put("text", prompt))
            if (image != null) {
                val bytes = ByteArrayOutputStream().use { out ->
                    image.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    out.toByteArray()
                }
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                parts.put(
                    JSONObject().put(
                        "inlineData", JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", base64)
                    )
                )
            }
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 1500))
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val response = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
                CrashReporter.geminiLog("Gemini HTTP $code: ${err.take(300)}")
                return@withContext Result.failure(Exception("Gemini HTTP $code"))
            }
            val text = JSONObject(response)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text")
            if (text.isNullOrBlank()) Result.failure(Exception("Gemini empty response"))
            else Result.success(text)
        } catch (e: CancellationException) {
            throw e // FIXED: cancel is not an error, don't fall back to Groq on cancel
        } catch (e: Exception) {
            CrashReporter.geminiLog("Gemini exception: ${e.message}")
            Result.failure(e)
        } finally {
            // FIXED: always close connection, no socket leak
            runCatching { conn?.disconnect() }
        }
    }

    // --- Groq ---
    private suspend fun tryGroq(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            if (groqKey.isBlank()) return@withContext Result.failure(Exception("No Groq API key"))
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
                .put("model", "llama-3.3-70b-versatile")
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
                CrashReporter.groqLog("Groq HTTP $code: ${err.take(300)}")
                return@withContext Result.failure(Exception("Groq HTTP $code"))
            }
            val text = JSONObject(response)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")
            if (text.isNullOrBlank()) Result.failure(Exception("Groq empty response"))
            else Result.success(text)
        } catch (e: CancellationException) {
            throw e // FIXED: cancel is not an error
        } catch (e: Exception) {
            CrashReporter.groqLog("Groq exception: ${e.message}")
            Result.failure(e)
        } finally {
            // FIXED: always close connection, no socket leak
            runCatching { conn?.disconnect() }
        }
    }
}