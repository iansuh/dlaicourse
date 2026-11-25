package app.numbaseball.ui.lobby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.core.protocol.Limits
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.message
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.design.ChaseLamps
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.ToastHost
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.rememberToastState
import app.numbaseball.ui.home.RoomCodes
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class JoinUiState(
    val code: String = "",
    val joining: Boolean = false,
    val error: UiError? = null,
    /** Bumped on every error: plates shake. */
    val shake: Int = 0,
    val joined: Boolean = false,
)

class JoinViewModel(private val session: OnlineSession) : ViewModel() {
    private val _state = MutableStateFlow(JoinUiState())
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    init {
        session.acquire()
    }

    /** Only the code alphabet is accepted; the 5th character joins at once (07 §2). */
    fun onCodeChange(raw: String) {
        val code = RoomCodes.normalize(raw)
        _state.update { it.copy(code = code, error = null) }
        if (code.length == Limits.ROOM_CODE_LENGTH) onJoin()
    }

    fun onJoin() {
        val current = _state.value
        if (current.joining || current.code.length != Limits.ROOM_CODE_LENGTH) return
        _state.update { it.copy(joining = true, error = null) }
        viewModelScope.launch {
            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { session.connection.first { it is ConnectionState.Connected } }
            val error = if (connected == null) UiError.Network else session.joinRoom(current.code).toUiError()
            _state.update {
                if (error == null) {
                    it.copy(joining = false, joined = true)
                } else {
                    it.copy(joining = false, error = error, shake = it.shake + 1)
                }
            }
        }
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    override fun onCleared() = session.release()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}

/**
 * Join with code (07 §2): prompt, five code plates driven by a hidden text field (ASCII keyboard, caps, no autocorrect),
 * pinned "Join" above the IME. Errors: toast + shake + `out` borders on all plates.
 */
@Composable
fun JoinScreen(state: JoinUiState, onCodeChange: (String) -> Unit, onJoin: () -> Unit, onBack: () -> Unit, onErrorShown: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val toast = rememberToastState()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    state.error?.let { error ->
        val text = stringResource(error.message())
        LaunchedEffect(state.shake) {
            toast.show(text, error = true)
            onErrorShown()
        }
    }
    NbScreen(
        imeAware = true,
        topBar = { TopBar(stringResource(R.string.join_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
        bottomBar = {
            NbButton(
                stringResource(R.string.join),
                onJoin,
                enabled = state.code.length == Limits.ROOM_CODE_LENGTH && !state.joining,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("join"),
                content = if (state.joining) ({ ChaseLamps() }) else null,
            )
        },
        overlay = { ToastHost(toast, bottomOffset = 88.dp) },
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            VSpacer(24.dp)
            Text(stringResource(R.string.join_prompt), style = NbType.title, color = NbColors.chalk)
            Text(stringResource(R.string.join_rule), style = NbType.caption, color = NbColors.text3)
            VSpacer(24.dp)
            Box(
                Modifier.fillMaxWidth().clickable(indication = null, interactionSource = null) {
                    runCatching { focus.requestFocus() }
                    keyboard?.show()
                },
                contentAlignment = Alignment.Center,
            ) {
                CodePlates(
                    state.code,
                    active = true,
                    error = state.error != null || (state.shake > 0 && state.code.length == Limits.ROOM_CODE_LENGTH && !state.joining && !state.joined),
                    shakeKey = state.shake,
                    description = stringResource(R.string.invite_code),
                )
                // The hidden field that drives the plates.
                BasicTextField(
                    value = state.code,
                    onValueChange = onCodeChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { onJoin() }),
                    modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focus).testTag("joinCode"),
                )
            }
        }
    }
}
