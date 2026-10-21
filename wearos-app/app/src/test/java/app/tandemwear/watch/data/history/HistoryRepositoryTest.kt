package app.tandemwear.watch.data.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Room in memory under Robolectric. SDK 34: Robolectric's SDK 35 image needs JDK 21. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryRepositoryTest {

    private lateinit var db: TandemDatabase
    private lateinit var repo: HistoryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TandemDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = HistoryRepository(db.historyDao())
    }

    @After
    fun tearDown() = db.close()

    private fun entry(n: Int, session: Long = 1, app: String = "net.whatsapp.WhatsApp") = HistoryEntry(
        androidId = 1000 + n,
        sessionId = session,
        uid = n.toLong(),
        appId = app,
        appName = "WhatsApp",
        categoryId = 4,
        title = "Sender $n",
        message = "Message $n",
        postedAt = 1_000L * n,
        positiveLabel = null,
        negativeLabel = "Clear"
    )

    @Test
    fun `keeps the newest 50 and shows 20 newest first`() = runBlocking {
        (1..60).forEach { repo.record(entry(it), store = true) }
        assertEquals(HistoryRepository.KEEP, repo.count().first())
        val shown = repo.recent().first()
        assertEquals(HistoryRepository.SHOW, shown.size)
        assertEquals("Sender 60", shown.first().title)
        assertEquals("Sender 41", shown.last().title)
    }

    @Test
    fun `history off still remembers the app`() = runBlocking {
        repo.record(entry(1, app = "com.burbn.instagram"), store = false)
        assertEquals(0, repo.count().first())
        assertEquals(listOf("com.burbn.instagram"), repo.apps().first().map { it.appId })
    }

    @Test
    fun `iphone removal only deletes the entry of that session`() = runBlocking {
        repo.record(entry(1, session = 1), store = true)
        repo.record(entry(1, session = 2), store = true) // same ids, later session
        repo.removedOnIphone(androidId = 1001, sessionId = 2)
        val left = repo.recent().first()
        assertEquals(1, left.size)
        assertEquals(1L, left.single().sessionId)
    }

    @Test
    fun `dismiss and clear`() = runBlocking {
        repo.record(entry(1), store = true)
        repo.record(entry(2), store = true)
        val first = repo.recent().first().last()
        repo.dismiss(first.id)
        assertNull(repo.entry(first.id).first())
        assertEquals(1, repo.count().first())
        repo.clear()
        assertEquals(0, repo.count().first())
    }
}
