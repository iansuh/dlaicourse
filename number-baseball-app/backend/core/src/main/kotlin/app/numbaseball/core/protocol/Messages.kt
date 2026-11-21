package app.numbaseball.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// WebSocket messages for /ws/v1. One JSON object per text frame; "type" selects the message.
// Client messages may carry "rid" (request id, any positive number). When present the server answers
// exactly once with `ack` (same rid) or `error` (same rid). Without rid only errors are reported.

/** Decoded strictly by [ClientMessageSerializer] (RFC 8259 literals only, string "type"). */
@Serializable(with = ClientMessageSerializer::class)
sealed interface ClientMessage {
    val rid: Long?
}

@Serializable @SerialName("ping")
data class Ping(override val rid: Long? = null) : ClientMessage

/** Ask for a fresh `room_state` + `chat_history` (or `room_left` if not in a room). */
@Serializable @SerialName("resync")
data class Resync(override val rid: Long? = null) : ClientMessage

@Serializable @SerialName("queue_join")
data class QueueJoin(val digits: Int, override val rid: Long? = null) : ClientMessage

@Serializable @SerialName("queue_leave")
data class QueueLeave(override val rid: Long? = null) : ClientMessage

@Serializable @SerialName("room_create")
data class RoomCreate(val settings: RoomSettings, override val rid: Long? = null) : ClientMessage

@Serializable @SerialName("room_join")
data class RoomJoin(val code: String, override val rid: Long? = null) : ClientMessage

@Serializable @SerialName("room_leave")
data class RoomLeave(override val rid: Long? = null) : ClientMessage

/** Host only, lobby only. Replaces all settings. */
@Serializable @SerialName("room_settings")
data class RoomSettingsUpdate(val settings: RoomSettings, override val rid: Long? = null) : ClientMessage

/** Lobby only. Without [playerId] you move yourself; moving someone else requires host. */
@Serializable @SerialName("team_set")
data class TeamSet(val team: Team, val playerId: String? = null, override val rid: Long? = null) : ClientMessage

/** Host only, private room, finished phase: back to the lobby for another match. */
@Serializable @SerialName("room_reset")
data class RoomReset(override val rid: Long? = null) : ClientMessage

@Serializable @SerialName("secret")
data class SetSecret(val secret: String, override val rid: Long? = null) : ClientMessage

/** [round] must equal `myDuel.round`; otherwise `stale_round`. */
@Serializable @SerialName("guess")
data class Guess(val guess: String, val round: Int, override val rid: Long? = null) : ClientMessage

@Serializable @SerialName("forfeit")
data class Forfeit(override val rid: Long? = null) : ClientMessage

@Serializable @SerialName("chat")
data class Chat(val text: String, override val rid: Long? = null) : ClientMessage

@Serializable @SerialName("phrase")
data class Phrase(val phraseId: String, override val rid: Long? = null) : ClientMessage

// ---------------------------------------------------------------------------------------------

@Serializable
sealed interface ServerMessage

/** First message on every connection. If [roomId] is set, `room_state` and `chat_history` follow at once. */
@Serializable @SerialName("welcome")
data class Welcome(
    val protocol: Int,
    val serverTime: Long,
    val account: AccountDto,
    val roomId: String?,
) : ServerMessage

@Serializable @SerialName("pong")
data class Pong(val rid: Long?, val serverTime: Long) : ServerMessage

@Serializable @SerialName("ack")
data class Ack(val rid: Long) : ServerMessage

@Serializable @SerialName("error")
data class ErrorMessage(
    val code: String,
    val message: String,
    val rid: Long? = null,
    val retryAfterMs: Long? = null,
) : ServerMessage

/** You are waiting in the quick-match queue. */
@Serializable @SerialName("queued")
data class Queued(val digits: Int, val since: Long) : ServerMessage

/** Full personalised room snapshot. Sent on join, on resync and after every change. */
@Serializable @SerialName("room_state")
data class RoomState(val room: RoomView) : ServerMessage

/** You are no longer in the room. */
@Serializable @SerialName("room_left")
data class RoomLeft(val roomId: String, val reason: LeaveReason) : ServerMessage

@Serializable @SerialName("chat")
data class ChatEvent(val roomId: String, val message: ChatMessage) : ServerMessage

/** Recent room chat, oldest first, already filtered for your block list. Replaces any chat you hold for the room. */
@Serializable @SerialName("chat_history")
data class ChatHistory(val roomId: String, val messages: List<ChatMessage>) : ServerMessage

/** A sender deleted their account: show their messages in this room as "deleted player". */
@Serializable @SerialName("chat_sender_deleted")
data class ChatSenderDeleted(val roomId: String, val senderId: String) : ServerMessage

// ---------------------------------------------------------------------------------------------

@Serializable
data class RoomSettings(
    val digits: Int,
    /** Players per team, 1..5. 1 means a 1:1 match. */
    val teamSize: Int,
    /** One of 0 (no timer), 30, 60, 120. */
    val roundSeconds: Int,
)

@Serializable
data class PlayerView(
    val id: String,
    val nickname: String,
    val tag: String,
    val team: Team,
    /** Has a live connection right now. */
    val connected: Boolean,
    /** Left the room during the current match (seat kept only to show results). */
    val left: Boolean,
    /** The account was deleted: show "deleted player" ([nickname] and [tag] are empty). */
    val deleted: Boolean = false,
)

@Serializable
data class RoomView(
    val id: String,
    val kind: RoomKind,
    /** Five-character join code; private rooms only. */
    val code: String?,
    val phase: RoomPhase,
    val settings: RoomSettings,
    /** Private rooms only. */
    val hostId: String?,
    val youId: String,
    val players: List<PlayerView>,
    /** Present while playing and after the match ended. */
    val match: MatchView?,
    /** Increases with every change of the room. Ignore a snapshot with a lower version than one you have. */
    val version: Long,
    val serverTime: Long,
)

@Serializable
data class Score(val a: Int, val b: Int)

@Serializable
data class DuelResult(
    /** null = draw (or cancelled). */
    val winner: Team?,
    val reason: EndReason,
)

@Serializable
data class MatchResult(
    /** null = team draw. */
    val winner: Team?,
    /** True when a side could no longer be caught and the remaining duels were cancelled. */
    val decidedEarly: Boolean,
)

@Serializable
data class DuelSummary(
    val index: Int,
    /** Team A player id. */
    val playerA: String,
    /** Team B player id. */
    val playerB: String,
    val phase: DuelPhase,
    /** Current round (1-based) while guessing, rounds played when finished, 0 while choosing. */
    val round: Int,
    val result: DuelResult?,
)

@Serializable
data class MatchView(
    /** 1 for the first match in this room, 2 after a reset, ... */
    val number: Int,
    val duels: List<DuelSummary>,
    /** Duel wins per team. */
    val score: Score,
    val result: MatchResult?,
    /** Your own duel with full detail; null if you have no duel in this match. */
    val myDuel: MyDuelView?,
)

@Serializable
data class RoundEntry(
    val round: Int,
    /** null = missed (no guess before the deadline). */
    val guess: String?,
    val strikes: Int,
    val balls: Int,
)

@Serializable
data class MyDuelView(
    val index: Int,
    val myTeam: Team,
    val opponentId: String,
    val phase: DuelPhase,
    /** Current round, 1-based. 0 while choosing. */
    val round: Int,
    /** Server-time epoch ms when the current phase/round times out; null = no timer. */
    val deadline: Long?,
    val mySecret: String?,
    val opponentHasSecret: Boolean,
    /** Your guess for the current round, if already submitted. */
    val myPendingGuess: String?,
    val opponentGuessed: Boolean,
    /** Your guesses against the opponent's secret. */
    val myRounds: List<RoundEntry>,
    /** The opponent's guesses against your secret. */
    val opponentRounds: List<RoundEntry>,
    /** Rounds missed in a row (forfeit at [Limits.MISS_LIMIT]). */
    val myMisses: Int,
    val opponentMisses: Int,
    val result: DuelResult?,
    /** Revealed only after the duel ended. */
    val opponentSecret: String?,
)

@Serializable
enum class ChatKind {
    @SerialName("text") TEXT,
    @SerialName("phrase") PHRASE,
}

@Serializable
data class ChatMessage(
    /** Increasing per room. */
    val id: Long,
    val at: Long,
    /** null when the sender deleted their account. */
    val senderId: String?,
    val senderNickname: String?,
    val senderTag: String?,
    val senderTeam: Team?,
    val kind: ChatKind,
    /** Filtered text (kind = text). */
    val text: String?,
    /** Quick-phrase id (kind = phrase); the client shows its localized text. */
    val phraseId: String?,
)
