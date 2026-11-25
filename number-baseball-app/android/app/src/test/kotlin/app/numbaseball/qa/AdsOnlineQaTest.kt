package app.numbaseball.qa

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.MatchResult
import app.numbaseball.core.protocol.RoomState
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.Team
import app.numbaseball.data.online.REVIEW_BOT_ID
import app.numbaseball.testing.SampleData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * AC-ADS for online matches: quick match through the real navigation, terms gate and `OnlineSession`, with the server
 * played by the fake client. The result reaches the results screen through the interstitial gate.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class AdsOnlineQaTest {
    @get:Rule val compose = createComposeRule()
    private val ui = QaDriver(compose)

    private fun room(finished: Boolean, version: Long, review: Boolean = false): RoomView {
        val duel = SampleData.duel(
            phase = if (finished) DuelPhase.FINISHED else DuelPhase.GUESSING,
            round = 3,
            result = if (finished) DuelResult(Team.A, EndReason.SOLVED) else null,
            deadline = null,
        )
        val base = SampleData.quickRoom(duel, version, matchResult = if (finished) MatchResult(Team.A, false) else null)
        if (!review) return base
        val bot = SampleData.opponent.copy(id = REVIEW_BOT_ID, nickname = "Reviewer")
        val botDuel = duel.copy(opponentId = REVIEW_BOT_ID)
        return base.copy(
            players = listOf(SampleData.me, bot),
            match = base.match!!.copy(myDuel = botDuel, duels = base.match!!.duels.map { it.copy(playerB = REVIEW_BOT_ID) }),
        )
    }

    private fun send(c: QaContainer, room: RoomView) = runBlocking { c.client.receive(RoomState(room)) }

    /** Home → Quick match → (server pairs us) → match screen in round 3. */
    private fun enterOnlineMatch(c: QaContainer, review: Boolean = false) {
        ui.waitTag("quickMatch")
        compose.onNodeWithTag("quickMatch").performClick()
        ui.waitText("Looking for an opponent")
        assertFalse("no banner while searching", ui.hasBanner())
        send(c, room(finished = false, version = 1, review = review))
        check(ui.tickUntil(10_000) { ui.exists("key_0") }) { "online match screen did not open" }
        compose.waitForIdle()
        assertFalse("no banner during the online match", ui.hasBanner())
    }

    private fun finishOnline(c: QaContainer, review: Boolean = false) = send(c, room(finished = true, version = 9, review = review))

    @Test fun dueIntervalAfterAnOnlineMatchShowsTheAdBetweenMatchEndAndResults() {
        val c = returningPlayer()
        c.ads.interstitial.primeCap(matches = 3, minutesAgo = 5)
        compose.setQaApp(c)
        enterOnlineMatch(c)
        finishOnline(c)
        check(ui.tickUntil { c.ads.adShown > 0 || ui.exists("resultPrimary") })
        assertEquals("online matches count and can show the ad", 1, c.ads.adShown)
        assertFalse(ui.exists("resultPrimary"))
        c.ads.closeAd()
        ui.awaitResult()
        assertFalse("a result reached through the ad shows no banner", ui.hasBanner())
        assertEquals(listOf(app.numbaseball.ads.FinishedMatchKind.ONLINE_DUEL), c.ads.interstitial.finishedEvents.map { it.first }.takeLast(1))
    }

    @Test fun onlineMatchWithTheCapNotDueGoesStraightToResultsWithBanner() {
        val c = returningPlayer()
        compose.setQaApp(c)
        enterOnlineMatch(c)
        finishOnline(c)
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertEquals(0, c.ads.adShown)
        assertTrue(ui.hasBanner())
    }

    @Test fun reviewRoomNeverShowsAnInterstitialAndDoesNotCountTowardTheCap() {
        val c = returningPlayer()
        c.ads.interstitial.primeCap(matches = 5, minutesAgo = 30)
        compose.setQaApp(c)
        enterOnlineMatch(c, review = true)
        val before = c.ads.interstitial.finishedSinceLastAd
        finishOnline(c, review = true)
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertEquals("review room: reviewers reach results without an interstitial", 0, c.ads.adShown)
        assertEquals("review room does not count", before, c.ads.interstitial.finishedSinceLastAd)
        assertEquals(app.numbaseball.ads.FinishedMatchKind.REVIEW_ROOM, c.ads.interstitial.finishedEvents.last().first)
    }

    @Test fun phraseSheetOpenSkipsTheAdAndTheMatchDoesNotResetTheCap() {
        val c = returningPlayer()
        c.ads.interstitial.primeCap(matches = 3, minutesAgo = 5)
        compose.setQaApp(c)
        enterOnlineMatch(c)
        // Open the quick-phrase sheet (chat for quick matches), then the match ends underneath it.
        compose.onNodeWithContentDescription("Chat", substring = true).performClick()
        compose.waitForIdle()
        finishOnline(c)
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertEquals("chat / phrase sheet open: no ad", 0, c.ads.adShown)
        assertTrue("the skipped match leaves the cap due", c.ads.interstitial.finishedSinceLastAd >= 4)
    }

    @Test fun onlineMatchEndingFromTheOpponentLeavingStillCountsOnlyOnce() {
        val c = returningPlayer()
        compose.setQaApp(c)
        enterOnlineMatch(c)
        finishOnline(c)
        // The server repeats the finished snapshot (resync after a reconnect): the match must not be counted twice.
        send(c, room(finished = true, version = 10))
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertEquals(1, c.ads.interstitial.finishedEvents.count { it.first == app.numbaseball.ads.FinishedMatchKind.ONLINE_DUEL })
    }
}
