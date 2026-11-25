package app.numbaseball.qa

import android.app.Activity
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import app.numbaseball.AppConfig
import app.numbaseball.AppContainer
import app.numbaseball.LocalAppContainer
import app.numbaseball.NumBaseballApp
import app.numbaseball.ads.AdsController
import app.numbaseball.ads.FinishedMatchKind
import app.numbaseball.ads.InterstitialController
import app.numbaseball.ads.InterstitialPolicy
import app.numbaseball.ads.LocalAdPreview
import app.numbaseball.ads.LocalAdsController
import app.numbaseball.audio.GameAudio
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.SilentGameAudio
import app.numbaseball.billing.BillingRepository
import app.numbaseball.billing.LocalBilling
import app.numbaseball.core.protocol.AcceptTermsRequest
import app.numbaseball.core.protocol.AccountDto
import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.CodeResponse
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.ServerInfo
import app.numbaseball.data.account.AccountApi
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.ApiResult
import app.numbaseball.data.age.AdAgeTreatment
import app.numbaseball.data.age.AgeRepository
import app.numbaseball.data.online.NetworkMonitor
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.online.SafetyRepository
import app.numbaseball.data.settings.LanguageSettings
import app.numbaseball.data.settings.SettingsRepository
import app.numbaseball.game.offline.OfflineGame
import app.numbaseball.testing.FakeAccountApi
import app.numbaseball.testing.FakeAccountStore
import app.numbaseball.testing.FakeAgeStore
import app.numbaseball.testing.FakeBillingRepository
import app.numbaseball.testing.FakeLanguageSettings
import app.numbaseball.testing.FakeOnlineClient
import app.numbaseball.testing.FakeSafetyApi
import app.numbaseball.testing.FakeSettingsRepository
import app.numbaseball.ui.navigation.NbApp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import app.numbaseball.ui.theme.NumBaseballTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// QA harness (docs/qa/TEST_PLAN.md, Android phase). Test sources only: the app's main code is not touched.

/** The server is not reachable at all: every REST call fails like airplane mode (DNS / connect error). */
class UnreachableAccountApi : AccountApi {
    var calls = 0
        private set

    private fun <T> down(): ApiResult<T> {
        calls++
        return ApiResult.NetworkError
    }

    override suspend fun createAccount(nickname: String): ApiResult<AuthResponse> = down()
    override suspend fun me(token: String): ApiResult<AccountDto> = down()
    override suspend fun deleteAccount(token: String): ApiResult<Unit> = down()
    override suspend fun rename(token: String, nickname: String): ApiResult<AccountDto> = down()
    override suspend fun createTransferCode(token: String): ApiResult<CodeResponse> = down()
    override suspend fun redeemTransferCode(code: String): ApiResult<AuthResponse> = down()
    override suspend fun serverInfo(): ApiResult<ServerInfo> = down()
    override suspend fun acceptTerms(token: String, request: AcceptTermsRequest): ApiResult<AccountDto> = down()
}

class QaNetwork(online: Boolean = true) : NetworkMonitor {
    val flow = MutableStateFlow(online)
    override val online: StateFlow<Boolean> = flow.asStateFlow()
}

/**
 * Ads controller that records what the app asks of it and counts finished matches with the real [InterstitialPolicy]
 * (clock = elapsed realtime + [skewMillis], so a test can "wait 5 minutes"). Banners are not drawn by it (the tests
 * give `LocalAdPreview`); `show` is recorded and closed by the test with [closeAd].
 */
class RecordingAds : AdsController {
    val consentCalls = mutableListOf<AdAgeTreatment>()
    val treatmentUpdates = mutableListOf<AdAgeTreatment>()
    var sdkInitialised = false
        private set
    val canRequest = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = canRequest.asStateFlow()
    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val bannerAdUnitId: String? = null

    override fun gatherConsent(activity: Activity, treatment: AdAgeTreatment) {
        consentCalls += treatment
        sdkInitialised = true
        canRequest.value = true
    }

    override fun updateAgeTreatment(treatment: AdAgeTreatment) {
        treatmentUpdates += treatment
    }

    override fun showPrivacyOptions(activity: Activity) = Unit

    val interstitial = RecordingInterstitials()
    override val interstitials: InterstitialController = interstitial
    val adShown: Int get() = interstitial.shown.size
    fun closeAd() = interstitial.close()
}

class RecordingInterstitials : InterstitialController {
    @Volatile var skewMillis = 0L
    private val policy = InterstitialPolicy { SystemClock.elapsedRealtime() + skewMillis }
    val finishedEvents = mutableListOf<Pair<FinishedMatchKind, EndReason?>>()
    val shown = mutableListOf<Long>()
    var preloads = 0
    var loaded = true
    private var pendingClose: (() -> Unit)? = null

    override fun onMatchFinished(kind: FinishedMatchKind, endReason: EndReason?, serverRestart: Boolean) {
        finishedEvents += kind to endReason
        policy.onMatchFinished(kind, endReason, serverRestart)
        if (policy.shouldPreload()) preloads++
    }

    override val finishedSinceLastAd: Int get() = policy.finishedSinceLastAd
    override val sinceLastAdMillis: Long get() = policy.sinceLastAdMillis
    var loadedReads = 0
    override val isLoaded: Boolean get() { loadedReads++; return loaded }

    override fun show(activity: Activity, onClosed: () -> Unit) {
        shown += SystemClock.elapsedRealtime()
        policy.onShown()
        pendingClose = onClosed
    }

    fun close() {
        pendingClose?.invoke()
        pendingClose = null
    }

    /** Pretend [n] counted matches finished earlier and the last ad was [minutesAgo] minutes ago. */
    fun primeCap(matches: Int, minutesAgo: Long) {
        repeat(matches) { policy.onMatchFinished(FinishedMatchKind.COMPUTER, EndReason.SOLVED) }
        skewMillis += minutesAgo * 60_000
    }
}

class QaContainer(
    val ageStore: FakeAgeStore = FakeAgeStore(),
    val accountStore: FakeAccountStore = FakeAccountStore(),
    val accountApi: AccountApi = FakeAccountApi(),
    val client: FakeOnlineClient = FakeOnlineClient(),
    val net: QaNetwork = QaNetwork(true),
    override val ads: RecordingAds = RecordingAds(),
    override val billing: BillingRepository = FakeBillingRepository(),
    override val offlineGame: OfflineGame = OfflineGame(),
    override val settingsRepository: SettingsRepository = FakeSettingsRepository(),
    minimumAge: Int = 14,
    year: Int = 2026,
) : AppContainer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    override val config = AppConfig(
        serverUrl = "https://nb.example.com",
        versionName = "qa",
        contactEmail = "support@example.com",
        minimumAge = minimumAge,
        maxAdContentRating = "G",
        adsEnabled = false,
        audioEnabled = false,
        bannerAdUnitId = "",
        interstitialAdUnitId = "",
    )
    override val ageRepository = AgeRepository(ageStore, { null }, minimumAge, scope, currentYear = { year })
    override val accountRepository = AccountRepository(accountStore, accountApi, scope)
    override val languageSettings: LanguageSettings = FakeLanguageSettings()
    override val onlineClient = client
    override val onlineSession = OnlineSession(client, scope)
    override val safety = SafetyRepository(FakeSafetyApi(), accountRepository, onlineSession)
    override val audio: GameAudio = SilentGameAudio
    override val network: NetworkMonitor = net
}

/** Robolectric application (`@Config(application = QaApp::class)`): tests configure [QaContainer] before launching. */
class QaApp : NumBaseballApp() {
    override fun createContainer(): AppContainer = QaContainer()
    override fun observeProcessLifecycle() = Unit
}

fun qaApp(): QaApp = ApplicationProvider.getApplicationContext()

/**
 * The same composition locals as `MainActivity.onCreate`, plus a banner stand-in (`LocalAdPreview`) so the banner
 * container (`adBanner` tag) is visible to the tests.
 */
fun ComposeContentTestRule.setQaApp(container: QaContainer, banner: Boolean = true) {
    setContent { QaAppContent(container, banner) }
}

@Composable
fun QaAppContent(container: QaContainer, banner: Boolean = true) {
    NumBaseballTheme {
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalAdsController provides container.ads,
            LocalBilling provides container.billing,
            LocalGameAudio provides container.audio,
            LocalAdPreview provides if (banner) ({ Box(Modifier.size(320.dp, 50.dp)) }) else null,
        ) {
            NbApp(container.ageRepository, container.accountRepository)
        }
    }
}


