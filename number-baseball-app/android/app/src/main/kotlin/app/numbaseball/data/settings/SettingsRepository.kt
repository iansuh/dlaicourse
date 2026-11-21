package app.numbaseball.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * README "Sound": effects on but silenced in silent/vibrate mode ("Follow silent mode", on), music off, separate volumes,
 * haptics on/off. Chat mute is client-side only (docs/protocol.md §9.1).
 */
data class AppSettings(
    val effectsEnabled: Boolean = true,
    val effectsVolume: Float = 0.8f,
    val musicEnabled: Boolean = false,
    val musicVolume: Float = 0.5f,
    val followSilentMode: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val chatMuted: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(change: (AppSettings) -> AppSettings)
}

/** Not part of Auto Backup (only the account store is). */
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val store = context.applicationContext.settingsDataStore
    private val defaults = AppSettings()

    override val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    override suspend fun update(change: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = change(prefs.toSettings())
            prefs[EFFECTS] = next.effectsEnabled
            prefs[EFFECTS_VOLUME] = next.effectsVolume.coerceIn(0f, 1f)
            prefs[MUSIC] = next.musicEnabled
            prefs[MUSIC_VOLUME] = next.musicVolume.coerceIn(0f, 1f)
            prefs[FOLLOW_SILENT] = next.followSilentMode
            prefs[HAPTICS] = next.hapticsEnabled
            prefs[CHAT_MUTED] = next.chatMuted
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        effectsEnabled = this[EFFECTS] ?: defaults.effectsEnabled,
        effectsVolume = this[EFFECTS_VOLUME] ?: defaults.effectsVolume,
        musicEnabled = this[MUSIC] ?: defaults.musicEnabled,
        musicVolume = this[MUSIC_VOLUME] ?: defaults.musicVolume,
        followSilentMode = this[FOLLOW_SILENT] ?: defaults.followSilentMode,
        hapticsEnabled = this[HAPTICS] ?: defaults.hapticsEnabled,
        chatMuted = this[CHAT_MUTED] ?: defaults.chatMuted,
    )

    private companion object {
        val EFFECTS = booleanPreferencesKey("effects")
        val EFFECTS_VOLUME = floatPreferencesKey("effects_volume")
        val MUSIC = booleanPreferencesKey("music")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val FOLLOW_SILENT = booleanPreferencesKey("follow_silent_mode")
        val HAPTICS = booleanPreferencesKey("haptics")
        val CHAT_MUTED = booleanPreferencesKey("chat_muted")
    }
}
