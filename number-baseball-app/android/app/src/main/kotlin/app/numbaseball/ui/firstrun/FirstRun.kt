package app.numbaseball.ui.firstrun

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.NicknameProblem
import app.numbaseball.data.account.NicknameRules
import app.numbaseball.data.account.RegistrationResult
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.theme.NbColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FirstRunUiState(
    val nickname: String = "",
    val nicknameProblem: NicknameProblem? = null,
    val transferMode: Boolean = false,
    val transferCode: String = "",
    val error: UiError? = null,
    val submitting: Boolean = false,
    /** Navigate to home. */
    val done: Boolean = false,
)

class FirstRunViewModel(private val accounts: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(FirstRunUiState())
    val state: StateFlow<FirstRunUiState> = _state.asStateFlow()

    fun onNicknameChange(value: String) = _state.update { it.copy(nickname = value, nicknameProblem = null, error = null) }
    fun onTransferCodeChange(value: String) = _state.update { it.copy(transferCode = value, error = null) }
    fun onTransferModeChange(enabled: Boolean) = _state.update { it.copy(transferMode = enabled, error = null) }

    fun onSubmit() {
        val current = _state.value
        if (current.submitting) return
        if (current.transferMode) {
            redeem(current.transferCode)
            return
        }
        val problem = NicknameRules.problem(current.nickname)
        if (problem != null) {
            _state.update { it.copy(nicknameProblem = problem) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = accounts.chooseNickname(current.nickname)
            _state.update {
                when (result) {
                    is RegistrationResult.Rejected -> it.copy(submitting = false, error = UiError.Code(result.code))
                    // Deferred: offline at first launch is fine; vs-computer works and registration is retried later.
                    is RegistrationResult.Registered, RegistrationResult.Deferred -> it.copy(submitting = false, done = true)
                }
            }
        }
    }

    private fun redeem(code: String) {
        if (code.isBlank()) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = accounts.redeemTransferCode(code.trim())
            _state.update { it.copy(submitting = false, error = result.toUiError(), done = result.toUiError() == null) }
        }
    }
}

@Composable
fun FirstRunScreen(
    state: FirstRunUiState,
    onNicknameChange: (String) -> Unit,
    onTransferCodeChange: (String) -> Unit,
    onTransferModeChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.app_name), onBack = null) {
        Text(stringResource(R.string.first_run_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.first_run_subtitle), color = NbColors.text2)
        if (state.transferMode) {
            OutlinedTextField(
                value = state.transferCode,
                onValueChange = onTransferCodeChange,
                label = { Text(stringResource(R.string.first_run_code_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                modifier = Modifier.testTag("transferCode"),
            )
            ErrorText(state.error)
            PrimaryButton(stringResource(R.string.first_run_redeem), onSubmit, enabled = !state.submitting && state.transferCode.isNotBlank())
            TextButton(onClick = { onTransferModeChange(false) }) { Text(stringResource(R.string.first_run_new_account)) }
        } else {
            OutlinedTextField(
                value = state.nickname,
                onValueChange = onNicknameChange,
                label = { Text(stringResource(R.string.first_run_nickname_label)) },
                supportingText = {
                    val problem = state.nicknameProblem
                    Text(if (problem == null) stringResource(R.string.first_run_nickname_hint) else stringResource(problem.message()))
                },
                isError = state.nicknameProblem != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.testTag("nickname"),
            )
            ErrorText(state.error)
            PrimaryButton(stringResource(R.string.first_run_start), onSubmit, enabled = !state.submitting && state.nickname.isNotBlank())
            TextButton(onClick = { onTransferModeChange(true) }) { Text(stringResource(R.string.first_run_have_code)) }
        }
    }
}

private fun NicknameProblem.message(): Int = when (this) {
    NicknameProblem.TOO_SHORT -> R.string.nickname_error_too_short
    NicknameProblem.TOO_LONG -> R.string.nickname_error_too_long
    NicknameProblem.INVALID_CHARACTERS -> R.string.nickname_error_invalid
    NicknameProblem.PROFANITY -> R.string.nickname_error_profanity
}
