package app.numbaseball.data.account

import app.numbaseball.core.protocol.AcceptTermsRequest
import app.numbaseball.testing.FakeAccountApi
import app.numbaseball.testing.account
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
        assertNull(registered.value.value.token)
    }

    @Test fun termsMustBeAcceptedWithTheAgeConfirmationAndAgainWhenTheVersionChanges() = runTest(UnconfinedTestDispatcher()) {
        api.meResult = ApiResult.Success(account().copy(termsVersion = "2026-11-v1"))
        val repo = AccountRepository(FakeAccountStore.registered(), api, backgroundScope)
        assertEquals(TermsState.Unknown, repo.terms.value)
        repo.loadTerms()
        assertEquals(TermsState.Required(updated = true, minimumAge = 14), repo.terms.value)
        assertEquals(ApiResult.Success(Unit), repo.acceptTerms(ageConfirmed = true))
        assertEquals(AcceptTermsRequest("2026-11-v2", ageConfirmed = true), api.acceptedTerms.single())
        assertEquals(TermsState.Accepted, repo.terms.value)
    }

    @Test fun transferRedemptionStoresTheNewToken() = runTest(UnconfinedTestDispatcher()) {
        val repo = AccountRepository(store, api, backgroundScope)
        assertTrue(repo.redeemTransferCode("ABCD-2345-EFGH") is ApiResult.Success)
        assertEquals("token-2", repo.token())
    }
}
