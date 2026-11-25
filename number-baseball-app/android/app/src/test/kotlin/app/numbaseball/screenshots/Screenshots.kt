package app.numbaseball.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.numbaseball.ads.LocalAdPreview
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
import app.numbaseball.data.settings.AppSettings
import app.numbaseball.ui.age.AgeScreen
import app.numbaseball.ui.age.AgeUiState
import app.numbaseball.ui.age.NotAvailableScreen
import app.numbaseball.ui.chat.ChatSheet
import app.numbaseball.ui.chat.ChatUiState
import app.numbaseball.ui.chat.QuickPhraseSheet
import app.numbaseball.ui.chatActions
import app.numbaseball.ui.computer.ComputerChoice
import app.numbaseball.ui.computer.ComputerSetupSheet
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
import app.numbaseball.ui.lobby.RoomSettingsSheet
import app.numbaseball.ui.match.MatchActions
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchPhase
import app.numbaseball.ui.match.MatchScreen
import app.numbaseball.ui.match.MatchSummary
import app.numbaseball.ui.match.MatchUiState
import app.numbaseball.ui.match.MemoState
import app.numbaseball.ui.match.Opponent
import app.numbaseball.ui.match.OpponentStatus
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.match.PracticeInfo
import app.numbaseball.ui.matchActions
import app.numbaseball.ui.profile.ProfileScreen
import app.numbaseball.ui.profile.ProfileUiState
import app.numbaseball.ui.quickmatch.FoundOpponent
import app.numbaseball.ui.quickmatch.QuickMatchScreen
import app.numbaseball.ui.quickmatch.QuickMatchUiState
import app.numbaseball.ui.report.BlockDialog
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
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.util.TimeZone
import org.junit.Before
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

    /** 9:41, the time of the reference shots' status bar (tests run in UTC). */
    const val AT = (9 * 3600 + 41 * 60) * 1000L

    fun lobbyRoom(players: List<PlayerView> = listOf(me, kestrel, mint, gross)) = RoomView(
        id = "r_lobby", kind = RoomKind.PRIVATE, code = "K7Q2M", phase = RoomPhase.LOBBY,
        settings = RoomSettings(digits = 4, teamSize = 3, roundSeconds = 60), hostId = ME, youId = ME,
        players = players, match = null, version = 1, serverTime = 0,
    )

    fun lobbyState(players: List<PlayerView> = listOf(me, kestrel, mint, gross), connection: ConnectionState = ConnectionState.Idle) = LobbyUiState(
        room = lobbyRoom(players),
        players = players.map { LobbyPlayer(it.id, it.nickname, it.tag, it.team, host = it.id == ME, you = it.id == ME, connected = true) },
        connection = connection,
        chat = listOf(
            ChatMessage(6, AT, mint.id, mint.nickname, mint.tag, Team.B, ChatKind.TEXT, "안녕하세요", null),
            ChatMessage(7, AT, mint.id, mint.nickname, mint.tag, Team.B, ChatKind.TEXT, "잘 부탁해요!", null),
        ),
        chatReadUpTo = 5,
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
        ChatMessage(1, AT, mint.id, mint.nickname, mint.tag, Team.B, ChatKind.TEXT, "잘 부탁해요!", null),
        ChatMessage(2, AT, kestrel.id, kestrel.nickname, kestrel.tag, Team.A, ChatKind.TEXT, "몇 자리로 할까요?", null),
        ChatMessage(3, AT, gross.id, gross.nickname, gross.tag, Team.B, ChatKind.TEXT, "그건 좀 *** 아닌가", null),
        ChatMessage(4, AT, ME, me.nickname, me.tag, Team.A, ChatKind.TEXT, "4자리 좋아요", null),
    )

    fun chatState(muted: Boolean = false, cooldown: Int = 0, messages: List<ChatMessage> = chat, input: String = "") =
        ChatUiState(roomId = "r", myId = ME, teamRoom = true, messages = messages, phrasesOnly = false, muted = muted, cooldown = cooldown, input = input)

    private val memoMarks = listOf(MemoMark.OUT, MemoMark.IN, MemoMark.OUT, MemoMark.IN, MemoMark.OUT, MemoMark.OUT, MemoMark.IN, MemoMark.OUT, MemoMark.OUT, MemoMark.IN)

    /** match-guess-ko: round 4, three rows (one OUT), memo with digit and cell marks reflected on the keypad. */
    fun guessState(digits: Int = 4): MatchUiState {
        val cells = List(digits) { MutableList(10) { MemoMark.NONE } }.also {
            it[0][1] = MemoMark.OUT; it[0][3] = MemoMark.IN; it[1][1] = MemoMark.OUT; it[2][3] = MemoMark.OUT; it[3][9] = MemoMark.OUT
        }
        return MatchUiState(
            digits = digits,
            opponent = Opponent.Player("Kestrel", "2048"),
            mode = MatchMode.QUICK,
            phase = MatchPhase.GUESSING,
            input = "39",
            mySecret = "0472",
            round = 4,
            myRounds = listOf(RoundEntry(1, "1234", 0, 2), RoundEntry(2, "5780", 0, 0), RoundEntry(3, "3196", 2, 2)),
            opponentRounds = rounds("0472", listOf("5832", "0291", "0724")),
            secondsLeft = 42,
            roundSeconds = 60,
            opponentStatus = OpponentStatus.Pitched,
            memo = MemoState(digits = digits, digitMarks = memoMarks, cells = cells),
        )
    }

    /** match-6digits-ko: secret 047281, three rows, memo collapsed to its digit row by the log rule. */
    fun sixDigits() = MatchUiState(
        digits = 6,
        opponent = Opponent.Player("Kestrel", "2048"),
        mode = MatchMode.QUICK,
        phase = MatchPhase.GUESSING,
        input = "391",
        mySecret = "047281",
        round = 4,
        myRounds = rounds("391652", listOf("123456", "578042", "319620")),
        opponentRounds = rounds("047281", listOf("583219", "029153", "072418")),
        secondsLeft = 42,
        roundSeconds = 60,
        opponentStatus = OpponentStatus.Pitched,
        memo = MemoState(digits = 6, digitMarks = List(10) { if (it == 1 || it == 3) MemoMark.IN else if (it == 7 || it == 8) MemoMark.OUT else MemoMark.NONE }),
    )

    /** Strikes and balls of [guess] against [secret] (the prototype's `P.judge`). */
    private fun judge(secret: String, guess: String): Pair<Int, Int> {
        val s = guess.indices.count { guess[it] == secret[it] }
        val b = guess.indices.count { guess[it] != secret[it] && guess[it] in secret }
        return s to b
    }

    private fun rounds(secret: String, guesses: List<String>) = guesses.mapIndexed { i, g -> judge(secret, g).let { (s, b) -> RoundEntry(i + 1, g, s, b) } }

    /** match-late-ko / match-phrases-ko (prototype `state=late`): round 7, entry 391, memo with the deduced positions. */
    fun lateState(): MatchUiState {
        val digitMarks = List(10) { if (it in listOf(1, 3, 6, 9)) MemoMark.IN else MemoMark.OUT }
        val cells = List(4) { MutableList(10) { MemoMark.NONE } }.also {
            it[0][3] = MemoMark.IN; it[1][9] = MemoMark.IN; it[2][1] = MemoMark.IN; it[3][2] = MemoMark.OUT; it[3][6] = MemoMark.IN
            it[0][1] = MemoMark.OUT; it[2][3] = MemoMark.OUT
        }
        return guessState().copy(
            round = 7,
            input = "391",
            myRounds = rounds("3916", listOf("1234", "5780", "3196", "3169", "3961", "3912")),
            opponentRounds = rounds("0472", listOf("5832", "0291", "0724", "0742", "0427", "0471")),
            memo = MemoState(digits = 4, digitMarks = digitMarks, cells = cells),
        )
    }

    fun resultSummary(outcome: Outcome = Outcome.WIN, mode: MatchMode = MatchMode.QUICK) = MatchSummary(
        mode, outcome, EndReason.SOLVED, rounds = 6, opponentSecret = "3916", digits = 4, mySecret = "0472",
        level = if (mode == MatchMode.COMPUTER) ComputerLevel.NORMAL else null,
        opponentName = "Kestrel",
        myStrikes = if (outcome == Outcome.WIN) listOf(0, 0, 2, 1, 2, 4) else listOf(0, 0, 2, 1, 2, 2),
        opponentStrikes = if (outcome == Outcome.WIN) listOf(1, 1, 1, 2, 2, 3) else listOf(1, 1, 1, 2, 2, 4),
        durationSeconds = 192,
    )

    val settingsState = SettingsUiState(registered = true, nickname = "새벽투수", tag = "4821", blockedCount = 2, language = "ko")
}

/** The stand-in creative design/shots draw in the ad band (prototype `.adslot .creative`, 320 × 50). Test frame only. */
@Composable
private fun SampleBannerCreative() {
    Row(
        Modifier.size(320.dp, 50.dp).background(Color(0xFFD9DCD4)),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.background(Color(0xFF3A3F3B), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
            Text("AD", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD9DCD4)))
        }
        Text("Sample banner creative · adaptive 360×50", style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFF3A3F3B)))
    }
}

/**
 * Every screen and state with sample data. Recorded locally (`./gradlew :app:recordRoborazziDebug`) into
 * `app/screenshots/` and verified on CI (`verifyRoborazziDebug`).
 *
 * File names follow `design/shots/`: the test passes the shot name with `{lang}` where the language goes, e.g.
 * `match-guess-{lang}` → `match-guess-ko.png` (phone, Korean) and `match-guess-en.png` (phone, English); the small window
 * variant is `match-small-{lang}` for the match and `<name>-small-{lang}` elsewhere; `-font200` marks the 200 % font
 * scale render. States without a reference shot use the same scheme with a descriptive name.
 *
 * The frame reproduces the shots' window: 24 dp status bar and 24 dp gesture area as real system-bar insets (screens
 * draw behind them edge to edge), an optional IME (230 dp visible, covering the gesture area as in `nickname-ko`), the ad band with the shots' sample creative, and reduced motion
 * (DESIGN_SYSTEM 8.4: final states). Audio and haptics are the silent defaults.
 */
@OptIn(ExperimentalRoborazziApi::class)
abstract class ScreenshotSuite(private val lang: String, private val small: Boolean = false, private val suffix: String = "") {
    @get:Rule val compose = createComposeRule()

    @Before fun utc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    private fun capture(
        shot: String,
        smallShot: String = shot.replace("-{lang}", "-small-{lang}"),
        ime: Dp = 0.dp,
        ads: Boolean = false,
        before: ComposeContentTestRule.() -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            NumBaseballTheme {
                CompositionLocalProvider(
                    LocalReduceMotion provides true,
                    LocalAdPreview provides if (ads) ({ SampleBannerCreative() }) else null,
                ) {
                    // A split-screen / pop-up window has no system bars of its own (match-small-*).
                    FakeSystemBars(ime = if (small) 0.dp else ime, bars = if (small) 0.dp else 24.dp)
                    Box(Modifier.fillMaxSize().background(NbColors.bg)) {
                        content()
                        if (ime > 0.dp && !small) {
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(ime).background(Color(0xFF1F2421)), contentAlignment = Alignment.Center) {
                                Text("IME", style = TextStyle(fontSize = 12.sp, color = Color(0xFF7D857F)))
                            }
                        }
                    }
                }
            }
        }
        compose.before()
        compose.waitForIdle()
        val name = (if (small) smallShot else shot).replace("{lang}", lang) + suffix
        captureScreenRoboImage("screenshots/$name.png", roborazziOptions = OPTIONS)
    }

    // 01 First run
    @Test fun age() = capture("age-{lang}") { AgeScreen(AgeUiState(year = "199"), {}, {}, {}, {}) }

    @Test fun ageError() = capture("age-error-{lang}") { AgeScreen(AgeUiState(year = "2091", invalid = true), {}, {}, {}, {}) }

    @Test fun ageNotAvailable() = capture("age-na-{lang}") { NotAvailableScreen(onPrivacyPolicy = {}) }

    @Test fun nickname() = capture("nickname-{lang}", ime = 230.dp) { FirstRunScreen(FirstRunUiState(nickname = "새벽투수"), {}, {}, {}, {}, {}) }

    @Test fun nicknameError() = capture("nickname-error-{lang}", ime = 230.dp) {
        FirstRunScreen(FirstRunUiState(nickname = "야", nicknameProblem = app.numbaseball.data.account.NicknameProblem.TOO_SHORT), {}, {}, {}, {}, {})
    }

    // 01 §2 How to play
    @Test fun howToPlay() = capture("how-to-play-{lang}") { HowToPlayScreen(onBack = {}) }

    // 02 Home
    private val homeState = HomeUiState(nickname = "새벽투수", tag = "4821", registered = true, stats = Stats(12, 8, 3), recentForm = "WWLWDLWWLW")

    @Test fun home() = capture("home-{lang}", ads = true) { HomeScreen(homeState, {}, {}, {}, {}, {}, {}, {}, {}, {}) }

    @Test fun homeOffline() = capture("home-offline-{lang}") { HomeScreen(homeState.copy(online = false), {}, {}, {}, {}, {}, {}, {}, {}, {}) }

    @Test fun homeNewPlayer() = capture("home-new-{lang}", ads = true) {
        HomeScreen(homeState.copy(stats = Stats(0, 0, 0), recentForm = ""), {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun homeLoading() = capture("home-loading-{lang}") { HomeScreen(homeState.copy(stats = null, recentForm = ""), {}, {}, {}, {}, {}, {}, {}, {}, {}) }

    // 03 vs Computer setup
    @Test fun computerSetup() = capture("cpu-setup-{lang}") {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(homeState, {}, {}, {}, {}, {}, {}, {}, {}, {})
            ComputerSetupSheet(ComputerChoice(3, ComputerLevel.NORMAL, 600), {}, {}, {})
        }
    }

    // 04 Quick match
    @Test fun search() = capture("search-{lang}") { QuickMatchScreen(QuickMatchUiState(digits = 4, startedAt = 0), {}, {}, {}, now = { 7_000L }) }

    @Test fun searchLong() = capture("search-60s-{lang}") { QuickMatchScreen(QuickMatchUiState(digits = 4, startedAt = 0), {}, {}, {}, now = { 64_000L }) }

    @Test fun searchFound() = capture("search-found-{lang}") {
        QuickMatchScreen(QuickMatchUiState(digits = 4, found = FoundOpponent("Kestrel", "2048")), {}, {}, {})
    }

    // 05 Match
    @Test fun matchSecret() = capture("match-secret-{lang}") {
        MatchScreen(
            MatchUiState(4, Opponent.Player("Kestrel", "2048"), MatchMode.QUICK, MatchPhase.CHOOSING_SECRET, input = "047", secondsLeft = 42, roundSeconds = 60, opponentStatus = OpponentStatus.Picking),
            matchActions().withChat(),
        )
    }

    @Test fun matchGuess() = capture("match-guess-{lang}", smallShot = "match-small-{lang}") { MatchScreen(Design.guessState(), matchActions().withChat()) }

    @Test fun matchWaiting() = capture("match-waiting-{lang}") {
        MatchScreen(Design.guessState().copy(phase = MatchPhase.WAITING_FOR_OPPONENT_GUESS, input = "3916", opponentStatus = OpponentStatus.Thinking), matchActions().withChat())
    }

    @Test fun matchLate() = capture("match-late-{lang}") { MatchScreen(Design.lateState(), matchActions().withChat()) }

    @Test fun matchSixDigits() = capture("match-6digits-{lang}") { MatchScreen(Design.sixDigits(), matchActions().withChat()) }

    @Test fun matchClockWarn() = capture("match-clock-warn-{lang}") { MatchScreen(Design.guessState().copy(secondsLeft = 8), matchActions().withChat()) }

    @Test fun matchClockCritical() = capture("match-clock-crit-{lang}") { MatchScreen(Design.guessState().copy(secondsLeft = 4), matchActions().withChat()) }

    @Test fun matchPhrases() = capture("match-phrases-{lang}") {
        Box(Modifier.fillMaxSize()) {
            MatchScreen(Design.lateState(), matchActions().withChat())
            QuickPhraseSheet(enabled = true, onPhrase = {}, onDismiss = {})
        }
    }

    @Test fun matchComputer() = capture("match-cpu-{lang}") {
        MatchScreen(
            Design.guessState().copy(opponent = Opponent.Computer(ComputerLevel.NORMAL), mode = MatchMode.COMPUTER, secondsLeft = 600, roundSeconds = 600, opponentStatus = OpponentStatus.Thinking),
            matchActions(),
        )
    }

    @Test fun matchPractice() = capture("match-practice-{lang}") {
        MatchScreen(
            Design.guessState().copy(
                opponent = Opponent.Computer(ComputerLevel.NORMAL), mode = MatchMode.PRACTICE, secondsLeft = 600, roundSeconds = 600,
                opponentStatus = OpponentStatus.Thinking, practice = PracticeInfo("K7Q2M", 3, 6),
            ),
            matchActions(),
        )
    }

    @Test fun matchOpponentDisconnected() = capture("match-opponent-offline-{lang}") {
        MatchScreen(Design.guessState().copy(opponentStatus = OpponentStatus.Disconnected(41)), matchActions().withChat())
    }

    // 14 Connection
    @Test fun matchReconnect() = capture("match-reconnect-{lang}") { MatchScreen(Design.guessState().copy(reconnectSeconds = 48), matchActions().withChat()) }

    // 06 Results
    @Test fun resultWin() = capture("result-win-{lang}", ads = true) {
        ResultsScreen(Design.resultSummary(), adDue = false, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = {})
    }

    @Test fun resultLose() = capture("result-lose-{lang}", ads = true) {
        ResultsScreen(Design.resultSummary(Outcome.LOSE), adDue = false, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = {})
    }

    @Test fun resultDraw() = capture("result-draw-{lang}", ads = true) {
        ResultsScreen(
            Design.resultSummary(Outcome.DRAW).copy(reason = EndReason.BOTH_SOLVED, myStrikes = listOf(0, 0, 2, 1, 2, 4)),
            adDue = false, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = {},
        )
    }

    @Test fun resultForfeit() = capture("result-forfeit-{lang}", ads = true) {
        ResultsScreen(Design.resultSummary(Outcome.LOSE).copy(reason = EndReason.TIMEOUT, iEnded = true), adDue = false, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = {})
    }

    @Test fun resultComputerLose() = capture("result-cpu-lose-{lang}") {
        ResultsScreen(Design.resultSummary(Outcome.LOSE, MatchMode.COMPUTER), adDue = true, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = null)
    }

    @Test fun resultComputerWin() = capture("result-cpu-win-{lang}", ads = true) {
        ResultsScreen(Design.resultSummary(Outcome.WIN, MatchMode.COMPUTER), adDue = false, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = null)
    }

    // 07 Room
    @Test fun createRoom() = capture("create-room-{lang}") {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(homeState, {}, {}, {}, {}, {}, {}, {}, {}, {})
            RoomSettingsSheet(
                title = androidx.compose.ui.res.stringResource(app.numbaseball.R.string.create_title),
                actionLabel = androidx.compose.ui.res.stringResource(app.numbaseball.R.string.create_title),
                settings = RoomSettings(4, 3, 60), onChange = {}, onConfirm = {}, onDismiss = {},
            )
        }
    }

    @Test fun join() = capture("join-{lang}", ime = 230.dp) { JoinScreen(JoinUiState(code = "K7Q"), {}, {}, {}, {}) }

    @Test fun lobby() = capture("lobby-{lang}") { RoomLobbyScreen(Design.lobbyState(), lobbyActions()) }

    @Test fun lobbyAlone() = capture("lobby-alone-{lang}") { RoomLobbyScreen(Design.lobbyState(players = listOf(Design.me)).copy(chat = emptyList()), lobbyActions()) }

    @Test fun lobbyReconnecting() = capture("lobby-reconnect-{lang}") {
        RoomLobbyScreen(Design.lobbyState(connection = ConnectionState.Reconnecting(1, 1_000L)), lobbyActions())
    }

    // 08 Team board
    @Test fun teamBoard() = capture("team-board-{lang}") { TeamBoardScreen(Design.teamRoom(final = false), BoardMode.LIVE, {}, {}) }

    @Test fun teamFinal() = capture("team-final-{lang}", ads = true) { TeamBoardScreen(Design.teamRoom(final = true), BoardMode.FINAL, {}, {}) }

    // 09 Chat
    @Test fun chat() = capture("chat-{lang}") {
        Box(Modifier.fillMaxSize()) {
            RoomLobbyScreen(Design.lobbyState(), lobbyActions())
            SheetFrame(onDismiss = {}, top = 136.dp) { ChatSheet(Design.chatState(), chatActions()) }
        }
    }

    @Test fun chatLimit() = capture("chat-limit-{lang}") {
        Box(Modifier.fillMaxSize()) {
            RoomLobbyScreen(Design.lobbyState(), lobbyActions())
            SheetFrame(onDismiss = {}, top = 136.dp) { ChatSheet(Design.chatState(cooldown = 3), chatActions()) }
        }
    }

    @Test fun chatEmpty() = capture("chat-empty-{lang}") {
        Box(Modifier.fillMaxSize()) {
            RoomLobbyScreen(Design.lobbyState(), lobbyActions())
            SheetFrame(onDismiss = {}, top = 136.dp) { ChatSheet(Design.chatState(messages = emptyList()), chatActions()) }
        }
    }

    @Test fun chatMuted() = capture("chat-muted-{lang}") {
        Box(Modifier.fillMaxSize()) {
            RoomLobbyScreen(Design.lobbyState(), lobbyActions())
            SheetFrame(onDismiss = {}, top = 136.dp) { ChatSheet(Design.chatState(muted = true), chatActions()) }
        }
    }

    @Test fun chatActionsSheet() = capture(
        "chat-actions-{lang}",
        before = { onNodeWithText("그건 좀 *** 아닌가", substring = true, useUnmergedTree = true).performTouchInput { longClick() } },
    ) {
        Box(Modifier.fillMaxSize()) {
            RoomLobbyScreen(Design.lobbyState(), lobbyActions())
            SheetFrame(onDismiss = {}, top = 136.dp) { ChatSheet(Design.chatState(), chatActions()) }
        }
    }

    // 10 Profile
    @Test fun profile() = capture("profile-{lang}", ads = true) {
        ProfileScreen(ProfileUiState(nickname = "새벽투수", tag = "4821", registered = true, stats = Stats(12, 8, 3), recentForm = "WWLWDLWWLW"), {}, {}, {})
    }

    @Test fun profileNoGames() = capture("profile-new-{lang}") {
        ProfileScreen(ProfileUiState(nickname = "새벽투수", tag = "4821", registered = true, stats = Stats(0, 0, 0)), {}, {}, {})
    }

    // 11 Settings
    @Test fun settings() = capture("settings-{lang}") {
        SettingsScreen(Design.settingsState, true, "1.0.0 (1)", "support@example.com", listOf("ko", "en"), settingsActions())
    }

    @Test fun settingsEnd() = capture(
        "settings-{lang}-end",
        smallShot = "settings-small-{lang}-end",
        before = { onNode(hasScrollAction()).performScrollToNode(hasText("1.0.0 (1)")) },
    ) {
        SettingsScreen(Design.settingsState, true, "1.0.0 (1)", "support@example.com", listOf("ko", "en"), settingsActions())
    }

    @Test fun settingsMusicOn() = capture("settings-music-{lang}") {
        SettingsScreen(Design.settingsState.copy(settings = AppSettings(musicEnabled = true, effectsEnabled = false)), false, "1.0.0 (1)", "support@example.com", listOf("ko", "en"), settingsActions())
    }

    @Test fun blockedPlayers() = capture("blocked-{lang}") {
        BlockedPlayersScreen(listOf(BlockedPlayer(Design.gross.id, "Größenwahn", "7731"), BlockedPlayer(Design.volna.id, "Volna", "1187")), {}, {}, {})
    }

    @Test fun blockedEmpty() = capture("blocked-empty-{lang}") { BlockedPlayersScreen(emptyList(), {}, {}, {}) }

    // 12 Player card, report, block
    @Test fun reportCard() = capture("report-card-{lang}") {
        Box(Modifier.fillMaxSize()) {
            TeamBoardScreen(Design.teamRoom(final = false), BoardMode.LIVE, {}, {})
            SheetFrame(onDismiss = {}) {
                PlayerCard(ReportTarget(Design.gross.id, "Größenwahn", "7731", team = Team.B), androidx.compose.ui.res.stringResource(app.numbaseball.R.string.team_b) + " · " + androidx.compose.ui.res.stringResource(app.numbaseball.R.string.card_context_quick), self = false, blocked = false, {}, {}, {}, record = Stats(31, 27, 4))
            }
        }
    }

    @Test fun reportReasons() = capture("report-reasons-{lang}") {
        Box(Modifier.fillMaxSize()) {
            TeamBoardScreen(Design.teamRoom(final = false), BoardMode.LIVE, {}, {})
            SheetFrame(onDismiss = {}) {
                ReportSheet(ReportUiState(ReportTarget(Design.gross.id, "Größenwahn", "7731"), choice = ReportChoice.HARASSMENT), {}, {}, {}, {}, {})
            }
        }
    }

    @Test fun reportDone() = capture("report-done-{lang}") {
        Box(Modifier.fillMaxSize()) {
            TeamBoardScreen(Design.teamRoom(final = false), BoardMode.LIVE, {}, {})
            SheetFrame(onDismiss = {}) {
                ReportSheet(ReportUiState(ReportTarget(Design.gross.id, "Größenwahn", "7731"), choice = ReportChoice.HARASSMENT, reported = true), {}, {}, {}, {}, {})
            }
        }
    }

    @Test fun reportDialog() = capture("report-dialog-{lang}") {
        Box(Modifier.fillMaxSize()) {
            TeamBoardScreen(Design.teamRoom(final = false), BoardMode.LIVE, {}, {})
            BlockDialog("Größenwahn#7731", onConfirm = {}, onDismiss = {})
        }
    }

    // 13 Terms gate
    @Test fun terms() = capture("terms-{lang}") { TermsGateScreen(updated = false, version = "2026-11-v1", submitting = false, failed = 0, {}, {}, {}, {}) }

    @Test fun termsUpdated() = capture("terms-updated-{lang}") { TermsGateScreen(updated = true, version = "2026-11-v2", submitting = false, failed = 0, {}, {}, {}, {}) }

    // 15 Legal reader
    @Test fun legal() = capture("legal-{lang}") {
        LegalTextScreen(
            title = androidx.compose.ui.res.stringResource(app.numbaseball.R.string.terms_of_use),
            page = legalPage(
                listOf(
                    LegalBlock.Heading("숫자야구: 조용한 한판 이용약관", 1),
                    LegalBlock.Paragraph("버전 2026-11-v1 · 시행일 [시행일]"),
                    LegalBlock.Paragraph(
                        "이 약관은 [개발자 표시명](이하 \"운영자\")이 제공하는 숫자야구 게임 앱 **숫자야구: 조용한 한판**(이하 \"앱\")의 이용 조건입니다. " +
                            "온라인 대결이나 채팅을 시작하기 전에 동의를 받으며, 동의하지 않으면 컴퓨터 대결만 이용할 수 있습니다.",
                    ),
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

    private fun MatchActions.withChat() = MatchActions(
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

/**
 * Real window insets for the frame: a 24 dp status bar, a 24 dp gesture area and an optional IME, dispatched to the
 * Compose view so screens lay out edge to edge exactly as on a device (DESIGN_SYSTEM 16).
 */
@Composable
private fun FakeSystemBars(ime: Dp, bars: Dp) {
    val view = LocalView.current
    val density = LocalDensity.current
    LaunchedEffect(view, ime, bars) {
        val bar = with(density) { bars.roundToPx() }
        val imePx = with(density) { ime.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, bar, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bar))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imePx))
            .setVisible(WindowInsetsCompat.Type.ime(), imePx > 0)
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, insets)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w360dp-h800dp-xhdpi")
class PhoneKoreanScreenshots : ScreenshotSuite("ko")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class PhoneEnglishScreenshots : ScreenshotSuite("en")

/** Split-screen / pop-up window (ADAPTIVE.md §1, 360 × 320 like `match-small-*`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w360dp-h320dp-xhdpi")
class SmallKoreanScreenshots : ScreenshotSuite("ko", small = true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h320dp-xhdpi")
class SmallEnglishScreenshots : ScreenshotSuite("en", small = true)

/** Font scale 200 % (DESIGN_SYSTEM 3.3, ADAPTIVE.md §2). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w360dp-h800dp-xhdpi", fontScale = 2.0f)
class LargeFontKoreanScreenshots : ScreenshotSuite("ko", suffix = "-font200")
