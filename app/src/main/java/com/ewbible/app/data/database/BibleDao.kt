@Dao
interface BibleDao {

    // ... your existing queries ...

    @Query("SELECT * FROM commentary WHERE book_id = :bookId AND chapter = :chapter")
    suspend fun getCommentary(bookId: Int, chapter: Int): CommentaryEntity?

    // Optional: if you ever need the raw compressed bytes explicitly
    @Query("SELECT html_compressed FROM commentary WHERE book_id = :bookId AND chapter = :chapter")
    suspend fun getCommentaryCompressed(bookId: Int, chapter: Int): ByteArray?

    // ... rest of your queries ...
}