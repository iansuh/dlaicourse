package app.numbaseball.qa

import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.billing.AdEntitlement
import app.numbaseball.data.age.AdAgeTreatment
import app.numbaseball.data.age.AgeBand
import app.numbaseball.testing.FakeBillingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * AC-ADS at integration level: the real `NbApp` navigation graph, ViewModels, `InterstitialGate`, banner gate and billing
 * entitlement, with a recording ads controller (real `InterstitialPolicy` counting) instead of AdMob.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class AdsIntegrationQaTest {
    @get:Rule val compose = createComposeRule()
    private val ui = QaDriver(compose)

    private fun start(c: QaContainer, banner: Boolean = true) = compose.setQaApp(c, banner)

    // ---- banners ----

    @Test fun bannerOnlyOnCalmScreensAndNeverDuringAMatch() {
        val c = returningPlayer()
        start(c)
        ui.waitTag("quickMatch")
        // Home (online): banner.
        assertTrue("home shows the banner", ui.hasBanner())

        // Settings: no banner.
        compose.onNodeWithContentDescription("Settings").performClick()
        ui.waitText("Settings")
        assertFalse("settings has no banner", ui.hasBanner())
        compose.onNodeWithContentDescription("Back").performClick()
        ui.waitTag("quickMatch")

        // Profile: banner (calm screen, online).
        compose.onNodeWithContentDescription("Mina", substring = true).performClick()
        compose.waitForIdle()
        assertTrue("profile shows the banner", ui.hasBanner())
        compose.onNodeWithContentDescription("Back").performClick()
        ui.waitTag("quickMatch")

        // Tutorial replay from Home help: no banner anywhere inside it.
        compose.onNodeWithContentDescription("How to play").performClick()
        ui.waitText("Next")
        assertFalse("tutorial has no banner", ui.hasBanner())
        compose.onNodeWithText("Skip").performClick()
        ui.waitTag("quickMatch")

        // vs Computer: the setup sheet, the secret entry and every guessing frame are banner-free.
        compose.onNodeWithText("vs Computer").performClick()
        compose.onNodeWithTag("cpuStart").performClick()
        ui.waitText("Pick your secret number")
        assertFalse("no banner on the secret entry", ui.hasBanner())
        ui.type("012")
        compose.onNodeWithText("Lock in").performClick()
        ui.waitText("Pitch")
        assertFalse("no banner while guessing", ui.hasBanner())
        ui.type("345")
        compose.onNodeWithText("Pitch").performClick()
        compose.waitForIdle()
        assertFalse("no banner while the opponent pitches", ui.hasBanner())
        ui.waitTag("key_0")
        assertFalse("no banner in round 2", ui.hasBanner())
        ui.forfeit()
        ui.awaitResultOrAd(c.ads)
        // Result of a vs-computer match with no ad before it: banner on the calm result screen.
        assertEquals(0, c.ads.adShown)
        ui.awaitResult()
        assertTrue("results show the banner when no interstitial came before", ui.hasBanner())
    }

    @Test fun offlineHomeHasNoBanner() {
        val c = returningPlayer()
        c.net.flow.value = false
        start(c)
        ui.waitTag("quickMatch")
        assertFalse(ui.hasBanner())
    }

    // ---- interstitial through the real gate ----

    @Test fun interstitialPolicyThroughTheRealMatchFlow() {
        val c = returningPlayer()
        start(c)

        // Matches 1 and 2: counted, no ad, straight to the results (cap 1 per 3 matches; also under 4 minutes).
        repeat(2) { n ->
            ui.startComputerMatch()
            ui.forfeit()
            ui.awaitResultOrAd(c.ads)
            ui.awaitResult()
            assertEquals("match ${n + 1}: no ad", 0, c.ads.adShown)
            assertEquals(n + 1, c.ads.interstitial.finishedSinceLastAd)
            ui.resultToHome()
        }

        // Match 3 right away: three matches played but only seconds since the process start: still no ad.
        ui.startComputerMatch()
        ui.forfeit()
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertEquals("3 matches but < 4 minutes: no ad", 0, c.ads.adShown)
        assertEquals(3, c.ads.interstitial.finishedSinceLastAd)
        ui.resultToHome()

        // 5 minutes later the next finished match is due: the ad sits between the match end and the results.
        c.ads.interstitial.skewMillis += 5 * 60_000
        ui.startComputerMatch()
        val forfeitAt = SystemClock.elapsedRealtime()
        ui.forfeit()
        check(ui.tickUntil { c.ads.adShown > 0 }) { "no ad although the cap is due" }
        val shownAt = c.ads.interstitial.shown.single()
        assertTrue("held at least 1 s after the result before the ad (was ${shownAt - forfeitAt} ms)", shownAt - forfeitAt >= 1_000)
        assertFalse("the results are not on screen while the ad is open", ui.exists("resultPrimary"))
        c.ads.closeAd()
        ui.awaitResult()
        assertFalse("a result reached through an ad shows no banner", ui.hasBanner())
        assertEquals(0, c.ads.interstitial.finishedSinceLastAd)
        ui.resultToHome()

        // The cap restarted: the very next match shows nothing, even though plenty of time passed.
        c.ads.interstitial.skewMillis += 10 * 60_000
        ui.startComputerMatch()
        ui.forfeit()
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertEquals("after an ad the next match is not due", 1, c.ads.adShown)
        // The result buttons never start an ad.
        ui.resultToHome()
        assertEquals(1, c.ads.adShown)
    }

    @Test fun noInterstitialAtAppStartEvenWithADueCap() {
        val c = returningPlayer()
        c.ads.interstitial.primeCap(matches = 5, minutesAgo = 30)
        start(c)
        ui.waitTag("quickMatch")
        ui.tick(10_000)
        compose.waitForIdle()
        assertEquals(0, c.ads.adShown)
        // Opening settings and coming back (screens, not matches) shows nothing either.
        compose.onNodeWithContentDescription("Settings").performClick()
        ui.waitText("Settings")
        compose.onNodeWithContentDescription("Back").performClick()
        ui.waitTag("quickMatch")
        assertEquals(0, c.ads.adShown)
    }

    // ---- remove_ads owned / unknown ----

    @Test fun removeAdsOwnerSeesNoBannerNoInterstitialNoConsentNoSdk() {
        val billing = FakeBillingRepository(entitlement = AdEntitlement.ADS_REMOVED)
        val c = returningPlayer(billing = billing)
        c.ads.interstitial.primeCap(matches = 5, minutesAgo = 30)
        start(c)
        ui.waitTag("quickMatch")
        assertFalse("owner: no banner on home", ui.hasBanner())
        ui.startComputerMatch()
        ui.forfeit()
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertEquals("owner: no interstitial although the cap is due", 0, c.ads.adShown)
        assertFalse("owner: no banner on results", ui.hasBanner())
        assertTrue("owner: no UMP / consent form call", c.ads.consentCalls.isEmpty())
        assertFalse("owner: Mobile Ads is never initialised", c.ads.sdkInitialised)
        assertTrue("owner: no request configuration either", c.ads.treatmentUpdates.isEmpty() || !c.ads.sdkInitialised)
    }

    @Test fun nothingAdRelatedRunsWhileTheEntitlementIsUnknownThenConsentStartsOnce() {
        val billing = FakeBillingRepository(entitlement = AdEntitlement.UNKNOWN)
        val c = returningPlayer(billing = billing)
        start(c)
        ui.waitTag("quickMatch")
        assertTrue("unknown entitlement: no consent yet", c.ads.consentCalls.isEmpty())
        assertFalse("unknown entitlement: no banner", ui.hasBanner())
        billing.entitlement.value = AdEntitlement.ADS_ALLOWED
        compose.waitForIdle()
        assertEquals("consent starts once ads are allowed, with the age treatment", listOf(AdAgeTreatment.UNSPECIFIED), c.ads.consentCalls)
        assertTrue(ui.hasBanner())
        // A refund later flips the entitlement back and forth: the banner follows, consent is not repeated by the app.
        billing.entitlement.value = AdEntitlement.ADS_REMOVED
        compose.waitForIdle()
        assertFalse("purchase while the app is open removes the banner at once", ui.hasBanner())
        billing.entitlement.value = AdEntitlement.ADS_ALLOWED
        compose.waitForIdle()
        assertTrue("refund brings the banner back", ui.hasBanner())
    }

    // ---- age treatment reaches the ads layer, nothing before the age answer ----

    @Test fun ageBandDecidesTheTreatmentPassedToConsent() {
        for ((band, expected) in listOf(
            AgeBand.CHILD to AdAgeTreatment.CHILD,
            AgeBand.TEEN to AdAgeTreatment.TEEN,
            AgeBand.ADULT to AdAgeTreatment.UNSPECIFIED,
        )) {
            val c = returningPlayer(band = band)
            compose.setQaApp(c)
            ui.waitTag("quickMatch")
            assertEquals(band.name, listOf(expected), c.ads.consentCalls)
            assertTrue(band.name, expected in c.ads.treatmentUpdates)
            // Same compose rule instance per iteration is not possible; recreate via activity recreation helper.
            break
        }
    }

    // ---- DEF-062: no ad SDK auto-start ----

    /**
     * The Mobile Ads SDK starts only from `GoogleAdsController.startSdk` (after the age answer, consent and the
     * entitlement). Its content provider would initialise it at process start, so it must be removed from the merged
     * manifest, and app measurement must be delayed.
     */
    @Test fun def062_theMergedManifestHasNoMobileAdsInitProvider() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PROVIDERS or android.content.pm.PackageManager.GET_META_DATA)
        val providers = info.providers.orEmpty().map { it.name }
        assertFalse("MobileAdsInitProvider is merged in: $providers", providers.any { it.endsWith("MobileAdsInitProvider") })
        val merged = java.io.File("build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml")
        assertTrue("merged debug manifest not found at $merged", merged.exists())
        val xml = merged.readText()
        assertFalse("the merged app manifest still declares MobileAdsInitProvider", xml.contains("com.google.android.gms.ads.MobileAdsInitProvider"))
        assertTrue("app measurement init is delayed", Regex("""DELAY_APP_MEASUREMENT_INIT"\s+android:value="true"""").containsMatchIn(xml))
        // The only place that initialises the SDK.
        val callers = java.io.File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" && it.readText().contains("MobileAds.initialize") }.map { it.name }.toList()
        assertEquals(listOf("GoogleAdsController.kt"), callers)
    }
}
