package app.numbaseball.billing

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google Play Billing Library (billing-ktx 9.x). Only Play Billing sells the item: no other payment path, no external
 * link. Nothing here logs; [PlayPurchase] hides the token from `toString`.
 */
class PlayBillingGateway(context: Context) : BillingGateway {
    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val updates: Flow<PurchaseUpdate> = _updates.asSharedFlow()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> _updates.tryEmit(PurchaseUpdate(result.status(), purchases.orEmpty().map { it.toPlayPurchase() })) }
        // Required for one-time products; PENDING purchases are reported but never granted (DefaultBillingRepository).
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // Reconnects by itself when the service drops after the first connection.
        .enableAutoServiceReconnection()
        .build()

    @Volatile private var details: ProductDetails? = null

    override val isReady: Boolean get() = client.isReady

    override suspend fun connect(): BillingStatus = suspendCancellableCoroutine { cont ->
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.status())
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(BillingStatus.RETRYABLE)
                }
            },
        )
    }

    override suspend fun queryProduct(): Pair<BillingStatus, String?> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(REMOVE_ADS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        val product = result.productDetailsList.orEmpty().firstOrNull { it.productId == REMOVE_ADS_PRODUCT_ID }
        val price = product?.oneTimePurchaseOfferDetails?.formattedPrice
        if (result.billingResult.status() != BillingStatus.OK || product == null || price == null) {
            return (result.billingResult.status().takeIf { it != BillingStatus.OK } ?: BillingStatus.UNAVAILABLE) to null
        }
        details = product
        return BillingStatus.OK to price
    }

    override suspend fun queryPurchases(): Pair<BillingStatus, List<PlayPurchase>> {
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        return result.billingResult.status() to result.purchasesList.map { it.toPlayPurchase() }
    }

    override suspend fun acknowledge(purchase: PlayPurchase): BillingStatus =
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.token).build()).status()

    override fun launchPurchase(activity: Activity): BillingStatus {
        val product = details ?: return BillingStatus.ERROR
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
            .build()
        return client.launchBillingFlow(activity, params).status()
    }
}

private fun Purchase.toPlayPurchase() = PlayPurchase(
    products = products,
    state = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> PlayPurchaseState.PURCHASED
        Purchase.PurchaseState.PENDING -> PlayPurchaseState.PENDING
        else -> PlayPurchaseState.UNSPECIFIED
    },
    acknowledged = isAcknowledged,
    token = purchaseToken,
)

private fun BillingResult.status(): BillingStatus = when (responseCode) {
    BillingClient.BillingResponseCode.OK -> BillingStatus.OK
    BillingClient.BillingResponseCode.USER_CANCELED -> BillingStatus.USER_CANCELED
    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BillingStatus.ITEM_ALREADY_OWNED
    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
    BillingClient.BillingResponseCode.NETWORK_ERROR,
    BillingClient.BillingResponseCode.ERROR,
    -> BillingStatus.RETRYABLE
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
    -> BillingStatus.UNAVAILABLE
    else -> BillingStatus.ERROR
}

private val Context.billingDataStore by preferencesDataStore(name = "billing")

/** Only a boolean is stored (never the token or order id); not part of Auto Backup, Play restores it. */
class DataStoreEntitlementStore(context: Context) : EntitlementStore {
    private val store = context.applicationContext.billingDataStore

    override suspend fun cachedOwned(): Boolean = store.data.map { it[REMOVE_ADS_OWNED] ?: false }.first()

    override suspend fun saveOwned(owned: Boolean) {
        store.edit { it[REMOVE_ADS_OWNED] = owned }
    }

    private companion object {
        val REMOVE_ADS_OWNED = booleanPreferencesKey("remove_ads_owned")
    }
}
