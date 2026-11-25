package app.numbaseball

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.numbaseball.ads.AdsController
import app.numbaseball.ads.DisabledAdsController
import app.numbaseball.ads.GoogleAdsController
import app.numbaseball.audio.AndroidGameAudio
import app.numbaseball.billing.BillingRepository
import app.numbaseball.billing.DataStoreEntitlementStore
import app.numbaseball.billing.DefaultBillingRepository
import app.numbaseball.billing.PlayBillingGateway
import app.numbaseball.billing.adsAllowed
import app.numbaseball.audio.GameAudio
import app.numbaseball.audio.SilentGameAudio
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.age.AgeRepository
import app.numbaseball.data.age.DataStoreAgeStore
import app.numbaseball.data.age.PlayAgeSignalsSource
import app.numbaseball.data.account.DataStoreAccountStore
import app.numbaseball.data.online.AndroidNetworkMonitor
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.NetworkMonitor
import app.numbaseball.data.online.OnlineClient
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.online.RestApi
import app.numbaseball.data.online.SafetyRepository
import app.numbaseball.data.online.ServerEndpoints
import app.numbaseball.data.online.StopReason
import app.numbaseball.data.online.WebSocketOnlineClient
import app.numbaseball.data.settings.AppCompatLanguageSettings
import app.numbaseball.data.settings.DataStoreSettingsRepository
import app.numbaseball.data.settings.LanguageSettings
import app.numbaseball.data.settings.SettingsRepository
import app.numbaseball.game.offline.OfflineGame
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Build-time configuration (BuildConfig in the app, fakes in tests). */
data class AppConfig(
    val serverUrl: String,
    val versionName: String,
    val contactEmail: String,
    val minimumAge: Int,
    val maxAdContentRating: String,
    val adsEnabled: Boolean,
    val audioEnabled: Boolean,
    val bannerAdUnitId: String,
    val interstitialAdUnitId: String,
) {
    /** Web pages served by our server (docs/protocol.md §14). */
    val privacyUrl: String get() = serverUrl.trimEnd('/') + "/privacy"
    val termsUrl: String get() = serverUrl.trimEnd('/') + "/terms"

    companion object {
        fun fromBuildConfig() = AppConfig(
            serverUrl = BuildConfig.SERVER_URL,
            versionName = BuildConfig.VERSION_NAME,
            contactEmail = BuildConfig.CONTACT_EMAIL,
            minimumAge = BuildConfig.MINIMUM_AGE,
            maxAdContentRating = BuildConfig.MAX_AD_CONTENT_RATING,
            adsEnabled = true,
            audioEnabled = true,
            bannerAdUnitId = BuildConfig.ADMOB_BANNER_ID,
            interstitialAdUnitId = BuildConfig.ADMOB_INTERSTITIAL_ID,
        )
    }
}

/** Manual dependency container: one instance per process, created by [NumBaseballApp]. */
interface AppContainer {
    val config: AppConfig
    val ageRepository: AgeRepository
    val accountRepository: AccountRepository
    val settingsRepository: SettingsRepository
    val languageSettings: LanguageSettings
    val onlineClient: OnlineClient
    val onlineSession: OnlineSession
    val safety: SafetyRepository
    val offlineGame: OfflineGame
    val ads: AdsController
    /** Play Billing "Remove ads" (README, owner decision 2026-11-25). */
    val billing: BillingRepository
    val audio: GameAudio
    val network: NetworkMonitor
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

class DefaultAppContainer(context: Context, override val config: AppConfig) : AppContainer {
    private val appContext = context.applicationContext

    /** Lives as long as the process; failures in one child do not cancel the others. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            // Client-side heartbeat: a missing pong fails the socket quickly so the reconnect starts.
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }
    private val endpoints by lazy { ServerEndpoints(config.serverUrl) }
    private val restApi by lazy { RestApi(httpClient, endpoints, config.versionName) }

    override val ageRepository by lazy {
        AgeRepository(DataStoreAgeStore(appContext), PlayAgeSignalsSource(appContext), config.minimumAge, appScope)
    }
    override val accountRepository by lazy { AccountRepository(DataStoreAccountStore(appContext), restApi, appScope) }
    override val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(appContext) }
    override val languageSettings: LanguageSettings by lazy { AppCompatLanguageSettings() }
    override val safety by lazy { SafetyRepository(restApi, accountRepository, onlineSession) }
    override val offlineGame by lazy { OfflineGame() }

    override val onlineClient: OnlineClient by lazy {
        WebSocketOnlineClient(
            // callTimeout must not apply to a long-lived socket.
            httpClient = httpClient.newBuilder().callTimeout(0, TimeUnit.SECONDS).build(),
            url = endpoints.webSocket.toString(),
            clientVersion = config.versionName,
            tokenProvider = { accountRepository.token() },
            scope = appScope,
        )
    }

    override val onlineSession by lazy {
        OnlineSession(onlineClient, appScope).also { session ->
            appScope.launch { session.welcomes.collect { accountRepository.onAccountUpdate(it.account) } }
            // HTTP 401 / close 4004: the token is dead (deleted or moved); keep the nickname for a new account.
            appScope.launch {
                onlineClient.state.collect { state ->
                    if (state is ConnectionState.Stopped &&
                        (state.reason == StopReason.UNAUTHORIZED || state.reason == StopReason.ACCOUNT_DELETED)
                    ) {
                        accountRepository.onTokenRejected()
                    }
                }
            }
        }
    }

    override val ads: AdsController by lazy {
        if (config.adsEnabled) {
            GoogleAdsController(
                appContext = appContext,
                bannerAdUnitId = config.bannerAdUnitId,
                interstitialAdUnitId = config.interstitialAdUnitId,
                scope = appScope,
                maxAdContentRating = config.maxAdContentRating,
                adsAllowed = { billing.entitlement.value.adsAllowed },
            )
        } else {
            DisabledAdsController
        }
    }

    override val billing: BillingRepository by lazy {
        DefaultBillingRepository(PlayBillingGateway(appContext), DataStoreEntitlementStore(appContext), appScope)
    }

    override val network: NetworkMonitor by lazy { AndroidNetworkMonitor(appContext) }

    override val audio: GameAudio by lazy {
        if (config.audioEnabled) AndroidGameAudio(appContext, settingsRepository.settings, appScope) else SilentGameAudio
    }
}
