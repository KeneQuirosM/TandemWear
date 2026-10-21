package app.tandemwear.watch.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User settings in DataStore. [settings] emits a new snapshot on every change, so the
 * service applies them live (no restart). Per-app rules are stored as three sets of
 * bundle IDs, which keeps the file small and the schema flat.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<TandemSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::fromPreferences)
        .distinctUntilChanged()

    suspend fun setAppRule(appId: String, rule: AppRule) {
        dataStore.edit { prefs ->
            prefs.toggle(Keys.DISABLED, appId, rule.disabled)
            prefs.toggle(Keys.MUTED, appId, rule.muted)
            prefs.toggle(Keys.HIDDEN, appId, rule.hideContent)
        }
    }

    suspend fun setQuietHours(quietHours: QuietHours) {
        dataStore.edit { prefs ->
            prefs[Keys.QUIET_ENABLED] = quietHours.enabled
            prefs[Keys.QUIET_START] = quietHours.startMinute
            prefs[Keys.QUIET_END] = quietHours.endMinute
            prefs[Keys.QUIET_ALLOW_CALLS] = quietHours.allowCalls
        }
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HISTORY_ENABLED] = enabled }
    }

    suspend fun setMusicEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MUSIC_ENABLED] = enabled }
    }

    suspend fun setHideContentOnLockScreen(hide: Boolean) {
        dataStore.edit { it[Keys.LOCK_SCREEN_PRIVATE] = hide }
    }

    private fun MutablePreferences.toggle(key: Preferences.Key<Set<String>>, appId: String, on: Boolean) {
        val current = this[key].orEmpty()
        this[key] = if (on) current + appId else current - appId
    }

    private object Keys {
        val DISABLED = stringSetPreferencesKey("apps_disabled")
        val MUTED = stringSetPreferencesKey("apps_muted")
        val HIDDEN = stringSetPreferencesKey("apps_hidden")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start_minute")
        val QUIET_END = intPreferencesKey("quiet_end_minute")
        val QUIET_ALLOW_CALLS = booleanPreferencesKey("quiet_allow_calls")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val LOCK_SCREEN_PRIVATE = booleanPreferencesKey("lock_screen_private")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
    }

    companion object {
        fun fromPreferences(prefs: Preferences): TandemSettings {
            val defaults = TandemSettings()
            val disabled = prefs[Keys.DISABLED].orEmpty()
            val muted = prefs[Keys.MUTED].orEmpty()
            val hidden = prefs[Keys.HIDDEN].orEmpty()
            val rules = (disabled + muted + hidden).associateWith { id ->
                AppRule(disabled = id in disabled, muted = id in muted, hideContent = id in hidden)
            }
            val q = defaults.quietHours
            val start = prefs[Keys.QUIET_START]?.takeIf { it in 0 until QuietHours.MINUTES_PER_DAY } ?: q.startMinute
            val end = prefs[Keys.QUIET_END]?.takeIf { it in 0 until QuietHours.MINUTES_PER_DAY } ?: q.endMinute
            return TandemSettings(
                appRules = rules,
                quietHours = QuietHours(
                    enabled = prefs[Keys.QUIET_ENABLED] ?: q.enabled,
                    startMinute = start,
                    endMinute = end,
                    allowCalls = prefs[Keys.QUIET_ALLOW_CALLS] ?: q.allowCalls
                ),
                historyEnabled = prefs[Keys.HISTORY_ENABLED] ?: defaults.historyEnabled,
                hideContentOnLockScreen = prefs[Keys.LOCK_SCREEN_PRIVATE] ?: defaults.hideContentOnLockScreen,
                musicEnabled = prefs[Keys.MUSIC_ENABLED] ?: defaults.musicEnabled
            )
        }
    }
}
