package app.numbaseball.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire protocol version. The WebSocket path (`/ws/v1`) and REST prefix (`/api/v1`) carry the major version.
 * Additive changes (new optional fields, new message types, new error codes) keep this number.
 * See docs/protocol.md for the full contract.
 */
const val PROTOCOL_VERSION = 1

/** The one JSON configuration both sides must use. Unknown keys are ignored for forward compatibility. */
val ProtocolJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

/** Fixed game and server limits shared by app and server. */
object Limits {
    val DIGITS = 3..6
    val TEAM_SIZE = 1..5
    /** Allowed round timers for private rooms. 0 means "no timer". */
    val ROUND_SECONDS = listOf(0, 30, 60, 120)
    const val QUICK_ROUND_SECONDS = 60
    /** Missed rounds in a row that forfeit a duel. */
    const val MISS_LIMIT = 2
    const val DISCONNECT_GRACE_MS = 60_000L
    const val CHAT_MAX_LENGTH = 100
    const val CHAT_HISTORY = 50
    const val NICKNAME_MIN = 2
    const val NICKNAME_MAX = 12
    const val ROOM_CODE_LENGTH = 5
    /** Largest WebSocket text message the server processes, in UTF-8 bytes. */
    const val MAX_MESSAGE_BYTES = 2048
    /** Frames larger than this make the server close the connection (code 1009). */
    const val MAX_FRAME_BYTES = 16 * 1024
}

@Serializable
enum class Team {
    @SerialName("A") A,
    @SerialName("B") B;

    val other: Team get() = if (this == A) B else A
}

@Serializable
enum class RoomKind {
    @SerialName("quick") QUICK,
    @SerialName("private") PRIVATE,
}

@Serializable
enum class RoomPhase {
    @SerialName("lobby") LOBBY,
    @SerialName("playing") PLAYING,
    @SerialName("finished") FINISHED,
}

@Serializable
enum class DuelPhase {
    /** Both players pick their secret number. */
    @SerialName("choosing") CHOOSING,
    /** Simultaneous guessing rounds. */
    @SerialName("guessing") GUESSING,
    @SerialName("finished") FINISHED,
}

@Serializable
enum class EndReason {
    /** The winner guessed the secret. */
    @SerialName("solved") SOLVED,
    /** Both guessed the secret in the same round (draw). */
    @SerialName("both_solved") BOTH_SOLVED,
    /** The loser missed [Limits.MISS_LIMIT] rounds in a row. */
    @SerialName("timeout") TIMEOUT,
    /** Both reached the miss limit in the same round (draw). */
    @SerialName("both_timeout") BOTH_TIMEOUT,
    /** The loser stayed disconnected longer than the grace period (draw if both at the same moment). */
    @SerialName("disconnect") DISCONNECT,
    /** The loser forfeited or left the room. */
    @SerialName("forfeit") FORFEIT,
    /** The team match was decided before this duel ended (no winner). */
    @SerialName("cancelled") CANCELLED,
}

@Serializable
enum class ReportReason {
    @SerialName("spam") SPAM,
    @SerialName("harassment") HARASSMENT,
    @SerialName("hate") HATE,
    @SerialName("sexual") SEXUAL,
    @SerialName("cheating") CHEATING,
    @SerialName("nickname") NICKNAME,
    @SerialName("other") OTHER,
}

/** Why the server says you are no longer in a room. */
@Serializable
enum class LeaveReason {
    /** You sent `room_leave`. */
    @SerialName("left") LEFT,
    /** You were disconnected longer than the grace period while the room was not playing. */
    @SerialName("inactive") INACTIVE,
    @SerialName("banned") BANNED,
    @SerialName("account_deleted") ACCOUNT_DELETED,
    /** The server closed the room (shutdown or idle cleanup). */
    @SerialName("closed") CLOSED,
}

/** Error codes used in both REST error bodies and WebSocket `error` messages. Clients must treat unknown codes generically. */
object ErrorCodes {
    // generic
    const val BAD_REQUEST = "bad_request"
    const val MALFORMED_MESSAGE = "malformed_message"
    const val MESSAGE_TOO_LARGE = "message_too_large"
    const val RATE_LIMITED = "rate_limited"
    const val NOT_FOUND = "not_found"
    const val INTERNAL = "internal_error"
    const val UNSUPPORTED_VERSION = "unsupported_version"

    // auth / accounts
    const val UNAUTHORIZED = "unauthorized"
    const val BANNED = "banned"
    const val INVALID_NICKNAME = "invalid_nickname"
    const val NICKNAME_PROFANITY = "nickname_profanity"
    const val NICKNAME_UNAVAILABLE = "nickname_unavailable"
    const val INVALID_CODE = "invalid_code"
    const val TOO_MANY_ATTEMPTS = "too_many_attempts"
    const val SESSION_REPLACED = "session_replaced"

    // rooms / matchmaking
    const val ROOM_NOT_FOUND = "room_not_found"
    const val ROOM_FULL = "room_full"
    const val TEAM_FULL = "team_full"
    const val ALREADY_IN_ROOM = "already_in_room"
    const val NOT_IN_ROOM = "not_in_room"
    const val NOT_HOST = "not_host"
    const val INVALID_SETTINGS = "invalid_settings"
    const val MATCH_IN_PROGRESS = "match_in_progress"
    const val WRONG_PHASE = "wrong_phase"
    const val ALREADY_QUEUED = "already_queued"
    const val NOT_QUEUED = "not_queued"

    // game
    const val INVALID_SECRET = "invalid_secret"
    const val INVALID_GUESS = "invalid_guess"
    const val SECRET_ALREADY_SET = "secret_already_set"
    const val ALREADY_GUESSED = "already_guessed"
    const val STALE_ROUND = "stale_round"
    const val NOT_IN_DUEL = "not_in_duel"

    // chat
    const val CHAT_EMPTY = "chat_empty"
    const val CHAT_TOO_LONG = "chat_too_long"
    const val CHAT_RATE_LIMITED = "chat_rate_limited"
    const val UNKNOWN_PHRASE = "unknown_phrase"
}
