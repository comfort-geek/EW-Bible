package com.ewbible.app.data.ai

import com.ewbible.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiKey: String
        get() = BuildConfig.GEMINI_API_KEY

    // FIXED: try newest -> oldest, 404 on one model no longer kills AI Chat
    private val models = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    )

    suspend fun askQuestion(question: String, context: String = ""): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    Exception("Gemini API key is missing. Add GEMINI_API_KEY in local.properties")
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
                    val bodyJson = JSONObject().apply {
                        put("contents", JSONArray().put(
                            JSONObject().put("parts", JSONArray().put(
                                JSONObject().put("text", prompt)
                            ))
                        ))
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
                        // 404 = model not found, try next model. Other errors = stop and show real error
                        if (response.code == 404) {
                            lastError = Exception("Model $model not found (404), trying next...")
                            continue
                        }
                        val errMsg = runCatching {
                            JSONObject(responseBody)
                                .getJSONObject("error")
                                .getString("message")
                        }.getOrNull() ?: "HTTP ${response.code}: $responseBody"
                        return@withContext Result.failure(Exception(errMsg))
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
                    lastError = e
                    // Network error - try next model once, then give up
                    continue
                }
            }

            Result.failure(lastError ?: Exception("Gemini failed - all models tried"))
        }
}