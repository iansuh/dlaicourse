package app.numbaseball.data.account

import app.numbaseball.core.protocol.AccountDto
import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.CodeResponse

/** Result of a REST call (docs/protocol.md §3). */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    /** The server answered with an `ErrorResponse`; localize by [code] (see ui/common/ErrorMessages.kt). */
    data class Failure(val status: Int, val code: String, val retryAfterSeconds: Long? = null) : ApiResult<Nothing>

    /** No answer at all: offline, DNS, TLS, timeout. */
    data object NetworkError : ApiResult<Nothing>
}

/** Account endpoints of `/api/v1`. */
interface AccountApi {
    suspend fun createAccount(nickname: String): ApiResult<AuthResponse>
    suspend fun me(token: String): ApiResult<AccountDto>
    suspend fun deleteAccount(token: String): ApiResult<Unit>
    suspend fun createTransferCode(token: String): ApiResult<CodeResponse>
    suspend fun redeemTransferCode(code: String): ApiResult<AuthResponse>
    suspend fun createDeletionCode(token: String): ApiResult<CodeResponse>
}

/**
 * Terms acceptance and web deletion requests (policy review R5, R6).
 * TODO(protocol): bind `PUT /api/v1/me/terms` and the deletion-request cancel call once docs/protocol.md defines their
 * bodies, and read `deletionRequestedAt` / the current terms version from the account and server config.
 */
interface PolicyApi {
    suspend fun acceptTerms(token: String, version: Int): ApiResult<Unit>
    suspend fun cancelDeletionRequest(token: String): ApiResult<Unit>
}

/** Until the endpoints exist: acceptance is stored on the device only, and there is never a pending web deletion. */
object PendingProtocolPolicyApi : PolicyApi {
    override suspend fun acceptTerms(token: String, version: Int): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun cancelDeletionRequest(token: String): ApiResult<Unit> = ApiResult.Success(Unit)
}

object TermsPolicy {
    /** TODO(protocol): use the version the server reports. */
    const val CURRENT_VERSION = 1
}
