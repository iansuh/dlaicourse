package app.numbaseball.qa

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.numbaseball.ads.AD_BANNER_TAG
import app.numbaseball.data.age.AgeBand
import app.numbaseball.testing.FakeAccountApi
import app.numbaseball.testing.FakeAccountStore
import app.numbaseball.testing.FakeAgeStore
import app.numbaseball.testing.account

/** A returning, registered, adult player with the current terms accepted (the common QA starting point). */
fun returningPlayer(
    band: AgeBand = AgeBand.ADULT,
    termsAccepted: Boolean = true,
    build: QaContainer.() -> Unit = {},
    ads: RecordingAds = RecordingAds(),
    billing: app.numbaseball.billing.BillingRepository = app.numbaseball.testing.FakeBillingRepository(),
    settings: app.numbaseball.data.settings.AppSettings = app.numbaseball.data.settings.AppSettings(),
): QaContainer {
    val api = FakeAccountApi().apply {
        meResult = app.numbaseball.data.account.ApiResult.Success(account().copy(termsVersion = if (termsAccepted) serverInfo.termsVersion else null))
    }
    return QaContainer(
        ageStore = FakeAgeStore(band),
        accountStore = FakeAccountStore.registered(),
        accountApi = api,
        ads = ads,
        billing = billing,
        settingsRepository = app.numbaseball.testing.FakeSettingsRepository(settings),
    ).also(build)
}

/** UI helpers over the real NbApp. */
class QaDriver(val compose: ComposeContentTestRule) {
    fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    fun shows(text: String) = compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
    fun hasBanner() = exists(AD_BANNER_TAG)

    fun waitTag(tag: String, timeout: Long = 10_000) = compose.waitUntil(timeout) { exists(tag) }
    fun waitText(text: String, timeout: Long = 10_000) = compose.waitUntil(timeout) { shows(text) }

    fun type(digits: String) = digits.forEach { compose.onNodeWithTag("key_$it").performClick() }

    /** Home → vs Computer → Start → secret 012 → Lock in. Leaves the match on its guessing phase. */
    fun startComputerMatch() {
        waitTag("quickMatch")
        compose.onNodeWithText("vs Computer").performClick()
        compose.onNodeWithTag("cpuStart").performClick()
        waitText("Pick your secret number")
        type("012")
        compose.onNodeWithText("Lock in").performClick()
        waitTag("key_0")
        compose.waitForIdle()
    }

    /** The match menu → Give up → confirm: the quickest way to a finished vs-computer match. */
    fun forfeit() {
        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Give up").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Give up").performClick()
    }

    /** Waits until either the result screen or an interstitial is up. */
    fun awaitResultOrAd(ads: RecordingAds, maxMillis: Long = 15_000) {
        check(tickUntil(maxMillis) { exists("resultPrimary") || ads.adShown > 0 }) { "neither results nor an ad after $maxMillis ms" }
    }

    fun awaitResult(maxMillis: Long = 15_000) {
        check(tickUntil(maxMillis) { exists("resultPrimary") }) { "no result screen after $maxMillis ms" }
    }

    /** Result → Home (the result's buttons accept input 1 s after the result appears). */
    fun resultToHome() {
        tick(1_200)
        compose.waitForIdle()
        compose.onNodeWithTag("resultSecondary").performClick()
        waitTag("quickMatch")
    }

    /**
     * Advances the compose clock and the device clock together. The interstitial gate measures its 1 s hold with
     * `SystemClock.elapsedRealtime()`, which Robolectric's paused looper does not move on its own.
     */
    fun tick(millis: Long) {
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(millis))
        compose.mainClock.advanceTimeBy(millis)
    }

    /** Ticks in 100 ms steps until [done] (at most [maxMillis] of device time). */
    fun tickUntil(maxMillis: Long = 15_000, done: () -> Boolean): Boolean {
        var spent = 0L
        while (spent <= maxMillis) {
            if (done()) return true
            tick(100)
            spent += 100
        }
        return done()
    }

    fun typeNickname(name: String) {
        compose.onNodeWithTag("nickname").performTextInput(name)
    }
}
