package com.ewbible.app.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val isDark = state.darkMode ?: isSystemInDarkTheme()
    val prefs = remember { AppPreferences(context) }
    val googleAuth = remember { GoogleAuth(context) }
    val savedScrollIndex by prefs.lastScrollIndex.collectAsState(initial = 0)
    val savedScrollOffset by prefs.lastScrollOffset.collectAsState(initial = 0)
    var settingsOpen by remember { mutableStateOf(false) }
    var showAllNotes by remember { mutableStateOf(false) }
    var dailyVerseDialog by remember { mutableStateOf(false) }
    var hasRestoredScroll by remember { mutableStateOf(false) }
    var lastBookChapter by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pendingScrollKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedBook by remember { mutableStateOf<Int?>(null) }
    var showHighlightPicker by remember { mutableStateOf(false) }
    var showBibleCom by remember { mutableStateOf(false) }
    var showLinkedVerseSheet by remember { mutableStateOf(false) }
    var linkedVerseBook by remember { mutableStateOf("") }
    var linkedVerseChapter by remember { mutableStateOf(1) }
    var linkedVerseNum by remember { mutableStateOf(1) }
    var linkedVerseEnd by remember { mutableStateOf(1) }
    var initialLoadDone by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    val gold = Color(0xFFC9A227)
    val background = if (isDark) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val panel = if (isDark) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val textColor = if (isDark) Color(0xFFF5F0E6) else Color(0xFF1E1E22)
    val currentBookId = state.currentBookId
    val currentBookName = BookCatalog.name(currentBookId)

    LaunchedEffect(state.isReadingRestored) {
        if (state.isReadingRestored) initialLoadDone = true
    }

    // FIX 1: Chapter changed -> remember we need to scroll (don't scroll yet, verses not loaded)
    LaunchedEffect(state.currentBookId, state.selectedChapter) {
        val key = state.currentBookId to state.selectedChapter
        if (lastBookChapter != null && lastBookChapter != key) {
            pendingScrollKey = key
        } else if (lastBookChapter == null) {
            lastBookChapter = key
        }
    }

    // FIX 2: Verses arrived -> now do the scroll
    LaunchedEffect(state.verses) {
        if (state.verses.isEmpty()) return@LaunchedEffect
        val key = state.currentBookId to state.selectedChapter
        if (!hasRestoredScroll && (savedScrollIndex > 0 || savedScrollOffset > 0)) {
            val target = savedScrollIndex.coerceIn(0, (state.verses.size + 2).coerceAtLeast(0))
            runCatching { listState.scrollToItem(target, savedScrollOffset) }
            hasRestoredScroll = true
            lastBookChapter = key
            pendingScrollKey = null
        } else if (pendingScrollKey == key) {
            runCatching { listState.scrollToItem(0) }
            lastBookChapter = key
            pendingScrollKey = null
        }
    }

    fun openBook(bookId: Int, chapter: Int) {
        hasRestoredScroll = true
        pendingScrollKey = null
        expandedBook = null
        viewModel.openBook(bookId, chapter)
        scope.launch {
            runCatching { listState.scrollToItem(0) }
            drawerState.close()
        }
    }
    fun openBook(bookName: String, chapter: Int) { openBook(BookCatalog.id(bookName), chapter) }

    if (!state.isReadingRestored && !initialLoadDone) {
        Box(modifier = Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
            Text("Loading...", color = textColor, fontSize = 18.sp)
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = state.selectedTab == "Bible",
        drawerContent = {
            BookDrawer(
                otBookEntities = state.books.filter { it.id <= 39 }.sortedBy { it.id },
                ntBookEntities = state.books.filter { it.id >= 40 }.sortedBy { it.id },
                chapterCounts = state.chapterCounts,
                selectedBookId = currentBookId,
                selectedChapter = state.selectedChapter,
                expandedBookId = expandedBook,
                background = background, textColor = textColor, panel = panel, gold = gold,
                secondaryText = textColor.copy(alpha = 0.6f),
                onOpenBook = { bookId, chapter -> openBook(bookId, chapter) },
                onSelectBook = { bookId -> viewModel.selectBook(bookId) },
                onToggleBook = { bookId -> expandedBook = if (expandedBook == bookId) null else bookId }
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Bible", "Open EW", "Search").forEach { tab ->
                        val selected = state.selectedTab == tab
                        Text(text = tab, color = if (selected) gold else textColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable { viewModel.selectTab(tab) }.padding(8.dp))
                    }
                }
                IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = textColor) }
            }
            if (state.selectedTab == "Bible") {
                Text(text = "$currentBookName ${state.selectedChapter}", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            // FIXED: KEEP ALL TABS ALIVE - no destroy on switch, no reload + block touches on hidden tabs
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Bible - visible when selected
                Box(modifier = Modifier.fillMaxSize()
                    .then(if (state.selectedTab == "Bible") Modifier else Modifier.alpha(0f))
                    .then(if (state.selectedTab == "Bible") Modifier else Modifier.size(0.dp))
                    .then(if (state.selectedTab == "Bible") Modifier else Modifier.clickable(enabled = false) {})
                ) {
                    BibleContent(
                        selectedBookId = currentBookId, selectedChapter = state.selectedChapter, summary = state.summary,
                        versesList = state.verses, currentHighlightMap = state.highlights, fontSize = state.fontSize,
                        gold = gold, panel = panel, textColor = textColor, listState = listState,
                        onVerseClick = { viewModel.selectVerse(it) }, onVerseLongClick = { viewModel.selectVerse(it) },
                        onNextChapter = { viewModel.nextChapter() }, onPreviousChapter = { viewModel.previousChapter() },
                        summaryLoading = state.summaryLoading
                    )
                }
                // Open EW - visible when selected
                Box(modifier = Modifier.fillMaxSize()
                    .then(if (state.selectedTab == "Open EW") Modifier else Modifier.alpha(0f))
                    .then(if (state.selectedTab == "Open EW") Modifier else Modifier.size(0.dp))
                    .then(if (state.selectedTab == "Open EW") Modifier else Modifier.clickable(enabled = false) {})
                ) {
                    OpenEWContent(
                        commentaryHtml = state.commentaryHtml, fontSize = state.fontSize,
                        textColor = textColor, hasError = state.commentaryError,
                        isLoading = state.commentaryHtml.isBlank() && !state.commentaryError,
                        bookName = currentBookName, chapter = state.selectedChapter,
                        onVerseClick = { book, chapter, verse, endVerse ->
                            linkedVerseBook = book; linkedVerseChapter = chapter
                            linkedVerseNum = verse; linkedVerseEnd = endVerse
                            viewModel.loadLinkedVerse(book, chapter, verse, endVerse)
                            showLinkedVerseSheet = true
                        },
                        onNextChapter = { viewModel.nextChapter() }, onPreviousChapter = { viewModel.previousChapter() },
                        onRetry = { viewModel.loadCommentary() }
                    )
                }
                // Search - visible when selected
                Box(modifier = Modifier.fillMaxSize()
                    .then(if (state.selectedTab == "Search") Modifier else Modifier.alpha(0f))
                    .then(if (state.selectedTab == "Search") Modifier else Modifier.size(0.dp))
                    .then(if (state.selectedTab == "Search") Modifier else Modifier.clickable(enabled = false) {})
                ) {
                    SearchContent(
                        searchQuery = searchQuery, searchFilter = state.searchFilter, verseResults = state.searchResults,
                        textColor = textColor, gold = gold, fontSize = state.fontSize,
                        onQueryChange = { searchQuery = it; viewModel.searchVerses(it) },
                        onFilterChange = { viewModel.setSearchFilter(it) },
                        onOpenVerse = { book, chapter, verse -> openBook(book, chapter); viewModel.selectTab("Bible"); viewModel.selectVerse(verse) }
                    )
                }
            }
            if (state.selectedVerse != null && state.selectedTab == "Bible") {
                Row(modifier = Modifier.fillMaxWidth().background(panel).padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { showHighlightPicker = true }) { Text("Highlight", color = gold) }
                    TextButton(onClick = { viewModel.showNoteDialog() }) { Text("Note", color = gold) }
                    TextButton(onClick = { viewModel.clearSelection() }) { Text("Done", color = textColor) }
                }
            }
        }
    }

    if (showLinkedVerseSheet) {
        ModalBottomSheet(onDismissRequest = { showLinkedVerseSheet = false }, containerColor = background, contentColor = textColor) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.linkedVerseRef.ifBlank {
                            if (linkedVerseEnd > linkedVerseNum) "$linkedVerseBook $linkedVerseChapter:$linkedVerseNum-$linkedVerseEnd"
                            else "$linkedVerseBook $linkedVerseChapter:$linkedVerseNum"
                        },
                        color = gold, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    IconButton(onClick = { showLinkedVerseSheet = false }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor) }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    Text(text = state.linkedVerseText.ifBlank { "Loading..." }, color = textColor, fontSize = state.fontSize.sp, lineHeight = (state.fontSize + 8).sp)
                }
                Button(
                    onClick = { showLinkedVerseSheet = false; openBook(linkedVerseBook, linkedVerseChapter); viewModel.selectTab("Bible"); viewModel.selectVerse(linkedVerseNum) },
                    colors = ButtonDefaults.buttonColors(containerColor = gold),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text("Open in Bible", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    SettingsPanel(
        visible = settingsOpen, isLoggedIn = state.isLoggedIn, userName = state.userName, userEmail = state.userEmail,
        fontSize = state.fontSize, darkMode = isDark, notesCount = state.notes.size,
        background = background, panel = panel, textColor = textColor, gold = gold,
        onClose = { settingsOpen = false }, onFontSizeChange = { viewModel.setFontSize(it) }, onDarkModeChange = { viewModel.setDarkMode(it) },
        onDailyVerse = { settingsOpen = false; viewModel.loadDailyVerse(); dailyVerseDialog = true },
        onShowNotes = { settingsOpen = false; showAllNotes = true },
        onGoogleSignIn = {
            scope.launch {
                val result = googleAuth.signInWithGoogle(activity)
                result.onSuccess { nameAndEmail ->
                    val parts = nameAndEmail.split("|", limit = 2)
                    viewModel.onUserSignedIn(parts.getOrNull(0) ?: "User", parts.getOrNull(1) ?: "")
                    settingsOpen = false
                }.onFailure { e ->
                    if (e !is GoogleAuth.UserCancelledException) {
                        Toast.makeText(context, e.message ?: "Sign-in failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        onSignOut = { googleAuth.signOut(); viewModel.onUserSignedOut(); settingsOpen = false },
        onOpenResource = { settingsOpen = false; showBibleCom = true }
    )

    if (dailyVerseDialog) {
        DailyVerseDialog(dailyVerseText = state.dailyVerse, gold = gold, textColor = textColor, background = background, fontSize = state.fontSize, onDismiss = { dailyVerseDialog = false })
    }

    if (showAllNotes) {
        AlertDialog(
            onDismissRequest = { showAllNotes = false }, containerColor = background,
            title = { Text("Notes (${state.notes.size})", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                if (state.notes.isEmpty()) {
                    Text("No notes yet.\nHold a verse in Bible tab to add a note.", color = textColor, fontSize = 14.sp)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                        items(state.notes, key = { "${it.bookId}-${it.chapter}-${it.verse}-${it.timestamp}" }) { note ->
                            val bookName = BookCatalog.name(note.bookId)
                            Column(modifier = Modifier.fillMaxWidth().clickable { showAllNotes = false; openBook(note.bookId, note.chapter); viewModel.selectVerse(note.verse) }.padding(vertical = 8.dp)) {
                                Text(text = "$bookName ${note.chapter}:${note.verse}", color = gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = note.noteText, color = textColor, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAllNotes = false }) { Text("Close", color = gold) } }
        )
    }

    if (state.showNoteDialog) {
        var noteText by remember(state.selectedVerse) { mutableStateOf(state.currentNoteText) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissNoteDialog() }, containerColor = background,
            title = { Text("Note", color = textColor) },
            text = {
                NotesCard(selectedBook = currentBookName, selectedChapter = state.selectedChapter, selectedVerse = state.selectedVerse, noteText = noteText, panel = panel, gold = gold, textColor = textColor, onNoteTextChange = { noteText = it }, onSaveNote = { viewModel.saveNote(noteText); viewModel.dismissNoteDialog() })
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { viewModel.dismissNoteDialog() }) { Text("Cancel", color = textColor) } }
        )
    }

    if (showHighlightPicker && state.selectedVerse != null) {
        AlertDialog(
            onDismissRequest = { showHighlightPicker = false }, containerColor = background,
            title = { Text("Highlight", color = textColor) },
            text = {
                HighlightCard(selectedVerse = state.selectedVerse, panel = panel, gold = gold, textColor = textColor,
                    onHighlightPicked = { color -> viewModel.saveHighlight(color) },
                    onRemoveHighlight = { viewModel.removeHighlight() },
                    onDismiss = { showHighlightPicker = false })
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showHighlightPicker = false }) { Text("Cancel", color = textColor) } }
        )
    }

    if (showBibleCom) {
        WebViewScreen(url = "https://www.bible.com", title = "Bible.com", background = background, textColor = textColor, gold = gold, onClose = { showBibleCom = false })
    }
}