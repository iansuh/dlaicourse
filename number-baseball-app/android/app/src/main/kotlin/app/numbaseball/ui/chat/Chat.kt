package app.numbaseball.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.SoundCue
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.ChatMessage
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.ServerInfo
import app.numbaseball.core.text.EmojiFilter
import app.numbaseball.core.text.QuickPhrases
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.online.RequestResult
import app.numbaseball.data.settings.SettingsRepository
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SectionLabel
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbLayout
import app.numbaseball.ui.theme.NbSpace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val roomId: String? = null,
    val myId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val muted: Boolean = false,
    /** Quick-match rooms allow quick phrases only (README "Chat safety"); free text only in private rooms. */
    val phrasesOnly: Boolean = true,
    val input: String = "",
    val sending: Boolean = false,
    val error: UiError? = null,
)

class ChatViewModel(
    private val session: OnlineSession,
    settings: SettingsRepository,
    serverInfo: StateFlow<ServerInfo?>,
) : ViewModel() {
    private val local = MutableStateFlow(ChatUiState())

    val state: StateFlow<ChatUiState> = combine(local, session.room, session.chat, settings.settings, serverInfo) { ui, room, chat, prefs, info ->
        ui.copy(
            roomId = room?.id,
            myId = room?.youId,
            messages = chat,
            muted = prefs.chatMuted,
            // Quick rooms: quick phrases only unless the server turned free chat on (`chat_not_allowed` otherwise).
            phrasesOnly = room == null || (room.kind == RoomKind.QUICK && info?.quickMatchFreeChat != true),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        session.acquire()
    }

    /** Text only: emoji are dropped while typing (the server cleans and masks again), length capped at 100 code points. */
    fun onInputChange(value: String) {
        val clean = EmojiFilter.strip(value)
        val capped = if (clean.codePointCount(0, clean.length) > Limits.CHAT_MAX_LENGTH) {
            clean.substring(0, clean.offsetByCodePoints(0, Limits.CHAT_MAX_LENGTH))
        } else {
            clean
        }
        local.update { it.copy(input = capped, error = null) }
    }

    fun onSend() {
        val text = local.value.input.trim()
        if (text.isEmpty() || state.value.phrasesOnly) return
        send(clearInput = true) { session.sendChat(text) }
    }

    fun onPhrase(phraseId: String) = send(clearInput = false) { session.sendPhrase(phraseId) }

    private fun send(clearInput: Boolean, call: suspend () -> RequestResult) {
        local.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val error = call().toUiError()
            local.update { it.copy(sending = false, error = error, input = if (error == null && clearInput) "" else it.input) }
        }
    }

    override fun onCleared() = session.release()
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPhrase: (String) -> Unit,
    onReport: (ChatMessage) -> Unit,
    onBack: () -> Unit,
) {
    val audio = LocalGameAudio.current
    val latest = state.messages.lastOrNull()
    LaunchedEffect(latest?.id) {
        if (latest != null && !state.muted && latest.senderId != state.myId) audio.play(SoundCue.CHAT_MESSAGE)
    }
    ScreenScaffold(title = stringResource(R.string.chat_title), onBack = onBack) {
        if (state.muted) {
            Text(stringResource(R.string.chat_muted), color = NbColors.text2)
        } else if (state.messages.isEmpty()) {
            Text(stringResource(R.string.chat_empty), color = NbColors.text3)
        } else {
            state.messages.forEach { message ->
                ChatLine(
                    message = message,
                    mine = message.senderId == state.myId,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { if (message.senderId != null && message.senderId != state.myId) onReport(message) },
                    ),
                )
            }
        }
        ErrorText(state.error)
        SectionLabel(stringResource(R.string.chat_quick_phrases))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(NbSpace.s8)) {
            QuickPhrases.ALL.forEach { id ->
                val text = phraseText(id) ?: return@forEach
                AssistChip(onClick = { onPhrase(id) }, label = { Text(text) }, enabled = !state.sending)
            }
        }
        if (state.phrasesOnly) {
            Text(stringResource(R.string.chat_phrases_only), color = NbColors.text3, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s8)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    label = { Text(stringResource(R.string.chat_hint)) },
                    supportingText = {
                        Text(stringResource(R.string.chat_counter, state.input.codePointCount(0, state.input.length), Limits.CHAT_MAX_LENGTH))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    modifier = Modifier.weight(1f).testTag("chatInput"),
                )
                TextButton(onClick = onSend, enabled = !state.sending && state.input.isNotBlank()) { Text(stringResource(R.string.chat_send)) }
            }
        }
    }
}

@Composable
private fun ChatLine(message: ChatMessage, mine: Boolean, modifier: Modifier) {
    val sender = if (message.senderId == null) {
        stringResource(R.string.chat_deleted_player)
    } else {
        "${message.senderNickname.orEmpty()}#${message.senderTag.orEmpty()}"
    }
    val text = when (message.kind) {
        ChatKind.TEXT -> message.text.orEmpty()
        ChatKind.PHRASE -> message.phraseId?.let { phraseText(it) }.orEmpty()
    }
    Column(modifier.fillMaxWidth().heightIn(min = NbLayout.touchMin)) {
        Text(sender, style = MaterialTheme.typography.labelSmall, color = if (mine) NbColors.ball else NbColors.text2)
        Text(text)
    }
}

/** Localized quick phrase; null for ids this app version does not know (docs/protocol.md §9.2: ignore them). */
@Composable
fun phraseText(id: String): String? = when (id) {
    "hello" -> R.string.phrase_hello
    "good_luck" -> R.string.phrase_good_luck
    "have_fun" -> R.string.phrase_have_fun
    "nice_guess" -> R.string.phrase_nice_guess
    "close_one" -> R.string.phrase_close_one
    "thinking" -> R.string.phrase_thinking
    "oops" -> R.string.phrase_oops
    "well_played" -> R.string.phrase_well_played
    "good_game" -> R.string.phrase_good_game
    "thanks" -> R.string.phrase_thanks
    "rematch" -> R.string.phrase_rematch
    "bye" -> R.string.phrase_bye
    else -> null
}?.let { stringResource(it) }
