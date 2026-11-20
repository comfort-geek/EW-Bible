package com.ewbible.app.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.ewbible.app.BuildConfig
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

class GeminiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // --- YOUR KEY ---
    // On GitHub Actions, key comes from GitHub Secret -> BuildConfig.GEMINI_API_KEY
    // This FALLBACK is ONLY for when you run on your own phone / Android Studio
    // and you don't have local.properties.
    // If your repo is PUBLIC, DO NOT commit a real key here. Use "PASTE..." and
    // rely on GitHub Secret. If your repo is PRIVATE, you can paste it here for testing.
    private val FALLBACK_API_KEY = "PASTE_YOUR_GEMINI_API_KEY_HERE"

    private val apiKey: String
        get() {
            val fromBuildConfig = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
            // Log only length, NEVER log the key itself
            Log.d("Gemini", "Key check - BuildConfig length: ${fromBuildConfig.length}, Fallback length: ${FALLBACK_API_KEY.length}")
            return if (fromBuildConfig.isNotBlank() && fromBuildConfig != "YOUR_API_KEY_HERE") {
                fromBuildConfig
            } else {
                FALLBACK_API_KEY
            }
        }

    private val models = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    )

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
                val msg = result.exceptionOrNull()?.message ?: "Unknown Gemini error"
                Log.e("Gemini", "askStreaming failed: $msg")
                emit("⚠️ $msg")
                return@flow
            }

            val text = result.getOrNull() ?: ""
            if (text.isBlank()) {
                emit("⚠️ Gemini returned an empty response. Try again.")
                return@flow
            }

            var i = 0
            while (i < text.length) {
                val end = minOf(i + 100, text.length)
                emit(text.substring(i, end))
                i = end
            }
        } catch (e: Exception) {
            Log.e("Gemini", "askStreaming crashed", e)
            emit("⚠️ Error: ${e.message}")
        }
    }

    suspend fun askQuestion(question: String, context: String = "", image: Bitmap? = null): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || apiKey == "PASTE_YOUR_GEMINI_API_KEY_HERE") {
                return@withContext Result.failure(
                    Exception("Gemini API key is missing. GitHub Secret did not reach BuildConfig AND FALLBACK is still placeholder. Check: 1) Secret name = GEMINI_API_KEY 2) Re-run workflow 3) Or paste real key in FALLBACK_API_KEY for testing")
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
                    Log.d("Gemini", "Model $model -> HTTP ${response.code}")

                    if (!response.isSuccessful) {
                        val errMsg = runCatching {
                            JSONObject(responseBody).getJSONObject("error").getString("message")
                        }.getOrNull() ?: "HTTP ${response.code}: $responseBody"
                        Log.e("Gemini", "Model $model failed: $errMsg")
                        if (response.code == 404) {
                            lastError = Exception("Model $model not found (404), trying next...")
                            continue
                        }
                        return@withContext Result.failure(Exception("Gemini Error ($model): $errMsg"))
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
                        lastError = Exception("Empty response from $model")
                        continue
                    }
                } catch (e: Exception) {
                    Log.e("Gemini", "Model $model exception", e)
                    lastError = e
                    continue
                }
            }

            Result.failure(lastError ?: Exception("Gemini failed - all models tried"))
        }
}