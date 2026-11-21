package app.numbaseball

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.numbaseball.ads.AdsController
import app.numbaseball.ads.DisabledAdsController
import app.numbaseball.ads.GoogleAdsController
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.DataStoreAccountStore
import app.numbaseball.data.online.OnlineClient
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.online.RestApi
import app.numbaseball.data.online.SafetyApi
import app.numbaseball.data.online.ServerEndpoints
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
    val adsEnabled: Boolean,
    val bannerAdUnitId: String,
    val interstitialAdUnitId: String,
) {
    companion object {
        fun fromBuildConfig() = AppConfig(
            serverUrl = BuildConfig.SERVER_URL,
            versionName = BuildConfig.VERSION_NAME,
            adsEnabled = true,
            bannerAdUnitId = BuildConfig.ADMOB_BANNER_ID,
            interstitialAdUnitId = BuildConfig.ADMOB_INTERSTITIAL_ID,
        )
    }
}

/** Manual dependency container: one instance per process, created by [NumBaseballApp]. */
interface AppContainer {
    val config: AppConfig
    val accountRepository: AccountRepository
    val settingsRepository: SettingsRepository
    val languageSettings: LanguageSettings
    val onlineClient: OnlineClient
    val onlineSession: OnlineSession
    val safetyApi: SafetyApi
    val offlineGame: OfflineGame
    val ads: AdsController
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

    override val accountRepository by lazy { AccountRepository(DataStoreAccountStore(appContext), restApi, appScope) }
    override val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(appContext) }
    override val languageSettings: LanguageSettings by lazy { AppCompatLanguageSettings() }
    override val safetyApi: SafetyApi get() = restApi
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
        }
    }

    override val ads: AdsController by lazy {
        if (config.adsEnabled) {
            GoogleAdsController(appContext, config.bannerAdUnitId, config.interstitialAdUnitId, appScope)
        } else {
            DisabledAdsController
        }
    }
}
