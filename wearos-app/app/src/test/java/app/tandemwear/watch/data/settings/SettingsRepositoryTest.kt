package app.tandemwear.watch.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.tandemwear.watch.notify.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings round-trip through DataStore and arrive on the flow the service observes. */
class SettingsRepositoryTest {

    /** In-memory DataStore: same contract (data + atomic updateData), no file. */
    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    private val repo = SettingsRepository(FakeDataStore())

    @Test
    fun `defaults when nothing is stored`() = runBlocking {
        assertEquals(TandemSettings(), repo.settings.first())
    }

    @Test
    fun `app rules are stored per app and removed when back to default`() = runBlocking {
        repo.setAppRule("net.whatsapp.WhatsApp", AppRule(muted = true))
        repo.setAppRule("com.burbn.instagram", AppRule(disabled = true, hideContent = true))
        var s = repo.settings.first()
        assertEquals(AppRule(muted = true), s.ruleFor("net.whatsapp.WhatsApp"))
        assertEquals(AppRule(disabled = true, hideContent = true), s.ruleFor("com.burbn.instagram"))
        assertEquals(AppRule(), s.ruleFor("com.google.Gmail"))

        repo.setAppRule("net.whatsapp.WhatsApp", AppRule())
        s = repo.settings.first()
        assertFalse(s.appRules.containsKey("net.whatsapp.WhatsApp"))
    }

    @Test
    fun `quiet hours, history and lock screen round trip`() = runBlocking {
        val quiet = QuietHours(enabled = true, startMinute = 23 * 60 + 30, endMinute = 6 * 60, allowCalls = false)
        repo.setQuietHours(quiet)
        repo.setHistoryEnabled(false)
        repo.setHideContentOnLockScreen(true)
        repo.setMusicEnabled(false)
        val s = repo.settings.first()
        assertEquals(quiet, s.quietHours)
        assertFalse(s.historyEnabled)
        assertTrue(s.hideContentOnLockScreen)
        assertFalse(s.musicEnabled)
    }

    @Test
    fun `changes are emitted live`() = runBlocking {
        val before = repo.settings.first()
        repo.setHistoryEnabled(!before.historyEnabled)
        assertEquals(!before.historyEnabled, repo.settings.first().historyEnabled)
    }

    @Test
    fun `VIPs and silenced conversations round trip`() = runBlocking {
        val mom = Conversation("net.whatsapp.WhatsApp", "Mamá")
        val work = Conversation("com.apple.MobileSMS", "Trabajo")
        repo.setVip(mom, true)
        repo.setVipRingsInQuietHours(true)
        repo.muteConversation(work, untilMs = 2_000, nowMs = 1_000)
        var s = repo.settings.first()
        assertEquals(listOf(mom), s.vips)
        assertTrue(s.vipRingsInQuietHours)
        assertEquals(2_000L, s.mutedUntil("com.apple.MobileSMS", "trabajo", 1_500))

        // Same conversation spelled differently: replaced, not duplicated
        repo.setVip(Conversation("net.whatsapp.WhatsApp", "mama"), false)
        repo.muteConversation(Conversation("com.apple.MobileSMS", "TRABAJO"), untilMs = 9_000, nowMs = 1_000)
        s = repo.settings.first()
        assertTrue(s.vips.isEmpty())
        assertEquals(1, s.mutedConversations.size)
        assertEquals(9_000L, s.mutedUntil("com.apple.MobileSMS", "Trabajo", 1_000))

        repo.unmuteConversation(work, nowMs = 1_000)
        assertTrue(repo.settings.first().mutedConversations.isEmpty())
    }
}
