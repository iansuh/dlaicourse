package app.numbaseball.data.online

import app.numbaseball.core.protocol.AccountDto
import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.BlockListResponse
import app.numbaseball.core.protocol.CodeResponse
import app.numbaseball.core.protocol.CreateAccountRequest
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ErrorResponse
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.RedeemTransferRequest
import app.numbaseball.core.protocol.ReportRequest
import app.numbaseball.core.protocol.ReportResponse
import app.numbaseball.data.account.AccountApi
import app.numbaseball.data.account.ApiResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Server addresses derived from the configured base URL (for example `https://nb.example.com`). */
class ServerEndpoints(baseUrl: String) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    val api: HttpUrl = base.newBuilder().addPathSegments("api/v1").build()

    /** OkHttp accepts http(s) URLs for WebSockets and upgrades them (ws/wss). */
    val webSocket: HttpUrl = base.newBuilder().addPathSegments("ws/v1").build()
}

/** OkHttp implementation of the `/api/v1` REST endpoints (docs/protocol.md §3). */
class RestApi(
    private val http: OkHttpClient,
    private val endpoints: ServerEndpoints,
    private val clientVersion: String,
    private val json: Json = ProtocolJson,
) : AccountApi, SafetyApi {

    override suspend fun createAccount(nickname: String): ApiResult<AuthResponse> =
        call(request("accounts").post(body(CreateAccountRequest.serializer(), CreateAccountRequest(nickname))), AuthResponse.serializer())

    override suspend fun me(token: String): ApiResult<AccountDto> =
        call(request("me", token).get(), AccountDto.serializer())

    override suspend fun deleteAccount(token: String): ApiResult<Unit> =
        callUnit(request("me", token).delete())

    override suspend fun createTransferCode(token: String): ApiResult<CodeResponse> =
        call(request("me/transfer-code", token).post(EMPTY_BODY), CodeResponse.serializer())

    override suspend fun redeemTransferCode(code: String): ApiResult<AuthResponse> =
        call(request("transfer").post(body(RedeemTransferRequest.serializer(), RedeemTransferRequest(code))), AuthResponse.serializer())

    override suspend fun createDeletionCode(token: String): ApiResult<CodeResponse> =
        call(request("me/deletion-code", token).post(EMPTY_BODY), CodeResponse.serializer())

    override suspend fun blocks(token: String): ApiResult<BlockListResponse> =
        call(request("me/blocks", token).get(), BlockListResponse.serializer())

    override suspend fun block(token: String, playerId: String): ApiResult<Unit> =
        callUnit(request("me/blocks", token, playerId).put(EMPTY_BODY))

    override suspend fun unblock(token: String, playerId: String): ApiResult<Unit> =
        callUnit(request("me/blocks", token, playerId).delete())

    override suspend fun report(token: String, request: ReportRequest): ApiResult<ReportResponse> =
        call(request("reports", token).post(body(ReportRequest.serializer(), request)), ReportResponse.serializer())

    private fun request(path: String, token: String? = null, lastSegment: String? = null): Request.Builder {
        val url = endpoints.api.newBuilder().addPathSegments(path)
            .apply { if (lastSegment != null) addPathSegment(lastSegment) }
            .build()
        return Request.Builder().url(url)
            .header("X-Client-Version", clientVersion)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
    }

    private fun <T> body(serializer: KSerializer<T>, value: T): RequestBody =
        json.encodeToString(serializer, value).toRequestBody(JSON)

    private suspend fun <T> call(builder: Request.Builder, serializer: KSerializer<T>): ApiResult<T> =
        execute(builder.build()) { json.decodeFromString(serializer, it) }

    private suspend fun callUnit(builder: Request.Builder): ApiResult<Unit> = execute(builder.build()) { }

    private suspend fun <T> execute(request: Request, parse: (String) -> T): ApiResult<T> = try {
        runInterruptible(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (response.isSuccessful) {
                    ApiResult.Success(parse(text))
                } else {
                    val error = runCatching { json.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
                    ApiResult.Failure(response.code, error?.code ?: fallbackCode(response.code), error?.retryAfterSeconds)
                }
            }
        }
    } catch (e: IOException) {
        ApiResult.NetworkError
    } catch (e: SerializationException) {
        ApiResult.Failure(status = 200, code = ErrorCodes.INTERNAL)
    }

    private fun fallbackCode(status: Int): String = when (status) {
        401 -> ErrorCodes.UNAUTHORIZED
        403 -> ErrorCodes.BANNED
        404 -> ErrorCodes.NOT_FOUND
        429 -> ErrorCodes.RATE_LIMITED
        else -> ErrorCodes.INTERNAL
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)
    }
}
