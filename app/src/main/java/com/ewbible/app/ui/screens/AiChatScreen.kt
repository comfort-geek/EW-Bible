package com.ewbible.app.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun cleanSourceNumbering(text: String): String {
    return text
        .replace(Regex("(?m)^Source\\s*\\d+\\s*[-:.]\\s*"), "")
        .replace(Regex("(?m)^\\d+\\.\\s*Source\\s*[-:.]\\s*"), "")
}

@Composable
private fun LinkText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val cleanText = remember(text) { cleanSourceNumbering(text) }
    val annotated = remember(cleanText, color) {
        buildAnnotatedString {
            val regex = Regex("""\*\*(.+?)\*\*|\[([^\]]+)\]\((https?://[^)]+)\)|(https?://[^\s)]+)""")
            var lastIndex = 0
            regex.findAll(cleanText).forEach { match ->
                if (match.range.first > lastIndex) {
                    append(cleanText.substring(lastIndex, match.range.first))
                }
                val boldText = match.groups[1]?.value
                val linkTitle = match.groups[2]?.value
                val linkUrl = match.groups[3]?.value
                val rawUrl = match.groups[4]?.value

                when {
                    boldText != null -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(boldText)
                        }
                    }
                    linkTitle != null && linkUrl != null -> {
                        val start = length
                        append(linkTitle)
                        addStyle(
                            style = SpanStyle(
                                color = Color(0xFF1A73E8),
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Bold
                            ),
                            start = start,
                            end = length
                        )
                        addStringAnnotation("URL", linkUrl, start, length)
                    }
                    rawUrl != null -> {
                        var url: String = rawUrl
                        while (url.endsWith(".") || url.endsWith(",") || url.endsWith(")") || url.endsWith("]")) {
                            url = url.dropLast(1)
                        }
                        val start = length
                        append(url)
                        addStyle(
                            style = SpanStyle(color = Color(0xFF1A73E8), textDecoration = TextDecoration.Underline),
                            start = start,
                            end = length
                        )
                        addStringAnnotation("URL", url, start, length)
                    }
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < cleanText.length) {
                append(cleanText.substring(lastIndex))
            }
        }
    }

    ClickableText(
        text = annotated,
        style = LocalTextStyle.current.copy(color = color, fontSize = 15.sp),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let { uriHandler.openUri(it.item) }
        }
    )
}

@Composable
private fun MessageBubble(
    text: String,
    isUser: Boolean,
    textColor: Color,
    gold: Color,
    panel: Color,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val cleanTextForCopy = remember(text) { cleanSourceNumbering(text) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(if (isUser) gold else panel, RoundedCornerShape(12.dp))
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = 10.dp,
                    bottom = if (isUser) 10.dp else 28.dp
                )
        ) {
            LinkText(
                text = text,
                color = if (isUser) Color.White else textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(text) {
                        detectTapGestures(
                            onLongPress = {
                                clipboard.setText(AnnotatedString(cleanTextForCopy))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
            )

            if (!isUser) {
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(cleanTextForCopy))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
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

    // FIX 1: DON'T reload on every open. Only init once.
    // Old code: loadChats() + delay(350) + openChat(first) every time = flash empty -> first chat
    // New: if we already have a chat open, do NOTHING. Keep what user was reading.
    LaunchedEffect(Unit) {
        val s = viewModel.state.value
        if (s.currentChatId != null) return@LaunchedEffect // already open, keep it
        if (s.chats.isNotEmpty()) return@LaunchedEffect // chats already loaded, wait for user
        viewModel.refreshModels(context)
        viewModel.loadChats()
        // No delay(350) - that was the visible blank flash
        // Do NOT auto-open first chat. Let ViewModel decide, or user picks from History.
        // If you MUST auto-open, do it only if chats were empty at start:
        // viewModel.startNewChat(currentRef) is now handled by ViewModel after load
    }

    // FIX 2: Scroll ONLY when message count changes, NOT on every streaming char.
    // Old code watched streamingText.length -> scrollToItem 20x/sec = flicker
    LaunchedEffect(state.messages.size, state.isLoading) {
        if (state.messages.isEmpty() && !state.isLoading) return@LaunchedEffect
        val lastIndex = if (state.isLoading) state.messages.size else state.messages.size - 1
        if (lastIndex < 0) return@LaunchedEffect
        // Small delay lets LazyColumn settle, prevents 1-frame jump
        delay(50)
        val visible = listState.layoutInfo.visibleItemsInfo
        val lastVisible = visible.lastOrNull()?.index ?: 0
        val isNearBottom = lastVisible >= lastIndex - 2
        if (isNearBottom) {
            runCatching { listState.scrollToItem(lastIndex) }
        }
    }

    // FIX 3: Gentle streaming scroll - max 1 scroll per 300ms, only if user is at bottom
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) return@LaunchedEffect
        while (state.isLoading) {
            delay(300)
            val lastIndex = state.messages.size // loading item index
            val visible = listState.layoutInfo.visibleItemsInfo
            val lastVisible = visible.lastOrNull()?.index ?: 0
            if (lastVisible >= lastIndex - 2) {
                runCatching { listState.scrollToItem(lastIndex) }
            }
        }
    }

    val gray = remember { Color.Gray }
    val titleText = "Ask Anything"
    val thinkingText = "Thinking..."

    if (showHistory) {
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

    Scaffold(
        containerColor = background,
        topBar = {
            Column(
                modifier = Modifier
                    .background(background)
                    .statusBarsPadding()
            ) {
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
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(background)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
            ) {
                items(state.messages, key = { it.id }, contentType = { if (it.isUser) "user" else "ai" }) { msg ->
                    MessageBubble(text = msg.text, isUser = msg.isUser, textColor = textColor, gold = gold, panel = panel)
                }
                if (state.isLoading) {
                    item(key = "loading", contentType = "loading") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            LinkText(
                                text = if (state.streamingText.isNotEmpty()) state.streamingText else thinkingText,
                                color = textColor,
                                modifier = Modifier.fillMaxWidth(0.85f).background(panel, RoundedCornerShape(12.dp)).padding(10.dp)
                            )
                        }
                    }
                }
            }

            if (selectedImage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Image attached ✓", color = gold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedImage = null }) { Text("Remove") }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { picker.launch("image/*") }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Image, null, tint = gold) }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(if (state.isLoading) "Type next question..." else "Ask anything...", maxLines = 1) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    enabled = true
                )
                IconButton(
                    onClick = {
                        val q = input.trim()
                        if (q.isBlank()) return@IconButton
                        viewModel.send(q, currentRef, selectedImage, selectedTranslation)
                        input = ""
                        selectedImage = null
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Default.Send, null, tint = if (input.isNotBlank()) gold else gray) }
            }
        }
    }
}