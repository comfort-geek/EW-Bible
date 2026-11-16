package com.ewbible.app.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewbible.app.data.preferences.AppPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val isDark = state.darkMode ?: isSystemInDarkTheme()

    val prefs = remember { AppPreferences(context) }
    val savedScrollIndex by prefs.lastScrollIndex.collectAsState(initial = 0)
    val savedScrollOffset by prefs.lastScrollOffset.collectAsState(initial = 0)

    var settingsOpen by remember { mutableStateOf(false) }
    var showAllNotes by remember { mutableStateOf(false) }
    var dailyVerseDialog by remember { mutableStateOf(false) }
    var hasRestoredScroll by remember { mutableStateOf(false) }
    var lastScrollRestoreKey by remember { mutableStateOf(0) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()

    val gold = Color(0xFFC9A227)
    val background = if (isDark) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val panel = if (isDark) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val textColor = if (isDark) Color(0xFFF5F0E6) else Color(0xFF1E1E22)

    LaunchedEffect(state.currentBookId, state.selectedChapter) { viewModel.loadSummary() }
    LaunchedEffect(state.currentBookId, state.selectedChapter, state.selectedTab) {
        if (state.selectedTab == "Open EW") viewModel.loadCommentary()
    }
    LaunchedEffect(state.verses, savedScrollIndex, savedScrollOffset, state.scrollRestoreKey) {
        if (state.verses.isEmpty()) return@LaunchedEffect
        if (state.scrollRestoreKey != lastScrollRestoreKey) {
            lastScrollRestoreKey = state.scrollRestoreKey
            hasRestoredScroll = false
        }
        if (hasRestoredScroll) return@LaunchedEffect
        if (savedScrollIndex > 0 || savedScrollOffset > 0) {
            val target = savedScrollIndex.coerceIn(0, (state.verses.size + 2).coerceAtLeast(0))
            runCatching { listState.scrollToItem(target, savedScrollOffset) }
        }
        hasRestoredScroll = true
    }

    fun openBook(bookName: String, chapter: Int) {
        viewModel.openBook(bookName, chapter)
        hasRestoredScroll = true
        scope.launch {
            listState.scrollToItem(0)
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = panel) {
                Text("Books", color = gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                LazyColumn {
                    items(state.books.sortedBy { it.id }) { book ->
                        Text(
                            text = book.name,
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openBook(book.name, 1) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Bible", "Open EW", "Search").forEach { tab ->
                        val selected = state.selectedTab == tab
                        Text(
                            text = tab,
                            color = if (selected) gold else textColor,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.clickable { viewModel.selectTab(tab) }.padding(8.dp)
                        )
                    }
                }
                IconButton(onClick = { settingsOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = textColor)
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state.selectedTab) {
                    "Bible" -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(state.verses) { verse ->
                            Text(
                                text = "${verse.verse}. ${verse.text}",
                                color = textColor,
                                fontSize = state.fontSize.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectVerse(verse) }
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }
                    // FIXED: Was Text(state.commentary) - did not compile + showed raw HTML tags
                    // Now: WebView + uses commentaryHtml + handles error / loading
                    "Open EW" -> {
                        when {
                            state.commentaryError -> {
                                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("Failed to load commentary.", color = textColor, fontSize = state.fontSize.sp)
                                }
                            }
                            state.commentaryHtml.isBlank() -> {
                                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("Loading...", color = textColor, fontSize = state.fontSize.sp)
                                }
                            }
                            else -> {
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = false
                                        }
                                    },
                                    update = { webView ->
                                        webView.loadDataWithBaseURL(
                                            null,
                                            state.commentaryHtml,
                                            "text/html",
                                            "UTF-8",
                                            null
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    "Search" -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Search verses...", color = textColor)
                    }
                }
            }
        }
    }

    // Settings Dialog
    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            containerColor = background,
            title = { Text("Settings", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("A-", color = textColor, fontSize = 13.sp)
                        Slider(
                            value = state.fontSize,
                            onValueChange = { viewModel.setFontSize(it) },
                            valueRange = 12f..28f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text("A+", color = textColor, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { settingsOpen = false; dailyVerseDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = panel)
                    ) { Text("Daily Verse", color = textColor, fontSize = 14.sp) }

                    Button(
                        onClick = { settingsOpen = false; showAllNotes = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = panel)
                    ) { Text("Notes (${state.notes.size})", color = textColor, fontSize = 14.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = { settingsOpen = false }) { Text("Done", color = gold) }
            }
        )
    }

    if (dailyVerseDialog) {
        DailyVerseDialog(
            dailyVerseText = state.dailyVerse,
            gold = gold,
            textColor = textColor,
            fontSize = state.fontSize,
            onDismiss = { dailyVerseDialog = false }
        )
    }

    if (showAllNotes) {
        AlertDialog(
            onDismissRequest = { showAllNotes = false },
            containerColor = background,
            title = { Text("Notes (${state.notes.size})", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                if (state.notes.isEmpty()) {
                    Text("No notes yet.\nHold a verse in Bible tab to add a note.", color = textColor, fontSize = 14.sp)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                        items(state.notes, key = { "${it.bookId}-${it.chapter}-${it.verse}-${it.timestamp}" }) { note ->
                            val bookName = state.books.firstOrNull { it.id == note.bookId }?.name ?: "Book ${note.bookId}"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAllNotes = false
                                        openBook(bookName, note.chapter)
                                        viewModel.selectVerse(note.verse)
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "$bookName ${note.chapter}:${note.verse}",
                                    color = gold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = note.noteText, color = textColor, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllNotes = false }) { Text("Close", color = gold) }
            }
        )
    }
}