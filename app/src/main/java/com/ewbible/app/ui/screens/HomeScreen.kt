package com.example.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---------- Helpers: keep DB untouched ----------
fun stripHtml(input: String): String {
    var s = input
    s = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    s = s.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
    s = s.replace(Regex("<[^>]+>"), "")
    s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
    return s.trim()
}

@Composable
fun ClickableVerseText(text: String, textColor: Color, fontSize: Float, gold: Color, onVerseClick: (String, Int) -> Unit) {
    val clean = stripHtml(text)
    val regex = Regex("""\b((?:1|2|3)\s)?([A-Z][a-z]+(?:\s[A-Z][a-z]+)?)\s(\d+):(\d+)\b""")
    val annotated = buildAnnotatedString {
        var last = 0
        regex.findAll(clean).forEach { m ->
            if (m.range.first > last) {
                withStyle(SpanStyle(color = textColor, fontSize = fontSize.sp)) { append(clean.substring(last, m.range.first)) }
            }
            val full = m.value
            val book = ((m.groupValues[1].trim() + " " + m.groupValues[2]).trim())
            val chap = m.groupValues[3].toIntOrNull() ?: 1
            pushStringAnnotation(tag = "VERSE", annotation = "$book|$chap")
            withStyle(SpanStyle(color = gold, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)) { append(full) }
            pop()
            last = m.range.last + 1
        }
        if (last < clean.length) {
            withStyle(SpanStyle(color = textColor, fontSize = fontSize.sp)) { append(clean.substring(last)) }
        }
    }
    ClickableText(
        text = annotated,
        style = TextStyle(color = textColor, fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp),
        onClick = { offset ->
            annotated.getStringAnnotations("VERSE", offset, offset).firstOrNull()?.let {
                val parts = it.item.split("|")
                onVerseClick(parts[0], parts[1].toIntOrNull() ?: 1)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(db: AppDatabase, prefs: AppPreferences, auth: GoogleAuth, sync: SyncManager) {
    val dao = remember { db.dao() }
    val scope = rememberCoroutineScope()

    var allBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var selectedBook by remember { mutableStateOf("Deuteronomy") }
    var selectedChapter by remember { mutableStateOf(1) }
    var verses by remember { mutableStateOf<List<Verse>>(emptyList()) }
    var summary by remember { mutableStateOf<Summary?>(null) }
    var commentary by remember { mutableStateOf<Commentary?>(null) }
    var highlights by remember { mutableStateOf<List<Highlight>>(emptyList()) }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var tab by remember { mutableStateOf("Bible") }
    var searchQuery by remember { mutableStateOf("") }
    // FIXED: No more List<Verse> + bookId/chapter. We store BookName + Chapter + Verse together.
    var searchResults by remember { mutableStateOf<List<Triple<String, Int, Verse>>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var dailyVerseText by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(16f) }
    var isDark by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf<String?>(null) }

    val bgColor = if (isDark) Color(0xFF202328) else Color(0xFFEDE6D3)
    val textColor = if (isDark) Color.White else Color(0xFF2B2820)
    val cardColor = if (isDark) Color(0xFF2A2E35) else Color(0xFFF7F1E3)
    val readingGold = Color(0xFFB78F3A)

    LaunchedEffect(Unit) {
        allBooks = dao.getAllBooks()
        val last = prefs.getReading()
        if (last != null) { selectedBook = last.first; selectedChapter = last.second }
        fontSize = prefs.getSettings().first
        isDark = prefs.getSettings().second
        userName = auth.getCurrentUserName()
    }

    LaunchedEffect(selectedBook, selectedChapter) {
        val bookId = allBooks.firstOrNull { it.name == selectedBook }?.id ?: 1
        verses = dao.getVerses(bookId, selectedChapter)
        try { summary = dao.getSummary(bookId, selectedChapter) } catch (_: Exception) { summary = null }
        try { commentary = dao.getCommentary(bookId, selectedChapter) } catch (_: Exception) { commentary = null }
        highlights = dao.getHighlights(bookId, selectedChapter)
        notes = dao.getNotes(bookId, selectedChapter)
        prefs.saveReading(selectedBook, selectedChapter)
    }

    fun goToVerse(bookName: String, chapter: Int) {
        selectedBook = bookName
        selectedChapter = chapter
        tab = "Bible"
        scope.launch { prefs.saveReading(bookName, chapter) }
    }

    val currentBookId = allBooks.firstOrNull { it.name == selectedBook }?.id ?: 1
    val maxChapter = allBooks.firstOrNull { it.name == selectedBook }?.chapters ?: 1
    val otBooks = listOf("Genesis","Exodus","Leviticus","Numbers","Deuteronomy","Joshua","Judges","Ruth","1 Samuel","2 Samuel","1 Kings","2 Kings","1 Chronicles","2 Chronicles","Ezra","Nehemiah","Esther","Job","Psalms","Proverbs","Ecclesiastes","Song of Solomon","Isaiah","Jeremiah","Lamentations","Ezekiel","Daniel","Hosea","Joel","Amos","Obadiah","Jonah","Micah","Nahum","Habakkuk","Zephaniah","Haggai","Zechariah","Malachi")
    val ntBooks = listOf("Matthew","Mark","Luke","John","Acts","Romans","1 Corinthians","2 Corinthians","Galatians","Ephesians","Philippians","Colossians","1 Thessalonians","2 Thessalonians","1 Timothy","2 Timothy","Titus","Philemon","Hebrews","James","1 Peter","2 Peter","1 John","2 John","3 John","Jude","Revelation")

    Box(Modifier.fillMaxSize().background(bgColor)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { drawerOpen = true }, contentPadding = PaddingValues(4.dp)) { Text("☰", color = textColor, fontSize = 22.sp) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    listOf("Bible", "Endurance", "Search").forEach { t ->
                        Text(
                            text = t,
                            color = if (tab == t) readingGold else textColor,
                            fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            maxLines = 1,
                            modifier = Modifier.clickable { tab = t }.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                Box {
                    TextButton(onClick = { settingsOpen = true }, contentPadding = PaddingValues(4.dp)) { Text("⋮", color = textColor, fontSize = 22.sp) }
                    DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                        DropdownMenuItem(text = { Text(userName ?: "Google sign in") }, onClick = {
                            settingsOpen = false
                            scope.launch { auth.signIn(); userName = auth.getCurrentUserName(); sync.syncNow() }
                        })
                        DropdownMenuItem(text = { Text(if (isDark) "Light mode" else "Dark mode") }, onClick = {
                            isDark = !isDark; settingsOpen = false
                            scope.launch { prefs.saveSettings(fontSize, isDark) }
                        })
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("A-", color = textColor)
                                    Slider(
                                        value = fontSize,
                                        onValueChange = { fontSize = it; scope.launch { prefs.saveSettings(it, isDark) } },
                                        valueRange = 12f..24f,
                                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                                    )
                                    Text("A+", color = textColor)
                                }
                            },
                            onClick = {}
                        )
                        DropdownMenuItem(text = { Text("Daily Verse") }, onClick = {
                            settingsOpen = false
                            val v = verses.randomOrNull()
                            dailyVerseText = if (v != null) "$selectedBook $selectedChapter:${v.verse}\n\n${v.text}" else "No verse is available yet."
                            dialog = "daily"
                        })
                    }
                }
            }

            Text(selectedBook + " " + selectedChapter, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            when (tab) {
                "Bible" -> {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        summary?.let { item ->
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = cardColor), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text("Chapter Summary", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                        Text(item.summary, color = textColor, fontSize = fontSize.sp, modifier = Modifier.padding(top = 6.dp))
                                        Text("What Happened", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
                                        Text(item.whatHappened, color = textColor, fontSize = fontSize.sp, modifier = Modifier.padding(top = 4.dp))
                                        Text("Application", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
                                        Text(item.application, color = textColor, fontSize = fontSize.sp, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                        items(verses) { v ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text("${v.verse} ", color = readingGold, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
                                Text(v.text, color = textColor, fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp)
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
                "Endurance" -> {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                        if (commentary != null) {
                            Text("Commentary - $selectedBook $selectedChapter", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
                            ClickableVerseText(commentary!!.text, textColor, fontSize, readingGold) { b, c -> goToVerse(b, c) }
                        } else {
                            Text("No commentary for this chapter yet.", color = textColor, fontSize = fontSize.sp)
                        }
                        Spacer(Modifier.height(80.dp))
                    }
                }
                "Search" -> {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Search Bible...") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            scope.launch {
                                val q = searchQuery.trim()
                                if (q.isEmpty()) { searchResults = emptyList(); return@launch }
                                isSearching = true
                                val out = mutableListOf<Triple<String, Int, Verse>>()
                                // FIXED: Uses only getVerses(bookId, chapter) which you already have
                                for (b in allBooks) {
                                    for (ch in 1..b.chapters) {
                                        val vs = try { dao.getVerses(b.id, ch) } catch (_: Exception) { emptyList() }
                                        for (v in vs) {
                                            if (v.text.contains(q, ignoreCase = true)) {
                                                out.add(Triple(b.name, ch, v))
                                                if (out.size >= 100) break
                                            }
                                        }
                                        if (out.size >= 100) break
                                    }
                                    if (out.size >= 100) break
                                }
                                searchResults = out
                                isSearching = false
                            }
                        }, modifier = Modifier.padding(top = 8.dp)) { Text(if (isSearching) "Searching..." else "Search") }

                        if (searchResults.isNotEmpty()) {
                            Text("${searchResults.size} results", color = readingGold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        LazyColumn(Modifier.weight(1f)) {
                            items(searchResults) { (bName, ch, v) ->
                                Column(Modifier.fillMaxWidth().clickable { goToVerse(bName, ch) }.padding(vertical = 8.dp)) {
                                    Text("$bName $ch:${v.verse}", color = readingGold, fontWeight = FontWeight.Bold)
                                    Text(v.text, color = textColor, fontSize = fontSize.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (drawerOpen) {
            AlertDialog(
                onDismissRequest = { drawerOpen = false },
                title = { Text("Select Book", color = textColor) },
                text = {
                    LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                        item { Text("OLD TESTAMENT", color = readingGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(otBooks) { b ->
                            Text(b, color = if (b == selectedBook) readingGold else textColor, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable {
                                selectedBook = b; selectedChapter = 1; drawerOpen = false
                            }.padding(vertical = 8.dp))
                        }
                        item { Text("NEW TESTAMENT", color = readingGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(ntBooks) { b ->
                            Text(b, color = if (b == selectedBook) readingGold else textColor, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable {
                                selectedBook = b; selectedChapter = 1; drawerOpen = false
                            }.padding(vertical = 8.dp))
                        }
                        item { Text("Chapter", color = readingGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items((1..maxChapter).toList()) { c ->
                            Text("Chapter $c", color = if (c == selectedChapter) readingGold else textColor, modifier = Modifier.fillMaxWidth().clickable {
                                selectedChapter = c; drawerOpen = false
                            }.padding(vertical = 8.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { drawerOpen = false }) { Text("Close", color = readingGold) } }
            )
        }

        if (dialog == "daily") {
            AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = bgColor,
                title = {
                    Text("Daily Verse", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                },
                text = {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val parts = dailyVerseText.split("\n\n", limit = 2)
                        Text(parts.getOrNull(0) ?: "", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(parts.getOrNull(1) ?: dailyVerseText, color = textColor, fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp, textAlign = TextAlign.Center)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { dialog = null }) { Text("Close", color = readingGold, fontWeight = FontWeight.Bold) }
                }
            )
        }
    }
}