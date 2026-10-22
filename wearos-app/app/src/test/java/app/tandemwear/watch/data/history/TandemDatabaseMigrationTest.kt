package app.tandemwear.watch.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import app.tandemwear.watch.calls.CallOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The version-1 schema, exactly as shipped before Phase 5.4 (same entities, version 1).
 * Room builds it for real, so the migration is tested against the actual old tables.
 */
@Database(entities = [HistoryEntry::class, SeenApp::class], version = 1, exportSchema = false)
abstract class TandemDatabaseV1 : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

/** SDK 34: Robolectric's SDK 35 image needs JDK 21. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TandemDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun entry(n: Int) = HistoryEntry(
        androidId = 1000 + n, sessionId = 7, uid = n.toLong(), appId = "net.whatsapp.WhatsApp",
        appName = "WhatsApp", categoryId = 4, title = "Mamá $n", message = "Hola $n",
        postedAt = 1_000L * n, positiveLabel = null, negativeLabel = "Borrar", contentHidden = n == 2
    )

    @Test
    fun `1 to 2 keeps the history and adds calls`() = runBlocking {
        // Version 1 with data
        val v1 = Room.databaseBuilder(context, TandemDatabaseV1::class.java, name).allowMainThreadQueries().build()
        v1.historyDao().insert(entry(1))
        v1.historyDao().insert(entry(2))
        v1.historyDao().upsertApp(SeenApp("net.whatsapp.WhatsApp", "WhatsApp", 4, 5_000))
        v1.close()

        // Open as version 2 with the real migration and no destructive fallback
        val v2 = TandemDatabase.builder(context, name).allowMainThreadQueries().build()
        try {
            val history = v2.historyDao().observeRecent(10).first()
            assertEquals(listOf("Mamá 2", "Mamá 1"), history.map { it.title })
            assertEquals(listOf(true, false), history.map { it.contentHidden })
            assertEquals("Borrar", history[0].negativeLabel)
            assertEquals("WhatsApp", v2.historyDao().observeApp("net.whatsapp.WhatsApp").first()?.displayName)

            v2.callDao().insert(CallEntry(caller = "Mamá", appName = "Teléfono", outcome = CallOutcome.MISSED.name, at = 9_000))
            assertEquals(listOf("Mamá"), v2.callDao().observeAll().first().map { it.caller })
        } finally {
            v2.close()
        }
    }
}
