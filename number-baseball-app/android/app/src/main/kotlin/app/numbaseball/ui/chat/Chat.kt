package app.numbaseball.ui.chat

import app.numbaseball.ui.design.isLargeFont

import androidx.compose.foundation.verticalScroll

import app.numbaseball.ui.design.SheetBottomSpace
import app.numbaseball.audio.SoundCue
import app.numbaseball.audio.LocalGameHaptics
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.HapticCue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.ChatMessage
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.ServerInfo
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.text.EmojiFilter
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.AccountState
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.online.RequestResult
import app.numbaseball.data.settings.SettingsRepository
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.InsetDivider
import app.numbaseball.ui.design.ListRow
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbModalSheet
import app.numbaseball.ui.design.NbSwitchVisual
import app.numbaseball.ui.design.NbTextField
import app.numbaseball.ui.design.SheetTitle
import app.numbaseball.ui.design.TeamBadge
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.fadingEnd
import app.numbaseball.ui.design.rowClick
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The six presets of 09 §4 / 05 §2.8, as protocol phrase ids (the design's "Tough one" has no id; `close_one` stands in). */
val DESIGN_PHRASES = listOf("good_luck", "good_game", "thinking", "close_one", "rematch", "well_played")

/** Chips in the room chat (shot chat-ko): the presets with a protocol id that matches their meaning. */
private val CHAT_CHIPS = listOf("good_luck", "good_game", "thinking", "rematch", "well_played")

/** One phrase per 3 s (09 §4); the text limit comes from the server. */
const val PHRASE_COOLDOWN_MS = 3_000L

/** The composer stops at 80 characters (09 §3, DESIGN_SYSTEM 12.15); the server accepts up to Limits.CHAT_MAX_LENGTH. */
const val CHAT_INPUT_MAX = 80

data class ChatUiState(
    val roomId: String? = null,
    val myId: String? = null,
    val teamRoom: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val muted: Boolean = false,
    /** Quick-match rooms allow quick phrases only (README "Chat safety"); free text only in private rooms. */
    val phrasesOnly: Boolean = true,
    /** Free text needs one finished online match (09 §1). */
    val needsFirstMatch: Boolean = false,
    val input: String = "",
    val sending: Boolean = false,
    /** Seconds until the next send (server rate limit): send and chips disabled. */
    val cooldown: Int = 0,
    /** One phrase per 3 s (09 §4), separate from the text limit: chips only. */
    val phraseCooldown: Int = 0,
    val restricted: Boolean = false,
    val offline: Boolean = false,
    val error: UiError? = null,
) {
    val canType: Boolean get() = !phrasesOnly && !needsFirstMatch && !restricted
}

class ChatViewModel(
    private val session: OnlineSession,
    private val settings: SettingsRepository,
    accounts: AccountRepository,
    serverInfo: StateFlow<ServerInfo?>,
) : ViewModel() {
    private val local = MutableStateFlow(ChatUiState())

    val state: StateFlow<ChatUiState> = combine(
        combine(local, session.room, session.chat, ::Triple),
        settings.settings,
        serverInfo,
        accounts.state,
        session.connection,
    ) { (ui, room, chat), prefs, info, account, connection ->
        val stats = (account as? AccountState.Registered)?.stats
        ui.copy(
            roomId = room?.id,
            myId = room?.youId,
            teamRoom = (room?.settings?.teamSize ?: 1) > 1,
            messages = chat,
            muted = prefs.chatMuted,
            // Quick rooms: quick phrases only unless the server turned free chat on (`chat_not_allowed` otherwise).
            phrasesOnly = room == null || (room.kind == RoomKind.QUICK && info?.quickMatchFreeChat != true),
            needsFirstMatch = stats != null && stats.wins + stats.losses + stats.draws == 0,
            offline = connection !is ConnectionState.Connected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        session.acquire()
        viewModelScope.launch { session.chat.collect { session.markChatRead() } }
    }

    /** Text only: emoji are dropped while typing (the server masks again), length capped by the protocol limit. */
    fun onInputChange(value: String) {
        val clean = EmojiFilter.strip(value)
        val capped = if (clean.codePointCount(0, clean.length) > CHAT_INPUT_MAX) {
            clean.substring(0, clean.offsetByCodePoints(0, CHAT_INPUT_MAX))
        } else {
            clean
        }
        local.update { it.copy(input = capped, error = null) }
    }

    fun onSend() {
        val text = local.value.input.trim()
        if (text.isEmpty() || !state.value.canType || local.value.cooldown > 0) return
        send(clearInput = true) { session.sendChat(text) }
    }

    fun onPhrase(phraseId: String) {
        if (local.value.cooldown > 0 || local.value.phraseCooldown > 0) return
        send(clearInput = false) { session.sendPhrase(phraseId) }
        viewModelScope.launch {
            for (left in (PHRASE_COOLDOWN_MS / 1000).toInt() downTo 1) {
                local.update { it.copy(phraseCooldown = left) }
                delay(1000)
            }
            local.update { it.copy(phraseCooldown = 0) }
        }
    }

    fun onMuteChange(muted: Boolean) {
        viewModelScope.launch { settings.update { it.copy(chatMuted = muted) } }
    }

    private fun send(clearInput: Boolean, call: suspend () -> RequestResult) {
        local.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val error = call().toUiError()
            val code = (error as? UiError.Code)?.code
            local.update {
                it.copy(
                    sending = false,
                    error = error,
                    input = if (error == null && clearInput) "" else it.input,
                    restricted = it.restricted || code == ErrorCodes.CHAT_RESTRICTED,
                )
            }
            if (code == ErrorCodes.CHAT_RATE_LIMITED || code == ErrorCodes.RATE_LIMITED) startCooldown(3)
        }
    }

    private fun startCooldown(seconds: Int) {
        viewModelScope.launch {
            for (left in seconds downTo 1) {
                local.update { it.copy(cooldown = left) }
                delay(1000)
            }
            local.update { it.copy(cooldown = 0) }
        }
    }

    override fun onCleared() = session.release()
}

class ChatActions(
    val onInputChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onPhrase: (String) -> Unit,
    val onMuteChange: (Boolean) -> Unit,
    val onClose: () -> Unit,
    val onCopy: (String) -> Unit,
    val onReportMessage: (ChatMessage) -> Unit,
    val onBlock: (ChatMessage) -> Unit,
    val onPlayerCard: (ChatMessage) -> Unit,
)

/** Chat sheet content (09 §2), hosted in a [app.numbaseball.ui.design.SheetFrame] from y = 136 dp. */
@Composable
fun ChatSheet(state: ChatUiState, actions: ChatActions) {
    var held by remember { mutableStateOf<ChatMessage?>(null) }
    Column(Modifier.fillMaxSize()) {
        SheetTitle(stringResource(R.string.chat_title)) {
            val muteLabel = stringResource(R.string.chat_mute)
            val on = stringResource(R.string.state_on)
            val off = stringResource(R.string.state_off)
            val audio = LocalGameAudio.current
            val haptics = LocalGameHaptics.current
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(NbRadius.m))
                    .rowClick {
                        val next = !state.muted
                        haptics.perform(if (next) HapticCue.TOGGLE_ON else HapticCue.TOGGLE_OFF)
                        audio.play(if (next) SoundCue.TOGGLE_ON else SoundCue.TOGGLE_OFF)
                        actions.onMuteChange(next)
                    }
                    .semantics(mergeDescendants = true) {
                        role = Role.Switch
                        contentDescription = muteLabel
                        stateDescription = if (state.muted) on else off
                    }
                    .padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(muteLabel, style = NbType.labelSmall, color = NbColors.text2)
                HSpacer(9.dp)
                NbSwitchVisual(state.muted)
            }
            IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), actions.onClose, tint = NbColors.chalk)
        }
        if (state.muted) {
            MutedState(Modifier.weight(1f)) { actions.onMuteChange(false) }
        } else {
            Messages(state, Modifier.weight(1f), held = held?.id, onLongPress = { held = it }, onName = actions.onPlayerCard)
            PhraseChips(state, actions.onPhrase)
        }
        Composer(state, actions)
    }
    held?.let { message ->
        val text = messageText(message)
        MessageActions(
            message = message,
            mine = message.senderId == state.myId,
            onDismiss = { held = null },
            onCopy = { actions.onCopy(text); held = null },
            onReport = { held = null; actions.onReportMessage(message) },
            onPlayerCard = { held = null; actions.onPlayerCard(message) },
            onBlock = { held = null; actions.onBlock(message) },
        )
    }
}

@Composable
private fun MutedState(modifier: Modifier, onUnmute: () -> Unit) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        NbIcon(R.drawable.ic_notifications_off, null, NbColors.text3)
        VSpacer(8.dp)
        Text(stringResource(R.string.chat_muted), style = NbType.body, color = NbColors.text2)
        NbButton(stringResource(R.string.chat_unmute), onUnmute, kind = ButtonKind.TEXT)
    }
}

@Composable
private fun messageText(message: ChatMessage): String =
    if (message.kind == ChatKind.PHRASE) message.phraseId?.let { phraseText(it) }.orEmpty() else message.text.orEmpty()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Messages(state: ChatUiState, modifier: Modifier, held: Long?, onLongPress: (ChatMessage) -> Unit, onName: (ChatMessage) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) list.scrollToItem(state.messages.size - 1) }
    if (state.messages.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Text(stringResource(R.string.chat_empty), style = NbType.caption, color = NbColors.text3, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(state.messages, key = { it.id }) { message ->
            val mine = message.senderId != null && message.senderId == state.myId
            val showTime = mine && message == state.messages.lastOrNull { it.senderId == state.myId }
            ChatLine(message, mine, state.teamRoom, held == message.id, showTime, onLongPress, onName)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatLine(
    message: ChatMessage,
    mine: Boolean,
    teamRoom: Boolean,
    held: Boolean,
    showTime: Boolean,
    onLongPress: (ChatMessage) -> Unit,
    onName: (ChatMessage) -> Unit,
) {
    val text = messageText(message)
    val name = message.senderNickname ?: stringResource(R.string.chat_deleted_player)
    val spoken = if (mine) "${stringResource(R.string.tag_me)}: $text" else "$name ${message.senderTag.orEmpty()}: $text"
    val optionsLabel = stringResource(R.string.msg_selected)
    val cardLabel = stringResource(R.string.msg_player_card)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxBubble = maxWidth * 0.8f
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (!mine) {
                // Not a tap target (it is 20 dp tall); the player card is in the long-press sheet and a TalkBack action (D2-10).
                Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (teamRoom && message.senderTeam != null) {
                        TeamBadge(message.senderTeam ?: Team.A, size = 16.dp)
                        HSpacer(6.dp)
                    }
                    // Name `micro` 12 sp, tag `numTag` 13 / 16 sp (prototype `.m .who .ptag`, D2-11).
                    Text(name, style = NbType.micro, color = NbColors.text2)
                    message.senderTag?.let { Text("#$it", style = NbType.numTag.copy(fontSize = 13.sp, lineHeight = 16.sp), color = NbColors.text3) }
                }
            }
            val shape = RoundedCornerShape(NbRadius.m)
            Box(
                Modifier
                    .widthIn(max = maxBubble)
                    .clip(shape)
                    .background(if (mine) NbColors.chalk else NbColors.surface4)
                    .then(if (held) Modifier.border(2.dp, NbColors.chalk, shape) else Modifier)
                    .combinedClickable(onClick = {}, onLongClick = { onLongPress(message) })
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(optionsLabel) { onLongPress(message); true },
                            if (!mine && message.senderId != null) CustomAccessibilityAction(cardLabel) { onName(message); true } else null,
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(maskedText(text, mine), style = NbType.body, color = if (mine) NbColors.onChalk else NbColors.chalk)
            }
            if (showTime) {
                VSpacer(4.dp)
                Text(SimpleDateFormat("H:mm", Locale.ROOT).format(Date(message.at)), style = NbType.caption.copy(fontSize = NbType.micro.fontSize), color = NbColors.text3)
            }
        }
    }
}

/** Masked words (`***`) in `text3` (09 §3). */
@Composable
private fun maskedText(text: String, mine: Boolean) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("***", i)
        if (start < 0) {
            append(text.substring(i))
            break
        }
        append(text.substring(i, start))
        var end = start
        while (end < text.length && text[end] == '*') end++
        // Others' bubbles: `text2` on `surface4` (5.59 : 1); `text3` there is 4.39 : 1, under AA (DESIGN_SYSTEM 2.2, D2-12).
        withStyle(SpanStyle(color = if (mine) NbColors.textDisabled else NbColors.text2)) { append(text.substring(start, end)) }
        i = end
    }
}

/** Quick-phrase chips: 32 dp outline chips, 8 dp gap, horizontal scroll with a 16 dp right fade (DESIGN_SYSTEM 12.15). */
@Composable
private fun PhraseChips(state: ChatUiState, onPhrase: (String) -> Unit) {
    val enabled = state.cooldown == 0 && state.phraseCooldown == 0 && !state.offline && !state.restricted
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        // The row's 8 dp vertical padding is part of each chip's target: 48 dp tall targets around 32 dp chips (12.15, D2-10).
        Row(
            Modifier.fillMaxWidth().fadingEnd().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CHAT_CHIPS.forEach { id ->
                val shape = RoundedCornerShape(NbRadius.s)
                Box(Modifier.heightIn(min = 48.dp).rowClick(enabled = enabled) { onPhrase(id) }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .heightIn(min = 32.dp)
                            .clip(shape)
                            .border(1.dp, if (enabled) NbColors.outline else NbColors.line, shape)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(phraseText(id).orEmpty(), style = NbType.labelSmall, color = if (enabled) NbColors.chalk else NbColors.textDisabled, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(state: ChatUiState, actions: ChatActions) {
    when {
        state.restricted -> Row(
            Modifier.fillMaxWidth().height(56.dp).background(NbColors.surface2).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NbIcon(R.drawable.ic_block, null, NbColors.text2, 20.dp)
            HSpacer(12.dp)
            Text(stringResource(R.string.chat_restricted), style = NbType.caption, color = NbColors.chalk)
        }
        !state.canType -> Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                stringResource(if (state.phrasesOnly) R.string.chat_phrases_only else R.string.chat_after_first_match),
                style = NbType.caption,
                color = NbColors.text3,
            )
        }
        else -> Column(Modifier.fillMaxWidth()) {
            if (state.cooldown > 0) {
                // Prototype `.limit`: 0 / 16 / 4, `timer` 16 dp, 6 dp gap.
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    NbIcon(R.drawable.ic_timer, null, NbColors.strike, 16.dp)
                    HSpacer(6.dp)
                    Text(pluralStringResource(R.plurals.chat_rate_limited, state.cooldown, state.cooldown), style = NbType.caption, color = NbColors.strike)
                }
            }
            // 4 dp top and right as specified; the field sits directly on the navigation inset like the reference render.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val length = state.input.codePointCount(0, state.input.length)
                NbTextField(
                    value = state.input,
                    onValueChange = actions.onInputChange,
                    placeholder = pluralStringResource(R.plurals.chat_hint, CHAT_INPUT_MAX, CHAT_INPUT_MAX),
                    height = 48.dp,
                    horizontalPadding = 12.dp,
                    textStyle = NbType.body.copy(color = NbColors.chalk),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { actions.onSend() }),
                    trailing = if (length >= CHAT_INPUT_MAX - 10) {
                        {
                            Text(
                                stringResource(R.string.chat_counter, length, CHAT_INPUT_MAX),
                                style = NbType.numTag,
                                color = if (length >= CHAT_INPUT_MAX) NbColors.strike else NbColors.text3,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f).testTag("chatInput"),
                )
                HSpacer(8.dp)
                val canSend = state.input.isNotBlank() && state.cooldown == 0 && !state.sending && !state.offline
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(NbRadius.m))
                        .background(if (canSend) NbColors.chalk else NbColors.surface2)
                        .rowClick(enabled = canSend, onClick = actions.onSend)
                        .testTag("chatSend"),
                    contentAlignment = Alignment.Center,
                ) {
                    NbIcon(R.drawable.ic_send, stringResource(R.string.chat_send), if (canSend) NbColors.onChalk else NbColors.textDisabled)
                }
            }
        }
    }
}

/** Long-press action sheet (09 §3): context line, copy, report message, block player (others only). */
@Composable
private fun MessageActions(
    message: ChatMessage,
    mine: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onPlayerCard: () -> Unit,
) {
    NbModalSheet(onDismiss = onDismiss) {
        // Prototype `.menu .ctx`: 4 / 16 / 12, 2 dp before the bold line; `.menu` 24 dp bottom padding.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Text(stringResource(R.string.msg_selected), style = NbType.caption, color = NbColors.text3)
            VSpacer(2.dp)
            val name = message.senderNickname?.let { "$it#${message.senderTag.orEmpty()}" } ?: stringResource(R.string.chat_deleted_player)
            Text("$name · ${messageText(message)}", style = NbType.bodyStrong, color = NbColors.chalk, maxLines = 2)
        }
        Hairline()
        ListRow(stringResource(R.string.msg_copy), icon = R.drawable.ic_content_copy, onClick = onCopy)
        if (!mine && message.senderId != null) {
            // The player card opens from here, not from the 20 dp name line above the bubble (48 dp targets, D2-10).
            InsetDivider(56.dp)
            ListRow(stringResource(R.string.msg_player_card), icon = R.drawable.ic_person, onClick = onPlayerCard)
            InsetDivider(56.dp)
            ListRow(stringResource(R.string.msg_report), icon = R.drawable.ic_flag, onClick = onReport)
            InsetDivider(56.dp)
            ListRow(stringResource(R.string.msg_block), icon = R.drawable.ic_block, subtitle = stringResource(R.string.block_sub), destructive = true, onClick = onBlock)
        }
        SheetBottomSpace()
    }
}

/** Quick-phrase sheet for quick matches (05 §2.8): note + 2 × 3 grid of tonal 48 dp buttons; disabled during the cooldown. */
@Composable
fun QuickPhraseSheet(enabled: Boolean, onPhrase: (String) -> Unit, onDismiss: () -> Unit) {
    NbModalSheet(onDismiss = onDismiss) {
        // Prototype (match.html?state=phrases): title row 4 dp below the grab, note 0 / 16 / 12, 2 × 3 grid with 8 dp
        // gaps, 24 dp sheet bottom.
        VSpacer(4.dp)
        SheetTitle(stringResource(R.string.phrases_title)) {
            IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), onDismiss, tint = NbColors.chalk)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp)) {
            Text(stringResource(R.string.phrases_note), style = NbType.caption, color = NbColors.text3)
            VSpacer(12.dp)
            // Large text: one phrase per row, so a word never has to break inside itself (D2-25).
            DESIGN_PHRASES.chunked(if (isLargeFont()) 1 else 2).forEachIndexed { i, pair ->
                if (i > 0) VSpacer(8.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { id ->
                        NbButton(phraseText(id).orEmpty(), { onPhrase(id) }, kind = ButtonKind.TONAL, enabled = enabled, modifier = Modifier.weight(1f).testTag("phrase_$id"))
                    }
                }
            }
        }
        SheetBottomSpace()
    }
}

@Composable
fun phraseText(id: String): String? = when (id) {
    "hello" -> stringResource(R.string.phrase_hello)
    "good_luck" -> stringResource(R.string.phrase_good_luck)
    "have_fun" -> stringResource(R.string.phrase_have_fun)
    "nice_guess" -> stringResource(R.string.phrase_nice_guess)
    "close_one" -> stringResource(R.string.phrase_close_one)
    "thinking" -> stringResource(R.string.phrase_thinking)
    "oops" -> stringResource(R.string.phrase_oops)
    "well_played" -> stringResource(R.string.phrase_well_played)
    "good_game" -> stringResource(R.string.phrase_good_game)
    "thanks" -> stringResource(R.string.phrase_thanks)
    "rematch" -> stringResource(R.string.phrase_rematch)
    "bye" -> stringResource(R.string.phrase_bye)
    else -> null
}
