package app.numbaseball.qa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.data.account.ApiResult
import app.numbaseball.data.account.AccountState
import app.numbaseball.data.age.AgeBand
import app.numbaseball.testing.FakeAccountApi
import app.numbaseball.testing.FakeAccountStore
import app.numbaseball.testing.FakeAgeStore
import app.numbaseball.testing.account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AC-TERMS: the terms gate before the first online match / chat; cannot be skipped; a version change re-prompts. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class TermsGateQaTest {
    @get:Rule val compose = createComposeRule()
    private val ui = QaDriver(compose)

    private fun api(current: String = "2026-11-v3", accepted: String?) = FakeAccountApi().apply {
        serverInfo = serverInfo.copy(termsVersion = current)
        meResult = ApiResult.Success(account().copy(termsVersion = accepted))
    }

    private fun player(api: FakeAccountApi, band: AgeBand = AgeBand.ADULT) =
        QaContainer(ageStore = FakeAgeStore(band), accountStore = FakeAccountStore.registered(), accountApi = api)

    private fun openQuickMatch() {
        ui.waitTag("quickMatch")
        compose.onNodeWithTag("quickMatch").performClick()
    }

    @Test fun neverAcceptedShowsTheFirstTimeGateAndNothingOnlineHappensBehindIt() {
        val c = player(api(accepted = null))
        compose.setQaApp(c)
        openQuickMatch()
        ui.waitText("Check the terms before playing online")
        compose.onNodeWithText("Terms version 2026-11-v3").assertIsDisplayed()
        assertEquals("no socket session while the gate is up", 0, c.client.users)
        assertTrue("no queue_join / room message", c.client.sent.isEmpty())
        assertFalse("the search screen is not composed behind the gate", ui.shows("Looking for an opponent"))
    }

    @Test fun theGateCannotBeSkippedOnlyClosedAndClosingStaysOffline() {
        val c = player(api(accepted = null))
        compose.setQaApp(c)
        openQuickMatch()
        ui.waitText("Check the terms before playing online")
        // No skip / later / continue-without button: the only exits are Agree and Close (back to Home).
        assertFalse(ui.shows("Skip") || ui.shows("Later") || ui.shows("Continue without agreeing"))
        compose.onNodeWithContentDescription("Close").performClick()
        ui.waitTag("quickMatch")
        assertTrue(c.client.sent.isEmpty())
        assertEquals(0, c.client.users)
        // Tapping again shows the gate again (acceptance is not implied by having seen it).
        openQuickMatch()
        ui.waitText("Check the terms before playing online")
    }

    @Test fun agreeingSendsTheCurrentVersionWithTheAgeConfirmationThenOnlinePlayStarts() {
        val a = api(accepted = null)
        val c = player(a)
        compose.setQaApp(c)
        openQuickMatch()
        ui.waitText("Check the terms before playing online")
        compose.onNodeWithTag("termsAgree").performClick()
        ui.waitText("Looking for an opponent")
        val request = a.acceptedTerms.single()
        assertEquals("2026-11-v3", request.version)
        assertTrue("age confirmation comes from the stored age band", request.ageConfirmed)
        assertTrue("the queue is joined only after acceptance", c.client.sent.any { it is QueueJoin })
    }

    @Test fun aNewTermsVersionReprompts_v2AcceptedThenV3() {
        val a = api(current = "2026-11-v3", accepted = "2026-11-v2")
        val c = player(a)
        compose.setQaApp(c)
        openQuickMatch()
        // "Changed" wording, not the first-time wording.
        ui.waitText("The terms have changed")
        assertFalse(ui.shows("Check the terms before playing online"))
        compose.onNodeWithText("Terms version 2026-11-v3").assertIsDisplayed()
        assertTrue(c.client.sent.isEmpty())
        compose.onNodeWithTag("termsAgree").performClick()
        ui.waitText("Looking for an opponent")
        assertEquals("2026-11-v3", a.acceptedTerms.single().version)
    }

    @Test fun anAcceptedCurrentVersionGoesStraightThroughAndAVersionBumpAsksAgain() {
        val a = api(current = "2026-11-v3", accepted = "2026-11-v3")
        val c = player(a)
        compose.setQaApp(c)
        openQuickMatch()
        ui.waitText("Looking for an opponent")
        assertTrue(a.acceptedTerms.isEmpty())
        // Back out of the search, the server now publishes v4 (the account still has v3): the next online entry asks again.
        compose.onNodeWithText("Cancel search").performClick()
        ui.waitTag("quickMatch")
        a.serverInfo = a.serverInfo.copy(termsVersion = "2026-11-v4")
        openQuickMatch()
        ui.waitText("The terms have changed")
        compose.onNodeWithText("Terms version 2026-11-v4").assertIsDisplayed()
    }

    @Test fun joinAndCreateRoomAreGatedToo() {
        val c = player(api(accepted = null))
        compose.setQaApp(c)
        ui.waitTag("quickMatch")
        compose.onNodeWithText("Join with code").performClick()
        ui.waitText("Check the terms before playing online")
        compose.onNodeWithContentDescription("Close").performClick()
        ui.waitTag("quickMatch")
        compose.onNodeWithText("Create room").performClick()
        compose.onNodeWithTag("roomConfirm").performClick()
        ui.waitText("Check the terms before playing online")
        assertTrue("no room_create is sent before the terms are accepted", c.client.sent.isEmpty())
    }

    @Test fun vsComputerNeverNeedsTheTerms() {
        val c = player(api(accepted = null))
        compose.setQaApp(c)
        ui.startComputerMatch()
        assertFalse(ui.shows("Check the terms before playing online"))
        assertTrue(c.client.sent.isEmpty())
    }

    @Test fun anUnderMinimumAccountCanNeverAgree() {
        val a = api(accepted = null)
        val c = player(a, band = AgeBand.UNDER_MINIMUM)
        // The app stops at the not-available screen; also the view model itself refuses to send the acceptance.
        val vm = app.numbaseball.ui.terms.TermsViewModel(c.accountRepository, c.ageRepository)
        vm.onAgree()
        assertTrue("no PUT /me/terms for an under-age player", a.acceptedTerms.isEmpty())
    }

    @Test fun serverRejectingAnOutdatedVersionKeepsTheGateAndRefreshesTheVersion() {
        val a = api(current = "2026-11-v3", accepted = null)
        a.acceptTermsResult = ApiResult.Failure(409, "terms_outdated")
        val c = player(a)
        compose.setQaApp(c)
        openQuickMatch()
        ui.waitText("Check the terms before playing online")
        a.serverInfo = a.serverInfo.copy(termsVersion = "2026-11-v4")
        compose.onNodeWithTag("termsAgree").performClick()
        ui.waitText("Terms version 2026-11-v4")
        assertFalse("the player is not let in on a rejected acceptance", ui.shows("Looking for an opponent"))
        assertTrue(c.client.sent.isEmpty())
    }

    /**
     * DEF-053: first launch offline (nickname kept, no account yet), the server answers 5xx when the connection comes back;
     * `HomeViewModel` retries registration only when connectivity flips, so Quick match -> Agree fails for good.
     */
    @Test fun def053_anOfflineRegisteredPlayerWhoseFirstRegistrationFailedCanNeverAcceptTheTerms() {
        val a = FakeAccountApi().apply {
            createResult = ApiResult.Failure(503, "unavailable")
            serverInfo = serverInfo.copy(termsVersion = "2026-11-v3")
        }
        val c = QaContainer(
            ageStore = FakeAgeStore(AgeBand.ADULT),
            accountStore = FakeAccountStore(app.numbaseball.data.account.StoredAccount(null, null, "Mina", null)),
            accountApi = a,
        )
        compose.setQaApp(c)
        ui.waitTag("quickMatch")
        assertTrue(c.accountRepository.state.value is AccountState.Unregistered)
        assertEquals("Home tried once and got a 503", 1, a.createdNicknames.size)
        // The server is fine again; the player taps Quick match -> terms gate -> Agree.
        a.createResult = ApiResult.Success(app.numbaseball.core.protocol.AuthResponse("token-1", account()))
        openQuickMatch()
        ui.waitText("Check the terms before playing online")
        compose.onNodeWithTag("termsAgree").performClick()
        compose.waitForIdle()
        knownDefect("DEF-053") {
            // Expected: the gate registers the account first (ensureRegistered) and the player gets in.
            assertTrue("registration was retried before the online feature", a.createdNicknames.size >= 2)
            assertTrue(ui.shows("Looking for an opponent"))
        }
        assertEquals("characterisation: no second registration attempt happened", 1, a.createdNicknames.size)
    }
}
