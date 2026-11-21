package com.ewbible.app.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.ewbible.app.BuildConfig
import com.ewbible.app.CrashReporter
import com.ewbible.app.data.database.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class AiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val geminiKey: String
        get() {
            val k = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
            CrashReporter.geminiLog("Key check - Gemini length: ${k.length}")
            return k
        }

    private val groqKey: String
        get() {
            val k = try { BuildConfig.GROQ_API_KEY } catch (e: Exception) { "" }
            CrashReporter.geminiLog("Key check - Groq length: ${k.length}")
            return k
        }

    // --- Matched config for BOTH ---
    private val systemPrompt = "You are a faithful Bible assistant. Answer clearly based on Scripture, keep it simple."
    private val temperature = 0.7
    private val maxTokens = 1024

    private val geminiModels = listOf(
        "gemini-3.5-flash-lite"
    )

    // Groq - free, very fast
    private val groqTextModel = "llama-3.3-70b-versatile"
    private val groqVisionModel = "llama-3.2-11b-vision-preview"

    fun askStreaming(
        question: String,
        currentRef: String = "",
        history: List<MessageEntity> = emptyList(),
        image: Bitmap? = null,
        bibleContext: String = "",
        translation: String = "NKJV"
    ): Flow<String> = flow {
        try {
            val context = buildString {
                if (bibleContext.isNotBlank()) {
                    append("Bible context ($translation, reading $currentRef):\n")
                    append(bibleContext)
                    append("\n\n")
                }
                if (history.isNotEmpty()) {
                    append("Chat history:\n")
                    history.takeLast(6).forEach {
                        append(if (it.isUser) "User: " else "AI: ")
                        append(it.text)
                        append("\n")
                    }
                    append("\n")
                }
            }

            val result = askQuestion(question, context, image)

            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Unknown AI error"
                CrashReporter.geminiLog("askStreaming failed: $msg")
                emit("⚠️ $msg")
                return@flow
            }

            val text = result.getOrNull() ?: ""
            if (text.isBlank()) {
                emit("⚠️ AI returned an empty response. Try again.")
                return@flow
            }

            var i = 0
            while (i < text.length) {
                val end = minOf(i + 100, text.length)
                emit(text.substring(i, end))
                i = end
            }
        } catch (e: Exception) {
            CrashReporter.geminiLog("askStreaming crashed: ${e.message}")
            emit("⚠️ Error: ${e.message}")
        }
    }

    suspend fun askQuestion(question: String, context: String = "", image: Bitmap? = null): Result<String> =
        withContext(Dispatchers.IO) {

            val prompt = buildString {
                if (context.isNotBlank()) {
                    append(context)
                    append("\n\n")
                }
                append("Answer this Bible question clearly, based on Scripture. Keep it simple and faithful:\n")
                append(question)
            }

            // --- 1. Try Gemini ---
            if (geminiKey.isNotBlank() && geminiKey != "YOUR_API_KEY_HERE") {
                for (model in geminiModels) {
                    val r = tryGemini(model, prompt, image)
                    if (r.isSuccess) return@withContext r
                    CrashReporter.geminiLog("Gemini $model failed, trying next: ${r.exceptionOrNull()?.message}")
                }
            } else {
                CrashReporter.geminiLog("Gemini skipped - no key")
            }

            // --- 2. Fallback to Groq ---
            if (groqKey.isNotBlank() && groqKey != "YOUR_API_KEY_HERE") {
                val r = tryGroq(prompt, image)
                if (r.isSuccess) return@withContext r
                CrashReporter.geminiLog("Groq failed: ${r.exceptionOrNull()?.message}")
                return@withContext Result.failure(r.exceptionOrNull() ?: Exception("Groq failed"))
            } else {
                CrashReporter.geminiLog("Groq skipped - no key")
            }

            Result.failure(Exception("Both Gemini and Groq failed or have no key. Check Secrets: GEMINI_API_KEY and GROQ_API_KEY, then re-run workflow."))
        }

    private fun bitmapToBase64(image: Bitmap): String {
        val stream = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun tryGemini(model: String, prompt: String, image: Bitmap?): Result<String> {
        return try {
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            if (image != null) {
                val b64 = bitmapToBase64(image)
                parts.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", b64)
                    })
                })
            }
            val bodyJson = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature)
                    put("maxOutputTokens", maxTokens)
                })
            }
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$geminiKey")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                CrashReporter.geminiLog("Model $model -> HTTP ${response.code}")
                if (!response.isSuccessful) {
                    val err = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull() ?: "HTTP ${response.code}: $body"
                    Result.failure(Exception("$model failed (${response.code}): $err"))
                } else {
                    val text = runCatching {
                        JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                            .getJSONObject(0).getString("text")
                    }.getOrNull()
                    if (!text.isNullOrBlank()) Result.success(text.trim())
                    else Result.failure(Exception("Empty response from $model"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tryGroq(prompt: String, image: Bitmap?): Result<String> {
        return try {
            // Use vision model if image exists, else text model - same features as Gemini
            val model = if (image != null) groqVisionModel else groqTextModel

            val userContent = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", prompt))
                if (image != null) {
                    val b64 = bitmapToBase64(image)
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                    })
                }
            }

            val bodyJson = JSONObject().apply {
                put("model", model)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userContent)
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $groqKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                CrashReporter.geminiLog("Groq $model -> HTTP ${response.code}")
                if (!response.isSuccessful) {
                    val err = runCatching {
                        JSONObject(body).getJSONObject("error").getString("message")
                    }.getOrNull() ?: "HTTP ${response.code}: $body"
                    Result.failure(Exception("Groq failed (${response.code}): $err"))
                } else {
                    val text = runCatching {
                        JSONObject(body).getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                    }.getOrNull()
                    if (!text.isNullOrBlank()) {
                        CrashReporter.geminiLog("Groq success, ${text.length} chars")
                        Result.success(text.trim())
                    } else Result.failure(Exception("Empty response from Groq"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}