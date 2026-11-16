package com.ewbible.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
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
    var searchQuery by remember { mutableStateOf("") }
    var expandedBook by remember { mutableStateOf<String?>(null) }
    var showHighlightPicker by remember { mutableStateOf(false) }

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
        expandedBook = null
        hasRestoredScroll = true
        scope.launch {
            listState.scrollToItem(0)
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            BookDrawer(
                otBookEntities = state.books.filter { it.id <= 39 }.sortedBy { it.id },
                ntBookEntities = state.books.filter { it.id >= 40 }.sortedBy { it.id },
                chapterCounts = state.chapterCounts,
                selectedBook = state.currentBook,
                selectedChapter = state.selectedChapter,
                expandedBook = expandedBook,
                background = background,
                textColor = textColor,
                panel = panel,
                gold = gold,
                secondaryText = textColor.copy(alpha = 0.6f),
                onOpenBook = { book, chapter -> openBook(book, chapter) },
                onSelectBook = { book -> viewModel.selectBook(book) },
                onToggleBook = { book ->
                    expandedBook = if (expandedBook == book) null else book
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
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

            if (state.selectedTab == "Bible" || state.selectedTab == "Open EW") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${state.currentBook} ${state.selectedChapter}",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Row {
                        Button(
                            onClick = { viewModel.previousChapter() },
                            enabled = state.selectedChapter > 1,
                            colors = ButtonDefaults.buttonColors(containerColor = panel)
                        ) { Text("<", color = textColor) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.nextChapter() },
                            colors = ButtonDefaults.buttonColors(containerColor = panel)
                        ) { Text(">", color = textColor) }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state.selectedTab) {
                    "Bible" -> BibleContent(
                        selectedBook = state.currentBook,
                        selectedChapter = state.selectedChapter,
                        summary = state.summary,
                        versesList = state.verses,
                        currentHighlightMap = state.highlights,
                        fontSize = state.fontSize,
                        gold = gold,
                        panel = panel,
                        textColor = textColor,
                        listState = listState,
                        onVerseClick = { viewModel.selectVerse(it) },
                        onVerseLongClick = { viewModel.selectVerse(it) }
                    )
                    "Open EW" -> OpenEWContent(
                        commentaryHtml = state.commentaryHtml,
                        fontSize = state.fontSize,
                        textColor = textColor,
                        secondaryText = textColor.copy(alpha = 0.6f),
                        hasError = state.commentaryError,
                        bookName = state.currentBook,
                        chapter = state.selectedChapter,
                        onVerseClick = { book, chapter, verse ->
                            openBook(book, chapter)
                            viewModel.selectTab("Bible")
                            viewModel.selectVerse(verse)
                        }
                    )
                    "Search" -> SearchContent(
                        searchQuery = searchQuery,
                        searchFilter = state.searchFilter,
                        verseResults = state.searchResults,
                        textColor = textColor,
                        gold = gold,
                        fontSize = state.fontSize,
                        onQueryChange = {
                            searchQuery = it
                            viewModel.searchVerses(it)
                        },
                        onFilterChange = { viewModel.setSearchFilter(it) },
                        onOpenVerse = { book, chapter, verse ->
                            openBook(book, chapter)
                            viewModel.selectTab("Bible")
                            viewModel.selectVerse(verse)
                        }
                    )
                }
            }

            if (state.selectedVerse != null && state.selectedTab == "Bible") {
                Row(
                    modifier = Modifier.fillMaxWidth().background(panel).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { showHighlightPicker = true }) { Text("Highlight", color = gold) }
                    TextButton(onClick = { viewModel.showNoteDialog() }) { Text("Note", color = gold) }
                    TextButton(onClick = { viewModel.clearSelection() }) { Text("Done", color = textColor) }
                }
            }
        }
    }

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
            background = background,
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

    if (state.showNoteDialog) {
        var noteText by remember(state.selectedVerse) { mutableStateOf(state.currentNoteText) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissNoteDialog() },
            containerColor = background,
            title = { Text("Note", color = textColor) },
            text = {
                NotesCard(
                    selectedBook = state.currentBook,
                    selectedChapter = state.selectedChapter,
                    selectedVerse = state.selectedVerse,
                    noteText = noteText,
                    panel = panel,
                    gold = gold,
                    textColor = textColor,
                    fontSize = state.fontSize,
                    onNoteTextChange = { noteText = it },
                    onSaveNote = {
                        viewModel.saveNote(noteText)
                        viewModel.dismissNoteDialog()
                    }
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissNoteDialog() }) { Text("Cancel", color = textColor) }
            }
        )
    }

    if (showHighlightPicker && state.selectedVerse != null) {
        AlertDialog(
            onDismissRequest = { showHighlightPicker = false },
            containerColor = background,
            title = { Text("Highlight", color = textColor) },
            text = {
                HighlightCard(
                    selectedVerse = state.selectedVerse,
                    panel = panel,
                    gold = gold,
                    textColor = textColor,
                    onHighlightPicked = { color ->
                        viewModel.saveHighlight(color) // FIXED: was setHighlight
                    },
                    onRemoveHighlight = { viewModel.removeHighlight() },
                    onDismiss = { showHighlightPicker = false }
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showHighlightPicker = false }) { Text("Cancel", color = textColor) }
            }
        )
    }
}