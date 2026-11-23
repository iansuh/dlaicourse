package app.numbaseball.audio

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import app.numbaseball.R

/**
 * Sound cues = the event hooks of DESIGN_SYSTEM 8.3 that have a sound (mapping in section 10). Files are loudness
 * normalised by the sound designer (design/sound/REPORT.md), so every cue plays at the user's effects volume.
 * [stackable] cues may overlap (lamps within one reveal); any other cue cuts its previous instance.
 */
enum class SoundCue(val files: List<Int>, val priority: Int, val stackable: Boolean = false) {
    KEY_DOWN(listOf(R.raw.sfx_keypad_1, R.raw.sfx_keypad_2, R.raw.sfx_keypad_3), priority = 1),
    BACKSPACE(listOf(R.raw.sfx_keypad_delete), priority = 1),
    KEY_REJECTED(listOf(R.raw.sfx_input_invalid), priority = 2),
    PITCH_SUBMIT(listOf(R.raw.sfx_guess_sent), priority = 2),
    SECRET_LOCKED(listOf(R.raw.sfx_secret_locked), priority = 2),
    OPPONENT_PITCHED(listOf(R.raw.sfx_opponent_guessed), priority = 1),
    LAMP_STRIKE(listOf(R.raw.sfx_hint_strike), priority = 3, stackable = true),
    LAMP_BALL(listOf(R.raw.sfx_hint_ball), priority = 3, stackable = true),
    LAMP_OUT(listOf(R.raw.sfx_hint_out), priority = 3),
    SOLVE(listOf(R.raw.sfx_solved), priority = 4),
    WIN(listOf(R.raw.sfx_win), priority = 5),
    LOSE(listOf(R.raw.sfx_lose), priority = 5),
    DRAW(listOf(R.raw.sfx_draw), priority = 5),
    TIMER_WARN(listOf(R.raw.sfx_timer_warning), priority = 4),
    TIMER_TICK(listOf(R.raw.sfx_timer_warning_last), priority = 4),
    TIMER_EXPIRED(listOf(R.raw.sfx_time_out), priority = 4),
    MATCH_FOUND(listOf(R.raw.sfx_match_found), priority = 5),
    CHAT_MESSAGE(listOf(R.raw.sfx_chat_message), priority = 1),
    QUICK_PHRASE(listOf(R.raw.sfx_quick_phrase), priority = 1),
    MEMO_MARK(listOf(R.raw.sfx_memo_mark), priority = 1),
    TOGGLE_ON(listOf(R.raw.sfx_toggle_on), priority = 1),
    TOGGLE_OFF(listOf(R.raw.sfx_toggle_off), priority = 1),
    NAV_FORWARD(listOf(R.raw.sfx_ui_navigate), priority = 0),
    NAV_BACK(listOf(R.raw.sfx_ui_back), priority = 0),
    ERROR(listOf(R.raw.sfx_error), priority = 2),
}

/** Menu loop: Home, lobby, results, settings, profile. Match loop: search, secret entry, match. */
enum class MusicTrack(@RawRes val file: Int) {
    MENU(R.raw.music_menu),
    MATCH(R.raw.music_match),
}

/** What screens use. Disabled in previews, tests and screenshots ([SilentGameAudio]). */
interface GameAudio {
    /** [rate] is the playback rate (0.5..2): lamp cues rise with the lamp index. */
    fun play(cue: SoundCue, rate: Float = 1f)

    /** The music the current screen wants; null for silence. Plays only if music is enabled and the app has focus. */
    fun setMusic(track: MusicTrack?)

    fun setAppInForeground(foreground: Boolean)
}

object SilentGameAudio : GameAudio {
    override fun play(cue: SoundCue, rate: Float) = Unit
    override fun setMusic(track: MusicTrack?) = Unit
    override fun setAppInForeground(foreground: Boolean) = Unit
}

val LocalGameAudio = staticCompositionLocalOf<GameAudio> { SilentGameAudio }

/** Asks for [track] when the screen enters. Screens without music leave the current track alone. */
@Composable
fun ScreenMusic(track: MusicTrack, audio: GameAudio) {
    LaunchedEffect(track) { audio.setMusic(track) }
}

/** Playback rate for lamp [index] of a reveal (DESIGN_SYSTEM 10: "playback rate by lamp index"). */
fun lampRate(index: Int): Float = (1f + 0.06f * index).coerceAtMost(1.5f)
