package app.numbaseball.data.online

import app.numbaseball.core.protocol.BlockedPlayer
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.Stats
import app.numbaseball.core.protocol.ReportReason
import app.numbaseball.core.protocol.ReportRequest
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    suspend fun block(playerId: String, nickname: String = "", tag: String = ""): ApiResult<Unit> {
        val token = accounts.token() ?: return ApiResult.Failure(401, ErrorCodes.UNAUTHORIZED)
        val result = api.block(token, playerId)
        if (result is ApiResult.Success) {
            session.hideSender(playerId)
            _blocked.update { list -> if (list.any { it.id == playerId }) list else list + BlockedPlayer(playerId, nickname, tag) }
        }
        return result
    }

    suspend fun unblock(playerId: String): ApiResult<Unit> {
        val token = accounts.token() ?: return ApiResult.Failure(401, ErrorCodes.UNAUTHORIZED)
        val result = api.unblock(token, playerId)
        if (result is ApiResult.Success) _blocked.update { list -> list.filter { it.id != playerId } }
        return result
    }

    private val _blocked = MutableStateFlow<List<BlockedPlayer>>(emptyList())

    /** Blocked players as last loaded by [loadBlocked] (Settings > Blocked players, player card state). */
    val blocked: StateFlow<List<BlockedPlayer>> = _blocked.asStateFlow()

    suspend fun loadBlocked(): ApiResult<Unit> {
        val token = accounts.token() ?: return ApiResult.Failure(401, ErrorCodes.UNAUTHORIZED)
        return when (val r = api.blocks(token)) {
            is ApiResult.Success -> {
                _blocked.value = r.value.blocked
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> ApiResult.Failure(r.status, r.code, r.retryAfterSeconds)
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    /** The player card's record (protocol 1.1 §3.4). `not_found` covers unknown, deleted and blocked players. */
    suspend fun playerRecord(playerId: String): ApiResult<Stats> {
        val token = accounts.token() ?: return ApiResult.Failure(401, ErrorCodes.UNAUTHORIZED)
        return when (val r = api.playerCard(token, playerId)) {
            is ApiResult.Success -> ApiResult.Success(r.value.stats)
            is ApiResult.Failure -> ApiResult.Failure(r.status, r.code, r.retryAfterSeconds)
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    private companion object {
        const val NOTE_MAX = 500
    }
}
