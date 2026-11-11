package com.ewbible.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.*
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.launch

enum class ReadingLevel { BOOKS, CHAPTERS, VERSES }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val googleAuth = remember { GoogleAuth(context) }
    val syncManager = remember { SyncManager() }
    val scope = rememberCoroutineScope()

    // --- LOAD SAVED MEMORY ---
    val savedMode by prefs.lastMode.collectAsState(initial = "World English Bible")
    val savedBook by prefs.lastBook.collectAsState(initial = "Genesis")
    val savedChapter by prefs.lastChapter.collectAsState(initial = 1)
    val savedVerse by prefs.lastVerse.collectAsState(initial = 1)
    val savedDark by prefs.isDarkMode.collectAsState(initial = true)
    val savedFont by prefs.fontSize.collectAsState(initial = 16f)
    val savedName by prefs.userName.collectAsState(initial = "Guest")
    val savedScrollIndex by prefs.lastScrollIndex.collectAsState(initial = 0)
    val savedScrollOffset by prefs.lastScrollOffset.collectAsState(initial = 0)

    var currentMode by remember { mutableStateOf("World English Bible") }
    var selectedBook by remember { mutableStateOf("Genesis") }
    var selectedChapter by remember { mutableStateOf(1) }
    var selectedVerse by remember { mutableStateOf<Int?>(null) }
    var isFirstLoad by remember { mutableStateOf(true) }

    // Load memory ONCE when app opens
    LaunchedEffect(savedMode, savedBook, savedChapter) {
        if (isFirstLoad) {
            currentMode = savedMode
            selectedBook = savedBook
            selectedChapter = savedChapter
            selectedVerse = savedVerse
            isFirstLoad = false
        }
    }

    // --- REAL DB DATA ---
    val booksList by db.bibleDao().getAllBooks().collectAsState(initial = emptyList())
    val currentBookId = booksList.find { it.name == selectedBook }?.id ?: 1
    val versesList by db.bibleDao().getVerses(currentBookId, selectedChapter).collectAsState(initial = emptyList())

    // Summary - ONLY in WEB
    var summary by remember(selectedBook, selectedChapter) { mutableStateOf<SummaryEntity?>(null) }
    LaunchedEffect(selectedBook, selectedChapter, currentBookId) {
        summary = db.bibleDao().getSummary(currentBookId, selectedChapter)
    }
    // Commentary - ONLY in Commentary mode
    var commentaryHtml by remember(selectedBook, selectedChapter) { mutableStateOf("") }
    LaunchedEffect(selectedBook, selectedChapter, currentBookId, currentMode) {
        if (currentMode == "Commentary") {
            commentaryHtml = db.bibleDao().getCommentary(currentBookId, selectedChapter)?.getHtml() ?: ""
        }
    }

    val leftDrawerState = rememberDrawerState(DrawerValue.Closed)
    var readingLevel by remember { mutableStateOf(ReadingLevel.BOOKS) }
    var showRightDrawer by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var isDarkMode by remember { mutableStateOf(true) }
    var fontSize by remember { mutableStateOf(16f) }
    var showDailyVerse by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("All") }
    var showHighlightColors by remember { mutableStateOf(false) }
    var highlightedVerses by remember { mutableStateOf<Map<Int, Color>>(emptyMap()) }
    val highlightColors = listOf(Color.Yellow, Color.Green, Color(0xFFFFB6C1), Color.Cyan, Color(0xFFFFA500))
    var syncStatus by remember { mutableStateOf("") }

    // Sync loaded prefs to UI state + login state
    LaunchedEffect(savedDark, savedFont, savedName) {
        if (isFirstLoad) { isDarkMode = savedDark; fontSize = savedFont }
        isLoggedIn = savedName != "Guest"
    }

    // Load highlights for current chapter from LOCAL Room
    val dbHighlights by db.bibleDao().getHighlights(currentBookId, selectedChapter).collectAsState(initial = emptyList())
    LaunchedEffect(dbHighlights) {
        val map = dbHighlights.associate { it.verse to Color(it.color.toLongOrNull() ?: 0xFFFFFF00) }
        // Only overwrite if we have local data, to avoid wiping cloud download
        if (dbHighlights.isNotEmpty()) highlightedVerses = map
    }

    val bgColor = if (isDarkMode) Color(0xFF121212) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val cardColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF0F0F0)

    // --- EXACT SCROLLING ---
    val listState = rememberLazyListState()
    var scrollRestored by remember { mutableStateOf(false) }

    LaunchedEffect(savedScrollIndex) {
        if (!scrollRestored && savedScrollIndex > 0) {
            try { listState.scrollToItem(savedScrollIndex, savedScrollOffset) } catch (e: Exception) {}
            scrollRestored = true
        }
    }

    // Save scroll + reading LOCALLY + TO CLOUD - debounced
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        kotlinx.coroutines.delay(500)
        scope.launch {
            val verseNum = listState.firstVisibleItemIndex + 1
            prefs.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            prefs.saveReading(currentMode, selectedBook, selectedChapter, verseNum)
            // CLOUD: Reading Progress
            if (isLoggedIn) {
                try {
                    syncManager.uploadProgress(currentMode, selectedBook, selectedChapter, verseNum, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                    syncStatus = "Synced ✅"
                } catch (e: Exception) { syncStatus = "" }
            }
        }
    }
    LaunchedEffect(isDarkMode, fontSize) {
        prefs.saveSettings(isDarkMode, fontSize)
    }

    fun openReading(mode: String, book: String, chapter: Int) {
        currentMode = mode
        selectedBook = book
        selectedChapter = chapter
        selectedVerse = 1
        scrollRestored = true
        scope.launch {
            prefs.saveReading(mode, book, chapter, 1)
            prefs.saveScroll(0, 0)
            if (isLoggedIn) syncManager.uploadProgress(mode, book, chapter, 1, 0, 0)
            listState.scrollToItem(0, 0)
            leftDrawerState.close()
        }
    }

    // Helper: Download all cloud data after login
    fun downloadCloudData() {
        scope.launch {
            syncStatus = "Syncing..."
            try {
                // 1. Progress - if cloud is newer, jump to it
                val cloudProgress = syncManager.getProgress()
                if (cloudProgress != null) {
                    // Only use cloud progress if local is Genesis 1 (fresh install)
                    if (selectedBook == "Genesis" && selectedChapter == 1 && savedScrollIndex == 0) {
                        currentMode = cloudProgress.mode
                        selectedBook = cloudProgress.book
                        selectedChapter = cloudProgress.chapter
                        selectedVerse = cloudProgress.verse
                        prefs.saveReading(cloudProgress.mode, cloudProgress.book, cloudProgress.chapter, cloudProgress.verse)
                        prefs.saveScroll(cloudProgress.scrollIndex, cloudProgress.scrollOffset)
                    }
                }
                // 2. Highlights
                val cloudHighlights = syncManager.getHighlights()
                cloudHighlights.forEach { hl ->
                    db.bibleDao().saveHighlight(HighlightEntity(hl.bookId, hl.chapter, hl.verse, hl.color))
                }
                // 3. Notes
                val cloudNotes = syncManager.getNotes()
                cloudNotes.forEach { n ->
                    db.bibleDao().saveNote(NoteEntity(bookId = n.bookId, chapter = n.chapter, verse = n.verse, noteText = n.noteText, timestamp = n.timestamp))
                }
                syncStatus = "Synced ✅"
            } catch (e: Exception) { syncStatus = "Sync failed" }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        ModalNavigationDrawer(
            drawerState = leftDrawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(text = currentMode, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Grow in the Word of God, every day.", color = Color(0xFFC9A227), fontSize = 12.sp)
                        if (syncStatus.isNotEmpty()) Text(text = syncStatus, color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = when (readingLevel) {
                            ReadingLevel.BOOKS -> "Book"
                            ReadingLevel.CHAPTERS -> "$selectedBook - Chapter"
                            ReadingLevel.VERSES -> "$selectedBook $selectedChapter - Verse"
                        }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            when (readingLevel) {
                                ReadingLevel.BOOKS -> {
                                    itemsIndexed(booksList) { _, book ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                            Text(text = book.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                            Text(text = ">", color = Color.Gray)
                                        }
                                        Divider()
                                    }
                                }
                                ReadingLevel.CHAPTERS -> {
                                    itemsIndexed((1..50).toList()) { _, ch ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                            Text(text = "Chapter $ch", fontSize = 16.sp, modifier = Modifier.weight(1f))
                                            Text(text = ">", color = Color.Gray)
                                        }
                                        Divider()
                                    }
                                }
                                ReadingLevel.VERSES -> {
                                    itemsIndexed((1..31).toList()) { _, v ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                            Text(text = "Verse $v", fontSize = 16.sp, modifier = Modifier.weight(1f))
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }
                        if (readingLevel == ReadingLevel.CHAPTERS) Button(onClick = { readingLevel = ReadingLevel.BOOKS }, modifier = Modifier.fillMaxWidth()) { Text("← Back to Books") }
                        if (readingLevel == ReadingLevel.VERSES) Button(onClick = { readingLevel = ReadingLevel.CHAPTERS }, modifier = Modifier.fillMaxWidth()) { Text("← Back to Chapters") }
                        Button(onClick = {
                            when (readingLevel) {
                                ReadingLevel.BOOKS -> readingLevel = ReadingLevel.CHAPTERS
                                ReadingLevel.CHAPTERS -> { openReading(currentMode, selectedBook, 1); readingLevel = ReadingLevel.VERSES }
                                ReadingLevel.VERSES -> scope.launch { leftDrawerState.close() }
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Open →") }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("EW Bible", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Grow in the Word of God, every day.", color = Color(0xFFC9A227), fontSize = 11.sp)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { if (leftDrawerState.isClosed) leftDrawerState.open() else leftDrawerState.close() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showRightDrawer = !showRightDrawer }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
                    )
                },
                containerColor = bgColor
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(text = currentMode, color = Color(0xFFC9A227), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "$selectedBook $selectedChapter", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(text = if (isLoggedIn) syncStatus.ifEmpty { "Continuing where you left off" } else "Continuing where you left off", color = Color.Gray, fontSize = 11.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openReading("World English Bible", selectedBook, selectedChapter) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (currentMode == "World English Bible") Color(0xFFC9A227) else cardColor)) {
                            Text("World English Bible", color = if (currentMode == "World English Bible") Color.Black else textColor, fontSize = 11.sp)
                        }
                        Button(onClick = { openReading("Commentary", selectedBook, selectedChapter) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (currentMode == "Commentary") Color(0xFFC9A227) else cardColor)) {
                            Text("Commentary", color = if (currentMode == "Commentary") Color.Black else textColor, fontSize = 11.sp)
                        }
                        Button(onClick = { showSearch = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = cardColor)) {
                            Text("Search", color = textColor, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
                        // SUMMARY - ONLY IN WEB
                        if (currentMode == "World English Bible") {
                            summary?.let { s ->
                                item(key = "summary-$selectedBook-$selectedChapter") {
                                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5EEDD))) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("📖 CHAPTER SUMMARY - $selectedBook $selectedChapter", color = Color(0xFFC9A227), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(s.summary, color = Color.Black, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("What Happened", color = Color(0xFFC9A227), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(s.whatHappened, color = Color.Black, fontSize = fontSize.sp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("Application For You", color = Color(0xFFC9A227), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(s.application, color = Color.Black, fontSize = fontSize.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Scroll down to start reading ↓", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        // VERSES - REAL DB
                        if (currentMode == "World English Bible") {
                            itemsIndexed(versesList, key = { _, v -> "$selectedBook-$selectedChapter-${v.verse}-WEB" }) { _, verse ->
                                val hl = highlightedVerses[verse.verse]
                                Row(modifier = Modifier.fillMaxWidth().background(hl ?: Color.Transparent, RoundedCornerShape(8.dp)).combinedClickable(
                                    onClick = { selectedVerse = verse.verse; scope.launch { prefs.saveReading(currentMode, selectedBook, selectedChapter, verse.verse); if (isLoggedIn) syncManager.uploadProgress(currentMode, selectedBook, selectedChapter, verse.verse, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) } },
                                    onLongClick = { selectedVerse = verse.verse; showHighlightColors = true }
                                ).padding(vertical = 10.dp, horizontal = 8.dp)) {
                                    Text(text = "${verse.verse}  ", color = Color(0xFFC9A227), fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
                                    Text(text = verse.text, color = textColor, fontSize = fontSize.sp)
                                }
                            }
                        } else {
                            item(key = "commentary-$selectedBook-$selectedChapter") {
                                Text(text = commentaryHtml, color = textColor, fontSize = fontSize.sp, modifier = Modifier.padding(vertical = 10.dp))
                            }
                        }
                    }
                    if (showHighlightColors) {
                        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = cardColor)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Highlight Verse $selectedVerse - Choose Color", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    highlightColors.forEach { c ->
                                        Button(onClick = {
                                            selectedVerse?.let { v ->
                                                highlightedVerses = highlightedVerses + (v to c)
                                                scope.launch {
                                                    // 1. Local Room
                                                    db.bibleDao().saveHighlight(HighlightEntity(bookId = currentBookId, chapter = selectedChapter, verse = v, color = c.value.toString()))
                                                    // 2. CLOUD
                                                    if (isLoggedIn) syncManager.uploadHighlight(currentBookId, selectedChapter, v, c.value.toString())
                                                }
                                            }
                                            showHighlightColors = false
                                        }, modifier = Modifier.size(40.dp), colors = ButtonDefaults.buttonColors(containerColor = c)) {}
                                    }
                                }
                                TextButton(onClick = {
                                    selectedVerse?.let { v ->
                                        highlightedVerses = highlightedVerses - v
                                        scope.launch {
                                            // Local delete is via REPLACE - save empty not needed, we just remove from cloud
                                            if (isLoggedIn) syncManager.deleteHighlight(currentBookId, selectedChapter, v)
                                        }
                                    }
                                    showHighlightColors = false
                                }) { Text("Remove Highlight") }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showRightDrawer, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }, modifier = Modifier.align(Alignment.TopEnd)) {
            Card(modifier = Modifier.fillMaxHeight().width(300.dp), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White)) {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { showRightDrawer = false }) { Text("Close ✕") }
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        if (!isLoggedIn) {
                            Button(onClick = {
                                scope.launch {
                                    val result = googleAuth.signInWithGoogle()
                                    result.onSuccess { data ->
                                        val name = data.split("|")[0]
                                        val email = data.split("|").getOrNull(1) ?: ""
                                        prefs.saveUser(name, email)
                                        isLoggedIn = true
                                        downloadCloudData()
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC9A227))) {
                                Text("Login to Sync Data", color = Color.Black)
                            }
                        } else {
                            Text("Welcome, $savedName 👋", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Synced with your Gmail account - $syncStatus", color = Color.Gray, fontSize = 12.sp)
                            TextButton(onClick = {
                                scope.launch { googleAuth.signOut(); prefs.clearUser(); isLoggedIn = false }
                            }) { Text("Logout") }
                            Button(onClick = { downloadCloudData() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Sync Now ↻") }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Dark Mode"); Switch(checked = isDarkMode, onCheckedChange = { isDarkMode = it })
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Text Size", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("A-", fontSize = 13.sp)
                            Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 12f..28f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            Text("A+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showDailyVerse = !showDailyVerse; showNotes = false }, modifier = Modifier.fillMaxWidth()) { Text(if (showDailyVerse) "Daily Verse ▲" else "Daily Verse ▼") }
                        if (showDailyVerse) {
                            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5EEDD))) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("DAILY VERSE - Linked to World English Bible", color = Color(0xFFC9A227), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("\"Your word is a lamp for my feet, a light on my path.\"", fontSize = 15.sp, color = Color.Black)
                                    Text("Psalm 119:105 - WEB", color = Color.Gray, fontSize = 12.sp)
                                    TextButton(onClick = { openReading("World English Bible", "Psalms", 119); showRightDrawer = false }) { Text("Open in World English Bible →") }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showNotes = !showNotes; showDailyVerse = false }, modifier = Modifier.fillMaxWidth()) { Text(if (showNotes) "Notes ▲" else "Notes ▼") }
                        if (showNotes) {
                            OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Write your note...") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            Button(onClick = {
                                scope.launch {
                                    val verseToSave = selectedVerse ?: 1
                                    // 1. Local Room
                                    db.bibleDao().saveNote(NoteEntity(bookId = currentBookId, chapter = selectedChapter, verse = verseToSave, noteText = noteText, timestamp = System.currentTimeMillis()))
                                    // 2. CLOUD
                                    if (isLoggedIn) syncManager.uploadNote(currentBookId, selectedChapter, verseToSave, noteText)
                                    noteText = ""
                                    syncStatus = "Note Saved ✅"
                                }
                            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save Note") }
                            Text(if (isLoggedIn) "Notes sync to cloud ✅" else "Login to sync notes", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (showSearch) {
            AlertDialog(
                onDismissRequest = { showSearch = false },
                title = { Text("Search - World English Bible Only") },
                text = {
                    Column {
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Search WEB...") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Filter:", fontWeight = FontWeight.Bold)
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("All","Gospel","Prophecy","Old Testament","New Testament").forEach { f ->
                                FilterChip(selected = searchFilter == f, onClick = { searchFilter = f }, label = { Text(f, fontSize = 12.sp) })
                            }
                        }
                        Text("Searching in: $searchFilter - World English Bible", color = Color.Gray, fontSize = 12.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showSearch = false }) { Text("Search") } },
                dismissButton = { TextButton(onClick = { showSearch = false }) { Text("Close") } }
            )
        }
    }
}
