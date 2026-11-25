package app.numbaseball.ui.match

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.numbaseball.R
import app.numbaseball.audio.GameAudio
import app.numbaseball.audio.GameHaptics
import app.numbaseball.audio.HapticCue
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.LocalGameHaptics
import app.numbaseball.audio.MusicTrack
import app.numbaseball.audio.ScreenMusic
import app.numbaseball.audio.SoundCue
import app.numbaseball.audio.TimerCues
import app.numbaseball.audio.ballLampRate
import app.numbaseball.audio.lampVolume
import app.numbaseball.audio.strikeLampRate
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.ui.computer.levelLabel
import app.numbaseball.ui.design.ActionState
import app.numbaseball.ui.design.ClockLine
import app.numbaseball.ui.design.ConnectionBanner
import app.numbaseball.ui.design.DigitKeypad
import app.numbaseball.ui.design.DigitSlots
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.KeypadMode
import app.numbaseball.ui.design.Lamp
import app.numbaseball.ui.design.LampColor
import app.numbaseball.ui.design.LocalReduceMotion
import app.numbaseball.ui.design.MiniPlates
import app.numbaseball.ui.design.NameWithTag
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.NbDialog
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.PitchClock
import app.numbaseball.ui.design.ToastHost
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.clockLevel
import app.numbaseball.ui.design.formatClock
import app.numbaseball.ui.design.isCompactHeight
import app.numbaseball.ui.design.isLargeFont
import app.numbaseball.ui.design.SheetFrame
import app.numbaseball.ui.design.rememberToastState
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbLayout
import app.numbaseball.ui.theme.NbMotion
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Actions of the match screen; one object keeps the screen signature readable. */
class MatchActions(
    val onDigit: (Char) -> Unit,
    val onDelete: () -> Unit,
    val onClear: () -> Unit,
    val onTapSlot: (Int) -> Unit,
    val onSubmit: () -> Unit,
    val onRandomSecret: () -> Unit,
    val onForfeit: () -> Unit,
    val onLeave: () -> Unit,
    val onMemoDigit: (Int) -> Unit,
    val onMemoCell: (Int, Int) -> Unit,
    val onMemoClear: () -> Unit,
    val onMemoCollapse: (Boolean) -> Unit,
    val onChat: (() -> Unit)? = null,
    val onReport: (() -> Unit)? = null,
    val onHowToPlay: () -> Unit = {},
    val onToRoom: (() -> Unit)? = null,
    val onTeamBoard: (() -> Unit)? = null,
    /** Called once the last reveal (and the solve flash) is over and a summary exists (DESIGN_SYSTEM 8.3 C). */
    val onFinished: (MatchSummary) -> Unit = {},
)

/** A quick phrase shown as a bubble under the duel strip for 2.8 s (05 §2.8). */
data class PhraseBubble(val id: Long, val sender: String, val text: String)

/**
 * The game (05): everything for deduction on one screen, one thumb, no ads. Normal layout: top bar · duel strip · log ·
 * memo · entry. Height-compact windows (< 480 dp): ADAPTIVE.md §1.
 */
@Composable
fun MatchScreen(state: MatchUiState, actions: MatchActions, bubble: PhraseBubble? = null, initialLargeMemo: Boolean = false) {
    val audio = LocalGameAudio.current
    val haptics = LocalGameHaptics.current
    val compact = isCompactHeight()
    val toast = rememberToastState()
    var showMenu by remember { mutableStateOf(false) }
    var confirmForfeit by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var memoInCompact by rememberSaveable { mutableStateOf(false) }
    val large = isLargeFont()
    var largeMemo by rememberSaveable { mutableStateOf(initialLargeMemo) }
    var shakeKey by remember { mutableIntStateOf(0) }
    val reveal = rememberReveal(state, audio, haptics, actions.onFinished)
    ScreenMusic(MusicTrack.MATCH, audio)
    MatchHooks(state, reveal.running, audio, haptics, toast::show)

    val secretPhase = state.phase == MatchPhase.CHOOSING_SECRET || state.phase == MatchPhase.WAITING_FOR_OPPONENT_SECRET
    val keypadLocked = !state.acceptsInput || reveal.locked
    val rejected: () -> Unit = {
        audio.play(SoundCue.KEY_REJECTED)
        haptics.perform(HapticCue.REJECT)
        shakeKey++
    }
    val onDigit: (Char) -> Unit = { d ->
        if (state.input.length >= state.digits) {
            rejected()
        } else {
            audio.play(SoundCue.KEY_DOWN)
            haptics.perform(HapticCue.KEY_TAP)
            actions.onDigit(d)
        }
    }
    val onDelete: () -> Unit = {
        audio.play(SoundCue.BACKSPACE)
        haptics.perform(HapticCue.KEY_TAP)
        actions.onDelete()
    }
    val onSubmit: () -> Unit = {
        if (state.input.length < state.digits) {
            rejected()
        } else {
            audio.play(if (secretPhase) SoundCue.SECRET_LOCKED else SoundCue.PITCH_SUBMIT)
            haptics.perform(HapticCue.CONFIRM)
            actions.onSubmit()
        }
    }
    // memo_cycle (DESIGN_SYSTEM 8.3 E): mark cross-fades, `sfx_memo_mark` + CLOCK_TICK.
    val onMemoDigit: (Int) -> Unit = { d ->
        audio.play(SoundCue.MEMO_MARK)
        haptics.perform(HapticCue.MEMO)
        actions.onMemoDigit(d)
    }
    val onMemoCell: (Int, Int) -> Unit = { p, d ->
        audio.play(SoundCue.MEMO_MARK)
        haptics.perform(HapticCue.MEMO)
        actions.onMemoCell(p, d)
    }
    val onLeave: () -> Unit = {
        if (!state.mode.online && state.myRounds.isEmpty()) actions.onLeave() else confirmLeave = true
    }
    val mySecretToast = state.mySecret?.let { stringResource(R.string.my_secret) + " " + it }

    NbScreen(
        topBar = {
            MatchTopBar(
                state = state,
                compact = compact,
                onClose = onLeave,
                onMenu = { showMenu = true },
                memoToggle = if (compact && !secretPhase) ({ memoInCompact = !memoInCompact }) else null,
                onTitleClick = actions.onTeamBoard,
                menu = {
                    MatchMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        state = state,
                        compact = compact,
                        onHowTo = actions.onHowToPlay,
                        onReport = actions.onReport,
                        onShowSecret = { mySecretToast?.let { toast.show(it) } },
                        onRandom = if (compact && state.phase == MatchPhase.CHOOSING_SECRET && state.acceptsInput) actions.onRandomSecret else null,
                        onForfeit = { confirmForfeit = true },
                    )
                },
            )
        },
        overlay = {
            ToastHost(toast, bottomOffset = if (compact) 8.dp else 16.dp + 172.dp + 24.dp)
            if (largeMemo && !secretPhase) {
                BackHandler { largeMemo = false }
                SheetFrame(onDismiss = { largeMemo = false }) {
                    LargeMemoSheet(state.memo, onMemoDigit, onMemoCell, actions.onMemoClear, onClose = { largeMemo = false })
                }
            }
        },
    ) {
        state.practice?.let { PracticeBand(it, actions.onToRoom) }
        state.reconnectSeconds?.let {
            ConnectionBanner(stringResource(R.string.reconnecting_title), stringResource(R.string.reconnecting_match), formatClock(it))
        }
        if (!compact) DuelStrip(state, actions.onChat, bubble)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val available = maxHeight
            Column(Modifier.fillMaxSize()) {
                when {
                    secretPhase -> HintLegend(compact, Modifier.weight(1f))
                    compact && memoInCompact -> {
                        MemoBoard(state.memo, collapsed = false, onMemoDigit, onMemoCell, actions.onMemoClear, onCollapse = null, margin = 12.dp)
                        Spacer(Modifier.weight(1f))
                    }
                    else -> {
                        val expandedMemo = memoHeight(state.digits, collapsed = false)
                        val header = if (compact) 0.dp else NbSize.historyHeader + 1.dp
                        val autoCollapse = available - header - expandedMemo < NbSize.historyRowLatest + NbSize.historyRow
                        // Large text (ADAPTIVE.md §2): the inline memo stays collapsed; expanding opens the large memo sheet.
                        val collapsed = large || (state.memo.collapsed ?: autoCollapse)
                        if (!compact) PitchLogHeader(state.digits)
                        PitchLog(state.digits, state.myRounds, state.opponentRounds, reveal.progress, compact, Modifier.weight(1f))
                        if (!compact) {
                            MemoBoard(
                                state.memo, collapsed, onMemoDigit, onMemoCell, actions.onMemoClear,
                                onCollapse = { collapse -> if (large && !collapse) largeMemo = true else actions.onMemoCollapse(collapse) },
                            )
                        }
                    }
                }
            }
        }
        EntryBlock(
            state = state,
            compact = compact,
            secretPhase = secretPhase,
            keypadLocked = keypadLocked,
            shakeKey = shakeKey,
            onDigit = onDigit,
            onDelete = onDelete,
            onClear = actions.onClear,
            onTapSlot = actions.onTapSlot,
            onSubmit = onSubmit,
            onRandom = actions.onRandomSecret,
            onTeamBoard = actions.onTeamBoard,
        )
    }

    if (confirmForfeit) {
        NbDialog(
            title = stringResource(R.string.forfeit_title),
            body = stringResource(R.string.forfeit_body),
            confirmLabel = stringResource(R.string.menu_forfeit),
            onConfirm = {
                confirmForfeit = false
                actions.onForfeit()
            },
            onDismiss = { confirmForfeit = false },
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
    if (confirmLeave) {
        NbDialog(
            title = stringResource(R.string.leave_title),
            body = stringResource(if (state.mode.online) R.string.leave_body else R.string.leave_cpu_body),
            confirmLabel = stringResource(R.string.leave),
            onConfirm = {
                confirmLeave = false
                actions.onLeave()
            },
            onDismiss = { confirmLeave = false },
            dismissLabel = stringResource(R.string.leave_continue),
            destructive = true,
        )
    }
}

@Composable
private fun modeName(state: MatchUiState): String = when (state.mode) {
    MatchMode.QUICK -> stringResource(R.string.mode_quick)
    MatchMode.ROOM -> stringResource(R.string.mode_room)
    MatchMode.TEAM -> stringResource(R.string.mode_team)
    MatchMode.COMPUTER -> stringResource(R.string.mode_cpu)
    MatchMode.PRACTICE -> stringResource(R.string.mode_practice)
}

@Composable
private fun digitsLabel(digits: Int) = pluralStringResource(R.plurals.digits_n, digits, digits)

/** Two-line title (DESIGN_SYSTEM 12.1 match variant): round over "mode · digits" (secret entry: mode over digits). */
@Composable
private fun MatchTopBar(
    state: MatchUiState,
    compact: Boolean,
    onClose: () -> Unit,
    onMenu: () -> Unit,
    memoToggle: (() -> Unit)?,
    onTitleClick: (() -> Unit)?,
    menu: @Composable () -> Unit,
) {
    val secretPhase = state.phase == MatchPhase.CHOOSING_SECRET || state.phase == MatchPhase.WAITING_FOR_OPPONENT_SECRET
    val title = when {
        compact && secretPhase -> stringResource(R.string.secret_short)
        secretPhase -> modeName(state)
        else -> stringResource(R.string.round_n, state.round.coerceAtLeast(1))
    }
    val subtitle = when {
        secretPhase -> digitsLabel(state.digits)
        state.mode == MatchMode.TEAM && state.teamScore != null -> stringResource(R.string.team_line, state.teamScore.first, state.teamScore.second)
        state.opponent is Opponent.Computer -> stringResource(R.string.mode_line, stringResource(R.string.mode_cpu) + " · " + levelLabel(state.opponent.level), digitsLabel(state.digits))
        else -> stringResource(R.string.mode_line, modeName(state), digitsLabel(state.digits))
    }
    val level = clockLevel(state.secondsLeft, paused = state.reconnectSeconds != null)
    Column(Modifier.fillMaxWidth()) {
        TopBar(
            title = null,
            navIcon = R.drawable.ic_close,
            navDescription = stringResource(R.string.leave),
            onNav = onClose,
            titleContent = {
                Column(
                    Modifier
                        .padding(start = 0.dp)
                        .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
                ) {
                    Text(title, style = NbType.subtitle, color = NbColors.chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (compact && !secretPhase) {
                        StatusLine(state.opponentStatus)
                    } else if (!isLargeFont()) {
                        // Large text: the mode subtitle is hidden (ADAPTIVE.md §2).
                        Text(subtitle, style = NbType.caption, color = NbColors.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            actions = {
                // Secondary top-bar action: `text2` (DESIGN_SYSTEM 7, D2-06).
                if (memoToggle != null) IconButton48(R.drawable.ic_edit_note, stringResource(R.string.memo), memoToggle, tint = NbColors.text2)
                state.secondsLeft?.let { PitchClock(it, level, Modifier.padding(start = 8.dp, end = 8.dp)) }
                Box {
                    IconButton48(R.drawable.ic_more_vert, stringResource(R.string.action_more), onMenu)
                    menu()
                }
            },
            bottom = {
                val total = state.roundSeconds
                val fraction = if (total != null && state.secondsLeft != null) state.secondsLeft / total.toFloat() else 1f
                ClockLine(fraction, level)
            },
            titleGap = 8.dp,
            height = 54.dp,
            compactHeight = 46.dp,
        )
    }
}

@Composable
private fun MatchMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    state: MatchUiState,
    compact: Boolean,
    onHowTo: () -> Unit,
    onReport: (() -> Unit)?,
    onShowSecret: () -> Unit,
    onForfeit: () -> Unit,
    onRandom: (() -> Unit)?,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = NbColors.surface3,
        shape = RoundedCornerShape(NbRadius.m),
    ) {
        val colors = MenuDefaults.itemColors(textColor = NbColors.chalk)
        // Compact secret entry has no shuffle key: random fill lives here (D2-35).
        if (onRandom != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_random_fill), style = NbType.body) }, onClick = { onDismiss(); onRandom() }, colors = colors)
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_how_to), style = NbType.body) }, onClick = { onDismiss(); onHowTo() }, colors = colors)
        if (compact && state.mySecret != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_show_secret), style = NbType.body) }, onClick = { onDismiss(); onShowSecret() }, colors = colors)
        }
        if (onReport != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_report), style = NbType.body) }, onClick = { onDismiss(); onReport() }, colors = colors)
        }
        if (state.mySecret != null && state.phase != MatchPhase.FINISHED) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_forfeit), style = NbType.body, color = NbColors.out) },
                onClick = { onDismiss(); onForfeit() },
            )
        }
    }
}

/** Status line under the opponent's name: 6 dp lamp + `caption` (DESIGN_SYSTEM 12.3). */
@Composable
private fun StatusLine(status: OpponentStatus, wrap: Boolean = false) {
    val (lit, text, color) = when (status) {
        OpponentStatus.Picking -> Triple(false, stringResource(R.string.opp_picking), NbColors.text2)
        OpponentStatus.Ready -> Triple(true, stringResource(R.string.opp_ready), NbColors.text2)
        OpponentStatus.Thinking -> Triple(false, stringResource(R.string.opp_thinking), NbColors.text2)
        OpponentStatus.Pitched -> Triple(true, stringResource(R.string.opp_pitched), NbColors.text2)
        is OpponentStatus.Disconnected -> Triple(false, stringResource(R.string.opp_disconnected, formatClock(status.secondsLeft)), NbColors.strike)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lamp(LampColor.BALL, lit, NbSize.lampS)
        HSpacer(8.dp)
        Text(text, style = NbType.caption, color = color, maxLines = if (wrap) 2 else 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Duel strip (DESIGN_SYSTEM 12.3): 52 dp, 16 dp margins, 12 dp gaps: `VS` · name + tag over status · quick-phrase
 * button (online) · "내 숫자" over mini plates.
 */
@Composable
private fun DuelStrip(state: MatchUiState, onChat: (() -> Unit)?, bubble: PhraseBubble?) {
    val secretPhase = state.phase == MatchPhase.CHOOSING_SECRET || state.phase == MatchPhase.WAITING_FOR_OPPONENT_SECRET
    val large = isLargeFont()
    val lead: @Composable () -> Unit = {
        when (state.opponent) {
            is Opponent.Computer -> NbIcon(R.drawable.ic_memory, null, NbColors.text3, 20.dp, Modifier.width(20.dp))
            // `VS` stays on one line (D2-01).
            is Opponent.Player -> Text("VS", style = NbType.numLabel, color = NbColors.text3, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 20.dp))
        }
    }
    val name: @Composable (Modifier) -> Unit = { m ->
        when (val o = state.opponent) {
            is Opponent.Computer -> Text(
                stringResource(R.string.mode_cpu) + " · " + levelLabel(o.level),
                style = NbType.subtitle,
                color = NbColors.chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = m,
            )
            is Opponent.Player -> NameWithTag(o.nickname, o.tag, m)
        }
    }
    val secret = state.mySecret?.takeIf { !secretPhase }
    Box {
        if (large) {
            // Large text (ADAPTIVE.md §2): two lines, 72 dp minimum: name row, then status + my secret.
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        lead()
                        HSpacer(12.dp)
                        name(Modifier.weight(1f, fill = false))
                    }
                    VSpacer(4.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { StatusLine(state.opponentStatus, wrap = true) }
                        if (secret != null) {
                            HSpacer(12.dp)
                            val spoken = stringResource(R.string.my_secret) + " " + secret.toList().joinToString(" ")
                            Box(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) { MiniPlates(secret) }
                        }
                    }
                }
                if (onChat != null) {
                    HSpacer(4.dp)
                    IconButton48(R.drawable.ic_chat, stringResource(R.string.chat_title), onChat, tint = NbColors.text2)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = NbLayout.duelStrip).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                lead()
                HSpacer(12.dp)
                Column(Modifier.weight(1f)) {
                    name(Modifier)
                    StatusLine(state.opponentStatus)
                }
                if (onChat != null) {
                    HSpacer(12.dp)
                    IconButton48(R.drawable.ic_chat, stringResource(R.string.chat_title), onChat, tint = NbColors.text2)
                }
                if (secret != null) {
                    HSpacer(12.dp)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.my_secret), style = NbType.caption, color = NbColors.text3)
                        VSpacer(2.dp)
                        MiniPlates(secret)
                    }
                }
            }
        }
        if (bubble != null) PhraseBubbleView(bubble, Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = NbLayout.duelStrip + 8.dp))
    }
}

@Composable
private fun PhraseBubbleView(bubble: PhraseBubble, modifier: Modifier) {
    Column(
        modifier
            .widthIn(max = 240.dp)
            .background(NbColors.surface4, RoundedCornerShape(NbRadius.m))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(bubble.sender, style = NbType.micro, color = NbColors.text2)
        Text(bubble.text, style = NbType.body, color = NbColors.chalk)
    }
}

/** Practice band (05 §2.11): 40 dp `surface3`: "방 K7Q2M · 3/6명 기다리는 중" + "방으로". */
@Composable
private fun PracticeBand(info: PracticeInfo, onToRoom: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(40.dp).background(NbColors.surface3).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.practice_band, info.roomCode, info.present, info.total), style = NbType.caption, color = NbColors.chalk, modifier = Modifier.weight(1f))
        if (onToRoom != null) NbButton(stringResource(R.string.practice_to_room), onToRoom, kind = ButtonKind.TEXT)
    }
}

/** Entry (05 §1): 1 dp `line`, 12 · slots 64 · 12 · keypad 172 · 12 (compact: 8 · 48 · 8 · 100 · 8). */
@Composable
private fun EntryBlock(
    state: MatchUiState,
    compact: Boolean,
    secretPhase: Boolean,
    keypadLocked: Boolean,
    shakeKey: Int,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onTapSlot: (Int) -> Unit,
    onSubmit: () -> Unit,
    onRandom: () -> Unit,
    onTeamBoard: (() -> Unit)?,
) {
    val margin = if (compact) 12.dp else 16.dp
    val gap = if (compact) 8.dp else 12.dp
    val waiting = state.phase == MatchPhase.WAITING_FOR_OPPONENT_GUESS || state.phase == MatchPhase.WAITING_FOR_OPPONENT_SECRET
    Column(Modifier.fillMaxWidth()) {
        if (!secretPhase) Hairline()
        if (state.phase == MatchPhase.FINISHED) {
            Column(Modifier.fillMaxWidth().padding(margin), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.duel_over), style = NbType.body, color = NbColors.text2, textAlign = TextAlign.Center)
                if (onTeamBoard != null) {
                    VSpacer(12.dp)
                    NbButton(stringResource(R.string.team_match), onTeamBoard, kind = ButtonKind.TONAL, modifier = Modifier.fillMaxWidth())
                }
            }
            return
        }
        if (secretPhase && !compact) {
            VSpacer(24.dp)
            Text(stringResource(R.string.secret_title), style = NbType.title, color = NbColors.chalk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            VSpacer(4.dp)
            Text(
                pluralStringResource(R.plurals.secret_rule, state.digits, state.digits),
                style = NbType.caption,
                color = NbColors.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            VSpacer(4.dp)
        }
        VSpacer(gap)
        DigitSlots(
            digits = state.digits,
            value = state.input.ifEmpty { if (waiting && state.phase == MatchPhase.WAITING_FOR_OPPONENT_SECRET) state.mySecret.orEmpty() else state.input },
            modifier = Modifier.padding(horizontal = margin),
            locked = waiting,
            error = state.inputProblem != null || state.error != null,
            compact = compact,
            showActive = state.acceptsInput,
            shakeKey = shakeKey,
            onTapSlot = onTapSlot,
        )
        VSpacer(gap)
        DigitKeypad(
            mode = if (secretPhase && !compact) KeypadMode.SECRET else KeypadMode.GUESS,
            used = state.input.toSet(),
            locked = keypadLocked,
            actionLabel = stringResource(if (secretPhase && (compact || isLargeFont())) R.string.lock_in_short else if (secretPhase) R.string.lock_in else R.string.pitch),
            actionState = when {
                waiting -> ActionState.WAITING
                state.reconnectSeconds != null -> ActionState.OFFLINE
                keypadLocked || state.input.length < state.digits -> ActionState.DISABLED
                else -> ActionState.ENABLED
            },
            waitingLabel = stringResource(if (secretPhase) R.string.waiting_ready else R.string.waiting_opp),
            onDigit = onDigit,
            onBackspace = onDelete,
            onAction = onSubmit,
            onClear = onClear,
            onRandom = onRandom,
            memo = if (secretPhase) emptyMap() else state.memo.keyMarks,
            compact = compact,
            modifier = Modifier.padding(horizontal = margin),
        )
        VSpacer(gap)
    }
}

/** State of The Count on the latest log row. */
class RevealHandle(val progress: RevealProgress, val running: Boolean, val locked: Boolean)

/**
 * Runs The Count (DESIGN_SYSTEM 8.3 B) when a new round arrives: lamps ignite 240 + 110 × i ms after the result, each
 * 180 ms; the keypad unlocks at `reveal_last`; a solve flashes then hands off to the result (8.3 C, 400 ms). Sound and
 * haptic hooks fire on the same frames. Reduced motion: lamps at once, hooks 60 ms apart (8.4).
 */
@Composable
private fun rememberReveal(state: MatchUiState, audio: GameAudio, haptics: GameHaptics, onFinished: (MatchSummary) -> Unit): RevealHandle {
    val reduce = LocalReduceMotion.current
    var shown by rememberSaveable { mutableIntStateOf(state.myRounds.size) }
    var running by remember { mutableStateOf(false) }
    val t = remember { Animatable(Float.MAX_VALUE) }
    val latest: RoundEntry? = state.myRounds.lastOrNull()
    val digits = state.digits
    LaunchedEffect(state.myRounds.size) {
        val entry = state.myRounds.lastOrNull()
        if (state.myRounds.size <= shown || entry == null) {
            shown = state.myRounds.size
            return@LaunchedEffect
        }
        shown = state.myRounds.size
        if (entry.guess == null) return@LaunchedEffect
        running = true
        val s = entry.strikes
        val b = entry.balls
        val lead = NbMotion.revealLeadIn.toFloat()
        val step = if (reduce) 60f else NbMotion.lampStagger.toFloat()
        val hooks = buildList {
            if (s == 0 && b == 0) add(lead to { audio.play(SoundCue.LAMP_OUT); haptics.perform(HapticCue.LAMP_OUT) })
            repeat(s) { i -> add(lead + step * i to { audio.play(SoundCue.LAMP_STRIKE, strikeLampRate(i), lampVolume(reduce)); haptics.perform(HapticCue.LAMP_STRIKE) }) }
            repeat(b) { j -> add(lead + step * (s + j) to { audio.play(SoundCue.LAMP_BALL, ballLampRate(j), lampVolume(reduce)); haptics.perform(HapticCue.LAMP_BALL) }) }
        }.sortedBy { it.first }
        val end = (hooks.lastOrNull()?.first ?: lead) + NbMotion.lampIgnite
        coroutineScope {
            if (reduce) {
                t.snapTo(end)
            } else {
                t.snapTo(0f)
                launch { t.animateTo(end, tween(end.toInt(), easing = LinearEasing)) }
            }
            var elapsed = 0f
            hooks.forEach { (time, fire) ->
                val wait = (if (reduce) time - lead else time) - elapsed
                if (wait > 0) delay(wait.toLong())
                elapsed += wait.coerceAtLeast(0f)
                fire()
            }
            val rest = end - (if (reduce) end else elapsed)
            if (!reduce && rest > 0) delay(rest.toLong())
        }
        if (s == digits) {
            audio.play(SoundCue.SOLVE)
            haptics.perform(HapticCue.SOLVE)
        }
        running = false
    }
    LaunchedEffect(state.summary, running) {
        val summary = state.summary ?: return@LaunchedEffect
        if (running) return@LaunchedEffect
        delay(NbMotion.resultDelay.toLong())
        onFinished(summary)
    }
    val time = t.value
    val progress = if (latest == null || latest.guess == null || !running && time == Float.MAX_VALUE) {
        RevealProgress.Done
    } else {
        val lead = NbMotion.revealLeadIn
        val stagger = NbMotion.lampStagger
        fun ignite(start: Int) = ((time - start) / NbMotion.lampIgnite).coerceIn(0f, 1f)
        RevealProgress(
            strikes = List(latest.strikes) { i -> ignite(lead + stagger * i) },
            balls = List(latest.balls) { j -> ignite(lead + stagger * (latest.strikes + j)) },
            out = ignite(lead),
            strikeText = if (latest.strikes == 0) 1f else ((time - lead) / NbMotion.short).coerceIn(0f, 1f),
            ballText = if (latest.balls == 0) 1f else ((time - (lead + stagger * latest.strikes)) / NbMotion.short).coerceIn(0f, 1f),
        )
    }
    val revealLast = latest?.let { NbMotion.revealLeadIn + NbMotion.lampStagger * maxOf(it.strikes + it.balls - 1, 0) } ?: 0
    return RevealHandle(progress, running, locked = running && time < revealLast)
}

/** Pitch clock and opponent hooks (DESIGN_SYSTEM 8.3 D, E). */
@Composable
private fun MatchHooks(state: MatchUiState, revealing: Boolean, audio: GameAudio, haptics: GameHaptics, toast: (String) -> Unit) {
    val expired = stringResource(R.string.timer_expired)
    val active = state.phase == MatchPhase.GUESSING || state.phase == MatchPhase.CHOOSING_SECRET
    val timerCues = remember { TimerCues() }
    LaunchedEffect(state.secondsLeft, active) {
        val s = state.secondsLeft ?: return@LaunchedEffect
        if (!active || state.reconnectSeconds != null) return@LaunchedEffect
        when (timerCues.onSecond(state.phase to state.round, s)) {
            TimerCues.Cue.WARN -> { audio.play(SoundCue.TIMER_WARN); haptics.perform(HapticCue.TIMER_WARN) }
            TimerCues.Cue.TICK -> { audio.play(SoundCue.TIMER_TICK); haptics.perform(HapticCue.TIMER_TICK) }
            TimerCues.Cue.EXPIRED -> { audio.play(SoundCue.TIMER_EXPIRED); toast(expired) }
            null -> Unit
        }
    }
    LaunchedEffect(state.opponentStatus) {
        if (state.opponentStatus == OpponentStatus.Pitched && state.mode.online && !revealing) audio.play(SoundCue.OPPONENT_PITCHED)
    }
}
