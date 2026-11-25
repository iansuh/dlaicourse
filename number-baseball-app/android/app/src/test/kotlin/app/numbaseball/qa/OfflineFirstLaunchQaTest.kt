package app.numbaseball.qa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.MainActivity
import app.numbaseball.core.protocol.Stats
import app.numbaseball.data.account.AccountState
import app.numbaseball.data.settings.AppSettings
import app.numbaseball.testing.FakeSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * AC-OFF: the first launch with no server reachable (airplane mode equivalent: network monitor offline, every REST call
 * fails, the socket never connects). The real `MainActivity` and navigation graph run on the QA container.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = QaApp::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class OfflineFirstLaunchQaTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun close() {
        scenario?.close()
    }

    private fun offlineContainer(): QaContainer {
        val c = qaApp().container as QaContainer
        return c
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun type(digits: String) = digits.forEach { compose.onNodeWithTag("key_$it").performClick() }

    private fun waitForTag(tag: String, timeout: Long = 10_000) =
        compose.waitUntil(timeout) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForText(text: String, timeout: Long = 10_000) =
        compose.waitUntil(timeout) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun fullFirstLaunchWithoutAnyNetworkEndsInAFinishedVsComputerMatch() {
        val server = UnreachableAccountApi()
        val c = QaContainer(
            accountApi = server,
            net = QaNetwork(false),
            settingsRepository = FakeSettingsRepository(AppSettings(cpuLevel = "HARD", cpuDigits = 3)),
        ).also { it.client.connected = false }
        // Replace the application's default container with the offline one before the activity reads it.
        val app = qaApp()
        val field = app.javaClass.superclass!!.getDeclaredField("container").apply { isAccessible = true }
        field.set(app, c)
        launch()

        // 1. Age gate comes first; nothing ad-related ran yet.
        compose.onNodeWithText("Enter the year and month you were born").assertIsDisplayed()
        assertTrue("no consent / ads call before the age answer", c.ads.consentCalls.isEmpty())
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        type("199005")
        compose.onNodeWithText("Confirm").performClick()

        // 2. Nickname. The server is down: the nickname is kept locally and the player goes on (AC-OFF-01).
        waitForTag("nickname")
        compose.onNodeWithTag("nickname").performTextInput("Mina")
        compose.onNodeWithText("Start").performClick()

        // 3. First run ends in the tutorial, not on a network error.
        waitForText("Next")
        assertTrue("the server was asked to register and failed quietly", server.calls >= 1)
        assertTrue(c.accountStore.value.value.token == null)
        assertEquals("Mina", c.accountStore.value.value.nickname)
        compose.onNodeWithText("Next").performClick()
        type("075")
        compose.onNodeWithText("Pitch").performClick()
        waitForText("Next")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        type("047")
        compose.onNodeWithText("Pitch").performClick()
        waitForText("Let’s play")
        compose.onNodeWithText("Let’s play").performClick()

        // 4. Home: offline band, online modes disabled, vs Computer works, no banner while offline.
        waitForTag("quickMatch")
        compose.onNodeWithText("Offline · only vs Computer is available").assertIsDisplayed()
        compose.onNodeWithTag("quickMatch").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mina", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasTestTag("adBanner")).fetchSemanticsNodes().let { assertTrue("no banner offline", it.isEmpty()) }

        // 5. A complete vs-computer match: secret, pitches until somebody solves, result screen.
        compose.onNodeWithText("vs Computer").performClick()
        compose.onNodeWithTag("cpuStart").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pick your secret number").assertIsDisplayed()
        type("012")
        compose.onNodeWithText("Lock in").performClick()
        val guesses = buildList {
            for (a in '0'..'9') for (b in '0'..'9') for (d in '0'..'9') if (a != b && b != d && a != d) add("$a$b$d")
        }
        var finished = false
        for (g in guesses.shuffled(kotlin.random.Random(3))) {
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasTestTag("resultPrimary")).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodes(hasTestTag("key_0")).fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodes(hasTestTag("resultPrimary")).fetchSemanticsNodes().isNotEmpty()) {
                finished = true
                break
            }
            // Keys of used digits are disabled and the entry clears after each pitch; skip a guess that cannot be typed.
            type(g)
            compose.onNodeWithText("Pitch").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasTestTag("resultPrimary")).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Pitch").fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodes(hasTestTag("resultPrimary")).fetchSemanticsNodes().isNotEmpty()) {
                finished = true
                break
            }
        }
        assertTrue("a vs-computer match must finish (the HARD computer solves 3 digits)", finished)
        compose.onNodeWithText("Play again").assertExists()
        // Never an interstitial for the first matches, and no online session was opened at any point.
        assertEquals(0, c.ads.adShown)
        assertEquals(0, c.client.users)
        assertTrue(c.client.sent.isEmpty())

        // 6. Result → Home works, still offline.
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("resultPrimary").assertExists(); true }.getOrDefault(false) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Home").performClick()
        waitForTag("quickMatch")
        assertFalse(c.accountStore.value.value.token != null)
        assertTrue(c.accountRepositoryState() is AccountState.Unregistered)
    }

    private fun QaContainer.accountRepositoryState() = accountRepository.state.value
}
