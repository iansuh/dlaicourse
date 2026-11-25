package app.numbaseball.ads

import app.numbaseball.core.protocol.EndReason

/** What just finished; only the first three can ever lead to an interstitial (06 §3). */
enum class FinishedMatchKind { COMPUTER, ONLINE_DUEL, TEAM_MATCH, PRACTICE, REVIEW_ROOM }

/**
 * Everything the interstitial rule looks at, sampled while the finished match is still on screen (06 §3, DESIGN_SYSTEM 13,
 * POLICY_CHECKLIST 11.1 safeguards). Times in milliseconds.
 */
data class InterstitialInput(
    val kind: FinishedMatchKind,
    val endReason: EndReason?,
    /** The match ended because the server restarted (no result for anyone). */
    val serverRestart: Boolean = false,
    /** The player owns `remove_ads` (or the entitlement is not known yet). */
    val adsRemoved: Boolean,
    val windowHeightDp: Int,
    val sinceResultMillis: Long,
    val pointerDown: Boolean,
    /** Time since the last pointer went up; null if there was no touch since the result. */
    val sinceLastTouchUpMillis: Long?,
    val chatOpen: Boolean,
    val keyboardOpen: Boolean,
    /** False once the player left the match screen (back, close, leave dialog, opponent disconnect ended it). */
    val stillOnScreen: Boolean,
    val appInForeground: Boolean,
    /** A rematch or the next team duel is about to start. */
    val nextMatchStarting: Boolean,
    /** Counted matches since the last interstitial, including this one. */
    val finishedSinceLastAd: Int,
    /** Since the last interstitial; process start counts as one, so nothing comes soon after launch. */
    val sinceLastAdMillis: Long,
    val adLoaded: Boolean,
)

sealed interface InterstitialDecision {
    /** Keep holding on the final frame and ask again. */
    data object Wait : InterstitialDecision
    data object Show : InterstitialDecision
    data class Skip(val reason: SkipReason) : InterstitialDecision
}

enum class SkipReason {
    ADS_REMOVED,
    NOT_ELIGIBLE,
    SMALL_WINDOW,
    NEXT_MATCH_STARTING,
    PLAYER_LEFT,
    APP_IN_BACKGROUND,
    CHAT_OR_KEYBOARD_OPEN,
    TOO_FEW_MATCHES,
    TOO_SOON,
    NOT_LOADED,
    HELD_TOO_LONG,
}

/**
 * The interstitial rule as a pure function (unit tested). Owner decision 2026-11-25: after vs-computer **and** online
 * matches, on the way to the results screen; at most one per [MATCHES_BETWEEN] finished matches (both kinds counted
 * together, a team match once) and at least [MIN_INTERVAL_MILLIS] apart; held ≥ [HOLD_MILLIS] after the result with no
 * finger down (and [TOUCH_SETTLE_MILLIS] after the last touch up). Never: practice, review room, cancelled or
 * server-restart endings, windows lower than [MIN_WINDOW_HEIGHT_DP], chat or keyboard open, player already gone, app in
 * the background, a rematch or next duel about to start, or `remove_ads` owned. It is only ever asked between a match end
 * and its results, so it can never fire during a match, the secret entry, on app start or on exit.
 */
object InterstitialRules {
    const val MATCHES_BETWEEN = 3
    const val MIN_INTERVAL_MILLIS = 4 * 60_000L
    const val HOLD_MILLIS = 1_000L
    const val TOUCH_SETTLE_MILLIS = 300L
    /** A finger that stays down this long means the player is busy: skip rather than show it late. */
    const val MAX_HOLD_MILLIS = 5_000L
    const val MIN_WINDOW_HEIGHT_DP = 480

    fun countsTowardCap(kind: FinishedMatchKind, endReason: EndReason?, serverRestart: Boolean = false): Boolean =
        kind in COUNTED && endReason != EndReason.CANCELLED && !serverRestart

    fun decide(input: InterstitialInput): InterstitialDecision {
        fun skip(reason: SkipReason) = InterstitialDecision.Skip(reason)
        with(input) {
            if (adsRemoved) return skip(SkipReason.ADS_REMOVED)
            if (!countsTowardCap(kind, endReason, serverRestart)) return skip(SkipReason.NOT_ELIGIBLE)
            if (windowHeightDp < MIN_WINDOW_HEIGHT_DP) return skip(SkipReason.SMALL_WINDOW)
            if (nextMatchStarting) return skip(SkipReason.NEXT_MATCH_STARTING)
            if (!stillOnScreen) return skip(SkipReason.PLAYER_LEFT)
            if (!appInForeground) return skip(SkipReason.APP_IN_BACKGROUND)
            // Never close the chat or the keyboard to make room for an ad; this match then does not reset the cap.
            if (chatOpen || keyboardOpen) return skip(SkipReason.CHAT_OR_KEYBOARD_OPEN)
            if (finishedSinceLastAd < MATCHES_BETWEEN) return skip(SkipReason.TOO_FEW_MATCHES)
            if (sinceLastAdMillis < MIN_INTERVAL_MILLIS) return skip(SkipReason.TOO_SOON)
            if (!adLoaded) return skip(SkipReason.NOT_LOADED)
            val touching = pointerDown || (sinceLastTouchUpMillis != null && sinceLastTouchUpMillis < TOUCH_SETTLE_MILLIS)
            if (sinceResultMillis < HOLD_MILLIS || touching) {
                return if (sinceResultMillis >= MAX_HOLD_MILLIS) skip(SkipReason.HELD_TOO_LONG) else InterstitialDecision.Wait
            }
            return InterstitialDecision.Show
        }
    }

    private val COUNTED = setOf(FinishedMatchKind.COMPUTER, FinishedMatchKind.ONLINE_DUEL, FinishedMatchKind.TEAM_MATCH)
}

/**
 * The cap's memory: counted matches and the time of the last interstitial (process start counts as one). Not
 * thread-safe; main thread only.
 */
class InterstitialPolicy(private val now: () -> Long) {
    var finishedSinceLastAd = 0
        private set
    private var lastAdAt = now()

    val sinceLastAdMillis: Long get() = now() - lastAdAt

    fun onMatchFinished(kind: FinishedMatchKind, endReason: EndReason?, serverRestart: Boolean = false) {
        if (InterstitialRules.countsTowardCap(kind, endReason, serverRestart)) finishedSinceLastAd++
    }

    /** Load one ahead during the last match before one can be due, so it is ready at the next match end. */
    fun shouldPreload(): Boolean = finishedSinceLastAd >= InterstitialRules.MATCHES_BETWEEN - 1

    fun onShown() {
        finishedSinceLastAd = 0
        lastAdAt = now()
    }
}
