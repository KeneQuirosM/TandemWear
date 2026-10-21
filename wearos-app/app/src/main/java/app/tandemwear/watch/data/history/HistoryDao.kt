package app.tandemwear.watch.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    /** Newest first. */
    @Query("SELECT * FROM history ORDER BY postedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE id = :id")
    fun observe(id: Long): Flow<HistoryEntry?>

    @Query("SELECT COUNT(*) FROM history")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    /** Keep only the newest [keep] entries. */
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY postedAt DESC, id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    /** Only the current session's entry: an old one may share the (reused) Android id. */
    @Query("DELETE FROM history WHERE androidId = :androidId AND sessionId = :sessionId")
    suspend fun deleteByAndroidId(androidId: Int, sessionId: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Upsert
    suspend fun upsertApp(app: SeenApp)

    @Query("SELECT * FROM seen_apps ORDER BY displayName COLLATE NOCASE")
    fun observeApps(): Flow<List<SeenApp>>

    @Query("SELECT * FROM seen_apps WHERE appId = :appId")
    fun observeApp(appId: String): Flow<SeenApp?>
}
