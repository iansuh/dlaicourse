package app.numbaseball.server

import app.numbaseball.core.ai.ComputerPlayer
import app.numbaseball.core.match.TeamMatch
import app.numbaseball.core.protocol.BestHint
import app.numbaseball.core.protocol.ChatMessage
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelSummary
import app.numbaseball.core.protocol.MatchView
import app.numbaseball.core.protocol.MyDuelView
import app.numbaseball.core.protocol.PlayerView
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.protocol.ServerMessage
import app.numbaseball.core.protocol.Team
import kotlinx.coroutines.channels.Channel

/** What the socket writer sends: a message, or a close frame (after which nothing more is sent). */
sealed interface Outgoing {
    data class Msg(val message: ServerMessage) : Outgoing
    data class Close(val code: Short, val reason: String) : Outgoing
}

/** One WebSocket connection of an account. The hub never suspends on it: a client that cannot keep up is closed. */
class ClientSession(val accountId: String, capacity: Int = 512) {
    val outgoing = Channel<Outgoing>(capacity)

    /** Completed by the socket writer once it has sent everything queued (including the close frame) or stopped. */
    val flushed = kotlinx.coroutines.CompletableDeferred<Unit>()

    @Volatile
    var open = true
        private set

    fun send(message: ServerMessage) {
        if (!open) return
        if (outgoing.trySend(Outgoing.Msg(message)).isFailure) close(1008, "too slow")
    }

    fun close(code: Short, reason: String) {
        if (!open) return
        open = false
        outgoing.trySend(Outgoing.Close(code, reason))
        outgoing.close()
    }
}

class Seat(
    val id: String,
    var nickname: String,
    var tag: String,
    var team: Team,
    val order: Long,
    val bot: Boolean = false,
) {
    var session: ClientSession? = null
    var disconnectedAt: Long? = null
    var left = false
    var deleted = false
    val connected: Boolean get() = bot || session != null
}

class ReviewBotState(val seatId: String, var ai: ComputerPlayer) {
    var observed = 0
    var saidHello = false
    var saidText = false
}

class Room(
    val id: String,
    val kind: RoomKind,
    /** Join code (private rooms). */
    val code: String?,
    var settings: RoomSettings,
    val createdAt: Long,
) {
    val seats = LinkedHashMap<String, Seat>()
    var hostId: String? = null
    var phase = RoomPhase.LOBBY
    var match: TeamMatch? = null
    var matchNumber = 0
    var resultRecorded = false
    var version = 0L
    val chat = ArrayDeque<ChatMessage>()
    var chatSeq = 0L
    /** Everyone who ever had a seat here (reports may come after leaving). */
    val members = HashSet<String>()
    /** Last time at least one human player was connected. */
    var lastActiveAt = createdAt
    /** Creation, last join, settings change or reset while in the lobby (idle private lobbies are closed). */
    var lobbyActivityAt = createdAt
    var bot: ReviewBotState? = null

    fun active(team: Team): List<Seat> = seats.values.filter { it.team == team && !it.left }
    fun humans(): List<Seat> = seats.values.filter { !it.bot && !it.left }
    val isFull: Boolean get() = active(Team.A).size >= settings.teamSize && active(Team.B).size >= settings.teamSize

    fun view(meId: String, now: Long): RoomView = RoomView(
        id = id,
        kind = kind,
        code = code,
        phase = phase,
        settings = settings,
        hostId = hostId,
        youId = meId,
        players = seats.values.map {
            PlayerView(
                id = it.id,
                nickname = if (it.deleted) "" else it.nickname,
                tag = if (it.deleted) "" else it.tag,
                team = it.team,
                connected = it.connected && !it.left,
                left = it.left,
                deleted = it.deleted,
            )
        },
        match = match?.let { matchView(it, meId) },
        version = version,
        serverTime = now,
    )

    private fun bestHint(rounds: List<RoundEntry>): BestHint? =
        rounds.filter { it.guess != null }.maxWithOrNull(compareBy<RoundEntry>({ it.strikes }, { it.balls }))?.let { BestHint(it.strikes, it.balls) }

    private fun matchView(m: TeamMatch, meId: String): MatchView {
        val duels = m.duels.mapIndexed { i, d ->
            DuelSummary(
                i, m.playerAt(i, Team.A), m.playerAt(i, Team.B), d.phase, d.round, d.result,
                bestHintA = bestHint(d.roundsOf(Team.A)), bestHintB = bestHint(d.roundsOf(Team.B)),
            )
        }
        val mine = m.locate(meId)?.let { (i, team) ->
            val d = m.duels[i]
            val other = team.other
            MyDuelView(
                index = i,
                myTeam = team,
                opponentId = m.playerAt(i, other),
                phase = d.phase,
                round = d.round,
                deadline = d.deadline,
                mySecret = d.secretOf(team),
                opponentHasSecret = d.secretOf(other) != null,
                myPendingGuess = d.pendingGuessOf(team),
                opponentGuessed = d.pendingGuessOf(other) != null,
                myRounds = d.roundsOf(team).toList(),
                opponentRounds = d.roundsOf(other).toList(),
                myMisses = d.missesOf(team),
                opponentMisses = d.missesOf(other),
                result = d.result,
                // The opponent's secret is revealed only once this duel is over.
                opponentSecret = if (d.phase == DuelPhase.FINISHED) d.secretOf(other) else null,
            )
        }
        return MatchView(matchNumber, duels, m.score(), m.result, mine)
    }
}
