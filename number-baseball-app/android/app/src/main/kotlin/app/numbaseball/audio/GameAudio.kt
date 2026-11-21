package app.numbaseball.audio

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Sound effect cues from README "Sound". [priority] follows SoundPool (higher wins when streams run out), [volume] is the
 * cue's mix level before the user's effects volume, [pitchVariation] is the random +/- playback-rate range that keeps
 * repeated cues (keypad) from sounding mechanical.
 */
enum class SoundCue(val id: String, val priority: Int, val volume: Float, val pitchVariation: Float) {
    KEYPAD("keypad", priority = 1, volume = 0.6f, pitchVariation = 0.04f),
    GUESS_SENT("guess_sent", priority = 2, volume = 0.8f, pitchVariation = 0.02f),
    HINT_STRIKE("hint_strike", priority = 3, volume = 0.9f, pitchVariation = 0f),
    HINT_BALL("hint_ball", priority = 3, volume = 0.9f, pitchVariation = 0f),
    HINT_OUT("hint_out", priority = 3, volume = 0.9f, pitchVariation = 0f),
    SOLVED("solved", priority = 4, volume = 1f, pitchVariation = 0f),
    WIN("win", priority = 5, volume = 1f, pitchVariation = 0f),
    LOSE("lose", priority = 5, volume = 1f, pitchVariation = 0f),
    DRAW("draw", priority = 5, volume = 1f, pitchVariation = 0f),
    MATCH_FOUND("match_found", priority = 5, volume = 1f, pitchVariation = 0f),
    TIMER_WARNING("timer_warning", priority = 4, volume = 0.8f, pitchVariation = 0f),
    CHAT_MESSAGE("chat_message", priority = 1, volume = 0.5f, pitchVariation = 0.03f),
    UI_NAVIGATE("ui_navigate", priority = 0, volume = 0.4f, pitchVariation = 0.03f),
}

enum class MusicTrack(val id: String) {
    MENU("music_menu"),
    MATCH("music_match"),
}

/** Raw resources for cues and tracks; null until the sound designer delivers the OGG files (design/sound). */
object SoundResources {
    // TODO(sound): map to R.raw.* when design/sound/INTEGRATION.md and the OGG files land.
    @RawRes fun effect(cue: SoundCue): Int? = null

    @RawRes fun music(track: MusicTrack): Int? = null
}

/** What screens use. Disabled in previews, tests and screenshots ([SilentGameAudio]). */
interface GameAudio {
    fun play(cue: SoundCue)

    /** The music the current screen wants; null for silence. Plays only if music is enabled and the app has focus. */
    fun setMusic(track: MusicTrack?)

    fun setAppInForeground(foreground: Boolean)
}

object SilentGameAudio : GameAudio {
    override fun play(cue: SoundCue) = Unit
    override fun setMusic(track: MusicTrack?) = Unit
    override fun setAppInForeground(foreground: Boolean) = Unit
}

val LocalGameAudio = staticCompositionLocalOf<GameAudio> { SilentGameAudio }

/** Asks for [track] when the screen enters. Screens without music leave the current track alone. */
@Composable
fun ScreenMusic(track: MusicTrack, audio: GameAudio) {
    LaunchedEffect(track) { audio.setMusic(track) }
}
