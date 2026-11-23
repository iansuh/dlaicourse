package app.numbaseball.audio

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.staticCompositionLocalOf

/** Haptic events of DESIGN_SYSTEM 9, each fired on its 8.3 hook. */
enum class HapticCue {
    KEY_TAP, REJECT, CONFIRM, LAMP_STRIKE, LAMP_BALL, LAMP_OUT, SOLVE, WIN, LOSE, DRAW,
    TIMER_WARN, TIMER_TICK, MATCH_FOUND, MEMO, LONG_PRESS, TOGGLE_ON, TOGGLE_OFF,
}

/** Haptics behind an interface so tests and screenshots swap in [SilentGameHaptics] (QA test hook). */
interface GameHaptics {
    fun perform(cue: HapticCue)
}

object SilentGameHaptics : GameHaptics {
    override fun perform(cue: HapticCue) = Unit
}

val LocalGameHaptics = staticCompositionLocalOf<GameHaptics> { SilentGameHaptics }

/**
 * One helper for every haptic (DESIGN_SYSTEM 16): checks the "Vibration" setting, uses [VibrationEffect.Composition]
 * primitives where the device supports them and falls back to [View.performHapticFeedback] constants otherwise.
 * Haptics play in silent mode too; only the setting turns them off.
 */
class ViewGameHaptics(
    private val view: View,
    private val vibrator: Vibrator?,
    private val enabled: () -> Boolean,
) : GameHaptics {

    override fun perform(cue: HapticCue) {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator != null && composed(cue, vibrator)) return
        view.performHapticFeedback(fallback(cue))
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun composed(cue: HapticCue, vibrator: Vibrator): Boolean {
        val steps: List<Triple<Int, Float, Int>> = when (cue) { // primitive, scale, delay ms
            HapticCue.LAMP_STRIKE -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 0))
            HapticCue.LAMP_BALL -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 0))
            HapticCue.LAMP_OUT -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f, 0))
            HapticCue.SOLVE -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0))
            HapticCue.WIN -> listOf(
                Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0),
                Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 70),
                Triple(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f, 70),
            )
            HapticCue.LOSE -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_THUD, 0.4f, 0))
            HapticCue.DRAW -> listOf(
                Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 0),
                Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 90),
            )
            HapticCue.TIMER_WARN -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 0))
            HapticCue.TIMER_TICK -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f, 0))
            HapticCue.MATCH_FOUND -> listOf(
                Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 0),
                Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 120),
            )
            else -> return false
        }
        if (!vibrator.areAllPrimitivesSupported(*steps.map { it.first }.toIntArray())) return false
        val composition = VibrationEffect.startComposition()
        steps.forEach { (primitive, scale, delay) -> composition.addPrimitive(primitive, scale, delay) }
        vibrator.vibrate(composition.compose())
        return true
    }

    private fun fallback(cue: HapticCue): Int = when (cue) {
        HapticCue.KEY_TAP -> HapticFeedbackConstants.KEYBOARD_TAP
        HapticCue.REJECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        HapticCue.CONFIRM, HapticCue.SOLVE, HapticCue.WIN, HapticCue.MATCH_FOUND ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        HapticCue.LAMP_OUT, HapticCue.LOSE, HapticCue.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        HapticCue.TOGGLE_ON ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
        HapticCue.TOGGLE_OFF ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
        else -> HapticFeedbackConstants.CLOCK_TICK
    }
}
