package com.ewbible.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.*
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ReadingLevel { BOOKS, CHAPTERS, VERSES }

// ------------------------------
// Verse reference parsing
// ------------------------------
private val biblesBookAliases = mapOf(
    "Genesis" to "Genesis",
    "Exodus" to "Exodus",
    "Leviticus" to "Leviticus",
    "Numbers" to "Numbers",
    "Deuteronomy" to "Deuteronomy",
    "Joshua" to "Joshua",
    "Judges" to "Judges",
    "Ruth" to "Ruth",
    "1 Samuel" to "1 Samuel",
    "2 Samuel" to "2 Samuel",
    "1 Kings" to "1 Kings",
    "2 Kings" to "2 Kings",
    "1 Chronicles" to "1 Chronicles",
    "2 Chronicles" to "2 Chronicles",
    "Ezra" to "Ezra",
    "Nehemiah" to "Nehemiah",
    "Esther" to "Esther",
    "Job" to "Job",
    "Psalms" to "Psalms",
    "Psalm" to "Psalms",
    "Proverbs" to "Proverbs",
    "Ecclesiastes" to "Ecclesiastes",
    "Song of Solomon" to "Song of Solomon",
    "Song of Songs" to "Song of Solomon",
    "Isaiah" to "Isaiah",
    "Jeremiah" to "Jeremiah",
    "Lamentations" to "Lamentations",
    "Ezekiel" to "Ezekiel",
    "Daniel" to "Daniel",
    "Hosea" to "Hosea",
    "Joel" to "Joel",
    "Amos" to "Amos",
    "Obadiah" to "Obadiah",
    "Jonah" to "Jonah",
    "Micah" to "Micah",
    "Nahum" to "Nahum",
    "Habakkuk" to "Habakkuk",
    "Zephaniah" to "Zephaniah",
    "Haggai" to "Haggai",
    "Zechariah" to "Zechariah",
    "Malachi" to "Malachi",
    "Matthew" to "Matthew",
    "Mark" to "Mark",
    "Luke" to "Luke",
    "John" to "John",
    "Acts" to "Acts",
    "Romans" to "Romans",
    "1 Corinthians" to "1 Corinthians",
    "2 Corinthians" to "2 Corinthians",
    "Galatians" to "Galatians",
    "Ephesians" to "Ephesians",
    "Philippians" to "Philippians",
    "Colossians" to "Colossians",
    "1 Thessalonians" to "1 Thessalonians",
    "2 Thessalonians" to "2 Thessalonians",
    "1 Timothy" to "1 Timothy",
    "2 Timothy" to "2 Timothy",
    "Titus" to "Titus",
    "Philemon" to "Philemon",
    "Hebrews" to "Hebrews",
    "James" to "James",
    "1 Peter" to "1 Peter",
    "2 Peter" to "2 Peter",
    "1 John" to "1 John",
    "2 John" to "2 John",
    "3 John" to "3 John",
    "Jude" to "Jude",
    "Revelation" to "Revelation"
)

private val verseReferenceRegex: Regex by lazy {
    val bookAlternation = biblesBookAliases.keys
        .sortedByDescending { it.length }
        .joinToString("|") { book ->
            book.split(" ").joinToString("\\s+") { Regex.escape(it) }
        }

    Regex(
        """(?<![A-Za-z])($bookAlternation)\s+(\d{1,3}):(\d{1,3})(?:\s*[-–—]\s*(?:(\d{1,3}):)?(\d{1,3}))?(?!\d)""",
        RegexOption.IGNORE_CASE
    )
}

data class VerseRef(
    val bookName: String,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val chapterEnd: Int = chapter
)

fun parseVerseRefs(text: String): List<Pair<IntRange, VerseRef>> {
    return verseReferenceRegex.findAll(text).mapNotNull { match ->
        val rawBook = match.groupValues[1]
        val canonicalBook = biblesBookAliases.entries
            .firstOrNull { it.key.equals(rawBook, ignoreCase = true) }
            ?.value ?: return@mapNotNull null

        val chapterStart = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        val verseStart = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
        val chapterEnd = match.groupValues[4].toIntOrNull() ?: chapterStart
        val verseEnd = match.groupValues[5].toIntOrNull() ?: verseStart

        if (chapterStart < 1 || verseStart < 1 ||
            chapterEnd < chapterStart || verseEnd < 1 ||
            (chapterEnd == chapterStart && verseEnd < verseStart)
        ) {
            return@mapNotNull null
        }

        match.range to VerseRef(
            bookName = canonicalBook,
            chapter = chapterStart,
            verseStart = verseStart,
            verseEnd = verseEnd,
            chapterEnd = chapterEnd
        )
    }.toList()
}

@Composable
fun ClickableCommentaryText(
    text: String,
    textColor: Color,
    fontSize: TextUnit,
    onVerseClick: (VerseRef) -> Unit
) {
    val refs = remember(text) { parseVerseRefs(text) }
    val annotatedString = remember(text, refs) {
        buildAnnotatedString {
            var last = 0
            refs.forEach { (range, _) ->
                append(text.substring(last, range.first))
                pushStringAnnotation(tag = "VERSE", annotation = refs.indexOfFirst { it.first == range }.toString())
                withStyle(
                    SpanStyle(
                        color = Color(0xFFC9A227),
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(text.substring(range))
                }
                pop()
                last = range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }

    ClickableText(
        text = annotatedString,
        style = TextStyle(color = textColor, fontSize = fontSize),
        onClick = { offset ->
            val annotation = annotatedString.getStringAnnotations("VERSE", offset, offset).firstOrNull()
            if (annotation != null) {
                val index = annotation.item.toInt()
                val ref = refs[index].second
                onVerseClick(ref)
            }
        }
    )
}

// ------------------------------
// Main screen
// ------------------------------
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val googleAuth = remember { GoogleAuth(context) }
    val syncManager = remember { SyncManager() }
    val scope = rememberCoroutineScope()

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
    var isFullScreen by remember { mutableStateOf(false) }

    // Android Back button - exit full screen first
    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    LaunchedEffect(savedMode, savedBook, savedChapter, savedVerse) {
        if (isFirstLoad) {
            currentMode = savedMode
            selectedBook = savedBook
            selectedChapter = savedChapter
            selectedVerse = savedVerse
            isFirstLoad = false
        }
    }

    val booksList by db.bibleDao().getAllBooks().collectAsState(initial = emptyList())
    val currentBookId = booksList.find { it.name == selectedBook }?.id ?: 1
    val versesList by db.bibleDao().getVerses(currentBookId, selectedChapter).collectAsState(initial = emptyList())

    var summary by remember(selectedBook, selectedChapter) { mutableStateOf<SummaryEntity?>(null) }
    LaunchedEffect(selectedBook, selectedChapter, currentBookId) {
        try {
            summary = db.bibleDao().getSummary(currentBookId, selectedChapter)
        } catch (_: Exception) {
            summary = null
        }
    }

    var commentaryHtml by remember(selectedBook, selectedChapter) { mutableStateOf("") }
    LaunchedEffect(selectedBook, selectedChapter, currentBookId, currentMode) {
        try {
            if (currentMode == "Endurance" || currentMode == "Commentary") {
                commentaryHtml = db.bibleDao().getCommentary(currentBookId, selectedChapter)?.getHtml() ?: ""
            }
        } catch (_: Exception) {
            commentaryHtml = ""
        }
    }

    var popupRef by remember { mutableStateOf<VerseRef?>(null) }
    var popupText by remember { mutableStateOf("") }

    fun loadVersePopup(ref: VerseRef) {
        popupRef = ref
        popupText = "Loading..."

        scope.launch {
            try {
                val book = booksList.firstOrNull { it.name.equals(ref.bookName, ignoreCase = true) }
                    ?: booksList.firstOrNull { it.name.equals(ref.bookName.replace("1 ", "").replace("2 ", ""), ignoreCase = true) }

                if (book == null) {
                    popupText = "Book not found in the Bible database."
                    return@launch
                }

                val results = buildList {
                    for (chapter in ref.chapter..ref.chapterEnd) {
                        val chapterVerses = db.bibleDao().getVerses(book.id, chapter).first()
                        val firstVerse = if (chapter == ref.chapter) ref.verseStart else 1
                        val lastVerse = if (chapter == ref.chapterEnd) ref.verseEnd else Int.MAX_VALUE

                        chapterVerses.filter { it.verse in firstVerse..lastVerse }.forEach { verse ->
                            add("${book.name} $chapter:${verse.verse}  ${verse.text}")
                        }
                    }
                }

                popupText = if (results.isEmpty()) "Verse not found." else results.joinToString("\n")
            } catch (_: Exception) {
                popupText = "Could not load verse."
            }
        }
    }

    @Composable
    fun VersePopupDialog() {
        popupRef?.let { ref ->
            AlertDialog(
                onDismissRequest = { popupRef = null },
                title = {
                    Text(
                        "${ref.bookName} ${ref.chapter}:${ref.verseStart}${if (ref.verseEnd != ref.verseStart && ref.chapter == ref.chapterEnd) "-${ref.verseEnd}" else ""}",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = { Text(popupText) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val r = popupRef ?: return@TextButton
                            popupRef = null
                            isFullScreen = false
                            currentMode = "World English Bible"
                            selectedBook = r.bookName
                            selectedChapter = r.chapter
                            selectedVerse = r.verseStart
                            scope.launch {
                                try {
                                    prefs.saveReading("World English Bible", r.bookName, r.chapter, r.verseStart)
                                    prefs.saveScroll(0, 0)
                                    listState.scrollToItem(0, 0)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    ) {
                        Text("Open in Bible →")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { popupRef = null }) {
                        Text("Close")
                    }
                }
            )
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

    LaunchedEffect(savedDark, savedFont, savedName) {
        if (isFirstLoad) {
            isDarkMode = savedDark
            fontSize = savedFont
        }
        isLoggedIn = savedName != "Guest"
    }

    val dbHighlights by db.bibleDao().getHighlights(currentBookId, selectedChapter).collectAsState(initial = emptyList())
    LaunchedEffect(dbHighlights) {
        try {
            highlightedVerses = dbHighlights.associate { hl ->
                hl.verse to Color(runCatching { hl.color.toLong() }.getOrDefault(0xFFFFFF00))
            }
        } catch (_: Exception) {
            highlightedVerses = emptyMap()
        }
    }

    val bgColor = if (isDarkMode) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val textColor = if (isDarkMode) Color(0xFFF5F0E6) else Color(0xFF1E1E22)
    val cardColor = if (isDarkMode) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val goldColor = Color(0xFFC9A227)

    val listState = rememberLazyListState()
    var scrollRestored by remember { mutableStateOf(false) }

    LaunchedEffect(savedScrollIndex) {
        if (!scrollRestored && savedScrollIndex > 0) {
            try {
                listState.scrollToItem(savedScrollIndex, savedScrollOffset)
            } catch (_: Exception) {
            }
            scrollRestored = true
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        kotlinx.coroutines.delay(500)
        scope.launch {
            try {
                val verseNum = listState.firstVisibleItemIndex + 1
                prefs.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                prefs.saveReading(currentMode, selectedBook, selectedChapter, verseNum)
                if (isLoggedIn) {
                    try {
                        syncManager.uploadProgress(
                            currentMode,
                            selectedBook,
                            selectedChapter,
                            verseNum,
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset
                        )
                        syncStatus = "Synced ✅"
                    } catch (_: Exception) {
                        syncStatus = ""
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isDarkMode, fontSize) {
        try {
            prefs.saveSettings(isDarkMode, fontSize)
        } catch (_: Exception) {
        }
    }

    fun openReading(mode: String, book: String, chapter: Int) {
        currentMode = mode
        selectedBook = book
        selectedChapter = chapter
        selectedVerse = 1
        scrollRestored = true
        scope.launch {
            try {
                prefs.saveReading(mode, book, chapter, 1)
                prefs.saveScroll(0, 0)
                if (isLoggedIn) syncManager.uploadProgress(mode, book, chapter, 1, 0, 0)
                listState.scrollToItem(0, 0)
                leftDrawerState.close()
            } catch (_: Exception) {
            }
        }
    }

    fun downloadCloudData() {
        scope.launch {
            syncStatus = "Syncing..."
            try {
                val cloudProgress = syncManager.getProgress()
                if (cloudProgress != null) {
                    if (selectedBook == "Genesis" && selectedChapter == 1 && savedScrollIndex == 0) {
                        currentMode = cloudProgress.mode
                        selectedBook = cloudProgress.book
                        selectedChapter = cloudProgress.chapter
                        selectedVerse = cloudProgress.verse
                        prefs.saveReading(
                            cloudProgress.mode,
                            cloudProgress.book,
                            cloudProgress.chapter,
                            cloudProgress.verse
                        )
                        prefs.saveScroll(cloudProgress.scrollIndex, cloudProgress.scrollOffset)
                    }
                }

                val cloudHighlights = syncManager.getHighlights()
                cloudHighlights.forEach { hl ->
                    try {
                        db.bibleDao().saveHighlight(
                            HighlightEntity(
                                bookId = hl.bookId,
                                chapter = hl.chapter,
                                verse = hl.verse,
                                color = hl.color
                            )
                        )
                    } catch (_: Exception) {
                    }
                }

                val cloudNotes = syncManager.getNotes()
                cloudNotes.forEach { note ->
                    try {
                        db.bibleDao().saveNote(
                            NoteEntity(
                                bookId = note.bookId,
                                chapter = note.chapter,
                                verse = note.verse,
                                noteText = note.noteText,
                                timestamp = note.timestamp
                            )
                        )
                    } catch (_: Exception) {
                    }
                }

                syncStatus = "Synced ✅"
            } catch (_: Exception) {
                syncStatus = "Sync failed"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // ================= FULL SCREEN MODE =================
        if (isFullScreen) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(bgColor).padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                if (currentMode == "World English Bible") {
                    summary?.let { s ->
                        item(key = "summary-full-$selectedBook-$selectedChapter") {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5EEDD))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "📖 CHAPTER SUMMARY - $selectedBook $selectedChapter",
                                        color = goldColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(s.summary, color = Color.Black, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("What Happened", color = goldColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(s.whatHappened, color = Color.Black, fontSize = fontSize.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Application For You", color = goldColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(s.application, color = Color.Black, fontSize = fontSize.sp)
                                }
                            }
                        }
                    }

                    if (versesList.isEmpty()) {
                        item {
                            Text(
                                "Loading World English Bible...",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    }

                    itemsIndexed(versesList, key = { _, v -> "full-$selectedBook-$selectedChapter-${v.verse}-WEB" }) { _, verse ->
                        val hl = highlightedVerses[verse.verse]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(hl ?: Color.Transparent, RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { selectedVerse = verse.verse },
                                    onLongClick = { selectedVerse = verse.verse; showHighlightColors = true }
                                )
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Text(text = "${verse.verse}  ", color = goldColor, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
                            Text(text = verse.text, color = textColor, fontSize = fontSize.sp)
                        }
                    }
                } else {
                    item(key = "commentary-full-$selectedBook-$selectedChapter") {
                        if (commentaryHtml.isEmpty()) {
                            Text("Loading Endurance...", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                        } else {
                            ClickableCommentaryText(
                                text = commentaryHtml,
                                textColor = textColor,
                                fontSize = fontSize.sp,
                                onVerseClick = { ref -> loadVersePopup(ref) }
                            )
                        }
                    }
                }
            }

            if (showHighlightColors) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Highlight Verse $selectedVerse - Choose Color", fontWeight = FontWeight.Bold, color = textColor)

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                highlightColors.forEach { c ->
                                    Button(
                                        onClick = {
                                            selectedVerse?.let { v ->
                                                highlightedVerses = highlightedVerses + (v to c)
                                                scope.launch {
                                                    try {
                                                        db.bibleDao().saveHighlight(
                                                            HighlightEntity(
                                                                bookId = currentBookId,
                                                                chapter = selectedChapter,
                                                                verse = v,
                                                                color = c.value.toString()
                                                            )
                                                        )
                                                        if (isLoggedIn) syncManager.uploadHighlight(currentBookId, selectedChapter, v, c.value.toString())
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            }
                                            showHighlightColors = false
                                        },
                                        modifier = Modifier.size(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = c)
                                    ) {}
                                }
                            }

                            TextButton(onClick = { showHighlightColors = false }) {
                                Text("Close")
                            }
                        }
                    }
                }
            }

            VersePopupDialog()
            return@Box
        }

        // ================= NORMAL HOME MODE =================
        ModalNavigationDrawer(
            drawerState = leftDrawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(text = currentMode, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Grow in the Word of God, every day.", color = goldColor, fontSize = 12.sp)
                        if (syncStatus.isNotEmpty()) {
                            Text(text = syncStatus, color = Color.Gray, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = when (readingLevel) {
                                ReadingLevel.BOOKS -> "Book"
                                ReadingLevel.CHAPTERS -> "$selectedBook - Chapter"
                                ReadingLevel.VERSES -> "$selectedBook $selectedChapter - Verse"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            when (readingLevel) {
                                ReadingLevel.BOOKS -> {
                                    itemsIndexed(booksList) { _, book ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp)
                                                .clickable {
                                                    selectedBook = book.name
                                                    readingLevel = ReadingLevel.CHAPTERS
                                                }
                                        ) {
                                            Text(text = book.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                            Text(text = ">", color = Color.Gray)
                                        }
                                        Divider()
                                    }
                                }

                                ReadingLevel.CHAPTERS -> {
                                    itemsIndexed((1..50).toList()) { _, ch ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp)
                                                .clickable {
                                                    openReading(currentMode, selectedBook, ch)
                                                    readingLevel = ReadingLevel.VERSES
                                                }
                                        ) {
                                            Text(text = "Chapter $ch", fontSize = 16.sp, modifier = Modifier.weight(1f))
                                            Text(text = ">", color = Color.Gray)
                                        }
                                        Divider()
                                    }
                                }

                                ReadingLevel.VERSES -> {
                                    itemsIndexed((1..31).toList()) { _, v ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                        ) {
                                            Text(text = "Verse $v", fontSize = 16.sp, modifier = Modifier.weight(1f))
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }

                        if (readingLevel == ReadingLevel.CHAPTERS) {
                            Button(onClick = { readingLevel = ReadingLevel.BOOKS }, modifier = Modifier.fillMaxWidth()) {
                                Text("← Back to Books")
                            }
                        }
                        if (readingLevel == ReadingLevel.VERSES) {
                            Button(onClick = { readingLevel = ReadingLevel.CHAPTERS }, modifier = Modifier.fillMaxWidth()) {
                                Text("← Back to Chapters")
                            }
                        }

                        Button(
                            onClick = {
                                when (readingLevel) {
                                    ReadingLevel.BOOKS -> readingLevel = ReadingLevel.CHAPTERS
                                    ReadingLevel.CHAPTERS -> {
                                        openReading(currentMode, selectedBook, 1)
                                        readingLevel = ReadingLevel.VERSES
                                    }
                                    ReadingLevel.VERSES -> scope.launch { leftDrawerState.close() }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("Open →")
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("EW Bible", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                                Text("Grow in the Word of God, every day.", color = goldColor, fontSize = 11.sp)
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (leftDrawerState.isClosed) leftDrawerState.open()
                                        else leftDrawerState.close()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showRightDrawer = !showRightDrawer }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = textColor)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = bgColor,
                            titleContentColor = textColor
                        )
                    )
                },
                containerColor = bgColor
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().