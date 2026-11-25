package app.numbaseball.testing

import android.app.Activity
import app.numbaseball.billing.AdEntitlement
import app.numbaseball.billing.BillingEvent
import app.numbaseball.billing.BillingGateway
import app.numbaseball.billing.BillingRepository
import app.numbaseball.billing.BillingStatus
import app.numbaseball.billing.EntitlementStore
import app.numbaseball.billing.PlayPurchase
import app.numbaseball.billing.PlayPurchaseState
import app.numbaseball.billing.PurchaseUiState
import app.numbaseball.billing.PurchaseUpdate
import app.numbaseball.billing.REMOVE_ADS_PRODUCT_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow

fun removeAdsPurchase(state: PlayPurchaseState = PlayPurchaseState.PURCHASED, acknowledged: Boolean = false, token: String = "secret-token-123") =
    PlayPurchase(listOf(REMOVE_ADS_PRODUCT_ID), state, acknowledged, token)

/** Scriptable Play Billing: connection results, purchases, product price and what was acknowledged or launched. */
class FakeBillingGateway : BillingGateway {
    val updatesFlow = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override val updates: Flow<PurchaseUpdate> = updatesFlow

    /** Results of successive connect() calls; the last one repeats. */
    var connectResults = mutableListOf(BillingStatus.OK)
    var connectCalls = 0
    var ready = false
    override val isReady: Boolean get() = ready

    var productResult: Pair<BillingStatus, String?> = BillingStatus.OK to "₩6,500"
    var productQueries = 0
    var purchasesResult: Pair<BillingStatus, List<PlayPurchase>> = BillingStatus.OK to emptyList()
    var purchaseQueries = 0
    var acknowledgeResult = BillingStatus.OK
    val acknowledged = mutableListOf<String>()
    var launchResult = BillingStatus.OK
    var launches = 0

    override suspend fun connect(): BillingStatus {
        val result = connectResults.getOrElse(connectCalls) { connectResults.last() }
        connectCalls++
        ready = result == BillingStatus.OK
        return result
    }

    override suspend fun queryProduct(): Pair<BillingStatus, String?> {
        productQueries++
        return productResult
    }

    override suspend fun queryPurchases(): Pair<BillingStatus, List<PlayPurchase>> {
        purchaseQueries++
        return purchasesResult
    }

    override suspend fun acknowledge(purchase: PlayPurchase): BillingStatus {
        if (acknowledgeResult == BillingStatus.OK) acknowledged += purchase.token
        return acknowledgeResult
    }

    override fun launchPurchase(activity: Activity): BillingStatus {
        launches++
        return launchResult
    }
}

class FakeEntitlementStore(var owned: Boolean = false) : EntitlementStore {
    override suspend fun cachedOwned(): Boolean = owned
    override suspend fun saveOwned(owned: Boolean) {
        this.owned = owned
    }
}

/** For the Robolectric app container and UI tests: a fixed state. */
class FakeBillingRepository(
    initial: PurchaseUiState = PurchaseUiState(),
    entitlement: AdEntitlement = AdEntitlement.ADS_ALLOWED,
) : BillingRepository {
    override val state = MutableStateFlow(initial)
    override val entitlement = MutableStateFlow(entitlement)
    val eventFlow = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<BillingEvent> = eventFlow
    var purchases = 0
    var restores = 0
    override fun start() = Unit
    override fun refresh() = Unit
    override fun retry() = Unit
    override fun purchase(activity: Activity) {
        purchases++
    }

    override fun restore() {
        restores++
    }
}
