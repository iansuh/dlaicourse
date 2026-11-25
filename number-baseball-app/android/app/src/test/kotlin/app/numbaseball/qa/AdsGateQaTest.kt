package app.numbaseball.qa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.ads.FinishedMatchKind
import app.numbaseball.ads.LocalAdsController
import app.numbaseball.ads.LocalTouchTracker
import app.numbaseball.ads.TouchTracker
import app.numbaseball.ads.rememberInterstitialGate
import app.numbaseball.billing.AdEntitlement
import app.numbaseball.billing.LocalBilling
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.testing.FakeBillingRepository
import app.numbaseball.ui.theme.NumBaseballTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The interstitial gate (`InterstitialGate`) hosted on a real Compose window with real insets, lifecycle, touch tracker,
 * billing entitlement and the real cap policy: every "skip" rule of POLICY_CHECKLIST 11.1 at integration level.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class AdsGateQaTest {
    @get:Rule val compose = createComposeRule()
    private val ui = QaDriver(compose)

    private val ads = RecordingAds()
    private val billing = FakeBillingRepository()
    private val touches = TouchTracker()
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).also { it.currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
    private val thens = mutableListOf<Boolean>()
    private var run by mutableStateOf(0)
    private var onScreen by mutableStateOf(true)
    private var chatOpen = false
    private var nextStarting = false
    private var imeDp by mutableStateOf(0)

    @Composable
    private fun Host() {
        NumBaseballTheme {
            CompositionLocalProvider(
                LocalAdsController provides ads,
                LocalBilling provides billing,
                LocalTouchTracker provides touches,
                LocalLifecycleOwner provides owner,
            ) {
                val view = LocalView.current
                LaunchedEffect(imeDp) {
                    val px = (imeDp * view.resources.displayMetrics.density).toInt()
                    val insets = WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
                        .setVisible(WindowInsetsCompat.Type.ime(), px > 0)
                        .build()
                    ViewCompat.dispatchApplyWindowInsets(view, insets)
                }
                Box(Modifier.fillMaxSize()) {
                    if (onScreen) {
                        val gate = rememberInterstitialGate()
                        LaunchedEffect(run) {
                            if (run > 0) {
                                gate.run(FinishedMatchKind.COMPUTER, EndReason.SOLVED, chatOpen = { chatOpen }, nextMatchStarting = { nextStarting }) { thens += it }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun begin(matches: Int = 3, minutesAgo: Long = 5) {
        ads.interstitial.primeCap(matches - 1, minutesAgo) // the run itself counts as the last one
        compose.setContent { Host() }
        compose.waitForIdle()
    }

    private fun matchEnds() {
        run++
        compose.waitForIdle()
    }

    @Test fun dueAdShowsAfterTheOneSecondHoldAndNeverBefore() {
        begin()
        matchEnds()
        ui.tick(900)
        assertEquals("not within the 1 s hold", 0, ads.adShown)
        assertTrue(ui.tickUntil(3_000) { ads.adShown == 1 })
        ads.closeAd()
        compose.waitForIdle()
        assertEquals("then() runs after the ad closed, with adShown = true", listOf(true), thens)
        assertEquals(0, ads.interstitial.finishedSinceLastAd)
    }

    @Test fun keyboardOpenSkipsTheAdAndKeepsTheCapDue() {
        begin()
        imeDp = 230
        compose.waitForIdle()
        matchEnds()
        ui.tick(2_000)
        assertEquals(0, ads.adShown)
        assertEquals("results are not held back", listOf(false), thens)
        assertEquals("the skipped match leaves the cap due", 3, ads.interstitial.finishedSinceLastAd)
    }

    @Test fun chatOpenSkipsTheAd() {
        begin()
        chatOpen = true
        matchEnds()
        ui.tick(2_000)
        assertEquals(0, ads.adShown)
        assertEquals(listOf(false), thens)
    }

    @Test fun nextMatchStartingSkipsTheAd() {
        begin()
        nextStarting = true
        matchEnds()
        ui.tick(2_000)
        assertEquals(0, ads.adShown)
        assertEquals(listOf(false), thens)
    }

    @Test fun appInTheBackgroundSkipsTheAd() {
        begin()
        owner.registry.currentState = Lifecycle.State.STARTED
        matchEnds()
        ui.tick(2_000)
        assertEquals(0, ads.adShown)
        assertEquals(listOf(false), thens)
    }

    @Test fun aFingerOnTheScreenHoldsTheAdBackAndTheSettleTimeApplies() {
        begin()
        touches.onPointers(true)
        matchEnds()
        ui.tick(3_000)
        assertEquals("finger down: still holding", 0, ads.adShown)
        touches.onPointers(false)
        ui.tick(200)
        assertEquals("300 ms settle after the last touch up", 0, ads.adShown)
        assertTrue(ui.tickUntil(2_000) { ads.adShown == 1 })
    }

    @Test fun aFingerHeldForFiveSecondsGivesUpInsteadOfShowingLate() {
        begin()
        touches.onPointers(true)
        matchEnds()
        assertTrue(ui.tickUntil(7_000) { thens.isNotEmpty() })
        assertEquals(0, ads.adShown)
        assertEquals(listOf(false), thens)
    }

    @Test fun leavingTheScreenDuringTheHoldCancelsTheAdForGood() {
        begin()
        matchEnds()
        ui.tick(500)
        onScreen = false
        compose.waitForIdle()
        ui.tick(5_000)
        assertEquals("the ad never shows after the player left", 0, ads.adShown)
        assertTrue("then() is not called either: no navigation from a screen that is gone", thens.isEmpty())
    }

    @Test fun buyingRemoveAdsDuringTheHoldCancelsTheAd() {
        begin()
        matchEnds()
        ui.tick(500)
        billing.entitlement.value = AdEntitlement.ADS_REMOVED
        ui.tick(2_000)
        assertEquals(0, ads.adShown)
        assertEquals(listOf(false), thens)
    }

    @Test fun unknownEntitlementIsTreatedAsOwned() {
        billing.entitlement.value = AdEntitlement.UNKNOWN
        begin()
        matchEnds()
        ui.tick(2_000)
        assertEquals(0, ads.adShown)
        assertEquals(listOf(false), thens)
    }

    @Test fun adNotLoadedYetContinuesAtOnceWithoutWaiting() {
        ads.interstitial.loaded = false
        begin()
        matchEnds()
        ui.tick(100)
        assertEquals(listOf(false), thens)
        assertEquals(0, ads.adShown)
    }

    @Test fun threeMatchesButUnderFourMinutesDoesNotShow() {
        begin(matches = 3, minutesAgo = 3)
        matchEnds()
        ui.tick(3_000)
        assertEquals(0, ads.adShown)
        assertEquals(listOf(false), thens)
    }

    @Test fun twoMatchesAfterAFiveMinutePauseDoesNotShow() {
        begin(matches = 2, minutesAgo = 5)
        matchEnds()
        ui.tick(3_000)
        assertEquals(0, ads.adShown)
        assertEquals(listOf(false), thens)
    }
}

/** 360 x 320 split-screen / pop-up window: no interstitial (POLICY_CHECKLIST 11.1, windows lower than 480 dp). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h320dp-xhdpi")
class AdsSmallWindowQaTest {
    @get:Rule val compose = createComposeRule()
    private val ui = QaDriver(compose)

    @Test fun smallWindowNeverShowsBannerOrInterstitial() {
        val c = returningPlayer()
        c.ads.interstitial.primeCap(matches = 5, minutesAgo = 30)
        compose.setQaApp(c)
        ui.waitTag("quickMatch")
        assertTrue("no banner in a 320 dp high window", !ui.hasBanner())
        ui.startComputerMatch()
        ui.forfeit()
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertEquals(0, c.ads.adShown)
        assertTrue(!ui.hasBanner())
    }
}
