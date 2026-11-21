package app.numbaseball.data.online

import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ReportReason
import app.numbaseball.core.protocol.ReportRequest
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.ApiResult

/** Report and block (Play UGC policy). Blocking also hides the player's lines already on screen. */
class SafetyRepository(
    private val api: SafetyApi,
    private val accounts: AccountRepository,
    private val session: OnlineSession,
) {
    suspend fun report(targetId: String, reason: ReportReason, roomId: String?, messageId: Long?, note: String?): ApiResult<Unit> {
        val token = accounts.token() ?: return ApiResult.Failure(401, ErrorCodes.UNAUTHORIZED)
        val request = ReportRequest(targetId, reason, roomId, messageId, note?.takeIf { it.isNotBlank() }?.take(NOTE_MAX))
        return when (val r = api.report(token, request)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> ApiResult.Failure(r.status, r.code, r.retryAfterSeconds)
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    suspend fun block(playerId: String): ApiResult<Unit> {
        val token = accounts.token() ?: return ApiResult.Failure(401, ErrorCodes.UNAUTHORIZED)
        val result = api.block(token, playerId)
        if (result is ApiResult.Success) session.hideSender(playerId)
        return result
    }

    private companion object {
        const val NOTE_MAX = 500
    }
}
