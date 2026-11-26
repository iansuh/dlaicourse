package app.numbaseball.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * README "Sound": effects on but silenced in silent/vibrate mode ("Follow silent mode", on), music off, separate volumes,
 * haptics on/off. Chat mute is client-side only (docs/protocol.md §9.1).
 */
data class AppSettings(
    val effectsEnabled: Boolean = true,
    val effectsVolume: Float = 0.7f,
    val musicEnabled: Boolean = true, // owner decision 2026-11-26: calm music on by default (still muted by "Follow silent mode")
    val musicVolume: Float = 0.4f,
    val followSilentMode: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val chatMuted: Boolean = false,
    /** Last used choices (02 dock, 03 setup sheet). */
    val quickDigits: Int = 3,
    val cpuDigits: Int = 3,
    val cpuLevel: String = "NORMAL",
    val cpuRoundSeconds: Int = 600,
    /** Create-room sheet (07 §1): first time 4 digits, 1:1, 60 s. */
    val roomDigits: Int = 4,
    val roomTeamSize: Int = 1,
    val roomRoundSeconds: Int = 60,
    /** Home "최근 10경기": online results finished on this device, oldest first; W, L or D (no server endpoint). */
    val recentForm: String = "",
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
            prefs[QUICK_DIGITS] = next.quickDigits
            prefs[CPU_DIGITS] = next.cpuDigits
            prefs[CPU_LEVEL] = next.cpuLevel
            prefs[CPU_ROUND_SECONDS] = next.cpuRoundSeconds
            prefs[RECENT_FORM] = next.recentForm.takeLast(10)
            prefs[ROOM_DIGITS] = next.roomDigits
            prefs[ROOM_TEAM_SIZE] = next.roomTeamSize
            prefs[ROOM_ROUND_SECONDS] = next.roomRoundSeconds
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
        quickDigits = this[QUICK_DIGITS] ?: defaults.quickDigits,
        cpuDigits = this[CPU_DIGITS] ?: defaults.cpuDigits,
        cpuLevel = this[CPU_LEVEL] ?: defaults.cpuLevel,
        cpuRoundSeconds = this[CPU_ROUND_SECONDS] ?: defaults.cpuRoundSeconds,
        recentForm = this[RECENT_FORM] ?: defaults.recentForm,
        roomDigits = this[ROOM_DIGITS] ?: defaults.roomDigits,
        roomTeamSize = this[ROOM_TEAM_SIZE] ?: defaults.roomTeamSize,
        roomRoundSeconds = this[ROOM_ROUND_SECONDS] ?: defaults.roomRoundSeconds,
    )

    private companion object {
        val EFFECTS = booleanPreferencesKey("effects")
        val EFFECTS_VOLUME = floatPreferencesKey("effects_volume")
        val MUSIC = booleanPreferencesKey("music")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val FOLLOW_SILENT = booleanPreferencesKey("follow_silent_mode")
        val HAPTICS = booleanPreferencesKey("haptics")
        val CHAT_MUTED = booleanPreferencesKey("chat_muted")
        val QUICK_DIGITS = intPreferencesKey("quick_digits")
        val CPU_DIGITS = intPreferencesKey("cpu_digits")
        val CPU_LEVEL = stringPreferencesKey("cpu_level")
        val CPU_ROUND_SECONDS = intPreferencesKey("cpu_round_seconds")
        val RECENT_FORM = stringPreferencesKey("recent_form")
        val ROOM_DIGITS = intPreferencesKey("room_digits")
        val ROOM_TEAM_SIZE = intPreferencesKey("room_team_size")
        val ROOM_ROUND_SECONDS = intPreferencesKey("room_round_seconds")
    }
}
