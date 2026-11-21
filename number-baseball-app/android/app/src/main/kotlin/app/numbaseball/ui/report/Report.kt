package app.numbaseball.ui.report

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.core.protocol.ReportReason
import app.numbaseball.data.account.ApiResult
import app.numbaseball.data.online.SafetyRepository
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SecondaryButton
import app.numbaseball.ui.common.SectionLabel
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who or what is reported: a player (from the player list) or one chat message. */
data class ReportTarget(val playerId: String, val playerName: String, val roomId: String?, val messageId: Long?)

data class ReportUiState(
    val target: ReportTarget,
    val reason: ReportReason? = null,
    val note: String = "",
    val blockToo: Boolean = false,
    val sending: Boolean = false,
    val reported: Boolean = false,
    val blocked: Boolean = false,
    val error: UiError? = null,
)

class ReportViewModel(target: ReportTarget, private val safety: SafetyRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState(target))
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun onReasonChange(reason: ReportReason) = _state.update { it.copy(reason = reason, error = null) }
    fun onNoteChange(note: String) = _state.update { it.copy(note = note.take(NOTE_MAX)) }
    fun onBlockTooChange(block: Boolean) = _state.update { it.copy(blockToo = block) }

    fun onSubmit() {
        val s = _state.value
        val reason = s.reason ?: return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val report = safety.report(s.target.playerId, reason, s.target.roomId, s.target.messageId, s.note)
            val error = report.toUiError()
            _state.update { it.copy(sending = false, reported = error == null, error = error) }
            if (error == null && s.blockToo) block()
        }
    }

    /** "Block only", or "Block too" offered after a report. */
    fun onBlock() {
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch { block() }
    }

    private suspend fun block() {
        val result = safety.block(_state.value.target.playerId)
        _state.update { it.copy(sending = false, blocked = result is ApiResult.Success, error = result.toUiError()) }
    }

    private companion object {
        const val NOTE_MAX = 500
    }
}

@Composable
fun ReportScreen(
    state: ReportUiState,
    onReasonChange: (ReportReason) -> Unit,
    onNoteChange: (String) -> Unit,
    onBlockTooChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onBlock: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.report_title), onBack = onBack) {
        Text(stringResource(R.string.report_target, state.target.playerName))
        if (state.reported) Text(stringResource(R.string.report_sent), color = NbColors.ball)
        if (state.blocked) Text(stringResource(R.string.report_blocked), color = NbColors.ball)
        ErrorText(state.error)
        if (!state.reported) {
            SectionLabel(stringResource(R.string.report_reason))
            ReportReason.entries.forEach { reason ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = NbLayout.touchMin)
                        .selectable(selected = state.reason == reason, onClick = { onReasonChange(reason) }),
                ) {
                    RadioButton(selected = state.reason == reason, onClick = null)
                    Text(reasonLabel(reason))
                }
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.report_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.blockToo, onCheckedChange = onBlockTooChange)
                Text(stringResource(R.string.report_block_too))
            }
            PrimaryButton(stringResource(R.string.report_submit), onSubmit, enabled = state.reason != null && !state.sending)
        }
        if (!state.blocked) {
            if (state.reported) Text(stringResource(R.string.report_block_offer))
            SecondaryButton(
                stringResource(if (state.reported) R.string.report_block_button else R.string.report_block_only),
                onBlock,
                enabled = !state.sending,
            )
        }
    }
}

@Composable
private fun reasonLabel(reason: ReportReason): String = stringResource(
    when (reason) {
        ReportReason.SPAM -> R.string.report_reason_spam
        ReportReason.HARASSMENT -> R.string.report_reason_harassment
        ReportReason.HATE -> R.string.report_reason_hate
        ReportReason.SEXUAL -> R.string.report_reason_sexual
        ReportReason.CHEATING -> R.string.report_reason_cheating
        ReportReason.NICKNAME -> R.string.report_reason_nickname
        ReportReason.OTHER -> R.string.report_reason_other
    },
)
