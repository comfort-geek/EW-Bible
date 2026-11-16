package com.ewbible.app.ui.screens

import android.app.Activity
import android.widget.Toast
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    var searchQuery by remember { mutableStateOf("") }
    var expandedBook by remember { mutableStateOf<String?>(null) }
    var showHighlightPicker by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()

    val gold = Color(0xFFC9A227)
    val background = if (isDark) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val panel = if (isDark) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val textColor = if (isDark) Color(0xFFF5F0E6) else Color(0xFF1E1E22)

    // FIXED: DELETED LaunchedEffect loadSummary here.
    // ViewModel.init already loads summary on book/chapter change.
    // Having it here too caused double load + flicker.
    LaunchedEffect(state.verses) {
        if (state.verses.isEmpty()) return@LaunchedEffect
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

    val swipeModifier = Modifier.pointerInput(state.currentBook, state.selectedChapter, state.selectedTab) {
        awaitPointerEventScope {
            var downX = 0f
            var downY = 0f
            var tracking = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                when (event.type) {
                    PointerEventType.Press -> {
                        downX = change.position.x
                        downY = change.position.y
                        tracking = true
                    }
                    PointerEventType.Release -> {
                        if (tracking) {
                            val dx = change.position.x - downX
                            val dy = change.position.y - downY
                            if (abs(dx) > 120 && abs(dx) > abs(dy) * 1.5f) {
                                if (dx < 0) viewModel.nextChapter() else viewModel.previousChapter()
                            }
                        }
                        tracking = false
                    }
                    else -> {}
                }
            }
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
                Text(
                    text = "${state.currentBook} ${state.selectedChapter}",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (state.selectedTab == "Bible") swipeModifier else Modifier)
            ) {
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
                        },
                        onNextChapter = { viewModel.nextChapter() },
                        onPreviousChapter = { viewModel.previousChapter() }
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

    SettingsPanel(
        visible = settingsOpen,
        isLoggedIn = state.isLoggedIn,
        userName = state.userName,
        userEmail = state.userEmail,
        fontSize = state.fontSize,
        darkMode = isDark,
        notesCount = state.notes.size,
        background = background,
        panel = panel,
        textColor = textColor,
        gold = gold,
        onClose = { settingsOpen = false },
        onFontSizeChange = { viewModel.setFontSize(it) },
        onDarkModeChange = { viewModel.setDarkMode(it) },
        onDailyVerse = { settingsOpen = false; dailyVerseDialog = true },
        onShowNotes = { settingsOpen = false; showAllNotes = true },
        onGoogleSignIn = {
            scope.launch {
                val result = googleAuth.signInWithGoogle(activity)
                result.onSuccess { nameAndEmail ->
                    val parts = nameAndEmail.split("|", limit = 2)
                    viewModel.onUserSignedIn(
                        parts.getOrNull(0) ?: "User",
                        parts.getOrNull(1) ?: ""
                    )
                    settingsOpen = false
                }.onFailure { e ->
                    if (e is GoogleAuth.UserCancelledException) {
                    } else {
                        Toast.makeText(context, e.message ?: "Sign-in failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        onSignOut = {
            googleAuth.signOut()
            viewModel.onUserSignedOut()
            settingsOpen = false
        }
    )

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
                        viewModel.saveHighlight(color)
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