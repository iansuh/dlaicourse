package app.numbaseball.qa

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.ads.FinishedMatchKind
import app.numbaseball.core.protocol.RoomState
import app.numbaseball.testing.SampleData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Room lobby, chat and the waiting-room practice game: no banner, never an interstitial (README "Monetization"). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class AdsLobbyQaTest {
    @get:Rule val compose = createComposeRule()
    private val ui = QaDriver(compose)

    private fun createRoom(c: QaContainer) {
        ui.waitTag("quickMatch")
        compose.onNodeWithText("Create room").performClick()
        compose.onNodeWithTag("roomConfirm").performClick()
        compose.waitForIdle()
        runBlocking { c.client.receive(RoomState(SampleData.privateLobby(players = listOf(SampleData.me)))) }
        check(ui.tickUntil(10_000) { ui.exists("practice") }) { "lobby did not show the practice button" }
    }

    @Test fun lobbyChatAndPracticeAreAdFreeAndPracticeNeverTriggersAnInterstitial() {
        val c = returningPlayer()
        c.ads.interstitial.primeCap(matches = 5, minutesAgo = 30)
        compose.setQaApp(c)
        createRoom(c)
        assertFalse("no banner in the room lobby while players are joining", ui.hasBanner())

        // Chat sheet from the lobby.
        compose.onNodeWithTag("chatPeek").performClick()
        compose.waitForIdle()
        assertFalse("no banner in chat", ui.hasBanner())
        compose.onAllNodesWithContentDescription("Close").onLast().performClick()
        compose.waitForIdle()

        // Practice game while waiting.
        compose.onNodeWithTag("practice").performClick()
        ui.waitText("Pick your secret number")
        assertFalse("no banner in the practice secret entry", ui.hasBanner())
        ui.type("012")
        compose.onNodeWithText("Lock in").performClick()
        ui.waitTag("key_0")
        assertFalse("no banner in the practice game", ui.hasBanner())
        val before = c.ads.interstitial.finishedSinceLastAd
        ui.forfeit()
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertEquals("practice: no interstitial even though the cap is due", 0, c.ads.adShown)
        assertEquals("practice does not count toward the cap", before, c.ads.interstitial.finishedSinceLastAd)
        assertEquals(FinishedMatchKind.PRACTICE, c.ads.interstitial.finishedEvents.last().first)
        assertFalse("no banner after practice either", ui.hasBanner())
    }
}
