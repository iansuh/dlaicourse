package app.numbaseball.ui.terms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.TermsState
import app.numbaseball.data.age.AgeBand
import app.numbaseball.data.age.AgeRepository
import app.numbaseball.data.age.AgeState
import app.numbaseball.ui.common.containerViewModel
import app.numbaseball.ui.common.toUiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TermsUiState(
    val terms: TermsState = TermsState.Unknown,
    val version: String? = null,
    val submitting: Boolean = false,
    /** Bumped on each failed acceptance (toast). */
    val failed: Int = 0,
)

/**
 * The age was confirmed on the first-launch screen (01 §0), so `ageConfirmed` sent with the acceptance comes from that
 * stored answer rather than a second checkbox (design 13: the gate never names the number).
 */
class TermsViewModel(private val accounts: AccountRepository, private val ages: AgeRepository) : ViewModel() {
    private val local = MutableStateFlow(TermsUiState(terms = accounts.terms.value))

    val state: StateFlow<TermsUiState> = combine(local, accounts.terms, accounts.serverInfo) { ui, terms, info ->
        ui.copy(terms = terms, version = info?.termsVersion)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch { accounts.loadTerms() }
    }

    fun onAgree() {
        val age = ages.state.value
        val ageConfirmed = age is AgeState.Known && age.band != AgeBand.UNDER_MINIMUM
        if (!ageConfirmed || local.value.submitting) return
        local.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val error = accounts.acceptTerms(ageConfirmed = true).toUiError()
            local.update { it.copy(submitting = false, failed = if (error != null) it.failed + 1 else it.failed) }
        }
    }

    fun onRetry() {
        viewModelScope.launch { accounts.loadTerms() }
    }
}

/** Wraps every online destination (quick match, room, join, chat): [content] is composed only after the terms are accepted. */
@Composable
fun TermsGate(onClose: () -> Unit, onReadTerms: () -> Unit, onReadPrivacy: () -> Unit, content: @Composable () -> Unit) {
    val vm = containerViewModel { TermsViewModel(accountRepository, ageRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    when (val terms = state.terms) {
        TermsState.Accepted -> content()
        TermsState.Unknown -> TermsLoadingScreen(onRetry = vm::onRetry, onClose = onClose)
        is TermsState.Required -> TermsGateScreen(
            updated = terms.updated,
            version = state.version,
            submitting = state.submitting,
            failed = state.failed,
            onReadTerms = onReadTerms,
            onReadPrivacy = onReadPrivacy,
            onAgree = vm::onAgree,
            onClose = onClose,
        )
    }
}
