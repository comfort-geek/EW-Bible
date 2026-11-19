package com.ewbible.app.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun MessageBubble(
    text: String,
    isUser: Boolean,
    textColor: Color,
    gold: Color,
    panel: Color,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            text,
            color = if (isUser) Color.White else textColor,
            fontSize = 15.sp,
            modifier = Modifier
                .background(if (isUser) gold else panel, RoundedCornerShape(12.dp))
                .padding(10.dp)
                .widthIn(max = 300.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    viewModel: ChatViewModel,
    currentRef: String,
    background: Color,
    textColor: Color,
    gold: Color,
    panel: Color,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var selectedTranslation by remember { mutableStateOf("NKJV") }
    val translations = remember { listOf("NKJV", "KJV", "NIV", "ESV") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // FIX: OnHeaderDecodedListener is (decoder, info, source) -> 3 params, not 2.
    // Your version had { decoder, info -> } = "Argument type mismatch" at :84
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val bitmap = runCatching {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val max = 1024
                        val scale = maxOf(info.size.width / max.toFloat(), info.size.height / max.toFloat(), 1f)
                        if (scale > 1f) decoder.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                    }
                }.getOrNull()
                if (bitmap != null) withContext(Dispatchers.Main) { selectedImage = bitmap }
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.loadChats() }

    LaunchedEffect(state.currentChatId) {
        if (state.currentChatId == null) viewModel.startNewChat(currentRef)
    }

    LaunchedEffect(state.messages.size, state.isLoading) {
        if (state.messages.isEmpty() && !state.isLoading) return@LaunchedEffect
        val lastIndex = if (state.isLoading) state.messages.size else state.messages.size - 1
        if (lastIndex < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val lastVisible = visible.lastOrNull()?.index ?: 0
        val isNearBottom = lastVisible >= lastIndex - 2
        if (isNearBottom) {
            runCatching { listState.animateScrollToItem(lastIndex) }
        }
    }

    val dimTextColor = remember(textColor) { textColor.copy(alpha = 0.5f) }
    val gray = remember { Color.Gray }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Text("Chat History", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                items(state.chats, key = { it.id }) { chat ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                viewModel.openChat(chat.id)
                                showHistory = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(chat.title, fontWeight = FontWeight.Bold)
                            Text(chat.ref, fontSize = 12.sp, color = gray)
                        }
                        IconButton(onClick = { viewModel.deleteChat(chat.id) }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = textColor) }
            IconButton(onClick = { showHistory = true }) { Icon(Icons.Default.History, null, tint = textColor) }
            Text("Ask Gemini", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(currentRef, color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { viewModel.startNewChat(currentRef) }) { Icon(Icons.Default.Add, null, tint = gold) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            translations.forEach { t ->
                val isSelected = selectedTranslation == t
                Box(
                    modifier = Modifier
                        .background(if (isSelected) gold else panel, shape = RoundedCornerShape(16.dp))
                        .clickable { selectedTranslation = t }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(t, color = if (isSelected) Color.White else textColor, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.messages, key = { it.id ?: it.text.hashCode() }, contentType = { if (it.isUser) "user" else "ai" }) { msg ->
                MessageBubble(text = msg.text, isUser = msg.isUser, textColor = textColor, gold = gold, panel = panel)
            }
            if (state.isLoading) {
                item(key = "loading", contentType = "loading") {
                    Text(
                        if (state.streamingText.isNotEmpty()) state.streamingText else "Gemini is thinking...",
                        color = textColor, fontSize = 15.sp,
                        modifier = Modifier.background(panel, RoundedCornerShape(12.dp)).padding(10.dp)
                    )
                }
            }
        }

        if (selectedImage != null) {
            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Image attached ✓", color = gold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectedImage = null }) { Text("Remove") }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { picker.launch("image/*") }) { Icon(Icons.Default.Image, null, tint = gold) }
            OutlinedTextField(value = input, onValueChange = { input = it }, placeholder = { Text("Ask anything...") }, modifier = Modifier.weight(1f), maxLines = 4)
            IconButton(onClick = {
                val q = input.trim()
                if (q.isBlank() || state.isLoading) return@IconButton
                viewModel.send(q, currentRef, selectedImage, selectedTranslation)
                input = ""
                selectedImage = null
            }) { Icon(Icons.Default.Send, null, tint = gold) }
        }
        Text("Daily: ${state.dailyCount}/50 • No login needed • Verses/notes never sent", color = dimTextColor, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
    }
}