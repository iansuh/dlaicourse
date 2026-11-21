package app.numbaseball.data.account

import app.numbaseball.testing.FakeAccountApi
import app.numbaseball.testing.FakeAccountStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryTest {
    private val store = FakeAccountStore()
    private val api = FakeAccountApi()

    @Test fun firstLaunchNeedsANicknameThenRegisters() = runTest(UnconfinedTestDispatcher()) {
        val repo = AccountRepository(store, api, backgroundScope)
        assertEquals(AccountState.NeedsNickname, repo.state.value)
        val result = repo.chooseNickname("  Mina ")
        assertEquals(listOf("Mina"), api.createdNicknames)
        assertTrue(result is RegistrationResult.Registered)
        val state = repo.state.value as AccountState.Registered
        assertEquals("Mina#0420", state.profile.displayName)
        assertEquals(3, state.stats?.wins)
        assertEquals("token-1", repo.token())
    }

    @Test fun offlineFirstLaunchKeepsTheNicknameAndRegistersLater() = runTest(UnconfinedTestDispatcher()) {
        val repo = AccountRepository(store, api, backgroundScope)
        api.createResult = ApiResult.NetworkError
        assertEquals(RegistrationResult.Deferred, repo.chooseNickname("Mina"))
        assertEquals(AccountState.Unregistered("Mina"), repo.state.value)

        api.createResult = FakeAccountApi().createResult
        assertTrue(repo.ensureRegistered() is RegistrationResult.Registered)
        assertTrue(repo.state.value is AccountState.Registered)
    }

    @Test fun aRejectedNicknameIsNotStored() = runTest(UnconfinedTestDispatcher()) {
        val repo = AccountRepository(store, api, backgroundScope)
        api.createResult = ApiResult.Failure(400, "nickname_profanity")
        assertEquals(RegistrationResult.Rejected("nickname_profanity"), repo.chooseNickname("bad"))
        assertEquals(AccountState.NeedsNickname, repo.state.value)
    }

    @Test fun a401ClearsTheTokenButKeepsTheNickname() = runTest(UnconfinedTestDispatcher()) {
        val repo = AccountRepository(FakeAccountStore.registered(), api, backgroundScope)
        api.meResult = ApiResult.Failure(401, "unauthorized")
        repo.refresh()
        assertEquals(AccountState.Unregistered("Mina"), repo.state.value)
        assertNull(repo.token())
    }

    @Test fun deletingClearsEverything() = runTest(UnconfinedTestDispatcher()) {
        val registered = FakeAccountStore.registered()
        val repo = AccountRepository(registered, api, backgroundScope)
        assertEquals(ApiResult.Success(Unit), repo.deleteAccount())
        assertEquals(AccountState.NeedsNickname, repo.state.value)
        assertNull(registered.value.value.acceptedTermsVersion)
    }

    @Test fun termsMustBeAcceptedAgainWhenTheVersionChanges() = runTest(UnconfinedTestDispatcher()) {
        val registered = FakeAccountStore.registered()
        val repo = AccountRepository(registered, api, backgroundScope, currentTermsVersion = 2)
        assertEquals(TermsState.Required(updated = true), repo.terms.value)
        assertEquals(ApiResult.Success(Unit), repo.acceptTerms())
        assertEquals(TermsState.Accepted, repo.terms.value)
        assertEquals(2, registered.value.value.acceptedTermsVersion)
    }

    @Test fun transferRedemptionStoresTheNewToken() = runTest(UnconfinedTestDispatcher()) {
        val repo = AccountRepository(store, api, backgroundScope)
        assertTrue(repo.redeemTransferCode("ABCD-2345-EFGH") is ApiResult.Success)
        assertEquals("token-2", repo.token())
    }
}
