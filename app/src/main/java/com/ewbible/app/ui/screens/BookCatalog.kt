package com.ewbible.app.data.database

data class BookInfo(
    val id: Int,
    val name: String,
    val key: String,
    val testament: String,
    val chapters: Int
)

object BookCatalog {

    val all: List<BookInfo> = listOf(
        BookInfo(1, "Genesis", "Genesis", "OT", 50),
        BookInfo(2, "Exodus", "Exodus", "OT", 40),
        BookInfo(3, "Leviticus", "Leviticus", "OT", 27),
        BookInfo(4, "Numbers", "Numbers", "OT", 36),
        BookInfo(5, "Deuteronomy", "Deuteronomy", "OT", 34),
        BookInfo(6, "Joshua", "Joshua", "OT", 24),
        BookInfo(7, "Judges", "Judges", "OT", 21),
        BookInfo(8, "Ruth", "Ruth", "OT", 4),
        BookInfo(9, "1 Samuel", "1 Samuel", "OT", 31),
        BookInfo(10, "2 Samuel", "2 Samuel", "OT", 24),
        BookInfo(11, "1 Kings", "1 Kings", "OT", 22),
        BookInfo(12, "2 Kings", "2 Kings", "OT", 25),
        BookInfo(13, "1 Chronicles", "1 Chronicles", "OT", 29),
        BookInfo(14, "2 Chronicles", "2 Chronicles", "OT", 36),
        BookInfo(15, "Ezra", "Ezra", "OT", 10),
        BookInfo(16, "Nehemiah", "Nehemiah", "OT", 13),
        BookInfo(17, "Esther", "Esther", "OT", 10),
        BookInfo(18, "Job", "Job", "OT", 42),
        BookInfo(19, "Psalms", "Psalms", "OT", 150),
        BookInfo(20, "Proverbs", "Proverbs", "OT", 31),
        BookInfo(21, "Ecclesiastes", "Ecclesiastes", "OT", 12),
        BookInfo(22, "Song of Solomon", "Song of Solomon", "OT", 8),
        BookInfo(23, "Isaiah", "Isaiah", "OT", 66),
        BookInfo(24, "Jeremiah", "Jeremiah", "OT", 52),
        BookInfo(25, "Lamentations", "Lamentations", "OT", 5),
        BookInfo(26, "Ezekiel", "Ezekiel", "OT", 48),
        BookInfo(27, "Daniel", "Daniel", "OT", 12),
        BookInfo(28, "Hosea", "Hosea", "OT", 14),
        BookInfo(29, "Joel", "Joel", "OT", 3),
        BookInfo(30, "Amos", "Amos", "OT", 9),
        BookInfo(31, "Obadiah", "Obadiah", "OT", 1),
        BookInfo(32, "Jonah", "Jonah", "OT", 4),
        BookInfo(33, "Micah", "Micah", "OT", 7),
        BookInfo(34, "Nahum", "Nahum", "OT", 3),
        BookInfo(35, "Habakkuk", "Habakkuk", "OT", 3),
        BookInfo(36, "Zephaniah", "Zephaniah", "OT", 3),
        BookInfo(37, "Haggai", "Haggai", "OT", 2),
        BookInfo(38, "Zechariah", "Zechariah", "OT", 14),
        BookInfo(39, "Malachi", "Malachi", "OT", 4),
        BookInfo(40, "Matthew", "Matthew", "NT", 28),
        BookInfo(41, "Mark", "Mark", "NT", 16),
        BookInfo(42, "Luke", "Luke", "NT", 24),
        BookInfo(43, "John", "John", "NT", 21),
        BookInfo(44, "Acts", "Acts", "NT", 28),
        BookInfo(45, "Romans", "Romans", "NT", 16),
        BookInfo(46, "1 Corinthians", "1 Corinthians", "NT", 16),
        BookInfo(47, "2 Corinthians", "2 Corinthians", "NT", 13),
        BookInfo(48, "Galatians", "Galatians", "NT", 6),
        BookInfo(49, "Ephesians", "Ephesians", "NT", 6),
        BookInfo(50, "Philippians", "Philippians", "NT", 4),
        BookInfo(51, "Colossians", "Colossians", "NT", 4),
        BookInfo(52, "1 Thessalonians", "1 Thessalonians", "NT", 5),
        BookInfo(53, "2 Thessalonians", "2 Thessalonians", "NT", 3),
        BookInfo(54, "1 Timothy", "1 Timothy", "NT", 6),
        BookInfo(55, "2 Timothy", "2 Timothy", "NT", 4),
        BookInfo(56, "Titus", "Titus", "NT", 3),
        BookInfo(57, "Philemon", "Philemon", "NT", 1),
        BookInfo(58, "Hebrews", "Hebrews", "NT", 13),
        BookInfo(59, "James", "James", "NT", 5),
        BookInfo(60, "1 Peter", "1 Peter", "NT", 5),
        BookInfo(61, "2 Peter", "2 Peter", "NT", 3),
        BookInfo(62, "1 John", "1 John", "NT", 5),
        BookInfo(63, "2 John", "2 John", "NT", 1),
        BookInfo(64, "3 John", "3 John", "NT", 1),
        BookInfo(65, "Jude", "Jude", "NT", 1),
        BookInfo(66, "Revelation", "Revelation", "NT", 22)
    )

    private val byId = all.associateBy { it.id }
    private val byName = all.associateBy { it.name.lowercase() }
    private val byKey = all.associateBy { it.key.lowercase() }

    fun name(id: Int): String = byId[id]?.name ?: "Genesis"
    fun id(name: String): Int {
        val clean = name.trim().lowercase()
        return byName[clean]?.id ?: byKey[clean]?.id ?: when (clean) {
            "psalm", "ps" -> 19
            "song of songs", "canticles" -> 22
            "1 sam" -> 9
            "2 sam" -> 10
            "1 kings", "1 kgs" -> 11
            "2 kings", "2 kgs" -> 12
            "1 chron" -> 13
            "2 chron" -> 14
            "1 cor" -> 46
            "2 cor" -> 47
            "1 thess" -> 52
            "2 thess" -> 53
            "1 tim" -> 54
            "2 tim" -> 55
            "1 pet" -> 60
            "2 pet" -> 61
            "1 jn" -> 62
            "2 jn" -> 63
            "3 jn" -> 64
            "rev" -> 66
            else -> 1
        }
    }
    fun testament(id: Int): String = byId[id]?.testament ?: "OT"
    fun chapters(id: Int): Int = byId[id]?.chapters ?: 1
    fun toEntity(info: BookInfo): BookEntity = BookEntity(
        id = info.id,
        name = info.name,
        key = info.key,
        testament = info.testament
    )
}