package app.numbaseball.testing

import app.numbaseball.core.protocol.AcceptTermsRequest
import app.numbaseball.core.protocol.AccountDto
import app.numbaseball.core.protocol.Ack
import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.BlockListResponse
import app.numbaseball.core.protocol.ClientMessage
import app.numbaseball.core.protocol.CodeResponse
import app.numbaseball.core.protocol.ReportRequest
import app.numbaseball.core.protocol.ReportResponse
import app.numbaseball.core.protocol.ServerInfo
import app.numbaseball.core.protocol.ServerMessage
import app.numbaseball.core.protocol.Stats
import app.numbaseball.data.account.AccountApi
import app.numbaseball.data.account.AccountStore
import app.numbaseball.data.account.ApiResult
import app.numbaseball.data.account.Profile
import app.numbaseball.data.account.StoredAccount
import app.numbaseball.data.age.AgeBand
import app.numbaseball.data.age.AgeStore
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.OnlineClient
import app.numbaseball.data.online.SafetyApi
import app.numbaseball.data.settings.AppSettings
import app.numbaseball.data.settings.LanguageSettings
import app.numbaseball.data.settings.SettingsRepository
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

fun account(id: String = "k2m9x4q1ab7p", nickname: String = "Mina", tag: String = "0420", stats: Stats = Stats(3, 1, 2)) =
    AccountDto(id = id, nickname = nickname, tag = tag, stats = stats, createdAt = 1_795_000_000_000)

class FakeAccountStore(initial: StoredAccount = StoredAccount(null, null, null, null)) : AccountStore {
    val value = MutableStateFlow(initial)
    override val account: Flow<StoredAccount> = value

    override suspend fun saveNickname(nickname: String) = value.update { it.copy(nickname = nickname) }
    override suspend fun saveRegistered(token: String, profile: Profile) =
        value.update { it.copy(token = token, id = profile.id, nickname = profile.nickname, tag = profile.tag) }
    override suspend fun saveProfile(profile: Profile) = value.update { it.copy(id = profile.id, nickname = profile.nickname, tag = profile.tag) }
    override suspend fun clearToken() = value.update { it.copy(token = null, id = null, tag = null) }
    override suspend fun clear() {
        value.value = StoredAccount(null, null, null, null)
    }

    companion object {
        fun registered() = FakeAccountStore(StoredAccount("token-1", "k2m9x4q1ab7p", "Mina", "0420"))
    }
}

class FakeAccountApi : AccountApi {
    var createResult: ApiResult<AuthResponse> = ApiResult.Success(AuthResponse("token-1", account()))
    var meResult: ApiResult<AccountDto> = ApiResult.Success(account())
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var codeResult: ApiResult<CodeResponse> = ApiResult.Success(CodeResponse("ABCD2345EFGH", 0))
    var redeemResult: ApiResult<AuthResponse> = ApiResult.Success(AuthResponse("token-2", account()))
    val createdNicknames = mutableListOf<String>()

    override suspend fun createAccount(nickname: String): ApiResult<AuthResponse> {
        createdNicknames += nickname
        return createResult
    }

    override suspend fun me(token: String) = meResult
    override suspend fun deleteAccount(token: String) = deleteResult
    override suspend fun rename(token: String, nickname: String): ApiResult<AccountDto> = ApiResult.Success(account().copy(nickname = nickname))
    var serverInfo = ServerInfo(1, 0, listOf(3, 4, 5, 6), listOf(1, 2, 3, 4, 5), listOf(30, 60, 120, 600), 60, 100, 2048, emptyList(), termsVersion = "2026-11-v2")
    var acceptTermsResult: ApiResult<AccountDto>? = null
    val acceptedTerms = mutableListOf<AcceptTermsRequest>()

    override suspend fun serverInfo(): ApiResult<ServerInfo> = ApiResult.Success(serverInfo)
    override suspend fun acceptTerms(token: String, request: AcceptTermsRequest): ApiResult<AccountDto> {
        acceptedTerms += request
        return acceptTermsResult ?: ApiResult.Success(account().copy(termsVersion = request.version))
    }
    override suspend fun createTransferCode(token: String) = codeResult
    override suspend fun redeemTransferCode(code: String) = redeemResult
}

class FakeSafetyApi : SafetyApi {
    val reports = mutableListOf<ReportRequest>()
    val blocked = mutableListOf<String>()
    var reportResult: ApiResult<ReportResponse> = ApiResult.Success(ReportResponse(1))

    override suspend fun blocks(token: String): ApiResult<BlockListResponse> = ApiResult.Success(BlockListResponse(emptyList()))
    override suspend fun block(token: String, playerId: String): ApiResult<Unit> {
        blocked += playerId
        return ApiResult.Success(Unit)
    }
    override suspend fun unblock(token: String, playerId: String): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun report(token: String, request: ReportRequest): ApiResult<ReportResponse> {
        reports += request
        return reportResult
    }
    var cardResult: ApiResult<app.numbaseball.core.protocol.PlayerCard> = ApiResult.Failure(404, "not_found")
    val cardKeys = mutableListOf<String>()
    override suspend fun playerCard(token: String, key: String): ApiResult<app.numbaseball.core.protocol.PlayerCard> {
        cardKeys += key
        return cardResult
    }
}

/** In-memory online client. Messages with a `rid` are acknowledged at once unless [errorFor] says otherwise. */
class FakeOnlineClient : OnlineClient {
    val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = mutableState
    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    override val messages: SharedFlow<ServerMessage> = _messages
    val sent = mutableListOf<ClientMessage>()
    var connected = true
    var users = 0
    var autoAck = true
    var errorFor: ((ClientMessage) -> ServerMessage?)? = null
    private val rids = AtomicLong()

    override fun acquire() {
        users++
    }

    override fun release() {
        users--
    }

    override fun retry() = Unit
    override fun setAppInForeground(foreground: Boolean) = Unit
    override fun nextRid(): Long = rids.incrementAndGet()

    override fun send(message: ClientMessage): Boolean {
        if (!connected) return false
        sent += message
        val rid = message.rid
        if (rid != null && autoAck) _messages.tryEmit(errorFor?.invoke(message) ?: Ack(rid))
        return true
    }

    suspend fun receive(message: ServerMessage) = _messages.emit(message)
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val value = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = value
    override suspend fun update(change: (AppSettings) -> AppSettings) = value.update(change)
}

class FakeLanguageSettings(var tag: String? = null) : LanguageSettings {
    override fun current(): String? = tag
    override fun set(tag: String?) {
        this.tag = tag
    }
}

class FakeAgeStore(initial: AgeBand? = null) : AgeStore {
    val value = MutableStateFlow(initial)
    override val band: Flow<AgeBand?> = value
    override suspend fun save(band: AgeBand) {
        value.value = band
    }
}
