package app.numbaseball.qa

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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import app.numbaseball.billing.PurchaseUiState
import app.numbaseball.billing.RemoveAdsState
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.BestHint
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
import app.numbaseball.core.protocol.SystemChatCodes
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
import app.numbaseball.ui.design.ToastHost
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
import app.numbaseball.ui.report.CardRecord
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
import app.numbaseball.ui.tutorial.Tutorial
import app.numbaseball.ui.tutorial.TutorialScreen
import app.numbaseball.ui.tutorial.TutorialState
import app.numbaseball.ui.tutorial.TutorialStep
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.util.TimeZone
import org.junit.Before
import org.junit.Rule

/** Sample data of the design shots (copied from the screenshot suite, which keeps its own private copy). */
/** Sample data matching design/shots, so renders can be compared side by side with the reference PNGs. */
object QaDesign {
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
                // Protocol 1.1 best hints for every duel (shot team-board-ko).
                DuelSummary(0, ME, mint.id, DuelPhase.FINISHED, 7, DuelResult(Team.A, EndReason.SOLVED), bestHintA = BestHint(4, 0), bestHintB = BestHint(2, 1)),
                if (final) {
                    DuelSummary(1, kestrel.id, gross.id, DuelPhase.FINISHED, 9, DuelResult(Team.B, EndReason.SOLVED), bestHintA = BestHint(2, 2), bestHintB = BestHint(4, 0))
                } else {
                    DuelSummary(1, kestrel.id, gross.id, DuelPhase.GUESSING, 5, null, bestHintA = BestHint(2, 1), bestHintB = BestHint(1, 2))
                },
                if (final) {
                    DuelSummary(2, night.id, volna.id, DuelPhase.FINISHED, 6, DuelResult(Team.A, EndReason.SOLVED), bestHintA = BestHint(4, 0), bestHintB = BestHint(1, 2))
                } else {
                    DuelSummary(2, night.id, volna.id, DuelPhase.GUESSING, 4, null, bestHintA = BestHint(1, 1), bestHintB = BestHint(0, 0))
                },
            ),
            score = if (final) Score(2, 1) else Score(1, 0),
            result = if (final) MatchResult(Team.A, decidedEarly = false) else null,
            myDuel = myDuel(finished = true),
        ),
        version = 9, serverTime = 0,
    )

    val chat = listOf(
        // Protocol 1.1 system line (shot chat-ko: "민트초코#0912 님이 들어왔어요").
        ChatMessage(0, AT, null, null, null, null, ChatKind.SYSTEM, null, null, systemCode = SystemChatCodes.PLAYER_JOINED, subjectId = mint.id, subjectNickname = mint.nickname, subjectTag = mint.tag),
        ChatMessage(1, AT, mint.id, mint.nickname, mint.tag, Team.B, ChatKind.TEXT, "잘 부탁해요!", null),
        ChatMessage(2, AT, kestrel.id, kestrel.nickname, kestrel.tag, Team.A, ChatKind.TEXT, "몇 자리로 할까요?", null),
        ChatMessage(3, AT, gross.id, gross.nickname, gross.tag, Team.B, ChatKind.TEXT, "그건 좀 *** 아닌가", null),
        ChatMessage(4, AT, ME, me.nickname, me.tag, Team.A, ChatKind.TEXT, "4자리 좋아요", null),
    )

    /** chat-system-ko: join / leave / deleted-player lines and a masked word in my bubble (09 §2.1). */
    val systemChat = listOf(
        ChatMessage(10, AT, volna.id, volna.nickname, volna.tag, Team.B, ChatKind.TEXT, "다들 수고했어요", null),
        ChatMessage(11, AT, null, null, null, null, ChatKind.SYSTEM, null, null, systemCode = SystemChatCodes.PLAYER_LEFT, subjectId = volna.id, subjectNickname = volna.nickname, subjectTag = volna.tag),
        ChatMessage(12, AT, ME, me.nickname, me.tag, Team.A, ChatKind.TEXT, "아 *** 졌다", null),
        ChatMessage(13, AT, null, null, null, null, ChatKind.SYSTEM, null, null, systemCode = SystemChatCodes.PLAYER_LEFT),
        ChatMessage(14, AT, null, null, null, null, ChatKind.SYSTEM, null, null, systemCode = SystemChatCodes.PLAYER_JOINED, subjectId = mint.id, subjectNickname = mint.nickname, subjectTag = mint.tag),
        ChatMessage(15, AT, mint.id, mint.nickname, mint.tag, Team.B, ChatKind.TEXT, "잘 부탁해요!", null),
        ChatMessage(16, AT, ME, me.nickname, me.tag, Team.A, ChatKind.TEXT, "다음 판도 같이 해요", null),
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


