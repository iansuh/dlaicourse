package app.numbaseball.ads

import app.numbaseball.core.protocol.EndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The interstitial rule (06 §3, DESIGN_SYSTEM 13, POLICY_CHECKLIST 11.1) as a pure function. */
class InterstitialRulesTest {
    /** Everything allows an ad; each test changes one thing. */
    private val due = InterstitialInput(
        kind = FinishedMatchKind.COMPUTER,
        endReason = EndReason.SOLVED,
        adsRemoved = false,
        windowHeightDp = 800,
        sinceResultMillis = 1_500,
        pointerDown = false,
        sinceLastTouchUpMillis = null,
        chatOpen = false,
        keyboardOpen = false,
        stillOnScreen = true,
        appInForeground = true,
        nextMatchStarting = false,
        finishedSinceLastAd = 3,
        sinceLastAdMillis = 5 * 60_000L,
        adLoaded = true,
    )

    private fun decide(input: InterstitialInput) = InterstitialRules.decide(input)
    private fun skip(reason: SkipReason) = InterstitialDecision.Skip(reason)

    @Test fun showsAfterComputerAndOnlineMatchesAndTheWholeTeamMatch() {
        assertEquals(InterstitialDecision.Show, decide(due))
        assertEquals(InterstitialDecision.Show, decide(due.copy(kind = FinishedMatchKind.ONLINE_DUEL)))
        assertEquals(InterstitialDecision.Show, decide(due.copy(kind = FinishedMatchKind.TEAM_MATCH, endReason = null)))
    }

    @Test fun neverInPracticeTheReviewRoomOrAfterACancelledOrRestartedMatch() {
        assertEquals(skip(SkipReason.NOT_ELIGIBLE), decide(due.copy(kind = FinishedMatchKind.PRACTICE)))
        assertEquals(skip(SkipReason.NOT_ELIGIBLE), decide(due.copy(kind = FinishedMatchKind.REVIEW_ROOM)))
        assertEquals(skip(SkipReason.NOT_ELIGIBLE), decide(due.copy(endReason = EndReason.CANCELLED)))
        assertEquals(skip(SkipReason.NOT_ELIGIBLE), decide(due.copy(serverRestart = true)))
    }

    @Test fun neverWhenRemoveAdsIsOwned() {
        assertEquals(skip(SkipReason.ADS_REMOVED), decide(due.copy(adsRemoved = true)))
    }

    @Test fun neverInWindowsLowerThan480dp() {
        assertEquals(skip(SkipReason.SMALL_WINDOW), decide(due.copy(windowHeightDp = 479)))
        assertEquals(InterstitialDecision.Show, decide(due.copy(windowHeightDp = 480)))
    }

    @Test fun capOnePerThreeMatches() {
        assertEquals(skip(SkipReason.TOO_FEW_MATCHES), decide(due.copy(finishedSinceLastAd = 2)))
    }

    @Test fun capFourMinutesApart() {
        assertEquals(skip(SkipReason.TOO_SOON), decide(due.copy(sinceLastAdMillis = 4 * 60_000L - 1)))
        assertEquals(InterstitialDecision.Show, decide(due.copy(sinceLastAdMillis = 4 * 60_000L)))
    }

    @Test fun holdsOneSecondAfterTheResult() {
        assertEquals(InterstitialDecision.Wait, decide(due.copy(sinceResultMillis = 999)))
        assertEquals(InterstitialDecision.Show, decide(due.copy(sinceResultMillis = 1_000)))
    }

    @Test fun waitsForTheLastTouchUpPlus300ms() {
        assertEquals(InterstitialDecision.Wait, decide(due.copy(pointerDown = true)))
        assertEquals(InterstitialDecision.Wait, decide(due.copy(sinceLastTouchUpMillis = 299)))
        assertEquals(InterstitialDecision.Show, decide(due.copy(sinceLastTouchUpMillis = 300)))
    }

    @Test fun aFingerThatStaysDownSkipsInsteadOfShowingLate() {
        assertEquals(skip(SkipReason.HELD_TOO_LONG), decide(due.copy(pointerDown = true, sinceResultMillis = InterstitialRules.MAX_HOLD_MILLIS)))
    }

    @Test fun neverWithChatOrKeyboardOpen() {
        assertEquals(skip(SkipReason.CHAT_OR_KEYBOARD_OPEN), decide(due.copy(chatOpen = true)))
        assertEquals(skip(SkipReason.CHAT_OR_KEYBOARD_OPEN), decide(due.copy(keyboardOpen = true)))
    }

    @Test fun neverAfterThePlayerLeftOrInTheBackground() {
        assertEquals(skip(SkipReason.PLAYER_LEFT), decide(due.copy(stillOnScreen = false)))
        assertEquals(skip(SkipReason.APP_IN_BACKGROUND), decide(due.copy(appInForeground = false)))
    }

    @Test fun neverWhenARematchOrTheNextDuelIsAboutToStart() {
        assertEquals(skip(SkipReason.NEXT_MATCH_STARTING), decide(due.copy(nextMatchStarting = true)))
    }

    @Test fun noSpinnerWhenNotLoaded() {
        assertEquals(skip(SkipReason.NOT_LOADED), decide(due.copy(adLoaded = false)))
    }

    @Test fun noAdIsNeverAHold() {
        // Not due → the results follow at once, even inside the first second.
        assertEquals(skip(SkipReason.TOO_FEW_MATCHES), decide(due.copy(finishedSinceLastAd = 1, sinceResultMillis = 0)))
    }
}

class InterstitialPolicyTest {
    private var now = 0L
    private val policy = InterstitialPolicy(now = { now })

    private fun finish(matches: Int, kind: FinishedMatchKind = FinishedMatchKind.COMPUTER) =
        repeat(matches) { policy.onMatchFinished(kind, EndReason.SOLVED) }

    @Test fun onlineAndComputerMatchesCountTogether() {
        finish(1, FinishedMatchKind.COMPUTER)
        finish(1, FinishedMatchKind.ONLINE_DUEL)
        finish(1, FinishedMatchKind.TEAM_MATCH)
        assertEquals(3, policy.finishedSinceLastAd)
    }

    @Test fun practiceReviewRoomAndCancelledDoNotCount() {
        finish(2, FinishedMatchKind.PRACTICE)
        finish(2, FinishedMatchKind.REVIEW_ROOM)
        policy.onMatchFinished(FinishedMatchKind.ONLINE_DUEL, EndReason.CANCELLED)
        policy.onMatchFinished(FinishedMatchKind.ONLINE_DUEL, EndReason.SOLVED, serverRestart = true)
        assertEquals(0, policy.finishedSinceLastAd)
    }

    @Test fun processStartCountsAsJustShown() {
        assertEquals(0, policy.sinceLastAdMillis)
        now = 3 * 60_000
        assertEquals(3 * 60_000L, policy.sinceLastAdMillis)
    }

    @Test fun showingResetsBothCaps() {
        finish(3)
        now = 10 * 60_000
        policy.onShown()
        assertEquals(0, policy.finishedSinceLastAd)
        assertEquals(0, policy.sinceLastAdMillis)
    }

    @Test fun preloadsBeforeTheMatchThatCanBeDue() {
        finish(1)
        assertFalse(policy.shouldPreload())
        finish(1)
        assertTrue(policy.shouldPreload())
    }
}
