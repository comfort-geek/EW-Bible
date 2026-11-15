package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewbible.app.data.preferences.AppPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()

    val state by viewModel.uiState.collectAsState()
    val isDark = state.darkMode ?: systemDark

    val prefs = remember { AppPreferences(context) }

    val savedScrollIndex by prefs.lastScrollIndex.collectAsState(initial = 0)
    val savedScrollOffset by prefs.lastScrollOffset.collectAsState(initial = 0)

    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("All") }
    var expandedBook by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showAllNotes by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showHighlightColors by remember { mutableStateOf(false) }
    var dailyVerseDialog by remember { mutableStateOf(false) }
    var hasRestoredScroll by remember { mutableStateOf(false) }
    var lastScrollRestoreKey by remember { mutableStateOf(0) }

    val drawerState = rememberDrawerState(
        initialValue = androidx.compose.material3.DrawerValue.Closed
    )
    val listState = rememberLazyListState()

    LaunchedEffect(state.currentBookId, state.selectedChapter) {
        viewModel.loadSummary()
    }

    LaunchedEffect(
        state.currentBookId,
        state.selectedChapter,
        state.selectedTab
    ) {
        if (state.selectedTab == "Open EW") {
            viewModel.loadCommentary()
        }
    }

    LaunchedEffect(
        state.verses,
        savedScrollIndex,
        savedScrollOffset,
        state.scrollRestoreKey
    ) {
        if (state.verses.isEmpty()) return@LaunchedEffect

        if (state.scrollRestoreKey != lastScrollRestoreKey) {
            lastScrollRestoreKey = state.scrollRestoreKey
            hasRestoredScroll = false
        }

        if (hasRestoredScroll) return@LaunchedEffect

        if (savedScrollIndex <= 0 && savedScrollOffset <= 0) {
            hasRestoredScroll = true
            return@LaunchedEffect
        }

        val targetIndex = savedScrollIndex
            .coerceIn(0, (state.verses.size + 2).coerceAtLeast(0))

        runCatching {
            listState.scrollToItem(
                index = targetIndex,
                scrollOffset = savedScrollOffset
            )
        }

        hasRestoredScroll = true
    }

    val gold = Color(0xFFC9A227)
    val background = if (isDark) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val panel = if (isDark) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val textColor = if (isDark) Color(0xFFF5F0E6) else Color(0xFF1E1E22)
    val secondaryText = if (isDark) Color(0xFFB7B0A6) else Color(0xFF524B40)

    val otBooks = state.books.filter { book ->
        val testament = book.testament.trim()
        testament.equals("OT", ignoreCase = true) ||
                testament.startsWith("Old", ignoreCase = true) ||
                (testament.isBlank() && book.id <= 39)
    }.sortedBy { it.id }

    val ntBooks = state.books.filter { book ->
        val testament = book.testament.trim()
        testament.equals("NT", ignoreCase = true) ||
                testament.startsWith("New", ignoreCase = true) ||
                (testament.isBlank() && book.id > 39)
    }.sortedBy { it.id }

    fun openBook(bookName: String, chapter: Int) {
        viewModel.openBook(bookName, chapter)
        expandedBook = bookName
        hasRestoredScroll = true

        scope.launch {
            listState.scrollToItem(0)
            drawerState.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        if (drawerState.isClosed) {
                            drawerState.open()
                        } else {
                            drawerState.close()
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = textColor
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Bible", "Open EW", "Search").forEach { tab ->
                    Text(
                        text = tab,
                        color = if (state.selectedTab == tab) gold else textColor,
                        fontWeight = if (state.selectedTab == tab) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable {
                                viewModel.selectTab(tab)
                            }
                            .padding(
                                vertical = 8.dp,
                                horizontal = 2.dp
                            )
                    )
                }
            }

            IconButton(
                onClick = {
                    settingsOpen = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Settings",
                    tint = textColor
                )
            }
        }

        ModalNavigationDrawer(
            modifier = Modifier.weight(1f),
            drawerState = drawerState,
            drawerContent = {
                BookDrawer(
                    otBookEntities = otBooks,
                    ntBookEntities = ntBooks,
                    chapterCounts = state.chapterCounts,
                    selectedBook = state.selectedBook,
                    selectedChapter = state.selectedChapter,
                    expandedBook = expandedBook,
                    background = background,
                    textColor = textColor,
                    panel = panel,
                    gold = gold,
                    secondaryText = secondaryText,
                    onOpenBook = { bookName, chapter ->
                        openBook(bookName, chapter)
                    },
                    onSelectBook = { bookName ->
                        openBook(bookName, 1)
                    },
                    onToggleBook = { bookName ->
                        expandedBook =
                            if (expandedBook == bookName) null else bookName
                    }
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (state.selectedTab) {
                        "Bible" -> {
                            BibleContent(
                                selectedBook = state.selectedBook,
                                selectedChapter = state.selectedChapter,
                                summary = state.summary,
                                versesList = state.verses,
                                currentHighlightMap = state.highlights,
                                fontSize = state.fontSize,
                                gold = gold,
                                panel = panel,
                                textColor = textColor,
                                listState = listState,
                                onVerseClick = { verse ->
                                    viewModel.saveReadingProgress(
                                        verse = verse,
                                        scrollIndex = listState.firstVisibleItemIndex,
                                        scrollOffset = listState.firstVisibleItemScrollOffset
                                    )
                                },
                                onVerseLongClick = { verse ->
                                    viewModel.selectVerse(verse)
                                    noteText = ""
                                    showNotes = true
                                    showHighlightColors = true
                                }
                            )
                        }

                        "Open EW" -> {
                            OpenEWContent(
                                commentaryHtml = state.commentaryHtml,
                                fontSize = state.fontSize,
                                textColor = textColor,
                                secondaryText = secondaryText,
                                hasError = state.commentaryError,
                                bookName = state.selectedBook,
                                chapter = state.selectedChapter,
                                onVerseClick = { book, chapter, verse ->
                                    openBook(book, chapter)
                                    viewModel.selectVerse(verse)
                                    viewModel.selectTab("Bible")
                                }
                            )
                        }

                        "Search" -> {
                            SearchContent(
                                searchQuery = searchQuery,
                                searchFilter = searchFilter,
                                filteredSearchResults = emptyList(),
                                verseResults = state.searchResults,
                                textColor = textColor,
                                gold = gold,
                                onQueryChange = { query ->
                                    searchQuery = query
                                    viewModel.setSearchQuery(query)
                                },
                                onFilterChange = { filter ->
                                    searchFilter = filter
                                    viewModel.setSearchFilter(filter)
                                },
                                onOpenBook = { bookName ->
                                    openBook(bookName, 1)
                                    viewModel.selectTab("Bible")
                                },
                                onOpenVerse = { bookName, chapter, verse ->
                                    openBook(bookName, chapter)
                                    viewModel.selectVerse(verse)
                                    viewModel.selectTab("Bible")
                                }
                            )
                        }
                    }
                }

                if (showNotes || showHighlightColors) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                showNotes = false
                                showHighlightColors = false
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (showNotes) {
                                NotesCard(
                                    selectedBook = state.selectedBook,
                                    selectedChapter = state.selectedChapter,
                                    selectedVerse = state.selectedVerse,
                                    noteText = noteText,
                                    panel = panel,
                                    gold = gold,
                                    textColor = textColor,
                                    onNoteTextChange = { noteText = it },
                                    onSaveNote = {
                                        viewModel.saveNote(noteText)
                                        noteText = ""
                                        showNotes = false
                                    }
                                )
                            }

                            if (showHighlightColors) {
                                HighlightCard(
                                    selectedVerse = state.selectedVerse,
                                    panel = panel,
                                    gold = gold,
                                    textColor = textColor,
                                    onHighlightPicked = { color ->
                                        viewModel.saveHighlight(color)
                                        showHighlightColors = false
                                    },
                                    onRemoveHighlight = {
                                        viewModel.removeHighlight()
                                        showHighlightColors = false
                                    },
                                    onDismiss = {
                                        showHighlightColors = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (settingsOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                settingsOpen = false
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.85f)
                                .align(Alignment.CenterEnd)
                                .background(background)
                                .clickable(enabled = false) { }
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Settings",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )

                                IconButton(
                                    onClick = {
                                        settingsOpen = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = textColor
                                    )
                                }
                            }

                            val isLoggedIn = state.isLoggedIn

                            if (isLoggedIn) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(gold),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = state.userName
                                                .firstOrNull()
                                                ?.toString()
                                                ?.uppercase()
                                                ?: "U",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = state.userName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Text(
                                            text = state.userEmail,
                                            fontSize = 12.sp,
                                            color = secondaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.handleGoogleLogout()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = panel
                                    )
                                ) {
                                    Text(
                                        text = "Log out",
                                        color = textColor,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "Not signed in",
                                    color = secondaryText,
                                    fontSize = 14.sp
                                )

                                Button(
                                    onClick = {
                                        settingsOpen = false
                                        viewModel.handleGoogleLogin()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Log in with Google",
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Divider(color = panel)

                            Text(
                                text = "Appearance",
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 14.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(panel)
                                    .clickable {
                                        viewModel.setDarkMode(!isDark)
                                    }
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Dark Mode",
                                    color = textColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Switch(
                                    checked = isDark,
                                    onCheckedChange = { enabled ->
                                        viewModel.setDarkMode(enabled)
                                    }
                                )
                            }

                            Text(
                                text = "Font size",
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 14.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "A-",
                                    color = textColor,
                                    fontSize = 13.sp
                                )

                                Slider(
                                    value = state.fontSize,
                                    onValueChange = { size ->
                                        viewModel.setFontSize(size)
                                    },
                                    valueRange = 12f..28f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(24.dp)
                                        .padding(horizontal = 6.dp)
                                )

                                Text(
                                    text = "A+",
                                    color = textColor,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = {
                                    settingsOpen = false
                                    dailyVerseDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = panel
                                )
                            ) {
                                Text(
                                    text = "Daily Verse",
                                    color = textColor,
                                    fontSize = 14.sp
                                )
                            }

                            Button(
                                onClick = {
                                    settingsOpen = false
                                    showAllNotes = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = panel
                                )
                            ) {
                                Text(
                                    text = "Notes (${state.notes.size})",
                                    color = textColor,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    if (dailyVerseDialog) {
        DailyVerseDialog(
            dailyVerseText = state.dailyVerse,
            gold = gold,
            textColor = textColor,
            fontSize = state.fontSize,
            onDismiss = {
                dailyVerseDialog = false
            }
        )
    }

    if (showAllNotes) {
        AlertDialog(
            onDismissRequest = {
                showAllNotes = false
            },
            title = {
                Text(
                    text = "Notes (${state.notes.size})",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                if (state.notes.isEmpty()) {
                    Text(
                        text = "No notes yet.\nHold a verse in Bible tab to add a note.",
                        color = textColor,
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    ) {
                        items(
                            state.notes,
                            key = { note ->
                                "${note.bookId}-${note.chapter}-${note.verse}-${note.timestamp}"
                            }
                        ) { note ->
                            val bookName = state.books
                                .firstOrNull { it.id == note.bookId }
                                ?.name
                                ?: "Book ${note.bookId}"

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAllNotes = false
                                        openBook(bookName, note.chapter)
                                        viewModel.selectVerse(note.verse)
                                        viewModel.selectTab("Bible")
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = "$bookName ${note.chapter}:${note.verse}",
                                    color = gold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )

                                Text(
                                    text = note.noteText,
                                    color = textColor,
                                    fontSize = 14.sp
                                )
                            }

                            Divider(color = panel)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAllNotes = false
                    }
                ) {
                    Text(
                        text = "Close",
                        color = gold
                    )
                }
            },
            containerColor = background
        )
    }
}