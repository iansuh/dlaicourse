package app.numbaseball

import app.numbaseball.ads.AdsController
import app.numbaseball.ads.DisabledAdsController
import app.numbaseball.audio.GameAudio
import app.numbaseball.audio.SilentGameAudio
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.age.AgeRepository
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.online.SafetyRepository
import app.numbaseball.game.offline.OfflineGame
import app.numbaseball.testing.FakeAccountApi
import app.numbaseball.testing.FakeAccountStore
import app.numbaseball.testing.FakeAgeStore
import app.numbaseball.testing.FakeLanguageSettings
import app.numbaseball.testing.FakeOnlineClient
import app.numbaseball.testing.FakeSafetyApi
import app.numbaseball.testing.FakeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Robolectric application (robolectric.properties): fakes only, no ads, no audio, no network, no process observer. */
class TestNumBaseballApp : NumBaseballApp() {
    override fun createContainer(): AppContainer = TestAppContainer()
    override fun observeProcessLifecycle() = Unit
}

class TestAppContainer(
    val ageStore: FakeAgeStore = FakeAgeStore(),
    val accountStore: FakeAccountStore = FakeAccountStore(),
    val client: FakeOnlineClient = FakeOnlineClient(),
) : AppContainer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    override val config = AppConfig(
        serverUrl = "https://nb.example.com",
        versionName = "test",
        contactEmail = "support@example.com",
        minimumAge = 14,
        maxAdContentRating = "G",
        adsEnabled = false,
        audioEnabled = false,
        bannerAdUnitId = "",
        interstitialAdUnitId = "",
    )
    override val ageRepository = AgeRepository(ageStore, { null }, minimumAge = 14, scope, currentYear = { 2026 })
    override val accountRepository = AccountRepository(accountStore, FakeAccountApi(), scope)
    override val settingsRepository = FakeSettingsRepository()
    override val languageSettings = FakeLanguageSettings()
    override val onlineClient = client
    override val onlineSession = OnlineSession(client, scope)
    override val safety = SafetyRepository(FakeSafetyApi(), accountRepository, onlineSession)
    override val offlineGame = OfflineGame()
    override val ads: AdsController = DisabledAdsController
    override val audio: GameAudio = SilentGameAudio
}
