package app.numbaseball.data.online

import app.numbaseball.core.protocol.BlockListResponse
import app.numbaseball.core.protocol.ReportRequest
import app.numbaseball.core.protocol.ReportResponse
import app.numbaseball.data.account.ApiResult

/** Block and report endpoints (docs/protocol.md §3.2, §3.3). */
interface SafetyApi {
    suspend fun blocks(token: String): ApiResult<BlockListResponse>
    suspend fun block(token: String, playerId: String): ApiResult<Unit>
    suspend fun unblock(token: String, playerId: String): ApiResult<Unit>
    suspend fun report(token: String, request: ReportRequest): ApiResult<ReportResponse>
}
