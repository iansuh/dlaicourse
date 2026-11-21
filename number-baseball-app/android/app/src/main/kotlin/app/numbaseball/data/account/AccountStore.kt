package app.numbaseball.data.account

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists the account token and identity. */
interface AccountStore {
    val account: Flow<StoredAccount>
    suspend fun saveNickname(nickname: String)
    suspend fun saveRegistered(token: String, profile: Profile)
    suspend fun saveProfile(profile: Profile)
    suspend fun saveAcceptedTerms(version: Int)
    /** Forgets the token but keeps the nickname, so a new account can be created with it. */
    suspend fun clearToken()
    suspend fun clear()
}

object AccountStoreFiles {
    /** DataStore name; the file is `files/datastore/account.preferences_pb`, the only path in the backup rules. */
    const val ACCOUNT = "account"
}

private val Context.accountDataStore by preferencesDataStore(name = AccountStoreFiles.ACCOUNT)

class DataStoreAccountStore(context: Context) : AccountStore {
    private val store = context.applicationContext.accountDataStore

    override val account: Flow<StoredAccount> = store.data.map { p ->
        StoredAccount(token = p[TOKEN], id = p[ID], nickname = p[NICKNAME], tag = p[TAG], acceptedTermsVersion = p[TERMS])
    }

    override suspend fun saveNickname(nickname: String) {
        store.edit { it[NICKNAME] = nickname }
    }

    override suspend fun saveRegistered(token: String, profile: Profile) {
        store.edit {
            it[TOKEN] = token
            it.putProfile(profile)
        }
    }

    override suspend fun saveProfile(profile: Profile) {
        store.edit { it.putProfile(profile) }
    }

    override suspend fun saveAcceptedTerms(version: Int) {
        store.edit { it[TERMS] = version }
    }

    override suspend fun clearToken() {
        store.edit {
            it.remove(TOKEN)
            it.remove(ID)
            it.remove(TAG)
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.putProfile(profile: Profile) {
        this[ID] = profile.id
        this[NICKNAME] = profile.nickname
        this[TAG] = profile.tag
    }

    private companion object {
        val TOKEN: Preferences.Key<String> = stringPreferencesKey("token")
        val ID: Preferences.Key<String> = stringPreferencesKey("id")
        val NICKNAME: Preferences.Key<String> = stringPreferencesKey("nickname")
        val TAG: Preferences.Key<String> = stringPreferencesKey("tag")
        val TERMS: Preferences.Key<Int> = intPreferencesKey("accepted_terms_version")
    }
}
