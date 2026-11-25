package app.numbaseball.billing

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Play Billing response codes, reduced to what the repository acts on. */
enum class BillingStatus {
    OK,
    USER_CANCELED,
    ITEM_ALREADY_OWNED,
    /** Service disconnected / unavailable / timeout / network: worth retrying. */
    RETRYABLE,
    /** Billing unavailable on this device or account (no Play Store, not signed in, country). */
    UNAVAILABLE,
    ERROR,
}

/** A purchase update from Play's `PurchasesUpdatedListener`. */
data class PurchaseUpdate(val status: BillingStatus, val purchases: List<PlayPurchase>)

/**
 * The thin, untestable part: Google's `BillingClient` ([PlayBillingGateway]); tests use a fake. Implementations keep the
 * product details they loaded so [launchPurchase] can use them.
 */
interface BillingGateway {
    val updates: Flow<PurchaseUpdate>
    val isReady: Boolean
    suspend fun connect(): BillingStatus
    /** Play's formatted price of `remove_ads`, or the failure. */
    suspend fun queryProduct(): Pair<BillingStatus, String?>
    suspend fun queryPurchases(): Pair<BillingStatus, List<PlayPurchase>>
    suspend fun acknowledge(purchase: PlayPurchase): BillingStatus
    /** Opens Play's sheet; [BillingStatus.ERROR] if the product details are not loaded. */
    fun launchPurchase(activity: Activity): BillingStatus
}

/** Persisted entitlement for an offline start (its own DataStore file, not backed up: Play restores it). */
interface EntitlementStore {
    suspend fun cachedOwned(): Boolean
    suspend fun saveOwned(owned: Boolean)
}

/**
 * All billing decisions (unit tested with a fake [BillingGateway]):
 * - connect with retry (1, 2, 4, 8 s; `enableAutoServiceReconnection` covers later drops), query purchases at [start]
 *   and on every [refresh] (resume), grant only on PURCHASED, acknowledge at once and again at the next query if that
 *   failed (3-day rule), PENDING never grants, a purchase that disappeared (refund, revocation) removes the entitlement;
 * - the cached entitlement answers at once on an offline start; it is replaced by every successful Play answer and kept
 *   when Play cannot be reached;
 * - [entitlement] leaves UNKNOWN once the cache says "owned", Play answered, or Play failed / took longer than
 *   [startTimeoutMillis], so ads can never be held back for good by a slow Play Store.
 */
class DefaultBillingRepository(
    private val gateway: BillingGateway,
    private val store: EntitlementStore,
    private val scope: CoroutineScope,
    private val retryDelaysMillis: List<Long> = listOf(1_000, 2_000, 4_000, 8_000),
    private val startTimeoutMillis: Long = 5_000,
) : BillingRepository {
    private val _state = MutableStateFlow(PurchaseUiState())
    override val state: StateFlow<PurchaseUiState> = _state.asStateFlow()

    private val _entitlement = MutableStateFlow(AdEntitlement.UNKNOWN)
    override val entitlement: StateFlow<AdEntitlement> = _entitlement.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private val mutex = Mutex()
    private var started = false
    private var price: String? = null
    private var ownership: Ownership? = null
    private var refreshJob: Job? = null
    /**
     * A purchase flow is open (or Play reported a purchase update): the next transition to PENDING / OWNED gets its toast,
     * whether the purchase list arrives through the listener or the resume query that races it.
     */
    @Volatile private var purchaseInFlight = false

    override fun start() {
        if (started) return
        started = true
        scope.launch {
            if (store.cachedOwned()) {
                ownership = Ownership.OWNED
                _entitlement.value = AdEntitlement.ADS_REMOVED
                _state.update { it.copy(removeAds = RemoveAdsState.Owned) }
            }
            launch { gateway.updates.collect(::onPurchaseUpdate) }
            launch {
                delay(startTimeoutMillis)
                if (_entitlement.value == AdEntitlement.UNKNOWN) _entitlement.value = AdEntitlement.ADS_ALLOWED
            }
            sync(retry = true)
        }
    }

    override fun refresh() {
        if (!started) return start()
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch { sync(retry = false) }
    }

    override fun retry() {
        if (!started) return start()
        _state.update { if (it.removeAds == RemoveAdsState.Unavailable) it.copy(removeAds = RemoveAdsState.Loading) else it }
        scope.launch { sync(retry = true) }
    }

    override fun purchase(activity: Activity) {
        if (ownership == Ownership.OWNED || ownership == Ownership.PENDING) return
        when (gateway.launchPurchase(activity)) {
            BillingStatus.OK -> purchaseInFlight = true
            BillingStatus.USER_CANCELED -> Unit
            BillingStatus.ITEM_ALREADY_OWNED -> scope.launch { syncAfterAlreadyOwned() }
            else -> _events.tryEmit(BillingEvent.PURCHASE_FAILED)
        }
    }

    /** Play says the item is owned (bought on another device, cache cleared): pick it up like a restore. */
    private suspend fun syncAfterAlreadyOwned() {
        sync(retry = false)
        if (ownership == Ownership.OWNED) _events.tryEmit(BillingEvent.RESTORED)
    }

    override fun restore() {
        if (_state.value.restoring) return
        _state.update { it.copy(restoring = true) }
        scope.launch {
            val event = mutex.withLock {
                // Restore reports its own result; no purchase toast on top of it.
                purchaseInFlight = false
                if (!ensureConnected(retry = false)) return@withLock BillingEvent.RESTORE_FAILED
                val (status, purchases) = gateway.queryPurchases()
                if (status != BillingStatus.OK) return@withLock BillingEvent.RESTORE_FAILED
                when (apply(purchases)) {
                    Ownership.OWNED -> BillingEvent.RESTORED
                    Ownership.PENDING -> BillingEvent.PENDING
                    Ownership.NONE -> {
                        loadProductIfNeeded()
                        BillingEvent.NOTHING_TO_RESTORE
                    }
                }
            }
            _state.update { it.copy(restoring = false) }
            _events.tryEmit(event)
        }
    }

    /** Purchases and, when not owned, the product's price. */
    private suspend fun sync(retry: Boolean) = mutex.withLock {
        if (!ensureConnected(retry)) {
            onPlayUnreachable()
            return@withLock
        }
        val (status, purchases) = gateway.queryPurchases()
        if (status == BillingStatus.OK) {
            apply(purchases)
        } else {
            onPlayUnreachable()
            return@withLock
        }
        loadProductIfNeeded()
    }

    private suspend fun loadProductIfNeeded() {
        if (ownership == Ownership.OWNED || ownership == Ownership.PENDING) return
        if (price == null) {
            val (status, formatted) = gateway.queryProduct()
            if (status == BillingStatus.OK && formatted != null) price = formatted
        }
        _state.update { it.copy(removeAds = price?.let { p -> RemoveAdsState.Available(p) } ?: RemoveAdsState.Unavailable) }
    }

    /** Keep the cached entitlement; an owner stays ad-free offline, everyone else gets the error row. */
    private fun onPlayUnreachable() {
        if (ownership == Ownership.OWNED) return
        if (_entitlement.value == AdEntitlement.UNKNOWN) _entitlement.value = AdEntitlement.ADS_ALLOWED
        _state.update { it.copy(removeAds = RemoveAdsState.Unavailable) }
    }

    private suspend fun ensureConnected(retry: Boolean): Boolean {
        if (gateway.isReady) return true
        var status = gateway.connect()
        if (!retry) return status == BillingStatus.OK
        for (wait in retryDelaysMillis) {
            if (status == BillingStatus.OK || status != BillingStatus.RETRYABLE) break
            delay(wait)
            status = gateway.connect()
        }
        return status == BillingStatus.OK
    }

    /** Applies a complete purchase list from Play: grant, acknowledge, cache, state. */
    private suspend fun apply(purchases: List<PlayPurchase>): Ownership {
        val result = RemoveAdsRules.ownership(purchases)
        val before = ownership
        // Grant first: if acknowledging fails, the next query (start or resume) tries again well within 3 days.
        RemoveAdsRules.toAcknowledge(purchases).forEach { gateway.acknowledge(it) }
        ownership = result
        store.saveOwned(result == Ownership.OWNED)
        _entitlement.value = if (result == Ownership.OWNED) AdEntitlement.ADS_REMOVED else AdEntitlement.ADS_ALLOWED
        _state.update {
            it.copy(
                removeAds = when (result) {
                    Ownership.OWNED -> RemoveAdsState.Owned
                    Ownership.PENDING -> RemoveAdsState.Pending
                    Ownership.NONE -> price?.let { p -> RemoveAdsState.Available(p) } ?: RemoveAdsState.Loading
                },
            )
        }
        if (purchaseInFlight && result != before) {
            when (result) {
                Ownership.OWNED -> {
                    purchaseInFlight = false
                    _events.tryEmit(BillingEvent.PURCHASED)
                }
                // Keep the flag: when the payment clears while the app is open, "광고를 제거했어요" follows.
                Ownership.PENDING -> _events.tryEmit(BillingEvent.PENDING)
                Ownership.NONE -> Unit
            }
        }
        return result
    }

    private suspend fun onPurchaseUpdate(update: PurchaseUpdate) {
        when (update.status) {
            BillingStatus.OK -> {
                // The listener reports only the purchases of this flow: merge with what we know by re-querying when
                // the list says nothing about our product.
                mutex.withLock {
                    if (update.purchases.none { REMOVE_ADS_PRODUCT_ID in it.products }) return@withLock
                    purchaseInFlight = true
                    if (apply(update.purchases) == Ownership.NONE) loadProductIfNeeded()
                }
            }
            BillingStatus.USER_CANCELED -> purchaseInFlight = false
            BillingStatus.ITEM_ALREADY_OWNED -> {
                purchaseInFlight = false
                syncAfterAlreadyOwned()
            }
            else -> {
                purchaseInFlight = false
                _events.tryEmit(BillingEvent.PURCHASE_FAILED)
            }
        }
    }
}
