package app.numbaseball.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.BlockedPlayer
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.ChatMessage
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.DuelSummary
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.MatchResult
import app.numbaseball.core.protocol.MatchView
import app.numbaseball.core.protocol.MyDuelView
import app.numbaseball.core.protocol.PlayerView
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.protocol.Score
import app.numbaseball.core.protocol.Stats
import app.numbaseball.core.protocol.Team
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.ui.age.AgeScreen
import app.numbaseball.ui.age.AgeUiState
import app.numbaseball.ui.age.NotAvailableScreen
import app.numbaseball.ui.chat.ChatSheet
import app.numbaseball.ui.chat.ChatUiState
import app.numbaseball.ui.chatActions
import app.numbaseball.ui.design.LocalReduceMotion
import app.numbaseball.ui.design.MemoMark
import app.numbaseball.ui.design.SheetFrame
import app.numbaseball.ui.firstrun.FirstRunScreen
import app.numbaseball.ui.firstrun.FirstRunUiState
import app.numbaseball.ui.home.HomeScreen
import app.numbaseball.ui.home.HomeUiState
import app.numbaseball.ui.legal.LegalBlock
import app.numbaseball.ui.legal.LegalTextScreen
import app.numbaseball.ui.legal.legalPage
import app.numbaseball.ui.lobby.JoinScreen
import app.numbaseball.ui.lobby.JoinUiState
import app.numbaseball.ui.lobby.LobbyActions
import app.numbaseball.ui.lobby.LobbyPlayer
import app.numbaseball.ui.lobby.LobbyUiState
import app.numbaseball.ui.lobby.RoomLobbyScreen
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchPhase
import app.numbaseball.ui.match.MatchScreen
import app.numbaseball.ui.match.MatchSummary
import app.numbaseball.ui.match.MatchUiState
import app.numbaseball.ui.match.MemoState
import app.numbaseball.ui.match.Opponent
import app.numbaseball.ui.match.OpponentStatus
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.matchActions
import app.numbaseball.ui.profile.ProfileScreen
import app.numbaseball.ui.profile.ProfileUiState
import app.numbaseball.ui.quickmatch.FoundOpponent
import app.numbaseball.ui.quickmatch.QuickMatchScreen
import app.numbaseball.ui.quickmatch.QuickMatchUiState
import app.numbaseball.ui.report.PlayerCard
import app.numbaseball.ui.report.ReportChoice
import app.numbaseball.ui.report.ReportSheet
import app.numbaseball.ui.report.ReportTarget
import app.numbaseball.ui.report.ReportUiState
import app.numbaseball.ui.results.ResultsScreen
import app.numbaseball.ui.rules.HowToPlayScreen
import app.numbaseball.ui.settings.BlockedPlayersScreen
import app.numbaseball.ui.settings.SettingsActions
import app.numbaseball.ui.settings.SettingsScreen
import app.numbaseball.ui.settings.SettingsUiState
import app.numbaseball.ui.team.BoardMode
import app.numbaseball.ui.team.TeamBoardScreen
import app.numbaseball.ui.terms.TermsGateScreen
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NumBaseballTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sample data matching design/shots, so renders can be compared side by side with the reference PNGs. */
private object Design {
    const val ME = "me000000000a"
    val me = PlayerView(ME, "새벽투수", "4821", Team.A, connected = true, left = false)
    val kestrel = PlayerView("kestrel00000", "Kestrel", "2048", Team.A, connected = true, left = false)
    val mint = PlayerView("mint00000000", "민트초코", "0912", Team.B, connected = true, left = false)
    val gross = PlayerView("gross0000000", "Größenwahn", "7731", Team.B, connected = true, left = false)
    val night = PlayerView("night0000000", "밤하늘", "5530", Team.A, connected = true, left = false)
    val volna = PlayerView("volna0000000", "Volna", "1187", Team.B, connected = false, left = false)

    fun lobbyRoom() = RoomView(
        id = "r_lobby", kind = RoomKind.PRIVATE, code = "K7Q2M", phase = RoomPhase.LOBBY,
        settings = RoomSettings(digits = 4, teamSize = 3, roundSeconds = 60), hostId = ME, youId = ME,
        players = listOf(me, kestrel, mint, gross), match = null, version = 1, serverTime = 0,
    )

    fun lobbyState() = LobbyUiState(
        room = lobbyRoom(),
        players = lobbyRoom().players.map { LobbyPlayer(it.id, it.nickname, it.tag, it.team, host = it.id == ME, you = it.id == ME, connected = true) },
        connection = ConnectionState.Idle,
        chat = listOf(ChatMessage(7, 0, mint.id, mint.nickname, mint.tag, Team.B, ChatKind.TEXT, "잘 부탁해요!", null)),
        chatReadUpTo = -1,
    )

    private fun myDuel(finished: Boolean) = MyDuelView(
        index = 0, myTeam = Team.A, opponentId = mint.id,
        phase = if (finished) DuelPhase.FINISHED else DuelPhase.GUESSING, round = 7, deadline = null, mySecret = "0472",
        opponentHasSecret = true, myPendingGuess = null, opponentGuessed = true,
        myRounds = listOf(RoundEntry(1, "1234", 0, 2), RoundEntry(2, "5678", 1, 0), RoundEntry(3, "3169", 1, 3), RoundEntry(4, "3961", 2, 2), RoundEntry(5, "3912", 3, 0), RoundEntry(6, "3916", 4, 0)),
        opponentRounds = listOf(RoundEntry(1, "0123", 1, 1), RoundEntry(2, "4567", 0, 2), RoundEntry(3, "0789", 2, 1)),
        myMisses = 0, opponentMisses = 0, result = if (finished) DuelResult(Team.A, EndReason.SOLVED) else null, opponentSecret = null,
    )

    fun teamRoom(final: Boolean) = RoomView(
        id = "r_team", kind = RoomKind.PRIVATE, code = "K7Q2M", phase = RoomPhase.PLAYING,
        settings = RoomSettings(digits = 4, teamSize = 3, roundSeconds = 60), hostId = ME, youId = ME,
        players = listOf(me, kestrel, night, mint, gross, volna.copy(connected = final)),
        match = MatchView(
            number = 1,
            duels = listOf(
                DuelSummary(0, ME, mint.id, DuelPhase.FINISHED, 7, DuelResult(Team.A, EndReason.SOLVED)),
                if (final) DuelSummary(1, kestrel.id, gross.id, DuelPhase.FINISHED, 9, DuelResult(Team.B, EndReason.SOLVED)) else DuelSummary(1, kestrel.id, gross.id, DuelPhase.GUESSING, 5, null),
                if (final) DuelSummary(2, night.id, volna.id, DuelPhase.FINISHED, 6, DuelResult(Team.A, EndReason.SOLVED)) else DuelSummary(2, night.id, volna.id, DuelPhase.GUESSING, 4, null),
            ),
            score = if (final) Score(2, 1) else Score(1, 0),
            result = if (final) MatchResult(Team.A, decidedEarly = false) else null,
            myDuel = myDuel(finished = true),
        ),
        version = 9, serverTime = 0,
    )

    val chat = listOf(
        ChatMessage(1, 0, mint.id, mint.nickname, mint.tag, Team.B, ChatKind.TEXT, "잘 부탁해요!", null),
        ChatMessage(2, 0, kestrel.id, kestrel.nickname, kestrel.tag, Team.A, ChatKind.TEXT, "몇 자리로 할까요?", null),
        ChatMessage(3, 0, gross.id, gross.nickname, gross.tag, Team.B, ChatKind.TEXT, "그건 좀 *** 아닌가", null),
        ChatMessage(4, 0, ME, me.nickname, me.tag, Team.A, ChatKind.TEXT, "4자리 좋아요", null),
    )

    /** match-guess-ko: round 4, three rows (one OUT), memo with digit and cell marks reflected on the keypad. */
    fun guessState(digits: Int = 4): MatchUiState {
        val g = { text: String -> text.take(digits).padEnd(digits, '8').let { if (digits > 4) it.replaceRange(4, digits, "07".take(digits - 4)) else it } }
        val marks = listOf(MemoMark.OUT, MemoMark.IN, MemoMark.OUT, MemoMark.IN, MemoMark.OUT, MemoMark.OUT, MemoMark.IN, MemoMark.OUT, MemoMark.OUT, MemoMark.IN)
        val cells = List(digits) { MutableList(10) { MemoMark.NONE } }.also {
            it[0][1] = MemoMark.OUT; it[0][3] = MemoMark.IN; it[1][1] = MemoMark.OUT; it[2][3] = MemoMark.OUT; it[3][9] = MemoMark.OUT
        }
        return MatchUiState(
            digits = digits,
            opponent = Opponent.Player("Kestrel", "2048"),
            mode = MatchMode.QUICK,
            phase = MatchPhase.GUESSING,
            input = "39",
            mySecret = "047215".take(digits),
            round = 4,
            myRounds = listOf(RoundEntry(1, g("1234"), 0, 2), RoundEntry(2, g("5780"), 0, 0), RoundEntry(3, g("3196"), 2, 2)),
            opponentRounds = listOf(RoundEntry(1, "1234", 1, 0), RoundEntry(2, "1243", 1, 1), RoundEntry(3, "0412", 1, 3)),
            secondsLeft = 42,
            roundSeconds = 60,
            opponentStatus = OpponentStatus.Pitched,
            memo = MemoState(digits = digits, digitMarks = marks, cells = cells),
        )
    }
}

/**
 * Every screen with sample state. Recorded locally (`./gradlew :app:recordRoborazziDebug`) into app/screenshots and
 * verified on CI (`verifyRoborazziDebug`). The frame adds the 24 dp status and gesture bars of design/shots, motion is
 * reduced (DESIGN_SYSTEM 8.4: final states, no animation), ads/audio/haptics are the disabled defaults.
 */
abstract class ScreenshotSuite(private val variant: String) {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            NumBaseballTheme {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    Column(Modifier.fillMaxSize().background(NbColors.bg)) {
                        Spacer(Modifier.fillMaxWidth().height(24.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                        Spacer(Modifier.fillMaxWidth().height(24.dp))
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/${name}_$variant.png", roborazziOptions = OPTIONS)
    }

    @Test fun age() = capture("01_age") { AgeScreen(AgeUiState(year = "19"), {}, {}, {}, {}) }

    @Test fun ageError() = capture("01_age_error") { AgeScreen(AgeUiState(year = "2031", invalid = true), {}, {}, {}, {}) }

    @Test fun notAvailable() = capture("02_not_available") { NotAvailableScreen(onPrivacyPolicy = {}) }

    @Test fun nickname() = capture("03_nickname") { FirstRunScreen(FirstRunUiState(nickname = "새벽투수"), {}, {}, {}, {}, {}) }

    @Test fun home() = capture("04_home") {
        HomeScreen(
            HomeUiState(nickname = "새벽투수", tag = "4821", registered = true, stats = Stats(12, 8, 3), recentForm = "WWLWDLWWLW"),
            {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun homeOffline() = capture("04_home_offline") {
        HomeScreen(HomeUiState(nickname = "새벽투수", tag = "4821", registered = true, stats = Stats(0, 0, 0), online = false), {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun search() = capture("05_search") { QuickMatchScreen(QuickMatchUiState(digits = 4, startedAt = 0), {}, {}, {}, now = { 7_000L }) }

    @Test fun searchFound() = capture("05_search_found") {
        QuickMatchScreen(QuickMatchUiState(digits = 4, found = FoundOpponent("Kestrel", "2048")), {}, {}, {})
    }

    @Test fun matchSecret() = capture("06_match_secret") {
        MatchScreen(
            MatchUiState(4, Opponent.Player("Kestrel", "2048"), MatchMode.QUICK, MatchPhase.CHOOSING_SECRET, input = "04", secondsLeft = 52, roundSeconds = 60, opponentStatus = OpponentStatus.Picking),
            matchActions(),
        )
    }

    @Test fun matchGuess() = capture("07_match_guess") { MatchScreen(Design.guessState(), matchActions().copyWithChat()) }

    @Test fun matchSixDigits() = capture("07_match_6digits") { MatchScreen(Design.guessState(6), matchActions()) }

    @Test fun matchComputer() = capture("07_match_cpu") {
        MatchScreen(Design.guessState().copy(opponent = Opponent.Computer(ComputerLevel.NORMAL), mode = MatchMode.COMPUTER, secondsLeft = 600, roundSeconds = 600, opponentStatus = OpponentStatus.Thinking), matchActions())
    }

    @Test fun matchReconnect() = capture("08_match_reconnect") { MatchScreen(Design.guessState().copy(reconnectSeconds = 48), matchActions()) }

    @Test fun resultWin() = capture("09_result_win") {
        ResultsScreen(
            MatchSummary(
                MatchMode.QUICK, Outcome.WIN, EndReason.SOLVED, rounds = 6, opponentSecret = "3916", digits = 4, mySecret = "0472", opponentName = "Kestrel",
                myStrikes = listOf(0, 0, 2, 1, 2, 4), opponentStrikes = listOf(1, 1, 1, 2, 2, 3), durationSeconds = 192,
            ),
            adDue = false, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = {},
        )
    }

    @Test fun resultComputerLose() = capture("09_result_cpu_lose") {
        ResultsScreen(
            MatchSummary(
                MatchMode.COMPUTER, Outcome.LOSE, EndReason.SOLVED, rounds = 6, opponentSecret = "3916", digits = 4, level = ComputerLevel.NORMAL, mySecret = "0472",
                myStrikes = listOf(0, 0, 2, 1, 2, 2), opponentStrikes = listOf(1, 1, 1, 2, 2, 4), durationSeconds = 192,
            ),
            adDue = true, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = null,
        )
    }

    @Test fun lobby() = capture("10_lobby") { RoomLobbyScreen(Design.lobbyState(), lobbyActions()) }

    @Test fun join() = capture("10_join") { JoinScreen(JoinUiState(code = "K7Q"), {}, {}, {}, {}) }

    @Test fun teamBoard() = capture("11_team_board") { TeamBoardScreen(Design.teamRoom(final = false), BoardMode.LIVE, {}, {}) }

    @Test fun teamFinal() = capture("11_team_final") { TeamBoardScreen(Design.teamRoom(final = true), BoardMode.FINAL, {}, {}) }

    @Test fun chat() = capture("12_chat") {
        Box(Modifier.fillMaxSize()) {
            RoomLobbyScreen(Design.lobbyState(), lobbyActions())
            SheetFrame(onDismiss = {}, top = 112.dp) {
                ChatSheet(ChatUiState(roomId = "r", myId = Design.ME, teamRoom = true, messages = Design.chat, phrasesOnly = false), chatActions())
            }
        }
    }

    @Test fun reportCard() = capture("13_report_card") {
        Box(Modifier.fillMaxSize()) {
            TeamBoardScreen(Design.teamRoom(final = false), BoardMode.LIVE, {}, {})
            SheetFrame(onDismiss = {}) {
                PlayerCard(ReportTarget(Design.gross.id, "Größenwahn", "7731", team = Team.B), "B팀 · 빠른 대전 기록", self = false, blocked = false, {}, {}, {})
            }
        }
    }

    @Test fun reportReasons() = capture("13_report_reasons") {
        Box(Modifier.fillMaxSize()) {
            TeamBoardScreen(Design.teamRoom(final = false), BoardMode.LIVE, {}, {})
            SheetFrame(onDismiss = {}) {
                ReportSheet(ReportUiState(ReportTarget(Design.gross.id, "Größenwahn", "7731"), choice = ReportChoice.HARASSMENT), {}, {}, {}, {}, {})
            }
        }
    }

    @Test fun reportDone() = capture("13_report_done") {
        Box(Modifier.fillMaxSize()) {
            TeamBoardScreen(Design.teamRoom(final = false), BoardMode.LIVE, {}, {})
            SheetFrame(onDismiss = {}) {
                ReportSheet(ReportUiState(ReportTarget(Design.gross.id, "Größenwahn", "7731"), choice = ReportChoice.HARASSMENT, reported = true), {}, {}, {}, {}, {})
            }
        }
    }

    @Test fun settings() = capture("14_settings") {
        SettingsScreen(SettingsUiState(registered = true, nickname = "새벽투수", tag = "4821", blockedCount = 2, language = "ko"), true, "1.0.0 (1)", "support@example.com", listOf("ko", "en"), settingsActions())
    }

    @Test fun blockedPlayers() = capture("14_blocked") {
        BlockedPlayersScreen(listOf(BlockedPlayer(Design.gross.id, "Größenwahn", "7731"), BlockedPlayer(Design.volna.id, "Volna", "1187")), {}, {}, {})
    }

    @Test fun profile() = capture("15_profile") {
        ProfileScreen(ProfileUiState(nickname = "새벽투수", tag = "4821", registered = true, stats = Stats(12, 8, 3), recentForm = "WWLWDLWWLW"), {}, {}, {})
    }

    @Test fun terms() = capture("16_terms") {
        TermsGateScreen(updated = false, version = "2026-11-v2", submitting = false, failed = 0, {}, {}, {}, {})
    }

    @Test fun legal() = capture("17_legal") {
        LegalTextScreen(
            title = "이용약관",
            page = legalPage(
                listOf(
                    LegalBlock.Heading("숫자야구: 조용한 한판 이용약관", 1),
                    LegalBlock.Paragraph("버전 2026-11-v2 · 시행일 [시행일]"),
                    LegalBlock.Paragraph("이 약관은 [개발자 표시명](이하 \"운영자\")이 제공하는 숫자야구 게임 앱 **숫자야구: 조용한 한판**(이하 \"앱\")의 이용 조건입니다."),
                    LegalBlock.Heading("1. 이용 자격", 3),
                    LegalBlock.Bullet("앱은 모르는 사람과 채팅할 수 있어 정해진 나이 이상을 대상으로 합니다."),
                    LegalBlock.Bullet("이용 정지된 사람이 새 계정을 만들어 계속 이용하는 것은 금지됩니다."),
                    LegalBlock.Heading("2. 계정", 3),
                    LegalBlock.Bullet("처음 실행할 때 기기에 계정이 자동으로 만들어집니다."),
                    LegalBlock.Heading("3. 채팅과 행동 규칙", 3),
                    LegalBlock.Heading("4. 아동 안전 (무관용)", 3),
                    LegalBlock.Heading("5. 신고, 차단, 채팅 끄기", 3),
                ),
            ),
            onBack = {},
            onWebVersion = null,
        )
    }

    @Test fun howToPlay() = capture("18_how_to_play") { HowToPlayScreen(onBack = {}) }

    private fun app.numbaseball.ui.match.MatchActions.copyWithChat() = app.numbaseball.ui.match.MatchActions(
        onDigit, onDelete, onClear, onTapSlot, onSubmit, onRandomSecret, onForfeit, onLeave, onMemoDigit, onMemoCell, onMemoClear, onMemoCollapse,
        onChat = {},
    )

    private fun lobbyActions() = LobbyActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun settingsActions() = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})

    private companion object {
        /** Tolerates tiny anti-aliasing differences between machines when CI verifies the committed images. */
        val OPTIONS = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class PhoneEnglishScreenshots : ScreenshotSuite("phone_en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w360dp-h800dp-xhdpi")
class PhoneKoreanScreenshots : ScreenshotSuite("phone_ko")

/** Split-screen / pop-up window size (ADAPTIVE.md compact height). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h320dp-xhdpi")
class SmallEnglishScreenshots : ScreenshotSuite("small_en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w360dp-h320dp-xhdpi")
class SmallKoreanScreenshots : ScreenshotSuite("small_ko")
