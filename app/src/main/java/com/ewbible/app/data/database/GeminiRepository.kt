package com.ewbible.app.database

import com.ewbible.app.database.BuildConfig
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GeminiRepository {

    // STREAMING: answer comes word-by-word
    fun askStreaming(
        question: String,
        currentRef: String,
        history: List<MessageEntity>,
        image: Bitmap?
    ): Flow<String> = flow {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) {
            emit("No API key found.")
            return@flow
        }

        // MEMORY: last 10 messages only - no verses, no notes
        val contents = JSONArray()

        // System instruction as first turn
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text",
                "You are a helpful Bible study assistant for EW Bible app. Answer clearly, simply, from the Bible. User is reading $currentRef. Do not invent verses. If unsure, say so."
            )))
        })
        contents.put(JSONObject().apply {
            put("role", "model")
            put("parts", JSONArray().put(JSONObject().put("text", "Understood. I will help with Bible questions about $currentRef.")))
        })

        // Last 10 messages for context
        history.takeLast(10).forEach { msg ->
            contents.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            })
        })

        // Current question + optional image
        val parts = JSONArray().put(JSONObject().put("text", question))
        if (image != null) {
            val stream = ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            parts.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64)
                })
            })
        }
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", parts)
        })

        val body = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            })
        }.toString()

        // Use streamGenerateContent for streaming
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=$key")
        val conn = withContext(Dispatchers.IO) {
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
                outputStream.use { it.write(body.toByteArray()) }
            }
        }

        // Read SSE stream
        withContext(Dispatchers.IO) {
            conn.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("data: ")) {
                        val json = line.removePrefix("data: ").trim()
                        if (json.isEmpty() || json == "[") continue
                        try {
                            val text = JSONObject(json)
                                .getJSONArray("candidates").getJSONObject(0)
                                .getJSONObject("content").getJSONArray("parts")
                                .getJSONObject(0).optString("text", "")
                            if (text.isNotEmpty()) emit(text)
                        } catch (e: Exception) { /* skip partial chunk */ }
                    }
                }
            }
        }
    }
}