package app.numbaseball.ui

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
import app.numbaseball.data.online.AlwaysOnlineNetworkMonitor
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.settings.AppSettings
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
import app.numbaseball.ui.match.LocalMatchState
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchPhase
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.match.toMatchUiState
import app.numbaseball.ui.quickmatch.QuickMatchViewModel
import app.numbaseball.ui.report.ReportChoice
import app.numbaseball.ui.report.ReportTarget
import app.numbaseball.ui.report.ReportViewModel
import app.numbaseball.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test fun firstRunValidatesLocallyThenRegisters() = runTest(main.dispatcher) {
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

    @Test fun firstRunShowsServerRejections() = runTest(main.dispatcher) {
        api.createResult = ApiResult.Failure(409, "nickname_unavailable")
        val vm = FirstRunViewModel(accounts())
        vm.onNicknameChange("Mina")
        vm.onSubmit()
        assertFalse(vm.state.value.done)
        assertEquals(app.numbaseball.ui.common.UiError.Code("nickname_unavailable"), vm.state.value.error)
    }

    @Test fun computerMatchKeypadRulesAndForfeit() = runBlocking {
        val vm = ComputerMatchViewModel(3, ComputerLevel.NORMAL, practice = false, OfflineGame(Dispatchers.Unconfined))
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
    }

    @Test fun onlineSnapshotMapsToTheMatchScreen() {
        val guessing = SampleData.quickRoom().toMatchUiState(LocalMatchState(LocalInput(text = "98", round = 3)), serverNow = 1_795_000_030_000)!!
        assertEquals(MatchPhase.GUESSING, guessing.phase)
        assertEquals("98", guessing.input)
        assertEquals(15, guessing.secondsLeft)
        assertEquals(2, guessing.opponentRounds.size)

        val stale = SampleData.quickRoom().toMatchUiState(LocalMatchState(LocalInput(text = "98", round = 2)), serverNow = 0)!!
        assertEquals("", stale.input)

        val waiting = SampleData.quickRoom(SampleData.duel(pending = "123")).toMatchUiState(LocalMatchState(), 0)!!
        assertEquals(MatchPhase.WAITING_FOR_OPPONENT_GUESS, waiting.phase)
        val choosing = SampleData.quickRoom(SampleData.duel(phase = DuelPhase.CHOOSING, round = 0, mySecret = null)).toMatchUiState(LocalMatchState(), 0)!!
        assertEquals(MatchPhase.CHOOSING_SECRET, choosing.phase)

        val won = SampleData.quickRoom(SampleData.duel(phase = DuelPhase.FINISHED, result = DuelResult(Team.A, EndReason.SOLVED)))
            .toMatchUiState(LocalMatchState(), 0)!!
        assertEquals(Outcome.WIN, won.summary?.outcome)
        assertEquals("719", won.summary?.opponentSecret)
    }

    @Test fun teamDuelOverWaitsForTheMatchResult() {
        val room = SampleData.quickRoom(SampleData.duel(phase = DuelPhase.FINISHED, result = DuelResult(Team.B, EndReason.TIMEOUT)))
            .let { it.copy(settings = it.settings.copy(teamSize = 2)) }
        assertNull(room.toMatchUiState(LocalMatchState(), 0)!!.summary)
        val decided = room.copy(match = room.match!!.copy(result = MatchResult(Team.A, decidedEarly = false)))
        val summary = decided.toMatchUiState(LocalMatchState(), 0)!!.summary!!
        assertTrue(summary.teamMatch)
        assertEquals(Outcome.WIN, summary.outcome)
    }

    @Test fun quickMatchSearchesAndNoticesTheQuickRoom() = runTest(main.dispatcher) {
        val client = FakeOnlineClient()
        val session = OnlineSession(client, backgroundScope)
        val settings = FakeSettingsRepository(AppSettings(quickDigits = 4))
        val vm = QuickMatchViewModel(session, settings, AlwaysOnlineNetworkMonitor(), clock = { 0L })
        backgroundScope.launch { vm.state.collect {} }
        assertEquals(1, client.users)
        // Opening the screen searches with the last used count (04).
        assertEquals(4, vm.state.value.digits)
        assertEquals(app.numbaseball.core.protocol.QueueJoin(4), client.sent.last())
        // Changing the count restarts the search at once and is remembered.
        vm.onDigitsChange(5)
        assertEquals(app.numbaseball.core.protocol.QueueJoin(5), client.sent.last())
        assertEquals(5, settings.value.value.quickDigits)
        client.receive(RoomState(SampleData.quickRoom()))
        assertEquals("Leo", vm.state.value.found?.nickname)
    }

    @Test fun chatIsPhrasesOnlyInQuickRoomsAndStripsEmoji() = runTest(main.dispatcher) {
        val client = FakeOnlineClient()
        val session = OnlineSession(client, backgroundScope)
        val vm = ChatViewModel(session, FakeSettingsRepository(), accounts(FakeAccountStore.registered()), MutableStateFlow(null))
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

    @Test fun chatShowsServerErrors() = runTest(main.dispatcher) {
        val client = FakeOnlineClient().apply { errorFor = { ErrorMessage("chat_rate_limited", "slow down", it.rid, 2_000) } }
        val session = OnlineSession(client, backgroundScope)
        val vm = ChatViewModel(session, FakeSettingsRepository(), accounts(FakeAccountStore.registered()), MutableStateFlow(null))
        backgroundScope.launch { vm.state.collect {} }
        client.receive(RoomState(SampleData.privateLobby()))
        vm.onInputChange("hello")
        vm.onSend()
        assertEquals(app.numbaseball.ui.common.UiError.Code("chat_rate_limited"), vm.state.value.error)
        assertEquals("hello", vm.state.value.input)
    }

    @Test fun homeNormalizesRoomCodesAndFollowsAgeSignals() = runTest(main.dispatcher) {
        assertEquals("K7QX2", RoomCodes.normalize("k7-qx 2!9"))
        val settings = FakeSettingsRepository()
        val vm = HomeViewModel(accounts(FakeAccountStore.registered()), ages(), settings, AlwaysOnlineNetworkMonitor())
        backgroundScope.launch { vm.state.collect {} }
        assertEquals("Mina", vm.state.value.nickname)
        assertEquals("0420", vm.state.value.tag)
        assertTrue(vm.state.value.onlineAllowed)
        assertTrue(vm.state.value.canPlayOnline)
        vm.onQuickDigits(6)
        assertEquals(6, settings.value.value.quickDigits)
        assertEquals(6, vm.state.value.quickDigits)
    }

    @Test fun homeOffersANewNicknameWhenTheOfflineOneIsRejected() = runTest(main.dispatcher) {
        api.createResult = ApiResult.Failure(400, "nickname_profanity")
        val store = FakeAccountStore().apply { value.value = value.value.copy(nickname = "Mina") }
        val vm = HomeViewModel(accounts(store), ages(), FakeSettingsRepository(), AlwaysOnlineNetworkMonitor())
        backgroundScope.launch { vm.state.collect {} }
        assertTrue(vm.state.value.nicknameRejected)
        assertFalse(vm.state.value.registered)
        assertFalse(vm.state.value.canPlayOnline)
    }

    @Test fun settingsShowCodesWithDashesAndDeleteNeedsConfirmation() = runTest(main.dispatcher) {
        val language = FakeLanguageSettings()
        val settings = FakeSettingsRepository()
        val repo = accounts(FakeAccountStore.registered())
        val safety = SafetyRepository(FakeSafetyApi(), repo, OnlineSession(FakeOnlineClient(), backgroundScope))
        val vm = SettingsViewModel(settings, repo, language, safety, clock = { 1_000L })
        backgroundScope.launch { vm.state.collect {} }
        vm.onTransferCode()
        assertEquals("ABCD2345EFGH", vm.state.value.transferCode?.code)
        assertEquals("ABCD-2345-EFGH", SettingsViewModel.formatCode(vm.state.value.transferCode!!.code))
        // No expiry from the server: 10 minutes from now.
        assertEquals(1_000L + SettingsViewModel.TRANSFER_MS, vm.state.value.transferCode?.expiresAt)
        vm.onSettingsChange { it.copy(musicEnabled = true, followSilentMode = false) }
        assertTrue(settings.value.value.musicEnabled)
        vm.onLanguageChange("ko")
        assertEquals("ko", language.tag)

        vm.onRename("Mina2")
        assertTrue(vm.state.value.renamed)
        assertEquals("Mina2", vm.state.value.nickname)
        vm.onDeleteConfirm()
        assertTrue(vm.state.value.deleted)
    }

    @Test fun reportThenOfferBlock() = runTest(main.dispatcher) {
        val safetyApi = FakeSafetyApi()
        val client = FakeOnlineClient()
        val repo = accounts(FakeAccountStore.registered())
        val safety = SafetyRepository(safetyApi, repo, OnlineSession(client, backgroundScope))
        val vm = ReportViewModel(ReportTarget(SampleData.OPPONENT, "Leo", "1234", roomId = "r_8f2k", messageId = 3), safety)
        vm.onSubmit()
        assertTrue(safetyApi.reports.isEmpty())
        vm.onChoice(ReportChoice.HARASSMENT)
        vm.onNoteChange("x".repeat(600))
        vm.onSubmit()
        assertTrue(vm.state.value.reported)
        assertEquals(ReportReason.HARASSMENT, safetyApi.reports.single().reason)
        // Design 12 §3: notes up to 200 characters.
        assertEquals(200, safetyApi.reports.single().note?.length)
        assertEquals(3L, safetyApi.reports.single().messageId)
        assertFalse(vm.state.value.blocked)
        vm.onBlock()
        assertTrue(vm.state.value.blocked)
        assertEquals(listOf(SampleData.OPPONENT), safetyApi.blocked)
        assertEquals(listOf(SampleData.OPPONENT), safety.blocked.value.map { it.id })
    }

    @Test fun designOnlyReasonsTravelAsOtherWithAMarker() = runTest(main.dispatcher) {
        val safetyApi = FakeSafetyApi()
        val repo = accounts(FakeAccountStore.registered())
        val safety = SafetyRepository(safetyApi, repo, OnlineSession(FakeOnlineClient(), backgroundScope))
        val vm = ReportViewModel(ReportTarget(SampleData.OPPONENT, "Leo", "1234"), safety)
        vm.onChoice(ReportChoice.CHILD_SAFETY)
        vm.onNoteChange("details")
        vm.onSubmit()
        assertEquals(ReportReason.OTHER, safetyApi.reports.single().reason)
        assertEquals("[child_safety] details", safetyApi.reports.single().note)
    }

    @Test fun ageScreenStoresOnlyTheBand() = runTest(main.dispatcher) {
        val store = FakeAgeStore()
        val repo = AgeRepository(store, { null }, 14, backgroundScope, currentYear = { 2026 })
        val vm = AgeViewModel(repo)
        assertEquals("", vm.state.value.year)
        vm.onConfirm()
        assertNull(store.value.value)
        // Out of range (01 §0: 1900 to the current year): error state, nothing stored, next key starts over.
        "2030".forEach(vm::onDigit)
        vm.onConfirm()
        assertTrue(vm.state.value.invalid)
        assertNull(store.value.value)
        vm.onDigit('2')
        assertEquals("2", vm.state.value.year)
        // Repeated digits are allowed here.
        "013".forEach(vm::onDigit)
        vm.onConfirm()
        assertEquals(AgeBand.UNDER_MINIMUM, store.value.value)
    }

    @Test fun chatLinesFromProtocol() {
        assertTrue(SampleData.chat().any { it.kind == ChatKind.PHRASE })
    }
}
