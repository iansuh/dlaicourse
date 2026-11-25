package app.numbaseball.ui.firstrun

import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.Limits
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.NicknameProblem
import app.numbaseball.data.account.NicknameRules
import app.numbaseball.data.account.RegistrationResult
import app.numbaseball.ui.age.Eyebrow
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.message
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.exactLineHeight
import app.numbaseball.ui.design.ChaseLamps
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.NbTextField
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.rowClick
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType
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

    /** Spaces and symbols are stripped as typed (01 §1); at most 12 characters. */
    fun onNicknameChange(value: String) {
        val clean = value.filter { Character.isLetterOrDigit(it.code) || Character.isSurrogate(it) }
        val capped = if (clean.codePointCount(0, clean.length) > Limits.NICKNAME_MAX) {
            clean.substring(0, clean.offsetByCodePoints(0, Limits.NICKNAME_MAX))
        } else {
            clean
        }
        // One character is "too short" while typing (01 §1 states); longer problems show after Start.
        val short = capped.codePointCount(0, capped.length) == 1
        _state.update { it.copy(nickname = capped, nicknameProblem = if (short) NicknameProblem.TOO_SHORT else null, error = null) }
    }

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

/**
 * Nickname (01 §1): the only step before the first game. The body scrolls; the Start button and the privacy line stay
 * pinned above the IME. "Move from another device" switches to the transfer-code form.
 */
@Composable
fun FirstRunScreen(
    state: FirstRunUiState,
    onNicknameChange: (String) -> Unit,
    onTransferCodeChange: (String) -> Unit,
    onTransferModeChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    val nickError: Int? = state.nicknameProblem?.message() ?: when ((state.error as? UiError.Code)?.code) {
        ErrorCodes.INVALID_NICKNAME, ErrorCodes.NICKNAME_PROFANITY -> R.string.nick_error_rejected
        ErrorCodes.NICKNAME_UNAVAILABLE -> R.string.nick_error_unavailable
        else -> null
    }
    // The field is focused on open, IME up (01 §1).
    val focus = remember { FocusRequester() }
    // Transfer mode (D2-40): the code field takes the focus and is scrolled into view above the IME and the pinned
    // button, and again whenever an error line appears under it.
    val codeFocus = remember { FocusRequester() }
    val codeInView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(state.transferMode) {
        if (!state.transferMode) {
            runCatching { focus.requestFocus() }
        } else {
            runCatching { codeFocus.requestFocus() }
            // After the IME and the window insets have settled.
            repeat(3) { androidx.compose.runtime.withFrameNanos { } }
            codeInView.bringIntoView()
        }
    }
    LaunchedEffect(state.error, state.transferMode) {
        if (state.transferMode && state.error != null) {
            repeat(3) { androidx.compose.runtime.withFrameNanos { } }
            codeInView.bringIntoView()
        }
    }
    NbScreen(imeAware = true) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            VSpacer(32.dp)
            Eyebrow { Text(stringResource(R.string.nick_eyebrow), style = NbType.labelSmall, color = NbColors.text2) }
            VSpacer(16.dp)
            Text(stringResource(R.string.nick_title), style = NbType.headline, color = NbColors.chalk, maxLines = 2, modifier = Modifier.exactLineHeight(NbType.headline))
            VSpacer(8.dp)
            Text(stringResource(R.string.nick_lead), style = NbType.body, color = NbColors.text2)
            VSpacer(32.dp)
            if (state.transferMode) {
              Column(Modifier.bringIntoViewRequester(codeInView)) {
                Text(stringResource(R.string.transfer_code_label), style = NbType.labelSmall, color = NbColors.text2)
                VSpacer(8.dp)
                NbTextField(
                    value = state.transferCode,
                    onValueChange = onTransferCodeChange,
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(codeFocus).testTag("transferCode"),
                )
                VSpacer(8.dp)
                Text(
                    state.error?.let { stringResource(it.message()) } ?: stringResource(R.string.transfer_note),
                    style = NbType.caption,
                    color = if (state.error != null) NbColors.out else NbColors.text3,
                )
              }
                NbButton(stringResource(R.string.nick_new_account), { onTransferModeChange(false) }, kind = ButtonKind.TEXT, modifier = Modifier.offset(x = (-8).dp))
            } else {
                Text(stringResource(R.string.nick_label), style = NbType.labelSmall, color = NbColors.text2)
                VSpacer(8.dp)
                NbTextField(
                    value = state.nickname,
                    onValueChange = onNicknameChange,
                    isError = nickError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("nickname"),
                    trailing = if (state.nickname.isNotEmpty()) {
                        { IconButton48(R.drawable.ic_close, stringResource(R.string.nick_clear), { onNicknameChange("") }, tint = NbColors.text3, iconSize = 20.dp) }
                    } else {
                        null
                    },
                )
                VSpacer(8.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (nickError != null) stringResource(nickError) else stringResource(R.string.nick_helper),
                        style = NbType.caption,
                        color = if (nickError != null) NbColors.out else NbColors.text3,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${state.nickname.codePointCount(0, state.nickname.length)} / ${Limits.NICKNAME_MAX}",
                        style = NbType.numTag,
                        color = if (nickError != null) NbColors.out else NbColors.text3,
                    )
                }
                // Directly under the nickname field so it stays visible above the IME (11 §Transfer, D2-17). The text
                // aligns with the field; the 8 dp button padding reaches into the margin.
                NbButton(stringResource(R.string.nick_transfer), { onTransferModeChange(true) }, kind = ButtonKind.TEXT, modifier = Modifier.offset(x = (-8).dp))
                VSpacer(8.dp)
                Hairline()
                Column(Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.nick_preview), style = NbType.caption, color = NbColors.text3)
                    VSpacer(10.dp)
                    Text(
                        buildAnnotatedString {
                            append(state.nickname.ifEmpty { "–" })
                            withStyle(SpanStyle(fontFamily = NbType.numTag.fontFamily, fontSize = 20.sp, color = NbColors.text3, fontWeight = NbType.numTag.fontWeight)) {
                                append("#····")
                            }
                        },
                        style = NbType.title,
                        color = NbColors.chalk,
                        maxLines = 1,
                    )
                    VSpacer(4.dp)
                    Text(stringResource(R.string.nick_tag_later), style = NbType.caption, color = NbColors.text3)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            val enabled = !state.submitting && if (state.transferMode) state.transferCode.isNotBlank() else state.nickname.isNotEmpty() && state.nicknameProblem == null
            NbButton(
                text = stringResource(if (state.transferMode) R.string.transfer_redeem else R.string.nick_start),
                onClick = onSubmit,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                content = if (state.submitting) ({ ChaseLamps() }) else null,
            )
            VSpacer(12.dp)
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.nick_privacy_note))
                    append(" ")
                    withStyle(SpanStyle(color = NbColors.text2, textDecoration = TextDecoration.Underline)) { append(stringResource(R.string.privacy_policy)) }
                },
                style = NbType.caption,
                color = NbColors.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().rowClick(onClick = onPrivacyPolicy),
            )
        }
    }
}

private fun NicknameProblem.message(): Int = when (this) {
    NicknameProblem.TOO_SHORT -> R.string.nick_error_short
    NicknameProblem.TOO_LONG -> R.string.nick_error_long
    NicknameProblem.INVALID_CHARACTERS -> R.string.nick_error_rejected
    NicknameProblem.PROFANITY -> R.string.nick_error_rejected
}
