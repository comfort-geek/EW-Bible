package com.ewbible.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.*
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val dimBackground = Color(0xFF202328)
private val dimSurface = Color(0xFF2A2F35)
private val dimText = Color(0xFFE6DDC9)
private val paperBackground = Color(0xFFF5EEDC)
private val paperSurface = Color(0xFFE9E0CC)
private val inkText = Color(0xFF29261F)
private val readingGold = Color(0xFFD8B36A)

private val gospelBooks = setOf("Matthew", "Mark", "Luke", "John")
private val prophecyBooks = setOf(
    "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos",
    "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah",
    "Malachi", "Revelation"
)

data class SearchHit(
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val dao = db.bibleDao()
    val scope = rememberCoroutineScope()
    val googleAuth = remember { GoogleAuth(context) }
    val syncManager = remember { SyncManager() }

    val books by dao.getAllBooks().collectAsState(initial = emptyList())
    val savedMode by prefs.lastMode.collectAsState(initial = "World English Bible")
    val savedBook by prefs.lastBook.collectAsState(initial = "Genesis")
    val savedChapter by prefs.lastChapter.collectAsState(initial = 1)
    val savedDark by prefs.isDarkMode.collectAsState(initial = true)
    val savedFont by prefs.fontSize.collectAsState(initial = 16f)
    val savedEmail by prefs.userEmail.collectAsState(initial = "")
    val savedScrollIndex by prefs.lastScrollIndex.collectAsState(initial = 0)
    val savedScrollOffset by prefs.lastScrollOffset.collectAsState(initial = 0)
    val allNotes by dao.getAllNotes().collectAsState(initial = emptyList())
    val allHighlights by dao.getAllHighlights().collectAsState(initial = emptyList())

    var currentMode by remember { mutableStateOf("World English Bible") }
    var selectedBook by remember { mutableStateOf("Genesis") }
    var selectedChapter by remember { mutableStateOf(1) }
    var selectedVerse by remember { mutableStateOf(1) }
    var isDarkMode by remember { mutableStateOf(true) }
    var fontSize by remember { mutableStateOf(16f) }
    var searchQuery by remember { mutableStateOf("") }
    var searchScope by remember { mutableStateOf("All") }
    var searchResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var summary by remember { mutableStateOf<SummaryEntity?>(null) }
    var commentaryHtml by remember { mutableStateOf("") }
    var highlightedVerses by remember { mutableStateOf<Map<Int, Color>>(emptyMap()) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var noteText by remember { mutableStateOf("") }
    var dailyVerseText by remember { mutableStateOf("") }
    var signInMessage by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var scrollRestored by remember { mutableStateOf(false) }

    val currentBookId = books.firstOrNull { it.name == selectedBook }?.id
        ?: books.firstOrNull()?.id
        ?: 1

    val maxChapter by dao.getMaxChapter(currentBookId).collectAsState(initial = 1)
    val chapterCount = maxOf(1, maxChapter)
    val verses by dao.getVerses(currentBookId, selectedChapter).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    val bgColor = if (isDarkMode) dimBackground else paperBackground
    val surfaceColor = if (isDarkMode) dimSurface else paperSurface
    val textColor = if (isDarkMode) dimText else inkText

    suspend fun saveProgress() {
        try {
            prefs.saveReading(currentMode, selectedBook, selectedChapter, selectedVerse)
            prefs.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            prefs.saveSettings(isDarkMode, fontSize)
            if (googleAuth.currentUser() != null) {
                syncManager.uploadProgress(
                    currentMode,
                    selectedBook,
                    selectedChapter,
                    selectedVerse,
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset
                )
            }
        } catch (_: Exception) {
        }
    }

    fun selectBook(bookName: String) {
        selectedBook = bookName
        selectedChapter = 1
        selectedVerse = 1
        scope.launch {
            prefs.saveReading(currentMode, bookName, 1, 1)
            prefs.saveScroll(0, 0)
            listState.scrollToItem(0, 0)
        }
    }

    fun selectChapter(chapter: Int) {
        selectedChapter = chapter.coerceAtLeast(1)
        selectedVerse = 1
        scope.launch {
            prefs.saveReading(currentMode, selectedBook, selectedChapter, 1)
            prefs.saveScroll(0, 0)
            listState.scrollToItem(0, 0)
        }
    }

    LaunchedEffect(savedMode, savedBook, savedChapter, savedDark, savedFont) {
        if (!initialized) {
            currentMode = savedMode
            selectedBook = savedBook
            selectedChapter = savedChapter
            selectedVerse = 1
            isDarkMode = savedDark
            fontSize = savedFont
            initialized = true
        }
    }

    LaunchedEffect(currentBookId, selectedChapter) {
        try {
            summary = dao.getSummary(currentBookId, selectedChapter)
        } catch (_: Exception) {
            summary = null
        }
    }

    LaunchedEffect(currentBookId, selectedChapter, currentMode) {
        try {
            commentaryHtml = if (currentMode == "Endurance") {
                dao.getCommentary(currentBookId, selectedChapter)?.getHtml() ?: ""
            } else {
                ""
            }
        } catch (_: Exception) {
            commentaryHtml = ""
        }
    }

    LaunchedEffect(currentBookId, selectedChapter) {
        try {
            dao.getHighlights(currentBookId, selectedChapter).collect { list ->
                highlightedVerses = list.associate { hl ->
                    hl.verse to Color(
                        runCatching { hl.color.toLong(16).toInt() }
                            .getOrDefault(0xFFFFE082.toInt())
                    )
                }
            }
        } catch (_: Exception) {
            highlightedVerses = emptyMap()
        }
    }

    LaunchedEffect(searchQuery, searchScope, books) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }

        val filteredBooks = when (searchScope) {
            "Gospel" -> books.filter { it.name in gospelBooks }
            "Prophecy" -> books.filter { it.name in prophecyBooks }
            "Old Testament" -> books.filter { it.testament.equals("Old Testament", true) || it.testament.equals("Old", true) }
            "New Testament" -> books.filter { it.testament.equals("New Testament", true) || it.testament.equals("New", true) }
            else -> books
        }

        val results = mutableListOf<SearchHit>()
        for (book in filteredBooks) {
            val bookChapterCount = runCatching { dao.getMaxChapter(book.id).first() }.getOrDefault(1)
            for (chapter in 1..bookChapterCount.coerceAtLeast(1)) {
                val rows = runCatching { dao.getVerses(book.id, chapter).first() }.getOrDefault(emptyList())
                rows.forEach { verse ->
                    if (verse.text.contains(searchQuery, ignoreCase = true)) {
                        results += SearchHit(book.name, chapter, verse.verse, verse.text)
                    }
                }
            }
        }
        searchResults = results
    }

    LaunchedEffect(initialized, savedScrollIndex, savedScrollOffset) {
        if (initialized && !scrollRestored && verses.isNotEmpty()) {
            try {
                val index = savedScrollIndex.coerceIn(0, verses.size - 1)
                listState.scrollToItem(index, savedScrollOffset)
                scrollRestored = true
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, initialized) {
        if (initialized) {
            kotlinx.coroutines.delay(600)
            selectedVerse = listState.firstVisibleItemIndex + 1
            scope.launch { saveProgress() }
        }
    }

    val modeLabel = when (currentMode) {
        "Endurance" -> "Endurance"
        "Search" -> "Search"
        else -> "Bible"
    }

    ModalNavigationDrawer(
        drawerState = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = bgColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Select Book",
                        color = textColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    if (books.isEmpty()) {
                        Text("Loading books...", color = Color.Gray)
                    } else {
                        val grouped = books.groupBy { it.testament }
                        grouped.forEach { (testament, items) ->
                            Text(
                                text = testament,
                                color = readingGold,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            items.forEach { book ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectBook(book.name)
                                            drawerOpen = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = book.name,
                                        color = if (book.name == selectedBook) readingGold else textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (book.name == selectedBook) {
                                        Text("●", color = readingGold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Chapter ${selectedChapter}",
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        items((1..chapterCount).toList()) { chapter ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectChapter(chapter)
                                        drawerOpen = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Chapter $chapter",
                                    color = if (chapter == selectedChapter) readingGold else textColor,
                                    modifier = Modifier.weight(1f)
                                )
                                if (chapter == selectedChapter) {
                                    Text("●", color = readingGold)
                                }
                            }
                        }
                    }
                }
            }
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            containerColor = bgColor,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "EW Bible · $modeLabel",
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { drawerOpen = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = textColor)
                            }

                            DropdownMenu(
                                expanded = settingsOpen,
                                onDismissRequest = { settingsOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (savedEmail.isBlank()) "Google sign in" else "Signed in: ${savedEmail}") },
                                    onClick = {
                                        settingsOpen = false
                                        scope.launch {
                                            if (googleAuth.currentUser() != null) {
                                                googleAuth.signOut()
                                                prefs.clearUser()
                                                signInMessage = "Signed out"
                                            } else {
                                                val result = googleAuth.signInWithGoogle()
                                                result.onSuccess {
                                                    val parts = it.split("|", limit = 2)
                                                    val userName = parts.getOrElse(0) { "User" }
                                                    val email = parts.getOrElse(1) { "" }
                                                    prefs.saveUser(userName, email)
                                                    signInMessage = "Signed in as $userName"
                                                }
                                                result.onFailure {
                                                    signInMessage = it.localizedMessage ?: "Google sign in failed"
                                                }
                                            }
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Dark mode") },
                                    trailingIcon = {
                                        Switch(
                                            checked = isDarkMode,
                                            onCheckedChange = { dark ->
                                                isDarkMode = dark
                                                scope.launch { prefs.saveSettings(dark, fontSize) }
                                            }
                                        )
                                    },
                                    onClick = { }
                                )

                                DropdownMenuItem(
                                    text = { Text("A−") },
                                    onClick = {
                                        fontSize = (fontSize - 1f).coerceAtLeast(12f)
                                        scope.launch { prefs.saveSettings(isDarkMode, fontSize) }
                                        settingsOpen = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("A+") },
                                    onClick = {
                                        fontSize = (fontSize + 1f).coerceAtMost(24f)
                                        scope.launch { prefs.saveSettings(isDarkMode, fontSize) }
                                        settingsOpen = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Daily Verse") },
                                    onClick = {
                                        settingsOpen = false
                                        val verse = verses.randomOrNull()
                                        dailyVerseText = if (verse != null) {
                                            "${selectedBook} ${selectedChapter}:${verse.verse}\n\n${verse.text}"
                                        } else {
                                            "No verse is available yet."
                                        }
                                        dialog = "daily"
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Notes") },
                                    onClick = {
                                        settingsOpen = false
                                        dialog = "notes"
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Highlights") },
                                    onClick = {
                                        settingsOpen = false
                                        dialog = "highlights"
                                    }
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Bible", "Endurance", "Search").forEach { tab ->
                        val selected = tab == modeLabel
                        Button(
                            onClick = {
                                currentMode = when (tab) {
                                    "Bible" -> "World English Bible"
                                    "Endurance" -> "Endurance"
                                    "Search" -> "Search"
                                    else -> "World English Bible"
                                }
                                scope.launch { saveProgress() }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) readingGold else surfaceColor,
                                contentColor = if (selected) Color(0xFF1B1B1B) else textColor
                            )
                        ) {
                            Text(tab)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$selectedBook $selectedChapter",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = { drawerOpen = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Book menu", tint = readingGold)
                    }
                }

                when (currentMode) {
                    "Search" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Search verse text") }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("All", "Gospel", "Prophecy", "Old Testament", "New Testament").forEach { filter ->
                                    FilterChip(
                                        selected = searchScope == filter,
                                        onClick = { searchScope = filter },
                                        label = { Text(filter, fontSize = 10.sp) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            if (searchQuery.isBlank()) {
                                Text("Type a word or phrase to search.", color = textColor)
                            } else {
                                LazyColumn(Modifier.fillMaxSize()) {
                                    items(searchResults) { hit ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedBook = hit.bookName
                                                    selectedChapter = hit.chapter
                                                    selectedVerse = hit.verse
                                                    currentMode = "World English Bible"
                                                    scope.launch { saveProgress() }
                                                }
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "${hit.bookName} ${hit.chapter}:${hit.verse}",
                                                color = readingGold,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = hit.text,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Endurance" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            item {
                                Text(
                                    text = "Endurance - ${selectedBook} ${selectedChapter}",
                                    color = readingGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            item {
                                if (commentaryHtml.isBlank()) {
                                    Text("Loading Endurance...", color = Color.Gray)
                                } else {
                                    Text(
                                        text = commentaryHtml,
                                        color = textColor,
                                        fontSize = fontSize.sp
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            summary?.let { item ->
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        colors = CardDefaults.cardColors(containerColor = surfaceColor)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Chapter Summary",
                                                color = readingGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                            Text(
                                                text = item.summary,
                                                color = textColor
                                            )
                                            Text(
                                                text = "What Happened",
                                                color = readingGold,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = item.whatHappened,
                                                color = textColor
                                            )

                                            Text(
                                                text = "Application",
                                                color = readingGold,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = item.application,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }

                            itemsIndexed(verses, key = { _, verse -> "${currentBookId}-${selectedChapter}-${verse.verse}" }) { _, verse ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(
                                            color = highlightedVerses[verse.verse] ?: Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            scope.launch {
                                                val existing = dao.getHighlights(currentBookId, selectedChapter).first()
                                                    .firstOrNull { it.verse == verse.verse }

                                                if (existing == null) {
                                                    val color = "FFFFE082"
                                                    dao.saveHighlight(HighlightEntity(currentBookId, selectedChapter, verse.verse, color))
                                                    if (googleAuth.currentUser() != null) {
                                                        syncManager.uploadHighlight(currentBookId, selectedChapter, verse.verse, color)
                                                    }
                                                } else {
                                                    dao.deleteHighlight(currentBookId, selectedChapter, verse.verse)
                                                    if (googleAuth.currentUser() != null) {
                                                        syncManager.deleteHighlight(currentBookId, selectedChapter, verse.verse)
                                                    }
                                                }
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "${verse.verse} ",
                                        color = readingGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = fontSize.sp
                                    )
                                    Text(
                                        text = verse.text,
                                        color = textColor,
                                        fontSize = fontSize.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    if (dialog == "daily") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Daily Verse") },
            text = { Text(dailyVerseText) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (dialog == "notes") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Notes") },
            text = {
                Column {
                    Text("Note for ${selectedBook} ${selectedChapter}:${selectedVerse}", color = textColor)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Write note") }
                    )
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp)
                    ) {
                        items(allNotes.filter {
                            it.bookId == currentBookId &&
                            it.chapter == selectedChapter &&
                            it.verse == selectedVerse
                        }) { note ->
                            Text(
                                text = "${note.noteText}",
                                color = textColor,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = noteText.trim()
                    if (n.isNotEmpty()) {
                        scope.launch {
                            dao.saveNote(
                                NoteEntity(
                                    bookId = currentBookId,
                                    chapter = selectedChapter,
                                    verse = selectedVerse,
                                    noteText = n,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            if (googleAuth.currentUser() != null) {
                                syncManager.uploadNote(currentBookId, selectedChapter, selectedVerse, n)
                            }
                        }
                    }
                    noteText = ""
                    dialog = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (dialog == "highlights") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Highlights") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp)
                ) {
                    items(allHighlights.filter {
                        it.bookId == currentBookId &&
                        it.chapter == selectedChapter
                    }) { highlight ->
                        Text(
                            text = "${highlight.verse}",
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        dao.deleteHighlight(highlight.bookId, highlight.chapter, highlight.verse)
                                        if (googleAuth.currentUser() != null) {
                                            syncManager.deleteHighlight(highlight.bookId, highlight.chapter, highlight.verse)
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (signInMessage.isNotBlank()) {
        LaunchedEffect(signInMessage) {
            kotlinx.coroutines.delay(2500)
            signInMessage = ""
        }
        Text(text = signInMessage, color = textColor)
    }

    BackHandler(
        enabled = drawerOpen || settingsOpen || dialog != null
    ) {
        if (dialog != null) {
            dialog = null
        } else if (settingsOpen) {
            settingsOpen = false
        } else {
            drawerOpen = false
        }
    }
}
