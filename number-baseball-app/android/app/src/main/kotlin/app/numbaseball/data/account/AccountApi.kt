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
}

/**
 * Terms acceptance (policy review R6).
 * TODO(protocol): bind `PUT /api/v1/me/terms {version, ageConfirmed}` and read the current terms version from the server
 * once docs/protocol.md defines them.
 */
fun interface PolicyApi {
    suspend fun acceptTerms(token: String, version: Int): ApiResult<Unit>
}

/** Until the endpoint exists the acceptance is stored on the device only. */
val PendingProtocolPolicyApi = PolicyApi { _, _ -> ApiResult.Success(Unit) }

object TermsPolicy {
    /** TODO(protocol): use the version the server reports. */
    const val CURRENT_VERSION = 1
}
