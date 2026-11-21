package app.numbaseball.data.account

import app.numbaseball.core.protocol.Stats

/** The player's public identity. Display form is `nickname#tag`. */
data class Profile(val id: String, val nickname: String, val tag: String) {
    val displayName: String get() = "$nickname#$tag"
}

sealed interface AccountState {
    data object Loading : AccountState

    /** First launch: no nickname yet. */
    data object NeedsNickname : AccountState

    /** A nickname is chosen but the server account does not exist yet (offline at first launch, or token revoked). */
    data class Unregistered(val nickname: String) : AccountState

    /** [stats] is null until the server has reported them in this process. */
    data class Registered(val profile: Profile, val stats: Stats?) : AccountState
}

/** What the stored account looks like on disk. Only this store is included in Auto Backup. */
data class StoredAccount(
    val token: String?,
    val id: String?,
    val nickname: String?,
    val tag: String?,
)
