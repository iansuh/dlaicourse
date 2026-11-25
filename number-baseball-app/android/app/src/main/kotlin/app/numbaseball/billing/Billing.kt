package app.numbaseball.billing

import android.app.Activity
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * "Remove ads" (README, owner decision 2026-11-25; POLICY_CHECKLIST 12; POLICY_REVIEW R22 / Android item 13): one
 * non-consumable Google Play product. The entitlement comes from Play only; the purchase token never leaves this
 * package: it is not logged, not persisted and never sent to our server.
 */
const val REMOVE_ADS_PRODUCT_ID = "remove_ads"

/** Play's purchase state, reduced to what matters here. */
enum class PlayPurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/**
 * One purchase as Play reports it. [token] is only kept in memory long enough to acknowledge the purchase;
 * [toString] leaves it out so it can never end up in a log or crash report by accident.
 */
class PlayPurchase(
    val products: List<String>,
    val state: PlayPurchaseState,
    val acknowledged: Boolean,
    val token: String,
) {
    override fun toString(): String = "PlayPurchase(products=$products, state=$state, acknowledged=$acknowledged)"
}

enum class Ownership { NONE, PENDING, OWNED }

/** Pure rules (unit tested): what a list of Play purchases means for `remove_ads`. */
object RemoveAdsRules {
    /** Granted only on PURCHASED; PENDING never grants (POLICY_CHECKLIST 12); a missing purchase (refund) is NONE. */
    fun ownership(purchases: List<PlayPurchase>, productId: String = REMOVE_ADS_PRODUCT_ID): Ownership {
        val ours = purchases.filter { productId in it.products }
        return when {
            ours.any { it.state == PlayPurchaseState.PURCHASED } -> Ownership.OWNED
            ours.any { it.state == PlayPurchaseState.PENDING } -> Ownership.PENDING
            else -> Ownership.NONE
        }
    }

    /** PURCHASED and not yet acknowledged: must be acknowledged within 3 days or Google refunds it. */
    fun toAcknowledge(purchases: List<PlayPurchase>, productId: String = REMOVE_ADS_PRODUCT_ID): List<PlayPurchase> =
        purchases.filter { productId in it.products && it.state == PlayPurchaseState.PURCHASED && !it.acknowledged }
}

/** Settings 11 §1.1 states of the "광고 제거" row. */
sealed interface RemoveAdsState {
    /** Product details not back yet. */
    data object Loading : RemoveAdsState

    /** Not purchased; [price] is Play's formatted price, never a hard-coded string. */
    data class Available(val price: String) : RemoveAdsState

    data object Owned : RemoveAdsState

    /** PENDING payment: not bought yet, ads keep showing. */
    data object Pending : RemoveAdsState

    /** Billing unavailable, not signed in to Play, or product details failed. */
    data object Unavailable : RemoveAdsState
}

data class PurchaseUiState(
    val removeAds: RemoveAdsState = RemoveAdsState.Loading,
    /** "구매 복원" is querying Play (the row shows the chase lamps instead of its subtitle). */
    val restoring: Boolean = false,
)

/**
 * Whether ads may run. [UNKNOWN] until the cached entitlement or Play answered at start: nothing ad-related (UMP,
 * Mobile Ads init) starts while it is unknown, so an owner never sees a consent form or an ad request.
 */
enum class AdEntitlement { UNKNOWN, ADS_REMOVED, ADS_ALLOWED }

/** One-off results shown as toasts (11 §1.1 "Flows and feedback"). */
enum class BillingEvent { PURCHASED, PENDING, PURCHASE_FAILED, RESTORED, NOTHING_TO_RESTORE, RESTORE_FAILED }

interface BillingRepository {
    val state: StateFlow<PurchaseUiState>
    val entitlement: StateFlow<AdEntitlement>
    val events: SharedFlow<BillingEvent>

    /** Connect (with retry), read the cached entitlement, query purchases and the product. Idempotent. */
    fun start()

    /** On every resume: re-query purchases (refunds, pending purchases that cleared, purchases from another device). */
    fun refresh()

    /** "다시 시도" in the error state: reconnect and reload the product. */
    fun retry()

    /** Opens Play's purchase sheet. The result arrives as a [BillingEvent] and a new [state]. */
    fun purchase(activity: Activity)

    /** "구매 복원": re-queries Play. */
    fun restore()
}

val AdEntitlement.adsAllowed: Boolean get() = this == AdEntitlement.ADS_ALLOWED

/** Previews and tests that do not care: product never loads, ads not removed. */
object NoBillingRepository : BillingRepository {
    override val state: StateFlow<PurchaseUiState> = MutableStateFlow(PurchaseUiState()).asStateFlow()
    override val entitlement: StateFlow<AdEntitlement> = MutableStateFlow(AdEntitlement.ADS_ALLOWED).asStateFlow()
    override val events: SharedFlow<BillingEvent> = MutableSharedFlow<BillingEvent>().asSharedFlow()
    override fun start() = Unit
    override fun refresh() = Unit
    override fun retry() = Unit
    override fun purchase(activity: Activity) = Unit
    override fun restore() = Unit
}

val LocalBilling = staticCompositionLocalOf<BillingRepository> { NoBillingRepository }
