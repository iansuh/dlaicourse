package app.numbaseball.core.protocol

import kotlinx.serialization.Serializable

// REST bodies for /api/v1. Every error response uses [ErrorResponse].

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    /** Set for `rate_limited` / `too_many_attempts`: seconds until a retry can succeed. */
    val retryAfterSeconds: Long? = null,
)

@Serializable
data class Stats(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0)

@Serializable
data class AccountDto(
    /** Public player id. Also used as `playerId` in rooms, for blocking and reporting. Not a secret. */
    val id: String,
    val nickname: String,
    /** Four digits, zero padded ("0420"). Display form is `nickname#tag`. */
    val tag: String,
    val stats: Stats,
    /** Epoch milliseconds. */
    val createdAt: Long,
)

@Serializable
data class CreateAccountRequest(val nickname: String)

/** Returned by account creation and transfer redemption. Store [token]; it is shown only once. */
@Serializable
data class AuthResponse(val token: String, val account: AccountDto)

@Serializable
data class RenameRequest(val nickname: String)

/** A one-time code. [expiresAt] is epoch milliseconds. */
@Serializable
data class CodeResponse(val code: String, val expiresAt: Long)

@Serializable
data class RedeemTransferRequest(val code: String)

@Serializable
data class BlockedPlayer(val id: String, val nickname: String, val tag: String)

@Serializable
data class BlockListResponse(val blocked: List<BlockedPlayer>)

@Serializable
data class ReportRequest(
    val targetId: String,
    val reason: ReportReason,
    /** Room the report is about (from `room.id`). Needed for a message report. */
    val roomId: String? = null,
    /** Chat message id within [roomId]. The server stores a snapshot of it and the recent chat. */
    val messageId: Long? = null,
    /** Optional free text, at most 500 characters. */
    val note: String? = null,
)

@Serializable
data class ReportResponse(val reportId: Long)

@Serializable
data class ServerInfo(
    val protocol: Int,
    val serverTime: Long,
    val digits: List<Int>,
    val teamSizes: List<Int>,
    val roundSeconds: List<Int>,
    val quickRoundSeconds: Int,
    val chatMaxLength: Int,
    val maxMessageBytes: Int,
    val quickPhrases: List<String>,
)

// ---- admin (Authorization: Bearer <ADMIN_TOKEN>) ----

@Serializable
data class AdminReport(
    val id: Long,
    val createdAt: Long,
    val reporterId: String?,
    val targetId: String?,
    val targetDisplay: String?,
    val reason: ReportReason,
    val note: String?,
    val roomId: String?,
    val messageId: Long?,
    /** JSON text of [ReportSnapshot], or null when no live room context was available. */
    val snapshot: ReportSnapshot?,
    val status: String,
    val resolution: String?,
    val resolvedAt: Long?,
)

@Serializable
data class ReportSnapshot(
    val message: ChatMessage?,
    val recent: List<ChatMessage>,
)

@Serializable
data class AdminReportList(val reports: List<AdminReport>)

@Serializable
data class ResolveReportRequest(val resolution: String)

@Serializable
data class BanRequest(val reason: String)
