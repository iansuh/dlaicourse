package app.numbaseball.audio

import android.media.AudioManager
import app.numbaseball.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun lampRatesClimbThePentatonicTables() {
        assertEquals(listOf(1.0f, 1.1225f, 1.3348f, 1.4983f, 1.6818f, 2.0f), List(6) { strikeLampRate(it) })
        assertEquals(listOf(1.0f, 1.1225f, 1.2599f, 1.4983f, 1.6818f, 2.0f), List(6) { ballLampRate(it) })
        assertEquals(0.7f, lampVolume(reducedMotion = true))
        assertEquals(1f, lampVolume(reducedMotion = false))
    }

    @Test fun timerPlaysOneWarningFiveTicksAndTimeOutPerRound() {
        val cues = TimerCues()
        val played = (60 downTo 0).mapNotNull { s -> cues.onSecond(1, s)?.let { s to it } }
        assertEquals(listOf(10 to TimerCues.Cue.WARN) + (5 downTo 1).map { it to TimerCues.Cue.TICK } + (0 to TimerCues.Cue.EXPIRED), played)
        // Recomposition or a repeated frame never plays twice; a new round starts over.
        assertNull(cues.onSecond(1, 3))
        assertEquals(TimerCues.Cue.TICK, cues.onSecond(2, 3))
        // Resuming below a threshold plays only the current second.
        val resumed = TimerCues()
        assertEquals(TimerCues.Cue.TICK, resumed.onSecond(7, 4))
        assertNull(resumed.onSecond(7, 9))
    }
}
