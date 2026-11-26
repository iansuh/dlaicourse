package app.numbaseball.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY: a sideloaded test APK is not known to Google Play, so real billing cannot sell `remove_ads`.
 * This demo gateway lets the owner try the whole flow (price, purchase, ads disappearing, restore). Release builds use
 * [PlayBillingGateway] (see src/release). Clearing the app's data "refunds" the demo purchase.
 */
fun createBillingGateway(context: Context): BillingGateway = DemoBillingGateway()

private class DemoBillingGateway : BillingGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val flow = MutableSharedFlow<PurchaseUpdate>(replay = 0, extraBufferCapacity = 4)
    private var owned: PlayPurchase? = null
    override val updates: Flow<PurchaseUpdate> = flow
    override val isReady: Boolean = true

    override suspend fun connect(): BillingStatus = BillingStatus.OK
    override suspend fun queryProduct(): Pair<BillingStatus, String?> = BillingStatus.OK to "US$4.99 (TEST)"
    override suspend fun queryPurchases(): Pair<BillingStatus, List<PlayPurchase>> = BillingStatus.OK to listOfNotNull(owned)
    override suspend fun acknowledge(purchase: PlayPurchase): BillingStatus {
        owned = PlayPurchase(purchase.products, purchase.state, true, purchase.token)
        return BillingStatus.OK
    }

    override fun launchPurchase(activity: Activity): BillingStatus {
        scope.launch {
            delay(800) // stands in for Play's purchase sheet
            val p = PlayPurchase(listOf(REMOVE_ADS_PRODUCT_ID), PlayPurchaseState.PURCHASED, false, "demo")
            owned = p
            flow.emit(PurchaseUpdate(BillingStatus.OK, listOf(p)))
        }
        return BillingStatus.OK
    }
}
