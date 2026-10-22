package app.tandemwear.watch.data.history

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import app.tandemwear.watch.calls.CallOutcome
import kotlinx.coroutines.flow.Flow

/** One call seen by the watch (Phase 5.4). Cleared with the history. */
@Entity(tableName = "calls", indices = [Index("at")])
data class CallEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Name or number as the iPhone gave it. */
    val caller: String,
    /** "Phone", "WhatsApp", "FaceTime"… */
    val appName: String,
    /** [CallOutcome] name. */
    val outcome: String,
    /** When it started ringing (or the Missed Call notification's date), ms since epoch. */
    val at: Long
) {
    val callOutcome: CallOutcome get() = CallOutcome.fromName(outcome)
}

@Dao
interface CallDao {
    @Insert
    suspend fun insert(entry: CallEntry): Long

    @Query("SELECT * FROM calls ORDER BY at DESC, id DESC")
    fun observeAll(): Flow<List<CallEntry>>

    @Query("SELECT * FROM calls WHERE at >= :sinceMs")
    suspend fun since(sinceMs: Long): List<CallEntry>

    @Query("DELETE FROM calls WHERE id NOT IN (SELECT id FROM calls ORDER BY at DESC, id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM calls")
    suspend fun clear()
}
