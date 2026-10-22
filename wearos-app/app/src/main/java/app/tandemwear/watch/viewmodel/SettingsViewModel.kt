package app.tandemwear.watch.viewmodel

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tandemwear.watch.data.history.HistoryRepository
import app.tandemwear.watch.data.history.SeenApp
import app.tandemwear.watch.data.history.TandemDatabase
import app.tandemwear.watch.data.settings.AppRule
import app.tandemwear.watch.data.settings.QuietHours
import app.tandemwear.watch.data.settings.SettingsRepository
import app.tandemwear.watch.data.settings.TandemSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import app.tandemwear.watch.notify.Conversation
import app.tandemwear.watch.notify.MuteDuration
import kotlinx.coroutines.flow.stateIn
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

/** Settings screens. Every change is written to DataStore; AncsService picks it up live. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val history = HistoryRepository(TandemDatabase.get(application).historyDao())

    val settings: StateFlow<TandemSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TandemSettings())

    /** Apps that have sent at least one notification, by name. */
    val apps: StateFlow<List<SeenApp>> = history.apps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun app(appId: String): Flow<SeenApp?> = history.app(appId)

    fun setAppRule(appId: String, rule: AppRule) = viewModelScope.launch { repository.setAppRule(appId, rule) }

    fun setQuietHours(quietHours: QuietHours) = viewModelScope.launch { repository.setQuietHours(quietHours) }

    fun setHistoryEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setHistoryEnabled(enabled)
        // Turning history off also forgets what was stored
        if (!enabled) launch(Dispatchers.IO) { history.clear() }
    }

    fun setMusicEnabled(enabled: Boolean) = viewModelScope.launch { repository.setMusicEnabled(enabled) }

    fun setHideContentOnLockScreen(hide: Boolean) = viewModelScope.launch { repository.setHideContentOnLockScreen(hide) }

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { history.clear() }

    fun setVip(conversation: Conversation, vip: Boolean) = viewModelScope.launch {
        repository.setVip(conversation, vip)
        // A VIP is meant to be heard: marking it lifts a silence on the same conversation
        if (vip) repository.unmuteConversation(conversation, System.currentTimeMillis())
    }

    fun setVipRingsInQuietHours(enabled: Boolean) = viewModelScope.launch { repository.setVipRingsInQuietHours(enabled) }

    fun mute(conversation: Conversation, duration: MuteDuration) = viewModelScope.launch {
        val now = ZonedDateTime.now()
        repository.muteConversation(conversation, duration.untilMillis(now), now.toInstant().toEpochMilli())
    }

    fun unmute(conversation: Conversation) = viewModelScope.launch {
        repository.unmuteConversation(conversation, System.currentTimeMillis())
    }

    /**
     * Sound and vibration per category are the system's notification channels (decision 2,
     * 21-10): open the app's notification settings.
     */
    fun openSystemNotificationSettings(): Boolean {
        val context = getApplication<Application>()
        return try {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
