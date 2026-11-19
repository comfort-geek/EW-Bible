package com.ewbible.app.data.database

import com.ewbible.app.BuildConfig
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GeminiRepository {

    fun askStreaming(
        question: String,
        currentRef: String,
        history: List<MessageEntity>,
        image: Bitmap?,
        bibleContext: String = "",
        translation: String = "NKJV"
    ): Flow<String> = flow {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) {
            emit("No API key found.")
            return@flow
        }

        val contents = JSONArray()

        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text",
                """
                You are a helpful Bible study assistant for EW Bible app.
                User is currently reading: $currentRef
                User's preferred translation: $translation

                RULES:
                1. Use the NKJV text below from the user's app as your anchor. Do not invent verse numbers.
                2. Answer in $translation. If $translation is not NKJV, give the $translation wording, but start with the NKJV from the app so user can compare.
                3. Answer clearly and simply. If unsure, say so.

                BIBLE CONTEXT (NKJV, from user's app):
                $bibleContext
                """.trimIndent()
            )))
        })
        contents.put(JSONObject().apply {
            put("role", "model")
            put("parts", JSONArray().put(JSONObject().put("text", "Understood. I will help with Bible questions about $currentRef in $translation.")))
        })

        history.takeLast(10).forEach { msg ->
            contents.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            })
        }

        val parts = JSONArray().put(JSONObject().put("text", question))
        if (image != null) {
            val scaled = if (image.width > 1024 || image.height > 1024) {
                val scale = 1024f / maxOf(image.width, image.height)
                Bitmap.createScaledBitmap(
                    image,
                    (image.width * scale).toInt(),
                    (image.height * scale).toInt(),
                    true
                )
            } else image
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            if (scaled != image) scaled.recycle()
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

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=$key")
        // FIXED: No withContext() around emit anymore.
        // flowOn(Dispatchers.IO) below already runs ALL of this on IO.
        // emit() inside withContext() = "Flow invariant is violated" crash.
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 60000
            outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }

        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val err = conn.errorStream?.bufferedReader()?.readText()?.take(500) ?: "HTTP $code"
                if (code == 429) emit("Sorry, too many requests. Please try again in a minute.")
                else emit("Sorry, Gemini returned an error ($code). Please try again.")
                return@flow
            }

            conn.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("data: ")) {
                        val json = line.removePrefix("data: ").trim()
                        if (json.isEmpty() || json == "[") continue
                        try {
                            val text = JSONObject(json)
                                .optJSONArray("candidates")?.optJSONObject(0)
                                ?.optJSONObject("content")?.optJSONArray("parts")
                                ?.optJSONObject(0)?.optString("text", "") ?: ""
                            if (text.isNotEmpty()) emit(text)
                        } catch (e: Exception) { /* skip partial chunk */ }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}