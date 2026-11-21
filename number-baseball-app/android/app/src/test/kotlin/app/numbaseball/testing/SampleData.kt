package app.numbaseball.testing

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
import app.numbaseball.core.protocol.Team

/** Protocol snapshots shaped like docs/protocol.md §13. */
object SampleData {
    const val ME = "k2m9x4q1ab7p"
    const val OPPONENT = "z8m1q0w2e3r4"

    val me = PlayerView(ME, "Mina", "0420", Team.A, connected = true, left = false)
    val opponent = PlayerView(OPPONENT, "Leo", "1234", Team.B, connected = true, left = false)

    fun duel(
        phase: DuelPhase = DuelPhase.GUESSING,
        round: Int = 3,
        mySecret: String? = "042",
        pending: String? = null,
        result: DuelResult? = null,
        deadline: Long? = 1_795_000_045_000,
    ) = MyDuelView(
        index = 0,
        myTeam = Team.A,
        opponentId = OPPONENT,
        phase = phase,
        round = round,
        deadline = deadline,
        mySecret = mySecret,
        opponentHasSecret = true,
        myPendingGuess = pending,
        opponentGuessed = false,
        myRounds = listOf(RoundEntry(1, "123", 0, 1), RoundEntry(2, "456", 0, 0)),
        opponentRounds = listOf(RoundEntry(1, "789", 0, 0), RoundEntry(2, null, 0, 0)),
        myMisses = 0,
        opponentMisses = 1,
        result = result,
        opponentSecret = if (result != null) "719" else null,
    )

    fun quickRoom(duel: MyDuelView = duel(), version: Long = 3, matchResult: MatchResult? = null) = RoomView(
        id = "r_8f2k",
        kind = RoomKind.QUICK,
        code = null,
        phase = if (duel.phase == DuelPhase.FINISHED) RoomPhase.FINISHED else RoomPhase.PLAYING,
        settings = RoomSettings(digits = 3, teamSize = 1, roundSeconds = 60),
        hostId = null,
        youId = ME,
        players = listOf(me, opponent),
        match = MatchView(
            number = 1,
            duels = listOf(DuelSummary(0, ME, OPPONENT, duel.phase, duel.round, duel.result)),
            score = Score(0, 0),
            result = matchResult,
            myDuel = duel,
        ),
        version = version,
        serverTime = 1_795_000_000_000,
    )

    fun privateLobby(players: List<PlayerView> = listOf(me, opponent.copy(connected = false)), teamSize: Int = 2) = RoomView(
        id = "r_lobby",
        kind = RoomKind.PRIVATE,
        code = "K7QX2",
        phase = RoomPhase.LOBBY,
        settings = RoomSettings(digits = 4, teamSize = teamSize, roundSeconds = 60),
        hostId = ME,
        youId = ME,
        players = players,
        match = null,
        version = 1,
        serverTime = 1_795_000_000_000,
    )

    fun teamMatch(): RoomView {
        val p3 = PlayerView("p3p3p3p3p3p3", "Sora", "0007", Team.A, connected = true, left = false)
        val p4 = PlayerView("p4p4p4p4p4p4", "Ivan", "4321", Team.B, connected = false, left = false)
        return privateLobby(listOf(me, opponent, p3, p4)).copy(
            phase = RoomPhase.PLAYING,
            match = MatchView(
                number = 1,
                duels = listOf(
                    DuelSummary(0, ME, OPPONENT, DuelPhase.FINISHED, 5, DuelResult(Team.A, EndReason.SOLVED)),
                    DuelSummary(1, p3.id, p4.id, DuelPhase.GUESSING, 4, null),
                ),
                score = Score(1, 0),
                result = null,
                myDuel = null,
            ),
        )
    }

    fun chat(): List<ChatMessage> = listOf(
        ChatMessage(1, 0, OPPONENT, "Leo", "1234", Team.B, ChatKind.PHRASE, null, "hello"),
        ChatMessage(2, 0, ME, "Mina", "0420", Team.A, ChatKind.TEXT, "ready when you are", null),
        ChatMessage(3, 0, OPPONENT, "Leo", "1234", Team.B, ChatKind.TEXT, "good luck, ****", null),
        ChatMessage(4, 0, null, null, null, null, ChatKind.TEXT, "see you", null),
    )
}
