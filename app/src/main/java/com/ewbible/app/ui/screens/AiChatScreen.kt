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
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val source = ImageDecoder.createSource(context.contentResolver, it)
            selectedImage = ImageDecoder.decodeBitmap(source)
        }
    }

    LaunchedEffect(Unit) { viewModel.loadChats() }

    LaunchedEffect(state.currentChatId) {
        if (state.currentChatId == null) viewModel.startNewChat(currentRef)
    }

    LaunchedEffect(state.messages.size, state.streamingText, state.isLoading) {
        if (state.messages.isNotEmpty() || state.isLoading) {
            try {
                // FIXED: Don't scroll past last item when not loading
                val lastIndex = if (state.isLoading) state.messages.size else state.messages.size - 1
                if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
            } catch (e: Exception) { /* ignore scroll race */ }
        }
    }

    // HISTORY DRAWER - FIXED: tap to open
    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Text("Chat History", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                items(state.chats) { chat ->
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
                            Text(chat.ref, fontSize = 12.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { viewModel.deleteChat(chat.id) }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
        // TOP BAR
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = textColor) }
            IconButton(onClick = { showHistory = true }) { Icon(Icons.Default.History, null, tint = textColor) }
            Text("Ask Gemini", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(currentRef, color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { viewModel.startNewChat(currentRef) }) { Icon(Icons.Default.Add, null, tint = gold) }
        }

        // MESSAGES
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.messages) { msg ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(msg.text, color = if (msg.isUser) Color.White else textColor, fontSize = 15.sp,
                        modifier = Modifier.background(if (msg.isUser) gold else panel, RoundedCornerShape(12.dp)).padding(10.dp).widthIn(max = 300.dp))
                }
            }
            if (state.isLoading) {
                item {
                    Text(
                        if (state.streamingText.isNotEmpty()) state.streamingText else "Gemini is thinking...",
                        color = textColor, fontSize = 15.sp,
                        modifier = Modifier.background(panel, RoundedCornerShape(12.dp)).padding(10.dp)
                    )
                }
            }
        }

        // IMAGE PREVIEW
        if (selectedImage != null) {
            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Image attached ✓", color = gold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectedImage = null }) { Text("Remove") }
            }
        }

        // INPUT
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { picker.launch("image/*") }) { Icon(Icons.Default.Image, null, tint = gold) }
            OutlinedTextField(value = input, onValueChange = { input = it }, placeholder = { Text("Ask anything...") }, modifier = Modifier.weight(1f), maxLines = 4)
            IconButton(onClick = {
                val q = input.trim()
                if (q.isBlank() || state.isLoading) return@IconButton
                viewModel.send(q, currentRef, selectedImage)
                input = ""
                selectedImage = null
            }) { Icon(Icons.Default.Send, null, tint = gold) }
        }
        Text("Daily: ${state.dailyCount}/50 • No login needed • Verses/notes never sent", color = textColor.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
    }
}