package app.numbaseball.ui.terms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.TermsState
import app.numbaseball.ui.common.UiError
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
    val terms: TermsState = TermsState.Required(updated = false),
    val submitting: Boolean = false,
    val error: UiError? = null,
)

class TermsViewModel(private val accounts: AccountRepository) : ViewModel() {
    private val local = MutableStateFlow(TermsUiState(terms = accounts.terms.value))

    val state: StateFlow<TermsUiState> = combine(local, accounts.terms) { ui, terms -> ui.copy(terms = terms) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun onAgree() {
        local.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val error = accounts.acceptTerms().toUiError()
            local.update { it.copy(submitting = false, error = error) }
        }
    }
}

/** Wraps every online destination (quick match, room, chat): [content] is composed only after the terms are accepted. */
@Composable
fun TermsGate(onBack: () -> Unit, onReadTerms: () -> Unit, content: @Composable () -> Unit) {
    val vm = containerViewModel { TermsViewModel(accountRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    when (val terms = state.terms) {
        TermsState.Accepted -> content()
        is TermsState.Required -> TermsGateScreen(
            updated = terms.updated,
            submitting = state.submitting,
            error = state.error,
            onReadTerms = onReadTerms,
            onAgree = vm::onAgree,
            onBack = onBack,
        )
    }
}
