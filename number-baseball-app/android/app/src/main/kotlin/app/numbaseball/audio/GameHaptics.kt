package app.numbaseball.audio

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

enum class HapticCue { KEY_TAP, CONFIRM, REJECT }

/** Haptics behind an interface so tests and screenshots swap in [SilentGameHaptics] (QA test hook). */
interface GameHaptics {
    fun perform(cue: HapticCue)
}

object SilentGameHaptics : GameHaptics {
    override fun perform(cue: HapticCue) = Unit
}

val LocalGameHaptics = staticCompositionLocalOf<GameHaptics> { SilentGameHaptics }

/** View haptics (no VIBRATE permission needed), following the "Vibration" setting. Constants from design/tokens.json. */
class ViewGameHaptics(private val view: View, private val enabled: () -> Boolean) : GameHaptics {
    override fun perform(cue: HapticCue) {
        if (!enabled()) return
        val constant = when (cue) {
            HapticCue.KEY_TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticCue.CONFIRM ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            HapticCue.REJECT ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}
