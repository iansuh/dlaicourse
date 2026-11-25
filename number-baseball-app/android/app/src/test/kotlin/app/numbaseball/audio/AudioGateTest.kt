package app.numbaseball.audio

import android.media.AudioManager
import app.numbaseball.data.settings.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGateTest {
    private val defaults = AppSettings()
    private val normal = AudioManager.RINGER_MODE_NORMAL

    private fun effects(
        settings: AppSettings = defaults,
        ringer: Int = normal,
        mode: Int = AudioManager.MODE_NORMAL,
        foreground: Boolean = true,
        focusLost: Boolean = false,
    ) = AudioGate.effectsAllowed(settings, ringer, mode, foreground, focusLost)

    @Test fun effectsAreOnByDefaultButFollowSilentAndVibrate() {
        assertTrue(effects())
        assertFalse(effects(ringer = AudioManager.RINGER_MODE_SILENT))
        assertFalse(effects(ringer = AudioManager.RINGER_MODE_VIBRATE))
        assertTrue(effects(settings = defaults.copy(followSilentMode = false), ringer = AudioManager.RINGER_MODE_SILENT))
    }

    @Test fun effectsStopInTheBackgroundDuringCallsAndOnFocusLoss() {
        assertFalse(effects(foreground = false))
        assertFalse(effects(mode = AudioManager.MODE_IN_CALL))
        assertFalse(effects(mode = AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(effects(focusLost = true))
        assertFalse(effects(settings = defaults.copy(effectsVolume = 0f)))
        assertFalse(effects(settings = defaults.copy(effectsEnabled = false)))
    }

    @Test fun musicIsOffByDefault() {
        assertFalse(AudioGate.musicAllowed(defaults, foreground = true, audioMode = AudioManager.MODE_NORMAL))
        val on = defaults.copy(musicEnabled = true)
        assertTrue(AudioGate.musicAllowed(on, foreground = true, audioMode = AudioManager.MODE_NORMAL))
        assertFalse(AudioGate.musicAllowed(on, foreground = false, audioMode = AudioManager.MODE_NORMAL))
        assertFalse(AudioGate.musicAllowed(on, foreground = true, audioMode = AudioManager.MODE_IN_CALL))
        assertFalse(AudioGate.musicAllowed(on, foreground = true, audioMode = AudioManager.MODE_NORMAL, ringerMode = AudioManager.RINGER_MODE_VIBRATE))
        assertTrue(
            AudioGate.musicAllowed(on.copy(followSilentMode = false), foreground = true, audioMode = AudioManager.MODE_NORMAL, ringerMode = AudioManager.RINGER_MODE_SILENT),
        )
    }
}
