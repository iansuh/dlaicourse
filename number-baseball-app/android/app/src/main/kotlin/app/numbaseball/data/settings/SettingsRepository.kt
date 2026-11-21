package app.numbaseball.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** "Silent by default": sound starts off. Chat mute is client-side only (docs/protocol.md §9.1). */
data class AppSettings(
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val chatMuted: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setChatMuted(muted: Boolean)
}

/** Not part of Auto Backup (only the account store is). */
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val store = context.applicationContext.settingsDataStore
    private val defaults = AppSettings()

    override val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            soundEnabled = p[SOUND] ?: defaults.soundEnabled,
            hapticsEnabled = p[HAPTICS] ?: defaults.hapticsEnabled,
            chatMuted = p[CHAT_MUTED] ?: defaults.chatMuted,
        )
    }

    override suspend fun setSoundEnabled(enabled: Boolean) = set(SOUND, enabled)
    override suspend fun setHapticsEnabled(enabled: Boolean) = set(HAPTICS, enabled)
    override suspend fun setChatMuted(muted: Boolean) = set(CHAT_MUTED, muted)

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        store.edit { it[key] = value }
    }

    private companion object {
        val SOUND = booleanPreferencesKey("sound")
        val HAPTICS = booleanPreferencesKey("haptics")
        val CHAT_MUTED = booleanPreferencesKey("chat_muted")
    }
}
