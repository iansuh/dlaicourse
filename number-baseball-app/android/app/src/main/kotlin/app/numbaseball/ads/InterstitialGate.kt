package app.numbaseball.ads

import android.app.Activity
import android.os.SystemClock
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.numbaseball.audio.GameAudio
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.billing.BillingRepository
import app.numbaseball.billing.LocalBilling
import app.numbaseball.billing.adsAllowed
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.ui.common.findActivity
import app.numbaseball.ui.common.rememberWindowHeightDp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Whether a finger is on the screen, and when the last one went up (06 §3: "no pointer down, then 300 ms"). */
class TouchTracker(private val now: () -> Long = SystemClock::elapsedRealtime) {
    var pointerDown: Boolean = false
        private set
    var lastUpAt: Long? = null
        private set

    fun onPointers(anyPressed: Boolean) {
        if (pointerDown && !anyPressed) lastUpAt = now()
        pointerDown = anyPressed
    }
}

val LocalTouchTracker = staticCompositionLocalOf { TouchTracker() }

/** Observes every touch in the window without consuming it (installed once at the app root). */
fun Modifier.trackTouches(tracker: TouchTracker): Modifier = pointerInput(tracker) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            tracker.onPointers(event.changes.any { it.pressed })
        }
    }
}

/**
 * Runs the interstitial between a finished match and its results (06 §3). Lives in the match (or live board)
 * destination's composition: if the player leaves that screen during the hold, the coroutine is cancelled and the ad is
 * never shown later. Each [run] counts the match once toward the cap.
 */
class InterstitialGate internal constructor(
    private val scope: CoroutineScope,
    private val ads: AdsController,
    private val billing: BillingRepository,
    private val audio: GameAudio,
    private val touches: TouchTracker,
    private val activity: () -> Activity?,
    private val lifecycle: () -> Lifecycle,
    private val windowHeightDp: State<Int>,
    private val imeVisible: State<Boolean>,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private var running = false

    /**
     * [then] goes on to the results; it is called at once when no ad is due (no extra wait), after the ad closes, or not
     * at all when the player already left the screen.
     */
    fun run(
        kind: FinishedMatchKind,
        endReason: EndReason?,
        chatOpen: () -> Boolean = { false },
        nextMatchStarting: () -> Boolean = { false },
        then: (adShown: Boolean) -> Unit,
    ) {
        if (running) return
        running = true
        val interstitials = ads.interstitials
        interstitials.onMatchFinished(kind, endReason)
        val resultAt = now()
        scope.launch {
            while (true) {
                val t = now()
                val input = InterstitialInput(
                    kind = kind,
                    endReason = endReason,
                    adsRemoved = !billing.entitlement.value.adsAllowed,
                    windowHeightDp = windowHeightDp.value,
                    sinceResultMillis = t - resultAt,
                    pointerDown = touches.pointerDown,
                    sinceLastTouchUpMillis = touches.lastUpAt?.takeIf { it >= resultAt }?.let { t - it },
                    chatOpen = chatOpen(),
                    keyboardOpen = imeVisible.value,
                    stillOnScreen = true,
                    appInForeground = lifecycle().currentState.isAtLeast(RESUMED),
                    nextMatchStarting = nextMatchStarting(),
                    finishedSinceLastAd = interstitials.finishedSinceLastAd,
                    sinceLastAdMillis = interstitials.sinceLastAdMillis,
                    adLoaded = interstitials.isLoaded,
                )
                when (val decision = InterstitialRules.decide(input)) {
                    InterstitialDecision.Wait -> delay(POLL_MILLIS)
                    InterstitialDecision.Show -> {
                        val host = activity()
                        if (host == null) {
                            then(false)
                        } else {
                            // Music pauses while the ad is open (DESIGN_SYSTEM 13); the results screen starts its own.
                            audio.setMusic(null)
                            interstitials.show(host) { then(true) }
                        }
                        return@launch
                    }
                    is InterstitialDecision.Skip -> {
                        if (decision.reason != SkipReason.PLAYER_LEFT) then(false)
                        return@launch
                    }
                }
            }
        }
    }

    private companion object {
        const val POLL_MILLIS = 50L
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberInterstitialGate(): InterstitialGate {
    val ads = LocalAdsController.current
    val billing = LocalBilling.current
    val audio = LocalGameAudio.current
    val touches = LocalTouchTracker.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val height = rememberUpdatedState(rememberWindowHeightDp())
    val ime = rememberUpdatedState(WindowInsets.isImeVisible)
    return remember(scope, ads, billing) {
        InterstitialGate(scope, ads, billing, audio, touches, { context.findActivity() }, { lifecycleOwner.lifecycle }, height, ime)
    }
}
