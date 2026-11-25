package app.numbaseball.qa

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.billing.AdEntitlement
import app.numbaseball.billing.BillingStatus
import app.numbaseball.billing.DataStoreEntitlementStore
import app.numbaseball.billing.DefaultBillingRepository
import app.numbaseball.billing.PlayPurchaseState
import app.numbaseball.billing.PurchaseUpdate
import app.numbaseball.billing.RemoveAdsState
import app.numbaseball.testing.FakeBillingGateway
import app.numbaseball.testing.FakeEntitlementStore
import app.numbaseball.testing.removeAdsPurchase
import app.numbaseball.ui.results.REMOVE_ADS_LINK_TAG
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Billing (POLICY_CHECKLIST 12) end to end: the real `DefaultBillingRepository` on the fake Play gateway, the real
 * navigation (Settings rows, result link, toasts, banners) and the ads layer. Purchase, pending, refund, restore and
 * the cached entitlement on an offline start; the purchase token is never logged or stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class BillingQaTest {
    @get:Rule val compose = createComposeRule()
    private val ui = QaDriver(compose)

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val gateway = FakeBillingGateway().apply { productResult = BillingStatus.OK to "$4.99" }
    private val store = FakeEntitlementStore()
    private val repo = DefaultBillingRepository(gateway, store, testScope.backgroundScope)

    @After fun stop() = testScope.cancel()

    private fun advance(ms: Long) {
        testScope.testScheduler.advanceTimeBy(ms)
        testScope.testScheduler.runCurrent()
        compose.waitForIdle()
    }

    private fun launchApp(): QaContainer {
        val c = returningPlayer(billing = repo)
        repo.start()
        advance(100)
        compose.setQaApp(c)
        ui.waitTag("quickMatch")
        return c
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("Settings").performClick()
        ui.waitText("Settings")
    }

    private fun backHome() {
        compose.onNodeWithContentDescription("Back").performClick()
        ui.waitTag("quickMatch")
    }

    private fun deliver(vararg update: PurchaseUpdate) {
        update.forEach { gateway.updatesFlow.tryEmit(it) }
        advance(50)
    }

    // ---- purchase ----

    @Test fun purchaseFromSettingsRemovesBannerAtOnceAcknowledgesAndCaches() {
        val c = launchApp()
        assertTrue("before the purchase: banner", ui.hasBanner())
        assertEquals(listOf(app.numbaseball.data.age.AdAgeTreatment.UNSPECIFIED), c.ads.consentCalls)
        openSettings()
        compose.onNodeWithTag("removeAdsRow").performScrollTo()
        compose.onNodeWithText("$4.99", substring = true).assertExists() // Play's price, never hard-coded
        compose.onNodeWithTag("removeAdsRow").performClick()
        assertEquals("Play's sheet was launched", 1, gateway.launches)
        deliver(PurchaseUpdate(BillingStatus.OK, listOf(removeAdsPurchase())))
        ui.waitText("Ads removed")
        assertEquals("acknowledged within the session", listOf("secret-token-123"), gateway.acknowledged)
        assertTrue("entitlement cached for an offline start", store.owned)
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        compose.onNodeWithText("Purchased · no ads").performScrollTo()
        assertFalse(ui.exists("restorePurchase"))
        backHome()
        assertFalse("no banner after the purchase", ui.hasBanner())
    }

    @Test fun userCancelledAndFailedPurchasesChangeNothing() {
        launchApp()
        openSettings()
        compose.onNodeWithTag("removeAdsRow").performScrollTo()
        gateway.launchResult = BillingStatus.USER_CANCELED
        compose.onNodeWithTag("removeAdsRow").performClick()
        advance(50)
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
        assertFalse(ui.shows("The purchase didn’t go through. Please try again"))
        gateway.launchResult = BillingStatus.ERROR
        compose.onNodeWithTag("removeAdsRow").performClick()
        ui.waitText("The purchase didn’t go through. Please try again")
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
        assertFalse(store.owned)
    }

    // ---- pending ----

    @Test fun pendingKeepsAdsShowsTheNoteAndClearingLaterRemovesThem() {
        launchApp()
        openSettings()
        compose.onNodeWithTag("removeAdsRow").performScrollTo().performClick()
        deliver(PurchaseUpdate(BillingStatus.OK, listOf(removeAdsPurchase(PlayPurchaseState.PENDING))))
        ui.waitText("Ads go away once the payment clears")
        compose.onNodeWithText("Payment pending · ads go away once it clears").performScrollTo()
        assertEquals("pending never grants", AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
        assertTrue("restore stays available while pending", ui.exists("restorePurchase"))
        assertTrue("the purchase row cannot start a second purchase", gateway.acknowledged.isEmpty())
        backHome()
        assertTrue("ads keep running while pending", ui.hasBanner())
        // The payment clears while the app is open: the next resume query grants it.
        gateway.purchasesResult = BillingStatus.OK to listOf(removeAdsPurchase())
        repo.refresh()
        advance(50)
        ui.waitText("Ads removed")
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        assertFalse(ui.hasBanner())
    }

    // ---- refund ----

    @Test fun refundBringsAdsBackAndConsentStartsThen() {
        gateway.purchasesResult = BillingStatus.OK to listOf(removeAdsPurchase(acknowledged = true))
        val c = launchApp()
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        assertFalse(ui.hasBanner())
        assertTrue("owner: no consent, no SDK", c.ads.consentCalls.isEmpty() && !c.ads.sdkInitialised)
        gateway.purchasesResult = BillingStatus.OK to emptyList() // refunded in the Play Console
        repo.refresh() // the next resume
        advance(50)
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
        assertFalse("the cache follows Play", store.owned)
        assertTrue("banner returns", ui.hasBanner())
        assertEquals("consent starts only now", 1, c.ads.consentCalls.size)
        // Settings offers the purchase again with Play's price.
        openSettings()
        compose.onNodeWithTag("removeAdsRow").performScrollTo()
        compose.onNodeWithText("$4.99", substring = true).assertExists()
    }

    // ---- restore ----

    @Test fun restoreFindsThePurchaseOnANewDevice() {
        launchApp()
        openSettings()
        gateway.purchasesResult = BillingStatus.OK to listOf(removeAdsPurchase(acknowledged = true))
        compose.onNodeWithTag("restorePurchase").performScrollTo().performClick()
        advance(50)
        ui.waitText("Remove ads restored")
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        assertTrue(store.owned)
    }

    @Test fun restoreWithNothingToRestoreAndWhenPlayIsUnreachable() {
        launchApp()
        openSettings()
        compose.onNodeWithTag("restorePurchase").performScrollTo().performClick()
        advance(50)
        ui.waitText("No purchase to restore on this Google account")
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
        gateway.purchasesResult = BillingStatus.RETRYABLE to emptyList()
        compose.onNodeWithTag("restorePurchase").performScrollTo().performClick()
        advance(50)
        ui.waitText("Can’t connect to Google Play")
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
    }

    // ---- offline-cached entitlement ----

    @Test fun cachedOwnerStaysAdFreeOnAnOfflineStart() {
        store.owned = true
        gateway.connectResults = mutableListOf(BillingStatus.RETRYABLE) // Play Store unreachable the whole time
        val c = launchApp()
        advance(30_000)
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        assertFalse("no banner while Play cannot be reached", ui.hasBanner())
        assertTrue("no consent form, no SDK", c.ads.consentCalls.isEmpty() && !c.ads.sdkInitialised)
        assertTrue("the cache is kept: Play could not answer", store.owned)
        openSettings()
        compose.onNodeWithText("Purchased · no ads").performScrollTo()
    }

    @Test fun nonOwnerWithPlayUnreachableGetsAdsAfterTheStartTimeoutAndAnErrorRow() {
        gateway.connectResults = mutableListOf(BillingStatus.RETRYABLE)
        val c = returningPlayer(billing = repo)
        repo.start()
        compose.setQaApp(c)
        advance(1_000)
        assertEquals("unknown at first: nothing ad-related runs", AdEntitlement.UNKNOWN, repo.entitlement.value)
        assertTrue(c.ads.consentCalls.isEmpty())
        advance(6_000)
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
        compose.waitForIdle()
        assertEquals(1, c.ads.consentCalls.size)
        assertEquals("still retrying 1+2+4+8 s: the row shows loading", RemoveAdsState.Loading, repo.state.value.removeAds)
        advance(10_000)
        assertEquals(RemoveAdsState.Unavailable, repo.state.value.removeAds)
    }

    @Test fun anAcknowledgementThatFailedIsRetriedOnTheNextResume() {
        gateway.purchasesResult = BillingStatus.OK to listOf(removeAdsPurchase())
        gateway.acknowledgeResult = BillingStatus.RETRYABLE
        launchApp()
        assertEquals("granted although the acknowledgement failed", AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        assertTrue(gateway.acknowledged.isEmpty())
        gateway.acknowledgeResult = BillingStatus.OK
        repo.refresh()
        advance(50)
        assertEquals(listOf("secret-token-123"), gateway.acknowledged)
    }

    @Test fun aPurchaseOfAnotherProductNeverGrants() {
        gateway.purchasesResult = BillingStatus.OK to listOf(app.numbaseball.billing.PlayPurchase(listOf("something_else"), PlayPurchaseState.PURCHASED, true, "t"))
        launchApp()
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
        assertTrue(ui.hasBanner())
    }

    // ---- result link integration ----

    @Test fun resultLinkOnlyAfterAVsComputerMatchWhilePlayOffersTheProduct() {
        val c = launchApp()
        ui.startComputerMatch()
        ui.forfeit()
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertTrue("quiet link on the result", ui.exists(REMOVE_ADS_LINK_TAG))
        compose.onNodeWithTag(REMOVE_ADS_LINK_TAG).performClick()
        assertEquals("the link opens Play's sheet directly", 1, gateway.launches)
        deliver(PurchaseUpdate(BillingStatus.OK, listOf(removeAdsPurchase())))
        ui.waitText("Ads removed")
        compose.waitUntil(5_000) { !ui.exists(REMOVE_ADS_LINK_TAG) }
        assertFalse("link and banner are gone once owned", ui.hasBanner())
    }

    @Test fun resultLinkIsHiddenWhilePendingAndWhileBillingIsUnavailable() {
        gateway.connectResults = mutableListOf(BillingStatus.UNAVAILABLE)
        val c = launchApp()
        advance(6_000)
        ui.startComputerMatch()
        ui.forfeit()
        ui.awaitResultOrAd(c.ads)
        ui.awaitResult()
        assertFalse("no link when Play billing is unavailable", ui.exists(REMOVE_ADS_LINK_TAG))
    }

    // ---- the token ----

    @Test fun thePurchaseTokenIsNeverLoggedPrintedOrStored() {
        val token = "TOKEN-must-never-leak-7c1e9"
        val logged = ByteArrayOutputStream()
        val oldOut = System.out
        val oldErr = System.err
        System.setOut(PrintStream(logged, true))
        System.setErr(PrintStream(logged, true))
        ShadowLog.clear()
        try {
            val toStrings = mutableListOf<String>()
            // Purchase, pending, refund, restore, a failed acknowledgement and an error update, all with the token.
            val p = removeAdsPurchase(token = token)
            toStrings += p.toString() + removeAdsPurchase(PlayPurchaseState.PENDING, token = token).toString()
            gateway.acknowledgeResult = BillingStatus.RETRYABLE
            launchApp()
            deliver(PurchaseUpdate(BillingStatus.OK, listOf(removeAdsPurchase(PlayPurchaseState.PENDING, token = token))))
            deliver(PurchaseUpdate(BillingStatus.OK, listOf(p)))
            gateway.purchasesResult = BillingStatus.OK to emptyList()
            repo.refresh(); advance(50)
            gateway.purchasesResult = BillingStatus.OK to listOf(p)
            repo.restore(); advance(50)
            deliver(PurchaseUpdate(BillingStatus.ERROR, emptyList()))
            val logs = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable?.stackTraceToString().orEmpty()}" }
            assertFalse("logcat contains the token", logs.contains(token))
            assertFalse("stdout/stderr contain the token", logged.toString().contains(token))
            assertFalse("toString leaks the token", toStrings.joinToString().contains(token))
            assertTrue(gateway.acknowledged.isEmpty() || gateway.acknowledged.all { it == token })
        } finally {
            System.setOut(oldOut)
            System.setErr(oldErr)
        }
        // Persistence: the cache is one boolean.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking { DataStoreEntitlementStore(context).saveOwned(true) }
        val raw = context.preferencesDataStoreFile("billing").readBytes().toString(Charsets.ISO_8859_1)
        assertTrue(raw.contains("remove_ads_owned"))
        assertFalse(raw.contains(token))
        // And the billing cache is not part of Auto Backup (Play restores it).
        for (name in listOf("backup_rules", "data_extraction_rules")) {
            assertFalse(name, java.io.File("src/main/res/xml/$name.xml").readText().contains("billing"))
        }
    }

    // ---- misc wiring ----

    @Test fun restoreAndPurchaseRowsExistInSettingsEvenWhileLoading() {
        gateway.productResult = BillingStatus.RETRYABLE to null
        launchApp()
        openSettings()
        compose.onNodeWithTag("restorePurchase").performScrollTo()
        assertTrue(compose.onAllNodes(hasTestTag("restorePurchase")).fetchSemanticsNodes().isNotEmpty())
    }
}
