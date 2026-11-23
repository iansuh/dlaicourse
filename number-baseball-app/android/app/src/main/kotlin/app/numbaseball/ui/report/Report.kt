package app.numbaseball.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ReportReason
import app.numbaseball.core.protocol.Team
import app.numbaseball.data.online.SafetyRepository
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.message
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.ChaseLamps
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.IconNote
import app.numbaseball.ui.design.ListRow
import app.numbaseball.ui.design.NameWithTag
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbDialog
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbTextField
import app.numbaseball.ui.design.RadioRow
import app.numbaseball.ui.design.TeamBadge
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who the card and the report are about. [messageId] / [messageText] for a message report. */
data class ReportTarget(
    val playerId: String,
    val nickname: String,
    val tag: String,
    val team: Team? = null,
    val roomId: String? = null,
    val messageId: Long? = null,
    val messageText: String? = null,
) {
    val displayName: String get() = "$nickname#$tag"
}

/** The eight reasons of 12 §3; the protocol has seven, so two travel as `other` with a marker in the note. */
enum class ReportChoice(val label: Int, val reason: ReportReason, val marker: String? = null) {
    HARASSMENT(R.string.reason_harassment, ReportReason.HARASSMENT),
    HATE(R.string.reason_hate, ReportReason.HATE),
    SEXUAL(R.string.reason_sexual, ReportReason.SEXUAL),
    PERSONAL_INFO(R.string.reason_personal_info, ReportReason.OTHER, "[personal_info]"),
    SPAM(R.string.reason_spam, ReportReason.SPAM),
    CHEATING(R.string.reason_cheating, ReportReason.CHEATING),
    CHILD_SAFETY(R.string.reason_child_safety, ReportReason.OTHER, "[child_safety]"),
    OTHER(R.string.reason_other, ReportReason.OTHER),
}

data class ReportUiState(
    val target: ReportTarget,
    val choice: ReportChoice? = null,
    val note: String = "",
    val sending: Boolean = false,
    val reported: Boolean = false,
    val blocked: Boolean = false,
    val error: UiError? = null,
)

class ReportViewModel(target: ReportTarget, private val safety: SafetyRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState(target, blocked = safety.blocked.value.any { it.id == target.playerId }))
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun onChoice(choice: ReportChoice) = _state.update { it.copy(choice = choice, error = null) }
    fun onNoteChange(note: String) = _state.update { it.copy(note = note.take(NOTE_MAX)) }

    fun onSubmit() {
        val s = _state.value
        val choice = s.choice ?: return
        if (s.sending) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val note = listOfNotNull(choice.marker, s.note.trim().takeIf { it.isNotEmpty() }).joinToString(" ").ifEmpty { null }
            val result = safety.report(s.target.playerId, choice.reason, s.target.roomId, s.target.messageId, note)
            val error = result.toUiError()
            _state.update { it.copy(sending = false, reported = error == null, error = error) }
        }
    }

    /** "Block this player too" on the done sheet. */
    fun onBlock() {
        val target = _state.value.target
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val error = safety.block(target.playerId, target.nickname, target.tag).toUiError()
            _state.update { it.copy(sending = false, blocked = error == null, error = error) }
        }
    }

    private companion object {
        const val NOTE_MAX = 200
    }
}

/**
 * Player card (12 §2): identity (badge in team rooms, name + tag, context line), actions: report (chevron) and block
 * (destructive, with subtitle) or unblock. The other player's record is not part of the protocol, so it is not shown.
 */
@Composable
fun PlayerCard(
    target: ReportTarget,
    context: String?,
    self: Boolean,
    blocked: Boolean,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (target.team != null) {
            TeamBadge(target.team)
            HSpacer(12.dp)
        }
        Column(Modifier.weight(1f)) {
            NameWithTag(target.nickname, target.tag, nameStyle = NbType.title, tagStyle = NbType.numTag.copy(fontSize = NbType.numMemo.fontSize))
            if (context != null) Text(context, style = NbType.caption, color = NbColors.text3)
        }
    }
    if (!self) {
        Hairline()
        ListRow(stringResource(R.string.report_action), icon = R.drawable.ic_flag, chevron = true, onClick = onReport, modifier = Modifier.testTag("cardReport"))
        Hairline()
        if (blocked) {
            ListRow(stringResource(R.string.unblock_action), icon = R.drawable.ic_block, onClick = onUnblock)
        } else {
            ListRow(
                stringResource(R.string.block_action),
                icon = R.drawable.ic_block,
                subtitle = stringResource(R.string.block_sub),
                destructive = true,
                onClick = onBlock,
                modifier = Modifier.testTag("cardBlock"),
            )
        }
    }
    VSpacer(24.dp)
}

/** Reasons sheet, then the sent sheet (12 §3). */
@Composable
fun ReportSheet(state: ReportUiState, onChoice: (ReportChoice) -> Unit, onNoteChange: (String) -> Unit, onSubmit: () -> Unit, onBlockToo: () -> Unit, onClose: () -> Unit) {
    if (state.reported) {
        ReportDone(state, onBlockToo, onClose)
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.report_title, state.target.displayName), style = NbType.title, color = NbColors.chalk, modifier = Modifier.semantics { heading() })
            state.target.messageText?.let {
                VSpacer(4.dp)
                Text(it, style = NbType.bodyStrong, color = NbColors.chalk, maxLines = 2)
            }
            Text(stringResource(R.string.report_pick), style = NbType.caption, color = NbColors.text3)
        }
        VSpacer(8.dp)
        Column(Modifier.selectableGroup()) {
            ReportChoice.entries.forEach { choice ->
                RadioRow(stringResource(choice.label), state.choice == choice, { onChoice(choice) })
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            VSpacer(16.dp)
            NbTextField(
                value = state.note,
                onValueChange = onNoteChange,
                placeholder = stringResource(R.string.report_note),
                height = 48.dp,
                textStyle = NbType.body.copy(color = NbColors.chalk),
                modifier = Modifier.fillMaxWidth(),
            )
            VSpacer(16.dp)
            IconNote(R.drawable.ic_info, stringResource(R.string.report_evidence))
            state.error?.let {
                VSpacer(8.dp)
                Text(
                    stringResource(if ((it as? UiError.Code)?.code == ErrorCodes.RATE_LIMITED) R.string.report_limit else it.message()),
                    style = NbType.caption,
                    color = NbColors.out,
                )
            }
            VSpacer(24.dp)
            NbButton(
                stringResource(R.string.report_send),
                onSubmit,
                enabled = state.choice != null && !state.sending,
                modifier = Modifier.fillMaxWidth().testTag("reportSend"),
                content = if (state.sending) ({ ChaseLamps() }) else null,
            )
            VSpacer(24.dp)
        }
    }
}

@Composable
private fun ReportDone(state: ReportUiState, onBlockToo: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VSpacer(16.dp)
        Box(Modifier.size(48.dp).background(NbColors.surface4, CircleShape), contentAlignment = Alignment.Center) {
            NbIcon(R.drawable.ic_check, null, NbColors.ball)
        }
        VSpacer(16.dp)
        Text(stringResource(R.string.report_done_title), style = NbType.title, color = NbColors.chalk, modifier = Modifier.semantics { heading() })
        VSpacer(8.dp)
        Text(stringResource(R.string.report_done_body), style = NbType.body, color = NbColors.text2, textAlign = TextAlign.Center)
        if (state.choice == ReportChoice.CHILD_SAFETY) {
            VSpacer(8.dp)
            Text(stringResource(R.string.report_child_safety_note), style = NbType.body, color = NbColors.text2, textAlign = TextAlign.Center)
        }
        VSpacer(24.dp)
        if (!state.blocked) {
            NbButton(
                stringResource(R.string.block_too),
                onBlockToo,
                kind = ButtonKind.DESTRUCTIVE_OUTLINE,
                icon = R.drawable.ic_block,
                enabled = !state.sending,
                modifier = Modifier.fillMaxWidth().testTag("blockToo"),
            )
            VSpacer(8.dp)
        }
        NbButton(stringResource(R.string.action_close), onClose, kind = ButtonKind.TONAL, modifier = Modifier.fillMaxWidth())
        VSpacer(24.dp)
    }
}

/** Block confirmation (12 §4). */
@Composable
fun BlockDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NbDialog(
        title = stringResource(R.string.block_title, name),
        body = stringResource(R.string.block_body),
        confirmLabel = stringResource(R.string.block_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.action_cancel),
        destructive = true,
    )
}
