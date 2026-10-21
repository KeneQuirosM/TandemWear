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
import kotlinx.coroutines.flow.stateIn
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

    fun setHideContentOnLockScreen(hide: Boolean) = viewModelScope.launch { repository.setHideContentOnLockScreen(hide) }

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { history.clear() }

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
