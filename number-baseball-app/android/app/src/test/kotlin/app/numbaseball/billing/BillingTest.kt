package app.numbaseball.billing

import android.app.Activity
import app.numbaseball.testing.FakeBillingGateway
import app.numbaseball.testing.FakeEntitlementStore
import app.numbaseball.testing.removeAdsPurchase
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveAdsRulesTest {
    @Test fun onlyPurchasedGrants() {
        assertEquals(Ownership.OWNED, RemoveAdsRules.ownership(listOf(removeAdsPurchase())))
        assertEquals(Ownership.PENDING, RemoveAdsRules.ownership(listOf(removeAdsPurchase(PlayPurchaseState.PENDING))))
        assertEquals(Ownership.NONE, RemoveAdsRules.ownership(listOf(removeAdsPurchase(PlayPurchaseState.UNSPECIFIED))))
        // Refund / revocation: the purchase is simply gone.
        assertEquals(Ownership.NONE, RemoveAdsRules.ownership(emptyList()))
        assertEquals(Ownership.NONE, RemoveAdsRules.ownership(listOf(PlayPurchase(listOf("other"), PlayPurchaseState.PURCHASED, true, "t"))))
    }

    @Test fun acknowledgesOnlyUnacknowledgedPurchases() {
        val purchases = listOf(removeAdsPurchase(acknowledged = true, token = "a"), removeAdsPurchase(token = "b"), removeAdsPurchase(PlayPurchaseState.PENDING, token = "c"))
        assertEquals(listOf("b"), RemoveAdsRules.toAcknowledge(purchases).map { it.token })
    }

    @Test fun theTokenNeverAppearsInToString() {
        assertFalse(removeAdsPurchase(token = "secret-token-123").toString().contains("secret-token-123"))
    }

    /** POLICY_REVIEW Android item 13: the token is read only in the Play gateway and never logged or sent anywhere. */
    @Test fun thePurchaseTokenStaysInTheBillingPackage() {
        val main = File("src/main/kotlin")
        val sources = main.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        val readers = sources.filter { it.readText().contains("purchaseToken") }.map { it.name }
        assertEquals(listOf("PlayBillingGateway.kt"), readers)
        val billing = sources.filter { it.path.contains("/billing/") }
        billing.forEach { file ->
            val text = file.readText()
            assertFalse("${file.name} logs", Regex("""\bLog\.[vdiwe]\(|println\(|printStackTrace""").containsMatchIn(text))
            assertFalse("${file.name} talks to our server", text.contains("okhttp") || text.contains("RestApi") || text.contains("OnlineClient"))
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBillingRepositoryTest {
    private val gateway = FakeBillingGateway()
    private val store = FakeEntitlementStore()

    private fun TestScope.repository() = DefaultBillingRepository(gateway, store, backgroundScope)

    private fun TestScope.settle(millis: Long = 20_000) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test fun notPurchasedShowsPlaysPrice() = runTest {
        val repo = repository()
        assertEquals(AdEntitlement.UNKNOWN, repo.entitlement.value)
        repo.start()
        settle(1)
        assertEquals(RemoveAdsState.Available("₩6,500"), repo.state.value.removeAds)
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
        assertFalse(store.owned)
    }

    @Test fun ownedFromPlayIsAcknowledgedCachedAndRemovesAds() = runTest {
        gateway.purchasesResult = app.numbaseball.billing.BillingStatus.OK to listOf(removeAdsPurchase(token = "tok"))
        val repo = repository()
        repo.start()
        settle(1)
        assertEquals(RemoveAdsState.Owned, repo.state.value.removeAds)
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        assertEquals(listOf("tok"), gateway.acknowledged)
        assertTrue(store.owned)
        // Owners never need the product details.
        assertEquals(0, gateway.productQueries)
    }

    @Test fun acknowledgementIsRetriedAtTheNextQuery() = runTest {
        gateway.purchasesResult = BillingStatus.OK to listOf(removeAdsPurchase(token = "tok"))
        gateway.acknowledgeResult = BillingStatus.RETRYABLE
        val repo = repository()
        repo.start()
        settle(1)
        // Granted anyway (Play allows 3 days), acknowledged on the next resume.
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        assertTrue(gateway.acknowledged.isEmpty())
        gateway.acknowledgeResult = BillingStatus.OK
        repo.refresh()
        settle(1)
        assertEquals(listOf("tok"), gateway.acknowledged)
    }

    @Test fun pendingDoesNotGrant() = runTest {
        gateway.purchasesResult = BillingStatus.OK to listOf(removeAdsPurchase(PlayPurchaseState.PENDING))
        val repo = repository()
        repo.start()
        settle(1)
        assertEquals(RemoveAdsState.Pending, repo.state.value.removeAds)
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
        assertTrue(gateway.acknowledged.isEmpty())
        assertFalse(store.owned)
    }

    @Test fun refundOnResumeBringsAdsBack() = runTest {
        store.owned = true
        gateway.purchasesResult = BillingStatus.OK to listOf(removeAdsPurchase(acknowledged = true))
        val repo = repository()
        repo.start()
        settle(1)
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        gateway.purchasesResult = BillingStatus.OK to emptyList()
        repo.refresh()
        settle(1)
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
        assertEquals(RemoveAdsState.Available("₩6,500"), repo.state.value.removeAds)
        assertFalse(store.owned)
    }

    @Test fun cachedEntitlementAnswersAtOnceOffline() = runTest {
        store.owned = true
        gateway.connectResults = mutableListOf(BillingStatus.RETRYABLE)
        val repo = repository()
        repo.start()
        runCurrent()
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        settle()
        // Play unreachable: the cache stays, the owner keeps the purchased row.
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        assertEquals(RemoveAdsState.Owned, repo.state.value.removeAds)
        assertTrue(store.owned)
    }

    @Test fun connectRetriesThenShowsTheErrorRow() = runTest {
        gateway.connectResults = mutableListOf(BillingStatus.RETRYABLE)
        val repo = repository()
        repo.start()
        settle()
        assertEquals(5, gateway.connectCalls)
        assertEquals(RemoveAdsState.Unavailable, repo.state.value.removeAds)
        // Ads are not held back forever by an unreachable Play Store.
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
    }

    @Test fun connectRecoversOnARetry() = runTest {
        gateway.connectResults = mutableListOf(BillingStatus.RETRYABLE, BillingStatus.RETRYABLE, BillingStatus.OK)
        val repo = repository()
        repo.start()
        settle()
        assertEquals(3, gateway.connectCalls)
        assertEquals(RemoveAdsState.Available("₩6,500"), repo.state.value.removeAds)
    }

    @Test fun billingUnavailableIsNotRetriedAndRetryReloads() = runTest {
        gateway.connectResults = mutableListOf(BillingStatus.UNAVAILABLE)
        val repo = repository()
        repo.start()
        settle()
        assertEquals(1, gateway.connectCalls)
        assertEquals(RemoveAdsState.Unavailable, repo.state.value.removeAds)
        gateway.connectResults = mutableListOf(BillingStatus.OK)
        gateway.connectCalls = 0
        repo.retry()
        settle(1)
        assertEquals(RemoveAdsState.Available("₩6,500"), repo.state.value.removeAds)
    }

    @Test fun productDetailsFailureIsTheErrorRow() = runTest {
        gateway.productResult = BillingStatus.UNAVAILABLE to null
        val repo = repository()
        repo.start()
        settle(1)
        assertEquals(RemoveAdsState.Unavailable, repo.state.value.removeAds)
    }

    @Test fun entitlementIsNotKnownUntilPlayAnswersOrTimesOut() = runTest {
        gateway.connectResults = mutableListOf(BillingStatus.RETRYABLE)
        val repo = repository()
        repo.start()
        runCurrent()
        assertEquals(AdEntitlement.UNKNOWN, repo.entitlement.value)
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
    }

    @Test fun purchaseFlowResults() = runTest {
        val repo = repository()
        val events = mutableListOf<BillingEvent>()
        backgroundScope.launch { repo.events.collect { events += it } }
        repo.start()
        settle(1)
        repo.purchase(Activity())
        assertEquals(1, gateway.launches)
        // Cancelled by the player: nothing.
        gateway.updatesFlow.emit(PurchaseUpdate(BillingStatus.USER_CANCELED, emptyList()))
        settle(1)
        assertTrue(events.isEmpty())
        // Pending first, then purchased.
        gateway.updatesFlow.emit(PurchaseUpdate(BillingStatus.OK, listOf(removeAdsPurchase(PlayPurchaseState.PENDING))))
        settle(1)
        assertEquals(RemoveAdsState.Pending, repo.state.value.removeAds)
        gateway.updatesFlow.emit(PurchaseUpdate(BillingStatus.OK, listOf(removeAdsPurchase(token = "new"))))
        settle(1)
        assertEquals(listOf(BillingEvent.PENDING, BillingEvent.PURCHASED), events)
        assertEquals(RemoveAdsState.Owned, repo.state.value.removeAds)
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
        assertEquals(listOf("new"), gateway.acknowledged)
        assertTrue(store.owned)
        // Already owned: no second purchase sheet.
        repo.purchase(Activity())
        assertEquals(1, gateway.launches)
    }

    @Test fun theResumeQueryRacingTheListenerToastsOnce() = runTest {
        val repo = repository()
        val events = mutableListOf<BillingEvent>()
        backgroundScope.launch { repo.events.collect { events += it } }
        repo.start()
        settle(1)
        repo.purchase(Activity())
        // Play's sheet closes: onResume's query sees the purchase before the listener does.
        gateway.purchasesResult = BillingStatus.OK to listOf(removeAdsPurchase(token = "t1"))
        repo.refresh()
        settle(1)
        gateway.updatesFlow.emit(PurchaseUpdate(BillingStatus.OK, listOf(removeAdsPurchase(acknowledged = true, token = "t1"))))
        settle(1)
        assertEquals(listOf(BillingEvent.PURCHASED), events)
        // A plain resume later says nothing.
        repo.refresh()
        settle(1)
        assertEquals(listOf(BillingEvent.PURCHASED), events)
    }

    @Test fun purchaseErrorsShowTheFailureToast() = runTest {
        val repo = repository()
        val events = mutableListOf<BillingEvent>()
        backgroundScope.launch { repo.events.collect { events += it } }
        repo.start()
        settle(1)
        gateway.launchResult = BillingStatus.ERROR
        repo.purchase(Activity())
        gateway.updatesFlow.emit(PurchaseUpdate(BillingStatus.RETRYABLE, emptyList()))
        settle(1)
        assertEquals(listOf(BillingEvent.PURCHASE_FAILED, BillingEvent.PURCHASE_FAILED), events)
        assertEquals(AdEntitlement.ADS_ALLOWED, repo.entitlement.value)
    }

    @Test fun restoreFindsNothingFindsThePurchaseOrFails() = runTest {
        val repo = repository()
        val events = mutableListOf<BillingEvent>()
        backgroundScope.launch { repo.events.collect { events += it } }
        repo.start()
        settle(1)
        repo.restore()
        assertTrue(repo.state.value.restoring)
        settle(1)
        assertFalse(repo.state.value.restoring)
        gateway.purchasesResult = BillingStatus.RETRYABLE to emptyList()
        repo.restore()
        settle(1)
        gateway.purchasesResult = BillingStatus.OK to listOf(removeAdsPurchase(acknowledged = true))
        repo.restore()
        settle(1)
        assertEquals(listOf(BillingEvent.NOTHING_TO_RESTORE, BillingEvent.RESTORE_FAILED, BillingEvent.RESTORED), events)
        assertEquals(AdEntitlement.ADS_REMOVED, repo.entitlement.value)
    }
}
