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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text,
            color = if (isUser) Color.White else textColor,
            fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(if (isUser) gold else panel, RoundedCornerShape(12.dp))
                .padding(10.dp)
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

    LaunchedEffect(Unit) {
        viewModel.loadChats()
        delay(350)
        val s = viewModel.state.value
        if (s.currentChatId == null) {
            if (s.chats.isNotEmpty()) {
                viewModel.openChat(s.chats.first().id)
            } else {
                viewModel.startNewChat(currentRef)
            }
        }
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

    val gray = remember { Color.Gray }

    val titleText = if (state.aiProvider == "Groq") "Ask Groq" else "Ask Gemini"
    val thinkingText = if (state.aiProvider == "Groq") "Groq is connecting..." else "Gemini is thinking..."

    if (showHistory) {
        // FIXED: No navigationBarsPadding() here.
        // ModalBottomSheet already handles system bars itself.
        // Adding it again pushed the sheet up / cut off the last chat.
        ModalBottomSheet(
            onDismissRequest = { showHistory = false }
        ) {
            Text("Chat History", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxHeight(0.6f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
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
                            Text(chat.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(chat.ref, fontSize = 12.sp, color = gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { viewModel.deleteChat(chat.id) }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
    ) {
        // --- Top bar ---
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, null, tint = textColor) }
            IconButton(onClick = { showHistory = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.History, null, tint = textColor) }
            Text(
                titleText,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                currentRef,
                color = gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { viewModel.startNewChat(currentRef) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, null, tint = gold) }
        }

        // --- Translation chips ---
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

        // --- Messages ---
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // FIXED: bottom 8.dp -> 16.dp, last bubble never touches the input bar
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
        ) {
            items(state.messages, key = { it.id }, contentType = { if (it.isUser) "user" else "ai" }) { msg ->
                MessageBubble(text = msg.text, isUser = msg.isUser, textColor = textColor, gold = gold, panel = panel)
            }
            if (state.isLoading) {
                item(key = "loading", contentType = "loading") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text(
                            if (state.streamingText.isNotEmpty()) state.streamingText else thinkingText,
                            color = textColor, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth(0.85f).background(panel, RoundedCornerShape(12.dp)).padding(10.dp)
                        )
                    }
                }
            }
        }

        // --- Image attached banner ---
        if (selectedImage != null) {
            Row(
                modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Image attached ✓", color = gold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectedImage = null }) { Text("Remove") }
            }
        }

        // --- Input ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { picker.launch("image/*") }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Image, null, tint = gold) }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask anything...", maxLines = 1) },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            IconButton(
                onClick = {
                    val q = input.trim()
                    if (q.isBlank() || state.isLoading) return@IconButton
                    viewModel.send(q, currentRef, selectedImage, selectedTranslation)
                    input = ""
                    selectedImage = null
                },
                modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.Send, null, tint = gold) }
        }
    }
}