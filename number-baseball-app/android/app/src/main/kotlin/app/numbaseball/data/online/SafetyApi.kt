package app.numbaseball.data.online

import app.numbaseball.core.protocol.BlockListResponse
import app.numbaseball.core.protocol.PlayerCard
import app.numbaseball.core.protocol.ReportRequest
import app.numbaseball.core.protocol.ReportResponse
import app.numbaseball.data.account.ApiResult

/** Block, report and player-card endpoints (docs/protocol.md §3.2–3.4). */
interface SafetyApi {
    suspend fun blocks(token: String): ApiResult<BlockListResponse>
    suspend fun block(token: String, playerId: String): ApiResult<Unit>
    suspend fun unblock(token: String, playerId: String): ApiResult<Unit>
    suspend fun report(token: String, request: ReportRequest): ApiResult<ReportResponse>

    /** Protocol 1.1 §3.4: another player's public record by id or `Nickname#1234`; 404 also when either side blocked. */
    suspend fun playerCard(token: String, key: String): ApiResult<PlayerCard>
}
