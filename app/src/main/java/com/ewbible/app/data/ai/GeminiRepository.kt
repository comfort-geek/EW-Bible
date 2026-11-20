package com.ewbible.app.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.ewbible.app.BuildConfig
import com.ewbible.app.data.database.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

class GeminiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiKey: String
        get() = BuildConfig.GEMINI_API_KEY

    private val models = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash"
    )

    // Called by ChatViewModel - returns Flow so UI can stream
    fun askStreaming(
        question: String,
        currentRef: String = "",
        history: List<MessageEntity> = emptyList(),
        image: Bitmap? = null,
        bibleContext: String = "",
        translation: String = "NKJV"
    ): Flow<String> = flow {
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

        // FIX: Don't use getOrThrow() here - it killed the Flow silently
        // and UI showed blank + Daily stayed 0/50
        val result = askQuestion(question, context, image)

        if (result.isFailure) {
            val msg = result.exceptionOrNull()?.message ?: "Gemini failed - unknown error"
            Log.e("GeminiRepository", "askStreaming failed: $msg")
            // Emit error as text so UI shows it instead of blank
            emit("⚠️ $msg")
            return@flow
        }

        val text = result.getOrThrow()
        if (text.isBlank()) {
            emit("⚠️ Gemini returned an empty response. Try again.")
            return@flow
        }

        // Emit in ~100 char chunks so ChatViewModel streaming works
        var i = 0
        while (i < text.length) {
            val end = minOf(i + 100, text.length)
            emit(text.substring(i, end))
            i = end
        }
    }.catch { e ->
        // Last safety net - never let Flow die silently
        Log.e("GeminiRepository", "Flow crashed", e)
        emit("⚠️ Error: ${e.message ?: "Unknown error"}")
    }

    suspend fun askQuestion(question: String, context: String = "", image: Bitmap? = null): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE" || apiKey == "null") {
                return@withContext Result.failure(
                    Exception("Gemini API key is missing. Add GEMINI_API_KEY in local.properties and rebuild. See README on GitHub.")
                )
            }

            val prompt = buildString {
                if (context.isNotBlank()) {
                    append("Bible context:\n")
                    append(context)
                    append("\n\n")
                }
                append("Answer this Bible question clearly, based on Scripture. Keep it simple and faithful:\n")
                append(question)
            }

            var lastError: Exception? = null

            for (model in models) {
                try {
                    val parts = JSONArray().put(JSONObject().put("text", prompt))
                    if (image != null) {
                        val stream = ByteArrayOutputStream()
                        image.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                        parts.put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", b64)
                            })
                        })
                    }

                    val bodyJson = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.7)
                            put("maxOutputTokens", 1024)
                        })
                    }

                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        Log.e("GeminiRepository", "Model $model HTTP ${response.code}: $responseBody")
                        if (response.code == 404) {
                            lastError = Exception("Model $model not found (404), trying next...")
                            continue
                        }
                        val errMsg = runCatching {
                            JSONObject(responseBody).getJSONObject("error").getString("message")
                        }.getOrNull() ?: "HTTP ${response.code}: $responseBody"
                        // Don't return immediately on 400/403 for first model if we have fallback,
                        // but for API key errors, fail fast - trying 2nd model won't help
                        if (response.code == 400 || response.code == 403) {
                            return@withContext Result.failure(Exception("Gemini error (${response.code}): $errMsg"))
                        }
                        lastError = Exception(errMsg)
                        continue
                    }

                    val text = runCatching {
                        JSONObject(responseBody)
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                    }.getOrNull()

                    if (!text.isNullOrBlank()) {
                        return@withContext Result.success(text.trim())
                    } else {
                        lastError = Exception("Empty response from $model - it may have been blocked. Try rephrasing.")
                        continue
                    }
                } catch (e: Exception) {
                    Log.e("GeminiRepository", "Model $model exception", e)
                    lastError = e
                    continue
                }
            }

            Result.failure(lastError ?: Exception("Gemini failed - all models tried"))
        }
}