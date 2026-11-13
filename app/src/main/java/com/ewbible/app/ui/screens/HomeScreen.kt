package com.ewbible.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.*
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// LIGHT DIMMED ERGONOMIC - warm paper, not bright white, not black
private val dimBackground = Color(0xFF202328)
private val dimSurface = Color(0xFF2A2F35)
private val dimText = Color(0xFFE6DDC9)
private val paperBackground = Color(0xFFEDE6D3)
private val paperSurface = Color(0xFFE2D9C2)
private val inkText = Color(0xFF2B2820)
private val readingGold = Color(0xFFB78F3A)
private val linkBlue = Color(0xFF2F5D8A)

private val gospelBooks = setOf("Matthew", "Mark", "Luke", "John")
private val prophecyBooks = setOf(
    "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos",
    "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah",
    "Malachi", "Revelation"
)

// All 66 books in order - for OT/NT grouping + verse-link detection
private val allBookNamesInOrder = listOf(
    "Genesis","Exodus","Leviticus","Numbers","Deuteronomy","Joshua","Judges","Ruth","1 Samuel","2 Samuel","1 Kings","2 Kings","1 Chronicles","2 Chronicles","Ezra","Nehemiah","Esther","Job","Psalms","Proverbs","Ecclesiastes","Song of Solomon","Isaiah","Jeremiah","Lamentations","Ezekiel","Daniel","Hosea","Joel","Amos","Obadiah","Jonah","Micah","Nahum","Habakkuk","Zephaniah","Haggai","Zechariah","Malachi",
    "Matthew","Mark","Luke","John","Acts","Romans","1 Corinthians","2 Corinthians","Galatians","Ephesians","Philippians","Colossians","1 Thessalonians","2 Thessalonians","1 Timothy","2 Timothy","Titus","Philemon","Hebrews","James","1 Peter","2 Peter","1 John","2 John","3 John","Jude","Revelation"
)
private val oldTestamentBooks = allBookNamesInOrder.take(39).toSet()
private val bookAliases = mapOf(
    "Gen" to "Genesis","Exod" to "Exodus","Ex" to "Exodus","Lev" to "Leviticus","Num" to "Numbers","Deut" to "Deuteronomy","Josh" to "Joshua","Judg" to "Judges","Ruth" to "Ruth",
    "1 Sam" to "1 Samuel","2 Sam" to "2 Samuel","1 Kings" to "1 Kings","2 Kings" to "2 Kings","1 Chr" to "1 Chronicles","2 Chr" to "2 Chronicles","Ezra" to "Ezra","Neh" to "Nehemiah","Esth" to "Esther",
    "Job" to "Job","Ps" to "Psalms","Psalm" to "Psalms","Prov" to "Proverbs","Eccl" to "Ecclesiastes","Song" to "Song of Solomon","Isa" to "Isaiah","Jer" to "Jeremiah","Lam" to "Lamentations","Ezek" to "Ezekiel","Dan" to "Daniel",
    "Hos" to "Hosea","Joel" to "Joel","Amos" to "Amos","Obad" to "Obadiah","Jonah" to "Jonah","Mic" to "Micah","Nah" to "Nahum","Hab" to "Habakkuk","Zeph" to "Zephaniah","Hag" to "Haggai","Zech" to "Zechariah","Mal" to "Malachi",
    "Matt" to "Matthew","Mk" to "Mark","Lk" to "Luke","Jn" to "John","Acts" to "Acts","Rom" to "Romans","1 Cor" to "1 Corinthians","2 Cor" to "2 Corinthians","Gal" to "Galatians","Eph" to "Ephesians","Phil" to "Philippians","Col" to "Colossians",
    "1 Thess" to "1 Thessalonians","2 Thess" to "2 Thessalonians","1 Tim" to "1 Timothy","2 Tim" to "2 Timothy","Titus" to "Titus","Philem" to "Philemon","Heb" to "Hebrews","Jas" to "James","1 Pet" to "1 Peter","2 Pet" to "2 Peter","1 Jn" to "1 John","2 Jn" to "2 John","3 Jn" to "3 John","Jude" to "Jude","Rev" to "Revelation"
)

data class SearchHit(val bookName: String, val chapter: Int, val verse: Int, val text: String)

private fun stripHtml(html: String): String {
    var t = html
        .replace("<hr[^>]*>".toRegex(RegexOption.IGNORE_CASE), "\n---\n")
        .replace("</p>".toRegex(RegexOption.IGNORE_CASE), "\n\n")
        .replace("<br[^>]*>".toRegex(RegexOption.IGNORE_CASE), "\n")
        .replace("</h[1-6]>".toRegex(RegexOption.IGNORE_CASE), "\n\n")
        .replace("<[^>]+>".toRegex(), "")
    t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
    return t.replace(Regex("\n{3,}"), "\n\n").trim()
}

private fun buildCommentaryAnnotated(text: String, baseColor: Color, fontSizeSp: Float): AnnotatedString {
    val clean = stripHtml(text)
    // Matches: Genesis 1:1, Deut 1:5, 1 Kings 2:3, John 3:16, Ps 23:1 etc.
    val pattern = Regex("""\b((?:[1-3]\s)?(?:Genesis|Exodus|Leviticus|Numbers|Deuteronomy|Joshua|Judges|Ruth|Samuel|Kings|Chronicles|Ezra|Nehemiah|Esther|Job|Psalms?|Proverbs|Ecclesiastes|Song of Solomon|Isaiah|Jeremiah|Lamentations|Ezekiel|Daniel|Hosea|Joel|Amos|Obadiah|Jonah|Micah|Nahum|Habakkuk|Zephaniah|Haggai|Zechariah|Malachi|Matthew|Mark|Luke|John|Acts|Romans|Corinthians|Galatians|Ephesians|Philippians|Colossians|Thessalonians|Timothy|Titus|Philemon|Hebrews|James|Peter|Jude|Revelation|Gen|Exod|Lev|Num|Deut|Josh|Judg|Chr|Neh|Esth|Prov|Eccl|Song|Isa|Jer|Lam|Ezek|Dan|Hos|Obad|Mic|Nah|Hab|Zeph|Hag|Zech|Mal|Matt|Mk|Lk|Jn|Rom|Cor|Gal|Eph|Phil|Col|Thess|Tim|Heb|Jas|Pet|Rev))\s+\d+:\d+(?:-\d+)?)""")
    return buildAnnotatedString {
        append(clean)
        pattern.findAll(clean).forEach { m ->
            addStyle(SpanStyle(color = linkBlue, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
            addStringAnnotation("VERSE_REF", m.value, m.range.first, m.range.last + 1)
        }
    }
}

private fun parseVerseRef(ref: String): Triple<String, Int, Int>? {
    val m = Regex("""^(.+?)\s+(\d+):(\d+)""").find(ref.trim()) ?: return null
    var book = m.groupValues[1].trim()
    // Normalize: "1 Sam" -> "1 Samuel", "Psalm" -> "Psalms", etc.
    book = bookAliases[book] ?: book
    if (book !in allBookNamesInOrder) {
        // Try adding missing number prefix handling, e.g. "Samuel" alone - keep as is
        val found = allBookNamesInOrder.firstOrNull { it.equals(book, ignoreCase = true) } ?: return null
        book = found
    }
    return Triple(book, m.groupValues[2].toIntOrNull() ?: 1, m.groupValues[3].toIntOrNull() ?: 1)
}

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
    val savedDark by prefs.isDarkMode.collectAsState(initial = false)
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
    var isDarkMode by remember { mutableStateOf(false) }
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

    val currentBookId = books.firstOrNull { it.name == selectedBook }?.id ?: books.firstOrNull()?.id ?: 1
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
                syncManager.uploadProgress(currentMode, selectedBook, selectedChapter, selectedVerse, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            }
        } catch (_: Exception) {}
    }

    fun selectBook(bookName: String) {
        selectedBook = bookName; selectedChapter = 1; selectedVerse = 1
        scope.launch { prefs.saveReading(currentMode, bookName, 1, 1); prefs.saveScroll(0, 0); listState.scrollToItem(0, 0) }
    }
    fun selectChapter(chapter: Int) {
        selectedChapter = chapter.coerceAtLeast(1); selectedVerse = 1
        scope.launch { prefs.saveReading(currentMode, selectedBook, selectedChapter, 1); prefs.saveScroll(0, 0); listState.scrollToItem(0, 0) }
    }
    fun jumpToVerse(book: String, chapter: Int, verse: Int) {
        selectedBook = book; selectedChapter = chapter; selectedVerse = verse
        currentMode = "World English Bible"
        scope.launch { prefs.saveReading(currentMode, book, chapter, verse); prefs.saveScroll((verse - 1).coerceAtLeast(0), 0); listState.scrollToItem((verse - 1).coerceAtLeast(0), 0) }
    }

    LaunchedEffect(savedMode, savedBook, savedChapter, savedDark, savedFont) {
        if (!initialized) { currentMode = savedMode; selectedBook = savedBook; selectedChapter = savedChapter; selectedVerse = 1; isDarkMode = savedDark; fontSize = savedFont; initialized = true }
    }
    LaunchedEffect(currentBookId, selectedChapter) {
        try { summary = dao.getSummary(currentBookId, selectedChapter) } catch (_: Exception) { summary = null }
    }
    LaunchedEffect(currentBookId, selectedChapter, currentMode) {
        try { commentaryHtml = if (currentMode == "Endurance") dao.getCommentary(currentBookId, selectedChapter)?.getHtml() ?: "" else "" } catch (_: Exception) { commentaryHtml = "" }
    }
    LaunchedEffect(currentBookId, selectedChapter) {
        try {
            dao.getHighlights(currentBookId, selectedChapter).collect { list ->
                highlightedVerses = list.associate { hl -> hl.verse to Color(runCatching { hl.color.toLong(16).toInt() }.getOrDefault(0xFFFFE082.toInt())) }
            }
        } catch (_: Exception) { highlightedVerses = emptyMap() }
    }
    LaunchedEffect(searchQuery, searchScope, books) {
        if (searchQuery.isBlank()) { searchResults = emptyList(); return@LaunchedEffect }
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
                rows.forEach { verse -> if (verse.text.contains(searchQuery, ignoreCase = true)) results += SearchHit(book.name, chapter, verse.verse, verse.text) }
            }
        }
        searchResults = results
    }
    LaunchedEffect(initialized, savedScrollIndex, savedScrollOffset) {
        if (initialized && !scrollRestored && verses.isNotEmpty()) {
            try { listState.scrollToItem(savedScrollIndex.coerceIn(0, verses.size - 1), savedScrollOffset); scrollRestored = true } catch (_: Exception) {}
        }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, initialized) {
        if (initialized) { kotlinx.coroutines.delay(600); selectedVerse = listState.firstVisibleItemIndex + 1; scope.launch { saveProgress() } }
    }

    val modeLabel = when (currentMode) { "Endurance" -> "Endurance"; "Search" -> "Search"; else -> "Bible" }

    // Books sorted OT then NT by canonical order - fixes drawer cut-off
    val orderedBooks = remember(books) {
        books.sortedWith(compareBy({ if (it.name in oldTestamentBooks || it.testament.contains("Old", true)) 0 else 1 }, { allBookNamesInOrder.indexOf(it.name).takeIf { i -> i >= 0 } ?: 999 }, { it.id }))
    }
    val oldBooks = orderedBooks.filter { it.name in oldTestamentBooks || it.testament.contains("Old", true) }
    val newBooks = orderedBooks.filter { it !in oldBooks }

    val drawerContent: @Composable () -> Unit = {
        ModalDrawerSheet(drawerContainerColor = bgColor, modifier = Modifier.fillMaxHeight().width(320.dp)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                item { Text("Select Book", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)) }
                if (books.isEmpty()) { item { Text("Loading books...", color = Color.Gray) } }
                else {
                    item { Text("OLD TESTAMENT", color = readingGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(oldBooks) { book ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectBook(book.name); drawerOpen = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(book.name, color = if (book.name == selectedBook) readingGold else textColor, modifier = Modifier.weight(1f))
                            if (book.name == selectedBook) Text("●", color = readingGold)
                        }
                    }
                    item { Text("NEW TESTAMENT", color = readingGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(newBooks) { book ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectBook(book.name); drawerOpen = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(book.name, color = if (book.name == selectedBook) readingGold else textColor, modifier = Modifier.weight(1f))
                            if (book.name == selectedBook) Text("●", color = readingGold)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)); Text("Chapter $selectedChapter", color = textColor, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)) }
                items((1..chapterCount).toList()) { chapter ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { selectChapter(chapter); drawerOpen = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Chapter $chapter", color = if (chapter == selectedChapter) readingGold else textColor, modifier = Modifier.weight(1f))
                        if (chapter == selectedChapter) Text("●", color = readingGold)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(containerColor = bgColor) { padding ->
            Column(modifier = Modifier.fillMaxSize().background(bgColor).padding(padding)) {
                // ONE TOP ROW: Menu + Tabs + Settings - NO TopAppBar, NO pills, NO duplicate header
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { drawerOpen = true }) { Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor) }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                        listOf("Bible", "Endurance", "Search").forEach { tab ->
                            val selected = tab == modeLabel
                            Box(modifier = Modifier.weight(1f).clickable {
                                currentMode = when (tab) { "Bible" -> "World English Bible"; "Endurance" -> "Endurance"; "Search" -> "Search"; else -> "World English Bible" }
                                scope.launch { saveProgress() }
                            }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(tab, color = if (selected) readingGold else textColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 1)
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = textColor) }
                        DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                            DropdownMenuItem(text = { Text(if (savedEmail.isBlank()) "Google sign in" else "Signed in: ${savedEmail}") }, onClick = {
                                settingsOpen = false
                                scope.launch {
                                    if (googleAuth.currentUser() != null) { googleAuth.signOut(); prefs.clearUser(); signInMessage = "Signed out" }
                                    else {
                                        val result = googleAuth.signInWithGoogle()
                                        result.onSuccess { val parts = it.split("|", limit = 2); val userName = parts.getOrElse(0) { "User" }; val email = parts.getOrElse(1) { "" }; prefs.saveUser(userName, email); signInMessage = "Signed in as $userName" }
                                        result.onFailure { signInMessage = it.localizedMessage ?: "Google sign in failed" }
                                    }
                                }
                            })
                            DropdownMenuItem(text = { Text("Dark mode") }, trailingIcon = { Switch(checked = isDarkMode, onCheckedChange = { dark -> isDarkMode = dark; scope.launch { prefs.saveSettings(dark, fontSize) } }) }, onClick = {})
                            // Font slider: A- [ slider ] A+
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("A-", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Slider(value = fontSize, onValueChange = { fontSize = it; scope.launch { prefs.saveSettings(isDarkMode, it) } }, valueRange = 12f..24f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                                    Text("A+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }, onClick = {})
                            DropdownMenuItem(text = { Text("Daily Verse") }, onClick = {
                                settingsOpen = false; val verse = verses.randomOrNull()
                                dailyVerseText = if (verse != null) "${selectedBook} ${selectedChapter}:${verse.verse}\n\n${verse.text}" else "No verse is available yet."
                                dialog = "daily"
                            })
                            DropdownMenuItem(text = { Text("Notes") }, onClick = { settingsOpen = false; dialog = "notes" })
                            DropdownMenuItem(text = { Text("Highlights") }, onClick = { settingsOpen = false; dialog = "highlights" })
                        }
                    }
                }
                // Single title - ONCE
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$selectedBook $selectedChapter", color = textColor, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.weight(1f))
                }
                when (currentMode) {
                    "Search" -> {
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search verse text") })
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("All", "Gospel", "Prophecy", "Old Testament", "New Testament").forEach { filter ->
                                    FilterChip(selected = searchScope == filter, onClick = { searchScope = filter }, label = { Text(filter, fontSize = 10.sp) })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (searchQuery.isBlank()) Text("Type a word or phrase to search.", color = textColor)
                            else LazyColumn(Modifier.fillMaxSize()) {
                                items(searchResults) { hit ->
                                    Column(modifier = Modifier.fillMaxWidth().clickable { selectedBook = hit.bookName; selectedChapter = hit.chapter; selectedVerse = hit.verse; currentMode = "World English Bible"; scope.launch { saveProgress() } }.padding(vertical = 8.dp)) {
                                        Text("${hit.bookName} ${hit.chapter}:${hit.verse}", color = readingGold, fontWeight = FontWeight.Bold)
                                        Text(hit.text, color = textColor)
                                    }
                                }
                            }
                        }
                    }
                    "Endurance" -> {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            item {
                                if (commentaryHtml.isBlank()) Text("Loading Endurance...", color = Color.Gray)
                                else {
                                    val annotated = remember(commentaryHtml, fontSize) { buildCommentaryAnnotated(commentaryHtml, textColor, fontSize) }
                                    ClickableText(text = annotated, style = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = fontSize.sp), onClick = { offset ->
                                        annotated.getStringAnnotations("VERSE_REF", offset, offset).firstOrNull()?.let { ann ->
                                            parseVerseRef(ann.item)?.let { (b, c, v) -> jumpToVerse(b, c, v) }
                                        }
                                    })
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                            summary?.let { item ->
                                item {
                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor)) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Chapter Summary", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            Text(item.summary, color = textColor)
                                            Text("What Happened", color = readingGold, fontWeight = FontWeight.Bold)
                                            Text(item.whatHappened, color = textColor)
                                            Text("Application", color = readingGold, fontWeight = FontWeight.Bold)
                                            Text(item.application, color = textColor)
                                        }
                                    }
                                }
                            }
                            itemsIndexed(verses, key = { _, verse -> "${currentBookId}-${selectedChapter}-${verse.verse}" }) { _, verse ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).background(color = highlightedVerses[verse.verse] ?: Color.Transparent, shape = RoundedCornerShape(8.dp)).clickable {
                                    scope.launch {
                                        val existing = dao.getHighlights(currentBookId, selectedChapter).first().firstOrNull { it.verse == verse.verse }
                                        if (existing == null) { val color = "FFFFE082"; dao.saveHighlight(HighlightEntity(currentBookId, selectedChapter, verse.verse, color)); if (googleAuth.currentUser() != null) syncManager.uploadHighlight(currentBookId, selectedChapter, verse.verse, color) }
                                        else { dao.deleteHighlight(currentBookId, selectedChapter, verse.verse); if (googleAuth.currentUser() != null) syncManager.deleteHighlight(currentBookId, selectedChapter, verse.verse) }
                                    }
                                }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                    Text("${verse.verse} ", color = readingGold, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
                                    Text(verse.text, color = textColor, fontSize = fontSize.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (drawerOpen) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable { drawerOpen = false }) {
                Surface(modifier = Modifier.fillMaxHeight().width(300.dp).align(Alignment.CenterStart), color = bgColor) { drawerContent() }
            }
        }
    }
    if (dialog == "daily") AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Daily Verse") }, text = { Text(dailyVerseText) }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
    if (dialog == "notes") {
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Notes") }, text = {
            Column {
                Text("Note for ${selectedBook} ${selectedChapter}:${selectedVerse}", color = textColor)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Write note") })
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(allNotes.filter { it.bookId == currentBookId && it.chapter == selectedChapter && it.verse == selectedVerse }) { note -> Text("${note.noteText}", color = textColor, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
        }, confirmButton = {
            TextButton(onClick = {
                val n = noteText.trim()
                if (n.isNotEmpty()) { scope.launch { dao.saveNote(NoteEntity(bookId = currentBookId, chapter = selectedChapter, verse = selectedVerse, noteText = n, timestamp = System.currentTimeMillis())); if (googleAuth.currentUser() != null) syncManager.uploadNote(currentBookId, selectedChapter, selectedVerse, n) } }
                noteText = ""; dialog = null
            }) { Text("Save") }
        }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
    }
    if (dialog == "highlights") {
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Highlights") }, text = {
            LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                items(allHighlights.filter { it.bookId == currentBookId && it.chapter == selectedChapter }) { highlight ->
                    Text("${highlight.verse}", color = textColor, modifier = Modifier.fillMaxWidth().clickable { scope.launch { dao.deleteHighlight(highlight.bookId, highlight.chapter, highlight.verse); if (googleAuth.currentUser() != null) syncManager.deleteHighlight(highlight.bookId, highlight.chapter, highlight.verse) } }.padding(vertical = 8.dp))
                }
            }
        }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
    }
    if (signInMessage.isNotBlank()) {
        LaunchedEffect(signInMessage) { kotlinx.coroutines.delay(2500); signInMessage = "" }
        Text(text = signInMessage, color = textColor)
    }
    BackHandler(enabled = drawerOpen || settingsOpen || dialog != null) { if (dialog != null) dialog = null else if (settingsOpen) settingsOpen = false else drawerOpen = false }
}