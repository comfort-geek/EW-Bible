package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.AppDatabase
import com.ewbible.app.data.database.AppPreferences
import com.ewbible.app.data.database.DailyVerseReference
import com.ewbible.app.data.database.HighlightEntity
import com.ewbible.app.data.database.NoteEntity
import com.ewbible.app.data.database.SummaryEntity
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val googleAuth = remember { GoogleAuth(context) }
    val syncManager = remember { SyncManager() }
    val scope = rememberCoroutineScope()

    val savedDark by prefs.isDarkMode.collectAsState(initial = true)
    val savedFont by prefs.fontSize.collectAsState(initial = 16f)
    val savedName by prefs.userName.collectAsState(initial = "Guest")
    val savedBook by prefs.lastBook.collectAsState(initial = "Genesis")
    val savedChapter by prefs.lastChapter.collectAsState(initial = 1)
    val savedVerse by prefs.lastVerse.collectAsState(initial = 1)
    val savedScrollIndex by prefs.lastScrollIndex.collectAsState(initial = 0)
    val savedScrollOffset by prefs.lastScrollOffset.collectAsState(initial = 0)
    val savedDailyVerseDate by prefs.dailyVerseDate.collectAsState(initial = "")
    val savedDailyVerseRef by prefs.dailyVerseReference.collectAsState(initial = null)

    var isDarkMode by remember { mutableStateOf(savedDark) }
    var fontSize by remember { mutableStateOf(savedFont) }
    var isLoggedIn by remember { mutableStateOf(savedName != "Guest") }
    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("All") }
    var selectedBook by remember { mutableStateOf(savedBook) }
    var selectedChapter by remember { mutableStateOf(savedChapter) }
    var selectedVerse by remember { mutableStateOf<Int?>(savedVerse) }
    var selectedTab by remember { mutableStateOf("Bible") }
    var expandedBook by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showHighlightColors by remember { mutableStateOf(false) }
    var dailyVerseDialog by remember { mutableStateOf(false) }
    var dailyVerseText by remember { mutableStateOf("Your word is a lamp for my feet, a light on my path. - Psalm 119:105") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val booksList by db.bibleDao().getAllBooks().collectAsState(initial = emptyList())
    val selectedBookEntity = booksList.firstOrNull {
        it.name.equals(selectedBook, ignoreCase = true) || it.key.equals(selectedBook, ignoreCase = true)
    }
    val currentBookId = selectedBookEntity?.id ?: booksList.firstOrNull()?.id ?: 1
    val versesList by db.bibleDao().getVerses(currentBookId, selectedChapter).collectAsState(initial = emptyList())
    val highlights by db.bibleDao().getHighlights(currentBookId, selectedChapter).collectAsState(initial = emptyList())
    val highlightMap = remember(highlights) {
        highlights.associate { highlight ->
            highlight.verse to runCatching { Color(highlight.color.toLong(16)) }.getOrDefault(Color.Yellow)
        }
    }

    var summary by remember(selectedBook, selectedChapter) { mutableStateOf<SummaryEntity?>(null) }
    LaunchedEffect(selectedBook, selectedChapter, currentBookId) {
        summary = db.bibleDao().getSummary(currentBookId, selectedChapter)
    }

    var commentaryHtml by remember(selectedBook, selectedChapter) { mutableStateOf("") }
    LaunchedEffect(selectedBook, selectedChapter, currentBookId, selectedTab) {
        if (selectedTab == "Open EW") {
            commentaryHtml = db.bibleDao().getCommentary(currentBookId, selectedChapter)?.getHtml().orEmpty()
        }
    }

    val gold = Color(0xFFC9A227)
    val background = if (isDarkMode) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val panel = if (isDarkMode) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val textColor = if (isDarkMode) Color(0xFFF5F0E6) else Color(0xFF1E1E22)
    val secondaryText = if (isDarkMode) Color(0xFFB7B0A6) else Color(0xFF524B40)

    val otBooks = remember(booksList) {
        booksList.filter { book ->
            val testament = book.testament.trim()
            testament.equals("OT", ignoreCase = true) || testament.startsWith("Old", ignoreCase = true) ||
                (testament.isBlank() && book.id <= 39)
        }.sortedBy { it.id }
    }
    val ntBooks = remember(booksList) {
        booksList.filter { book ->
            val testament = book.testament.trim()
            testament.equals("NT", ignoreCase = true) || testament.startsWith("New", ignoreCase = true) ||
                (testament.isBlank() && book.id > 39)
        }.sortedBy { it.id }
    }
    val availableBooks = remember(booksList) { booksList.sortedBy { it.id }.map { it.name } }
    val filteredSearchResults = remember(searchQuery, searchFilter, availableBooks, otBooks, ntBooks) {
        val gospelBooks = setOf("Matthew", "Mark", "Luke", "John")
        val prophecyBooks = setOf("Isaiah", "Jeremiah", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Micah", "Zechariah", "Habakkuk", "Malachi", "Jonah", "Nahum", "Obadiah")
        val candidates = when (searchFilter) {
            "Gospel" -> availableBooks.filter { it in gospelBooks }
            "Prophecy" -> availableBooks.filter { it in prophecyBooks }
            "Old Testament" -> availableBooks.filter { name -> otBooks.any { it.name == name } }
            "New Testament" -> availableBooks.filter { name -> ntBooks.any { it.name == name } }
            else -> availableBooks
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) candidates else candidates.filter { it.contains(query, ignoreCase = true) }
    }

    LaunchedEffect(booksList, savedBook, savedChapter, savedVerse) {
        if (booksList.isEmpty()) return@LaunchedEffect
        val restoredBook = booksList.firstOrNull {
            it.name.equals(savedBook, ignoreCase = true) || it.key.equals(savedBook, ignoreCase = true)
        } ?: booksList.minByOrNull { it.id } ?: return@LaunchedEffect
        selectedBook = restoredBook.name
        selectedChapter = savedChapter.coerceAtLeast(1)
        selectedVerse = savedVerse.coerceAtLeast(1)
    }

    LaunchedEffect(savedDark, savedFont, savedName) {
        isDarkMode = savedDark
        fontSize = savedFont
        isLoggedIn = savedName != "Guest"
    }

    LaunchedEffect(booksList, savedDailyVerseDate, savedDailyVerseRef) {
        if (booksList.isEmpty()) return@LaunchedEffect
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val reference = savedDailyVerseRef
        if (savedDailyVerseDate == today && reference != null) {
            val book = booksList.firstOrNull { it.id == reference.bookId }
            val verse = book?.let { db.bibleDao().getVerses(it.id, reference.chapter).first().firstOrNull { row -> row.verse == reference.verse } }
            if (book != null && verse != null) {
                dailyVerseText = "${verse.text} - ${book.name} ${reference.chapter}:${reference.verse}"
                return@LaunchedEffect
            }
        }
        val psalms = booksList.firstOrNull { it.name.equals("Psalms", true) || it.key.equals("Psalms", true) } ?: return@LaunchedEffect
        val verse = db.bibleDao().getVerses(psalms.id, 119).first().firstOrNull { it.verse == 105 } ?: return@LaunchedEffect
        dailyVerseText = "${verse.text} - Psalm 119:105"
        prefs.saveDailyVerse(today, DailyVerseReference(psalms.id, 119, 105))
    }

    LaunchedEffect(isDarkMode, fontSize) {
        prefs.saveSettings(isDarkMode, fontSize)
    }

    LaunchedEffect(selectedBook, selectedChapter, versesList.size, summary) {
        if (versesList.isEmpty()) return@LaunchedEffect
        val itemCount = versesList.size + if (summary == null) 0 else 1
        listState.scrollToItem(savedScrollIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0)), savedScrollOffset.coerceAtLeast(0))
    }

    fun openBook(bookName: String, chapter: Int) {
        selectedBook = bookName
        selectedChapter = chapter
        selectedVerse = 1
        expandedBook = bookName
        scope.launch {
            prefs.saveReading("World English Bible", bookName, chapter, 1)
            prefs.saveScroll(0, 0)
            listState.scrollToItem(0)
            drawerState.close()
        }
    }

    fun saveReadingProgress(verse: Int) {
        selectedVerse = verse
        scope.launch {
            prefs.saveReading("World English Bible", selectedBook, selectedChapter, verse)
            prefs.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    fun handleGoogleLogin() {
        scope.launch {
            runCatching { googleAuth.signInWithGoogle() }.getOrNull()?.onSuccess { data ->
                val name = data.substringBefore('|').ifBlank { "User" }
                val email = data.substringAfter('|', "")
                prefs.saveUser(name, email)
                isLoggedIn = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            BookDrawer(
                booksList = booksList,
                otBookEntities = otBooks,
                ntBookEntities = ntBooks,
                selectedBook = selectedBook,
                selectedChapter = selectedChapter,
                expandedBook = expandedBook,
                background = background,
                textColor = textColor,
                panel = panel,
                gold = gold,
                secondaryText = secondaryText,
                onOpenBook = { bookName, chapter -> openBook(bookName, chapter) },
                onSelectBook = { bookName ->
                    selectedBook = bookName
                    selectedChapter = 1
                },
                onToggleBook = { bookName -> expandedBook = if (expandedBook == bookName) null else bookName },
                db = db,
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxSize().background(background)) {
            TopAppBar(
                title = { Text("EW Bible", color = textColor, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                    }
                },
                actions = {
                    IconButton(onClick = { settingsOpen = !settingsOpen }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = textColor)
                    }
                    DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                        DropdownMenuItem(text = { Text(if (isLoggedIn) "Logged in" else "Log in with Google") }, onClick = { settingsOpen = false; if (!isLoggedIn) handleGoogleLogin() })
                        DropdownMenuItem(text = { Text(if (isDarkMode) "Light mode" else "Dark mode") }, onClick = { settingsOpen = false; isDarkMode = !isDarkMode })
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("A-", color = textColor)
                                    Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 12f..28f, modifier = Modifier.width(120.dp).padding(horizontal = 6.dp))
                                    Text("A+", color = textColor)
                                }
                            },
                            onClick = {},
                        )
                        DropdownMenuItem(text = { Text("Daily Verse") }, onClick = { settingsOpen = false; dailyVerseDialog = true })
                        DropdownMenuItem(text = { Text(if (showNotes) "Hide Notes" else "Notes") }, onClick = { settingsOpen = false; showNotes = !showNotes })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf("Bible", "Open EW", "Search").forEach { tab ->
                    Text(
                        text = tab,
                        color = if (selectedTab == tab) gold else textColor,
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f).clickable { selectedTab = tab }.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            when (selectedTab) {
                "Bible" -> BibleContent(
                    selectedBook = selectedBook,
                    selectedChapter = selectedChapter,
                    summary = summary,
                    versesList = versesList,
                    currentHighlightMap = highlightMap,
                    fontSize = fontSize,
                    gold = gold,
                    panel = panel,
                    textColor = textColor,
                    listState = listState,
                    onVerseClick = ::saveReadingProgress,
                    onVerseLongClick = { verse -> selectedVerse = verse; showHighlightColors = true },
                )
                "Open EW" -> OpenEWContent(commentaryHtml = commentaryHtml, fontSize = fontSize, textColor = textColor, secondaryText = secondaryText)
                "Search" -> SearchContent(
                    searchQuery = searchQuery,
                    searchFilter = searchFilter,
                    filteredSearchResults = filteredSearchResults,
                    textColor = textColor,
                    gold = gold,
                    onQueryChange = { searchQuery = it },
                    onFilterChange = { searchFilter = it },
                    onOpenBook = { bookName -> openBook(bookName, 1); selectedTab = "Bible" },
                )
            }

            if (showNotes) {
                NotesCard(
                    noteText = noteText,
                    panel = panel,
                    gold = gold,
                    textColor = textColor,
                    onNoteTextChange = { noteText = it },
                    onSaveNote = {
                        val verse = selectedVerse ?: 1
                        scope.launch {
                            db.bibleDao().saveNote(
                                NoteEntity(
                                    bookId = currentBookId,
                                    chapter = selectedChapter,
                                    verse = verse,
                                    noteText = noteText,
                                    timestamp = System.currentTimeMillis(),
                                )
                            )
                            if (isLoggedIn) syncManager.uploadNote(currentBookId, selectedChapter, verse, noteText)
                            noteText = ""
                        }
                    },
                )
            }

            if (showHighlightColors) {
                HighlightCard(
                    selectedVerse = selectedVerse,
                    currentBookId = currentBookId,
                    selectedChapter = selectedChapter,
                    panel = panel,
                    gold = gold,
                    textColor = textColor,
                    db = db,
                    isLoggedIn = isLoggedIn,
                    syncManager = syncManager,
                    onDismiss = { showHighlightColors = false },
                )
            }
        }
    }

    if (dailyVerseDialog) {
        DailyVerseDialog(
            dailyVerseText = dailyVerseText,
            gold = gold,
            textColor = textColor,
            fontSize = fontSize,
            onDismiss = { dailyVerseDialog = false },
        )
    }
}
