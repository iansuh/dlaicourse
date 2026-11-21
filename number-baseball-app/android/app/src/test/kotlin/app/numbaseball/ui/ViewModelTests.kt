package app.numbaseball.ui

import app.numbaseball.ads.ComputerMatchFinishedListener
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.Chat
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.MatchResult
import app.numbaseball.core.protocol.Phrase
import app.numbaseball.core.protocol.ReportReason
import app.numbaseball.core.protocol.RoomState
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.rules.NumberProblem
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.ApiResult
import app.numbaseball.data.account.NicknameProblem
import app.numbaseball.data.age.AgeBand
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
import app.numbaseball.testing.MainDispatcherRule
import app.numbaseball.testing.SampleData
import app.numbaseball.ui.age.AgeViewModel
import app.numbaseball.ui.chat.ChatViewModel
import app.numbaseball.ui.firstrun.FirstRunViewModel
import app.numbaseball.ui.home.HomeViewModel
import app.numbaseball.ui.home.RoomCodes
import app.numbaseball.ui.match.ComputerMatchViewModel
import app.numbaseball.ui.match.LocalInput
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchPhase
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.match.toMatchUiState
import app.numbaseball.ui.quickmatch.QuickMatchViewModel
import app.numbaseball.ui.report.ReportTarget
import app.numbaseball.ui.report.ReportViewModel
import app.numbaseball.ui.settings.CodeKind
import app.numbaseball.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** ViewModel tests on the JVM with fakes; `Dispatchers.Main` is an unconfined test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTests {
    @get:Rule val main = MainDispatcherRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val api = FakeAccountApi()

    private fun accounts(store: FakeAccountStore = FakeAccountStore()) = AccountRepository(store, api, scope)
    private fun ages(band: AgeBand? = AgeBand.ADULT) = AgeRepository(FakeAgeStore(band), { null }, 14, scope, currentYear = { 2026 })

    @Test fun firstRunValidatesLocallyThenRegisters() = runTest {
        val vm = FirstRunViewModel(accounts())
        vm.onNicknameChange("a")
        vm.onSubmit()
        assertEquals(NicknameProblem.TOO_SHORT, vm.state.value.nicknameProblem)
        assertTrue(api.createdNicknames.isEmpty())

        vm.onNicknameChange("Mina")
        vm.onSubmit()
        assertTrue(vm.state.value.done)
        assertEquals(listOf("Mina"), api.createdNicknames)
    }

    @Test fun firstRunShowsServerRejections() = runTest {
        api.createResult = ApiResult.Failure(409, "nickname_unavailable")
        val vm = FirstRunViewModel(accounts())
        vm.onNicknameChange("Mina")
        vm.onSubmit()
        assertFalse(vm.state.value.done)
        assertEquals(app.numbaseball.ui.common.UiError.Code("nickname_unavailable"), vm.state.value.error)
    }

    @Test fun computerMatchKeypadRulesAndForfeit() = runBlocking {
        var finished = 0
        val vm = ComputerMatchViewModel(3, ComputerLevel.NORMAL, practice = false, OfflineGame(Dispatchers.Unconfined), ComputerMatchFinishedListener { finished++ })
        "1123".forEach(vm::onDigit)
        assertEquals("123", vm.state.value.input)
        vm.onDelete()
        vm.onSubmit()
        assertEquals(NumberProblem.WRONG_LENGTH, vm.state.value.inputProblem)
        vm.onDigit('0')
        vm.onSubmit()
        vm.state.first { it.phase == MatchPhase.GUESSING }
        assertEquals("120", vm.state.value.mySecret)
        assertEquals(1, vm.state.value.round)

        vm.onForfeit()
        val summary = vm.state.first { it.summary != null }.summary!!
        assertEquals(Outcome.LOSE, summary.outcome)
        assertEquals(EndReason.FORFEIT, summary.reason)
        assertEquals(MatchMode.COMPUTER, summary.mode)
        assertEquals(1, finished)
    }

    @Test fun practiceMatchesDoNotCountForInterstitials() = runBlocking {
        var finished = 0
        val vm = ComputerMatchViewModel(3, ComputerLevel.EASY, practice = true, OfflineGame(Dispatchers.Unconfined), ComputerMatchFinishedListener { finished++ })
        vm.onRandomSecret()
        vm.onSubmit()
        vm.state.first { it.phase == MatchPhase.GUESSING }
        vm.onForfeit()
        vm.state.first { it.summary != null }
        assertEquals(0, finished)
    }

    @Test fun onlineSnapshotMapsToTheMatchScreen() {
        val guessing = SampleData.quickRoom().toMatchUiState(LocalInput(text = "98", round = 3), serverNow = 1_795_000_030_000)!!
        assertEquals(MatchPhase.GUESSING, guessing.phase)
        assertEquals("98", guessing.input)
        assertEquals(15, guessing.secondsLeft)
        assertEquals(2, guessing.opponentRounds.size)

        val stale = SampleData.quickRoom().toMatchUiState(LocalInput(text = "98", round = 2), serverNow = 0)!!
        assertEquals("", stale.input)

        val waiting = SampleData.quickRoom(SampleData.duel(pending = "123")).toMatchUiState(LocalInput(), 0)!!
        assertEquals(MatchPhase.WAITING_FOR_OPPONENT_GUESS, waiting.phase)
        val choosing = SampleData.quickRoom(SampleData.duel(phase = DuelPhase.CHOOSING, round = 0, mySecret = null)).toMatchUiState(LocalInput(), 0)!!
        assertEquals(MatchPhase.CHOOSING_SECRET, choosing.phase)

        val won = SampleData.quickRoom(SampleData.duel(phase = DuelPhase.FINISHED, result = DuelResult(Team.A, EndReason.SOLVED)))
            .toMatchUiState(LocalInput(), 0)!!
        assertEquals(Outcome.WIN, won.summary?.outcome)
        assertEquals("719", won.summary?.opponentSecret)
    }

    @Test fun teamDuelOverWaitsForTheMatchResult() {
        val room = SampleData.quickRoom(SampleData.duel(phase = DuelPhase.FINISHED, result = DuelResult(Team.B, EndReason.TIMEOUT)))
            .let { it.copy(settings = it.settings.copy(teamSize = 2)) }
        assertNull(room.toMatchUiState(LocalInput(), 0)!!.summary)
        val decided = room.copy(match = room.match!!.copy(result = MatchResult(Team.A, decidedEarly = false)))
        val summary = decided.toMatchUiState(LocalInput(), 0)!!.summary!!
        assertTrue(summary.teamMatch)
        assertEquals(Outcome.WIN, summary.outcome)
    }

    @Test fun quickMatchSearchesAndNoticesTheQuickRoom() = runTest {
        val client = FakeOnlineClient()
        val session = OnlineSession(client, backgroundScope)
        val vm = QuickMatchViewModel(session)
        backgroundScope.launch { vm.state.collect {} }
        assertEquals(1, client.users)
        vm.onDigitsChange(5)
        vm.onSearch()
        assertTrue(vm.state.value.searching)
        assertEquals(app.numbaseball.core.protocol.QueueJoin(5), client.sent.last())
        client.receive(RoomState(SampleData.quickRoom()))
        assertTrue(vm.state.value.matchFound)
    }

    @Test fun chatIsPhrasesOnlyInQuickRoomsAndStripsEmoji() = runTest {
        val client = FakeOnlineClient()
        val session = OnlineSession(client, backgroundScope)
        val vm = ChatViewModel(session, FakeSettingsRepository())
        backgroundScope.launch { vm.state.collect {} }
        client.receive(RoomState(SampleData.quickRoom()))
        assertTrue(vm.state.value.phrasesOnly)
        vm.onInputChange("hi")
        vm.onSend()
        assertTrue(client.sent.none { it is Chat })
        vm.onPhrase("good_luck")
        assertEquals("good_luck", (client.sent.last() as Phrase).phraseId)

        client.receive(RoomState(SampleData.privateLobby()))
        assertFalse(vm.state.value.phrasesOnly)
        vm.onInputChange("hi 😀 there")
        assertEquals("hi  there", vm.state.value.input)
        vm.onSend()
        assertEquals("hi  there", (client.sent.last() as Chat).text)
        assertEquals("", vm.state.value.input)
    }

    @Test fun chatShowsServerErrors() = runTest {
        val client = FakeOnlineClient().apply { errorFor = { ErrorMessage("chat_rate_limited", "slow down", it.rid, 2_000) } }
        val session = OnlineSession(client, backgroundScope)
        val vm = ChatViewModel(session, FakeSettingsRepository())
        backgroundScope.launch { vm.state.collect {} }
        client.receive(RoomState(SampleData.privateLobby()))
        vm.onInputChange("hello")
        vm.onSend()
        assertEquals(app.numbaseball.ui.common.UiError.Code("chat_rate_limited"), vm.state.value.error)
        assertEquals("hello", vm.state.value.input)
    }

    @Test fun homeNormalizesRoomCodesAndFollowsAgeSignals() = runTest {
        assertEquals("K7QX2", RoomCodes.normalize("k7-qx 2!9"))
        val vm = HomeViewModel(accounts(FakeAccountStore.registered()), ages())
        backgroundScope.launch { vm.state.collect {} }
        vm.onRoomCodeChange("k7qx2")
        assertTrue(vm.state.value.roomCodeComplete)
        assertEquals("Mina#0420", vm.state.value.displayName)
        assertTrue(vm.state.value.onlineAllowed)
    }

    @Test fun homeOffersANewNicknameWhenTheOfflineOneIsRejected() = runTest {
        api.createResult = ApiResult.Failure(400, "nickname_profanity")
        val store = FakeAccountStore().apply { value.value = value.value.copy(nickname = "Mina") }
        val vm = HomeViewModel(accounts(store), ages())
        backgroundScope.launch { vm.state.collect {} }
        assertTrue(vm.state.value.nicknameRejected)
        assertFalse(vm.state.value.registered)
    }

    @Test fun settingsShowCodesWithDashesAndDeleteNeedsConfirmation() = runTest {
        val language = FakeLanguageSettings()
        val settings = FakeSettingsRepository()
        val vm = SettingsViewModel(settings, accounts(FakeAccountStore.registered()), language)
        backgroundScope.launch { vm.state.collect {} }
        vm.onTransferCode()
        assertEquals(CodeKind.TRANSFER, vm.state.value.code?.kind)
        assertEquals("ABCD-2345-EFGH", vm.state.value.code?.code)
        vm.onSettingsChange { it.copy(musicEnabled = true, followSilentMode = false) }
        assertTrue(settings.value.value.musicEnabled)
        vm.onLanguageChange("ko")
        assertEquals("ko", language.tag)

        vm.onDeleteRequest()
        assertTrue(vm.state.value.confirmDelete)
        vm.onDeleteConfirm()
        assertTrue(vm.state.value.deleted)
    }

    @Test fun reportThenOfferBlock() = runTest {
        val safetyApi = FakeSafetyApi()
        val client = FakeOnlineClient()
        val repo = accounts(FakeAccountStore.registered())
        val safety = SafetyRepository(safetyApi, repo, OnlineSession(client, backgroundScope))
        val vm = ReportViewModel(ReportTarget(SampleData.OPPONENT, "Leo#1234", "r_8f2k", 3), safety)
        vm.onSubmit()
        assertTrue(safetyApi.reports.isEmpty())
        vm.onReasonChange(ReportReason.HARASSMENT)
        vm.onNoteChange("x".repeat(600))
        vm.onSubmit()
        assertTrue(vm.state.value.reported)
        assertEquals(500, safetyApi.reports.single().note?.length)
        assertEquals(3L, safetyApi.reports.single().messageId)
        assertFalse(vm.state.value.blocked)
        vm.onBlock()
        assertTrue(vm.state.value.blocked)
        assertEquals(listOf(SampleData.OPPONENT), safetyApi.blocked)
    }

    @Test fun ageScreenStoresOnlyTheBand() = runTest {
        val store = FakeAgeStore()
        val repo = AgeRepository(store, { null }, 14, backgroundScope, currentYear = { 2026 })
        val vm = AgeViewModel(repo)
        assertEquals(2026, vm.state.value.years.first())
        assertNull(vm.state.value.selectedYear)
        vm.onContinue()
        assertNull(store.value.value)
        vm.onYearSelected(2012)
        vm.onContinue()
        assertEquals(AgeBand.UNDER_MINIMUM, store.value.value)
    }

    @Test fun chatLinesFromProtocol() {
        assertTrue(SampleData.chat().any { it.kind == ChatKind.PHRASE })
    }
}
