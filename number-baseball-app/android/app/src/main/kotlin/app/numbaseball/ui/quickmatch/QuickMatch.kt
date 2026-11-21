package app.numbaseball.ui.quickmatch

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.online.QueueState
import app.numbaseball.data.online.SessionEvent
import app.numbaseball.ui.common.ChoiceRow
import app.numbaseball.ui.common.ConnectionStatus
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SecondaryButton
import app.numbaseball.ui.common.SectionLabel
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.theme.NbColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickMatchUiState(
    val digits: Int = 3,
    val searching: Boolean = false,
    val connection: ConnectionState = ConnectionState.Idle,
    val error: UiError? = null,
    /** A quick room arrived: open the match. */
    val matchFound: Boolean = false,
)

class QuickMatchViewModel(private val session: OnlineSession) : ViewModel() {
    private val local = MutableStateFlow(QuickMatchUiState())

    val state: StateFlow<QuickMatchUiState> = combine(local, session.queue, session.connection, session.room) { ui, queue, connection, room ->
        ui.copy(
            searching = queue is QueueState.Searching,
            connection = connection,
            matchFound = room != null && room.kind == RoomKind.QUICK && room.phase == RoomPhase.PLAYING,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        session.acquire()
        viewModelScope.launch {
            session.events.collect { if (it is SessionEvent.Error) local.update { s -> s.copy(error = UiError.Code(it.code)) } }
        }
    }

    fun onDigitsChange(digits: Int) = local.update { if (state.value.searching) it else it.copy(digits = digits) }

    fun onSearch() {
        local.update { it.copy(error = null) }
        // Sent now if connected, otherwise on the next welcome (the queue does not survive reconnects).
        session.startQuickMatch(local.value.digits)
    }

    fun onCancel() = session.cancelQuickMatch()

    fun onRetryConnection() = session.retryConnection()

    override fun onCleared() {
        if (state.value.searching && !state.value.matchFound) session.cancelQuickMatch()
        session.release()
    }
}

@Composable
fun QuickMatchScreen(
    state: QuickMatchUiState,
    onDigitsChange: (Int) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    onRetryConnection: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.quick_title), onBack = onBack) {
        Text(stringResource(R.string.quick_rules), color = NbColors.text2)
        SectionLabel(stringResource(R.string.setup_digits))
        ChoiceRow(Limits.DIGITS.toList(), state.digits, { it.toString() }) { if (!state.searching) onDigitsChange(it) }
        ConnectionStatus(state.connection, onRetryConnection)
        ErrorText(state.error)
        if (state.searching) {
            CircularProgressIndicator()
            Text(stringResource(R.string.quick_searching))
            SecondaryButton(stringResource(R.string.quick_cancel), onCancel)
        } else {
            PrimaryButton(stringResource(R.string.quick_search), onSearch)
        }
    }
}
