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
enum class SoundCue(
    val files: List<Int>,
    val priority: Int,
    val stackable: Boolean = false,
    /** Random playback-rate variation, +/- this fraction (design/sound/INTEGRATION.md section 2, `cues.py`). */
    val rateVariation: Float = 0f,
    /** Random volume variation: the cue plays at 1 - x .. 1 of the effects volume. */
    val volumeVariation: Float = 0f,
) {
    KEY_DOWN(listOf(R.raw.sfx_keypad_1, R.raw.sfx_keypad_2, R.raw.sfx_keypad_3), priority = 1, rateVariation = 0.03f, volumeVariation = 0.15f),
    BACKSPACE(listOf(R.raw.sfx_keypad_delete), priority = 1, rateVariation = 0.02f),
    KEY_REJECTED(listOf(R.raw.sfx_input_invalid), priority = 2),
    PITCH_SUBMIT(listOf(R.raw.sfx_guess_sent), priority = 2, rateVariation = 0.02f),
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
    MEMO_MARK(listOf(R.raw.sfx_memo_mark), priority = 0, rateVariation = 0.04f, volumeVariation = 0.15f),
    TOGGLE_ON(listOf(R.raw.sfx_toggle_on), priority = 0),
    TOGGLE_OFF(listOf(R.raw.sfx_toggle_off), priority = 0),
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
    /**
     * [rate] is the playback rate (0.5..2): lamp cues rise with the lamp index. [volume] (0..1) scales the effects
     * volume for this play only (lamps at 0.7 under reduced motion).
     */
    fun play(cue: SoundCue, rate: Float = 1f, volume: Float = 1f)

    /** The music the current screen wants; null for silence. Plays only if music is enabled and the app has focus. */
    fun setMusic(track: MusicTrack?)

    fun setAppInForeground(foreground: Boolean)
}

object SilentGameAudio : GameAudio {
    override fun play(cue: SoundCue, rate: Float, volume: Float) = Unit
    override fun setMusic(track: MusicTrack?) = Unit
    override fun setAppInForeground(foreground: Boolean) = Unit
}

val LocalGameAudio = staticCompositionLocalOf<GameAudio> { SilentGameAudio }

/** Asks for [track] when the screen enters. Screens without music leave the current track alone. */
@Composable
fun ScreenMusic(track: MusicTrack, audio: GameAudio) {
    LaunchedEffect(track) { audio.setMusic(track) }
}

/**
 * Lamp playback rates: pentatonic steps in D major so a run of lamps climbs (design/sound/INTEGRATION.md section 2,
 * `cues.STRIKE_RATES` / `BALL_RATES`). The index is 0-based within its kind: the first ball lamp is 1.0 even after strikes.
 */
private val STRIKE_RATES = floatArrayOf(1.0f, 1.1225f, 1.3348f, 1.4983f, 1.6818f, 2.0f)
private val BALL_RATES = floatArrayOf(1.0f, 1.1225f, 1.2599f, 1.4983f, 1.6818f, 2.0f)

fun strikeLampRate(index: Int): Float = STRIKE_RATES[index.coerceIn(0, STRIKE_RATES.lastIndex)]

fun ballLampRate(index: Int): Float = BALL_RATES[index.coerceIn(0, BALL_RATES.lastIndex)]

/** Lamp cue volume: 0.7 under reduced motion, where the lamps of a row fire 60 ms apart (INTEGRATION.md section 2). */
fun lampVolume(reducedMotion: Boolean): Float = if (reducedMotion) 0.7f else 1f

/**
 * Pitch clock cues (DESIGN_SYSTEM 8.3 D, INTEGRATION.md section 5): one `timer_warn` at 10 s, one `timer_tick` at each
 * of 5..1 s, `timer_expired` at 0. A per-round set of handled seconds stops double plays from recompositions; a clock
 * that resumes below a threshold plays only the current second, never the missed ones.
 */
class TimerCues {
    enum class Cue { WARN, TICK, EXPIRED }

    private var round: Any? = null
    private val handled = HashSet<Int>()

    /** The cue for [secondsLeft] in [round], or null when that second has no cue or was already handled. */
    fun onSecond(round: Any?, secondsLeft: Int): Cue? {
        if (round != this.round) {
            this.round = round
            handled.clear()
        }
        val cue = when (secondsLeft) {
            10 -> Cue.WARN
            in 1..5 -> Cue.TICK
            0 -> Cue.EXPIRED
            else -> null
        } ?: return null
        return if (handled.add(secondsLeft)) cue else null
    }
}
