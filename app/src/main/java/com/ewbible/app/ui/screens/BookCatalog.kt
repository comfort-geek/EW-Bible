package com.ewbible.app.data.database

// Single source of truth. ID is king. Name is only for display.
// Matches your DB exactly: books.id 1-66 = commentary.book_id 1-66 = verses.book_id 1-66
// 0 orphans - proven by your JOIN test.

data class BookInfo(
    val id: Int,
    val key: String,
    val name: String,
    val testament: String // "OT" or "NT"
)

object BookCatalog {
    val all = listOf(
        BookInfo(1, "genesis", "Genesis", "OT"),
        BookInfo(2, "exodus", "Exodus", "OT"),
        BookInfo(3, "leviticus", "Leviticus", "OT"),
        BookInfo(4, "numbers", "Numbers", "OT"),
        BookInfo(5, "deuteronomy", "Deuteronomy", "OT"),
        BookInfo(6, "joshua", "Joshua", "OT"),
        BookInfo(7, "judges", "Judges", "OT"),
        BookInfo(8, "ruth", "Ruth", "OT"),
        BookInfo(9, "1-samuel", "1 Samuel", "OT"),
        BookInfo(10, "2-samuel", "2 Samuel", "OT"),
        BookInfo(11, "1-kings", "1 Kings", "OT"),
        BookInfo(12, "2-kings", "2 Kings", "OT"),
        BookInfo(13, "1-chronicles", "1 Chronicles", "OT"),
        BookInfo(14, "2-chronicles", "2 Chronicles", "OT"),
        BookInfo(15, "ezra", "Ezra", "OT"),
        BookInfo(16, "nehemiah", "Nehemiah", "OT"),
        BookInfo(17, "esther", "Esther", "OT"),
        BookInfo(18, "job", "Job", "OT"),
        BookInfo(19, "psalms", "Psalms", "OT"),
        BookInfo(20, "proverbs", "Proverbs", "OT"),
        BookInfo(21, "ecclesiastes", "Ecclesiastes", "OT"),
        BookInfo(22, "song-of-solomon", "Song of Solomon", "OT"),
        BookInfo(23, "isaiah", "Isaiah", "OT"),
        BookInfo(24, "jeremiah", "Jeremiah", "OT"),
        BookInfo(25, "lamentations", "Lamentations", "OT"),
        BookInfo(26, "ezekiel", "Ezekiel", "OT"),
        BookInfo(27, "daniel", "Daniel", "OT"),
        BookInfo(28, "hosea", "Hosea", "OT"),
        BookInfo(29, "joel", "Joel", "OT"),
        BookInfo(30, "amos", "Amos", "OT"),
        BookInfo(31, "obadiah", "Obadiah", "OT"),
        BookInfo(32, "jonah", "Jonah", "OT"),
        BookInfo(33, "micah", "Micah", "OT"),
        BookInfo(34, "nahum", "Nahum", "OT"),
        BookInfo(35, "habakkuk", "Habakkuk", "OT"),
        BookInfo(36, "zephaniah", "Zephaniah", "OT"),
        BookInfo(37, "haggai", "Haggai", "OT"),
        BookInfo(38, "zechariah", "Zechariah", "OT"),
        BookInfo(39, "malachi", "Malachi", "OT"),
        BookInfo(40, "matthew", "Matthew", "NT"),
        BookInfo(41, "mark", "Mark", "NT"),
        BookInfo(42, "luke", "Luke", "NT"),
        BookInfo(43, "john", "John", "NT"),
        BookInfo(44, "acts", "Acts", "NT"),
        BookInfo(45, "romans", "Romans", "NT"),
        BookInfo(46, "1-corinthians", "1 Corinthians", "NT"),
        BookInfo(47, "2-corinthians", "2 Corinthians", "NT"),
        BookInfo(48, "galatians", "Galatians", "NT"),
        BookInfo(49, "ephesians", "Ephesians", "NT"),
        BookInfo(50, "philippians", "Philippians", "NT"),
        BookInfo(51, "colossians", "Colossians", "NT"),
        BookInfo(52, "1-thessalonians", "1 Thessalonians", "NT"),
        BookInfo(53, "2-thessalonians", "2 Thessalonians", "NT"),
        BookInfo(54, "1-timothy", "1 Timothy", "NT"),
        BookInfo(55, "2-timothy", "2 Timothy", "NT"),
        BookInfo(56, "titus", "Titus", "NT"),
        BookInfo(57, "philemon", "Philemon", "NT"),
        BookInfo(58, "hebrews", "Hebrews", "NT"),
        BookInfo(59, "james", "James", "NT"),
        BookInfo(60, "1-peter", "1 Peter", "NT"),
        BookInfo(61, "2-peter", "2 Peter", "NT"),
        BookInfo(62, "1-john", "1 John", "NT"),
        BookInfo(63, "2-john", "2 John", "NT"),
        BookInfo(64, "3-john", "3 John", "NT"),
        BookInfo(65, "jude", "Jude", "NT"),
        BookInfo(66, "revelation", "Revelation", "NT")
    )

    private val byId = all.associateBy { it.id }
    private val byKey = all.associateBy { it.key.lowercase() }
    private val byName = all.associateBy { it.name.lowercase() }

    fun name(id: Int): String = byId[id]?.name ?: "Genesis"
    fun key(id: Int): String = byId[id]?.key ?: "genesis"
    fun info(id: Int): BookInfo = byId[id] ?: all[0]

    // Accepts "Genesis", "genesis", "1 Samuel", "1-samuel" - all resolve to same ID
    fun id(nameOrKey: String): Int {
        val n = nameOrKey.trim().lowercase()
        return byName[n]?.id ?: byKey[n]?.id ?: byKey[n.replace(" ", "-")]?.id ?: 1
    }

    val otBooks: List<BookInfo> = all.filter { it.testament == "OT" }
    val ntBooks: List<BookInfo> = all.filter { it.testament == "NT" }

    fun toEntity(info: BookInfo): BookEntity =
        BookEntity(id = info.id, key = info.key, name = info.name, testament = info.testament)
}