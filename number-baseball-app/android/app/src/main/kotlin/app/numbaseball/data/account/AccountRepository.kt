package app.numbaseball.data.account

import app.numbaseball.core.protocol.AcceptTermsRequest
import app.numbaseball.core.protocol.AccountDto
import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.CodeResponse
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ServerInfo
import app.numbaseball.core.protocol.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface RegistrationResult {
    data class Registered(val profile: Profile) : RegistrationResult

    /** The server refused the nickname ([code] is `invalid_nickname`, `nickname_profanity` or `nickname_unavailable`). */
    data class Rejected(val code: String) : RegistrationResult

    /** Server unreachable or busy: the nickname is kept and registration is retried before going online. */
    data object Deferred : RegistrationResult
}

/**
 * Device account bootstrap (README "Accounts"): the player picks a nickname, the server creates the account and returns a
 * token that we keep in the backed-up store. Offline at first launch is fine: vs-computer works and registration is
 * retried by [ensureRegistered] before any online feature.
 */
class AccountRepository(
    private val store: AccountStore,
    private val api: AccountApi,
    scope: CoroutineScope,
) {
    private val stats = MutableStateFlow<Stats?>(null)
    /** Terms version the account accepted (from `AccountDto`); null = never or not loaded. */
    private val acceptedTerms = MutableStateFlow<String?>(null)
    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)

    /** `GET /config`: current terms version, minimum age, quick-match chat rule. Null until loaded. */
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()
    private val registrationLock = Mutex()

    val state: StateFlow<AccountState> = combine(store.account, stats) { stored, s -> stored.toState(s) }
        .stateIn(scope, SharingStarted.Eagerly, AccountState.Loading)

    /** Terms gate (docs/protocol.md §3.1): the account must have accepted the server's current version. */
    val terms: StateFlow<TermsState> = combine(_serverInfo, acceptedTerms) { info, accepted ->
        when {
            info == null -> TermsState.Unknown
            accepted == info.termsVersion -> TermsState.Accepted
            else -> TermsState.Required(updated = accepted != null, minimumAge = info.minimumAge)
        }
    }.stateIn(scope, SharingStarted.Eagerly, TermsState.Unknown)

    suspend fun token(): String? = store.account.first().token

    /** First run: validates locally, then tries to create the account right away. */
    suspend fun chooseNickname(raw: String): RegistrationResult {
        val nickname = NicknameRules.normalize(raw)
        return registrationLock.withLock {
            val result = register(nickname)
            if (result == RegistrationResult.Deferred) store.saveNickname(nickname)
            result
        }
    }

    /** Creates the server account for a nickname chosen while offline. Safe to call repeatedly. */
    suspend fun ensureRegistered(): RegistrationResult = registrationLock.withLock {
        val stored = store.account.first()
        val profile = stored.toProfile()
        when {
            stored.token != null && profile != null -> RegistrationResult.Registered(profile)
            stored.nickname != null -> register(stored.nickname)
            else -> RegistrationResult.Rejected(ErrorCodes.INVALID_NICKNAME)
        }
    }

    suspend fun redeemTransferCode(code: String): ApiResult<Profile> = registrationLock.withLock {
        when (val r = api.redeemTransferCode(code)) {
            is ApiResult.Success -> ApiResult.Success(save(r.value))
            is ApiResult.Failure -> ApiResult.Failure(r.status, r.code, r.retryAfterSeconds)
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    /** Refreshes nickname, tag and stats from `GET /me`. */
    suspend fun refresh() {
        val token = token() ?: return
        when (val r = api.me(token)) {
            is ApiResult.Success -> onAccountUpdate(r.value)
            is ApiResult.Failure -> if (r.status == 401) onTokenRejected()
            ApiResult.NetworkError -> Unit
        }
    }

    /** Fresh account data from the server (for example the `welcome` message). */
    suspend fun onAccountUpdate(account: AccountDto) {
        if (token() == null) return
        store.saveProfile(account.toProfile())
        stats.value = account.stats
        acceptedTerms.value = account.termsVersion
    }

    /** HTTP 401: the token is unknown (account deleted or moved to another device). The nickname stays for a new account. */
    suspend fun onTokenRejected() {
        stats.value = null
        acceptedTerms.value = null
        store.clearToken()
    }

    /** Loads the server config and the account's accepted terms version (for the terms gate). */
    suspend fun loadTerms() {
        when (val r = api.serverInfo()) {
            is ApiResult.Success -> _serverInfo.value = r.value
            else -> Unit
        }
        refresh()
    }

    /** `PUT /me/terms` with the current version and the explicit minimum-age confirmation. */
    suspend fun acceptTerms(ageConfirmed: Boolean): ApiResult<Unit> {
        val version = _serverInfo.value?.termsVersion ?: return ApiResult.NetworkError
        val result = withToken { api.acceptTerms(it, AcceptTermsRequest(version, ageConfirmed)) }
        return when (result) {
            is ApiResult.Success -> {
                onAccountUpdate(result.value)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> {
                if (result.code == ErrorCodes.TERMS_OUTDATED) loadTerms()
                ApiResult.Failure(result.status, result.code, result.retryAfterSeconds)
            }
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    /** PATCH /me: keeps the tag when free, otherwise the server assigns a new one (docs/protocol.md §2). */
    suspend fun rename(nickname: String): ApiResult<Unit> {
        val result = withToken { api.rename(it, nickname.trim()) }
        return when (result) {
            is ApiResult.Success -> {
                onAccountUpdate(result.value)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> ApiResult.Failure(result.status, result.code, result.retryAfterSeconds)
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    suspend fun createTransferCode(): ApiResult<CodeResponse> = withToken { api.createTransferCode(it) }

    suspend fun deleteAccount(): ApiResult<Unit> {
        val token = token()
        val result = if (token == null) ApiResult.Success(Unit) else api.deleteAccount(token)
        if (result is ApiResult.Success || (result is ApiResult.Failure && result.status == 401)) {
            stats.value = null
            acceptedTerms.value = null
            store.clear()
            return ApiResult.Success(Unit)
        }
        return result
    }

    private suspend fun register(nickname: String): RegistrationResult =
        when (val r = api.createAccount(nickname)) {
            is ApiResult.Success -> RegistrationResult.Registered(save(r.value))
            is ApiResult.Failure ->
                if (r.code in NICKNAME_ERRORS) RegistrationResult.Rejected(r.code) else RegistrationResult.Deferred
            ApiResult.NetworkError -> RegistrationResult.Deferred
        }

    private suspend fun save(auth: AuthResponse): Profile {
        val profile = auth.account.toProfile()
        store.saveRegistered(auth.token, profile)
        stats.value = auth.account.stats
        acceptedTerms.value = auth.account.termsVersion
        return profile
    }

    private suspend fun <T> withToken(call: suspend (String) -> ApiResult<T>): ApiResult<T> {
        val token = token() ?: return ApiResult.Failure(401, ErrorCodes.UNAUTHORIZED)
        val result = call(token)
        if (result is ApiResult.Failure && result.status == 401) onTokenRejected()
        return result
    }

    private companion object {
        val NICKNAME_ERRORS = setOf(ErrorCodes.INVALID_NICKNAME, ErrorCodes.NICKNAME_PROFANITY, ErrorCodes.NICKNAME_UNAVAILABLE)
    }
}

private fun AccountDto.toProfile() = Profile(id = id, nickname = nickname, tag = tag)

private fun StoredAccount.toProfile(): Profile? =
    if (id != null && nickname != null && tag != null) Profile(id, nickname, tag) else null

private fun StoredAccount.toState(stats: Stats?): AccountState {
    val profile = toProfile()
    return when {
        token != null && profile != null -> AccountState.Registered(profile, stats)
        nickname != null -> AccountState.Unregistered(nickname)
        else -> AccountState.NeedsNickname
    }
}
