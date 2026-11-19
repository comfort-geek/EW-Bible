package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatMsg(val text: String, val isUser: Boolean)

@Composable
fun AiChatScreen(
    currentRef: String,
    background: Color,
    textColor: Color,
    gold: Color,
    panel: Color,
    onClose: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf(ChatMsg("Hi! Ask me anything about $currentRef, or any Bible question.", false))) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor) }
            Text("Ask Gemini", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(currentRef, color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        text = msg.text,
                        color = if (msg.isUser) Color.White else textColor,
                        fontSize = 15.sp,
                        modifier = Modifier.background(if (msg.isUser) gold else panel, RoundedCornerShape(12.dp)).padding(10.dp).widthIn(max = 300.dp)
                    )
                }
            }
            if (isLoading) {
                item { Text("Gemini is thinking...", color = textColor.copy(alpha = 0.6f), fontSize = 13.sp) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask anything...") },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            IconButton(
                onClick = {
                    val q = input.trim()
                    if (q.isBlank() || isLoading) return@IconButton
                    input = ""
                    messages = messages + ChatMsg(q, true)
                    isLoading = true
                    scope.launch {
                        val answer = askGemini(q, currentRef)
                        messages = messages + ChatMsg(answer, false)
                        isLoading = false
                    }
                }
            ) { Icon(Icons.Default.Send, contentDescription = "Send", tint = gold) }
        }
    }
}

private suspend fun askGemini(question: String, currentRef: String): String = withContext(Dispatchers.IO) {
    try {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) return@withContext "No API key found. Add GEMINI_API_KEY to local.properties and rebuild."
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key")
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text",
                    "You are a helpful Bible study assistant for the EW Bible app. Answer clearly, simply, from the Bible. User is reading $currentRef. Question: $question"
                )))
            }))
        }.toString()

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 30000
            readTimeout = 30000
            outputStream.use { it.write(body.toByteArray()) }
        }
        val response = conn.inputStream.bufferedReader().readText()
        JSONObject(response)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
            .getJSONObject(0).getString("text")
    } catch (e: Exception) {
        "Sorry, I couldn't answer that. Check internet / Try later. (${e.message})"
    }
}