package app.numbaseball.ui.match

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
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
import androidx.compose.ui.semantics.liveRegion
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
import app.numbaseball.ui.design.LegendChip
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.KeypadMode
import app.numbaseball.ui.design.Avatar
import app.numbaseball.ui.design.Dot
import app.numbaseball.ui.design.LocalReduceMotion
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
import app.numbaseball.ui.design.targetOverflow
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

/** A quick phrase shown as a bubble under the opponent row for 2.8 s (05 §2.8); [mine]: the one I sent (`accentTint`, left). */
data class PhraseBubble(val id: Long, val sender: String, val text: String, val mine: Boolean = false)

/**
 * The game (05, DESIGN_SYSTEM v2): top bar with the timer pill and progress line · opponent row · log · entry head
 * (my number, 메모) · slots · keypad. The memo lives in a sheet (12.9). Height-compact windows (< 480 dp): ADAPTIVE.md §1.
 */
@Composable
fun MatchScreen(
    state: MatchUiState,
    actions: MatchActions,
    bubble: PhraseBubble? = null,
    initialLargeMemo: Boolean = false,
    initialOpponentLog: Boolean = false,
    /** Opens the memo sheet at once (screenshot `match-memo-ko`). */
    initialMemo: Boolean = false,
    /** The interactive tutorial (01 §2) runs on this screen with a coach box and its own action key. */
    tutorial: app.numbaseball.ui.tutorial.TutorialUi? = null,
    /** Shows this toast once on open (screenshot `match-toast-ko`). */
    initialToast: String? = null,
) {
    val audio = LocalGameAudio.current
    val haptics = LocalGameHaptics.current
    val compact = isCompactHeight()
    val toast = rememberToastState()
    if (initialToast != null) LaunchedEffect(initialToast) { toast.show(initialToast) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmForfeit by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val large = isLargeFont()
    // The memo sheet (normal or, at large text, the transposed one) survives rotation and resize (05 §3).
    var memoOpen by rememberSaveable { mutableStateOf(initialMemo || initialLargeMemo) }
    var opponentLog by rememberSaveable { mutableStateOf(initialOpponentLog) }
    var shakeKey by remember { mutableIntStateOf(0) }
    val reveal = rememberReveal(state, audio, haptics, actions.onFinished)
    ScreenMusic(MusicTrack.MATCH, audio)
    MatchHooks(state, reveal.running, audio, haptics, toast::show)

    val secretPhase = state.phase == MatchPhase.CHOOSING_SECRET || state.phase == MatchPhase.WAITING_FOR_OPPONENT_SECRET
    val keypadLocked = !state.acceptsInput || reveal.locked
    val memoAvailable = !secretPhase && tutorial?.showMemo != false
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
        if (tutorial != null || (!state.mode.online && state.myRounds.isEmpty())) actions.onLeave() else confirmLeave = true
    }
    val mySecretToast = state.mySecret?.let { stringResource(R.string.my_secret) + " " + it }
    val openMemo: () -> Unit = { memoOpen = true }

    NbScreen(
        topBar = {
            MatchTopBar(
                state = state,
                compact = compact,
                onClose = onLeave,
                onMenu = { showMenu = true },
                memoToggle = if (compact && memoAvailable) openMemo else null,
                tutorial = tutorial,
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
                        onMemo = if (memoAvailable) openMemo else null,
                        onOpponentLog = if (!secretPhase && tutorial == null) ({ opponentLog = true }) else null,
                        onForfeit = { confirmForfeit = true },
                    )
                },
            )
        },
        overlay = {
            if (opponentLog && !secretPhase) {
                BackHandler { opponentLog = false }
                SheetFrame(onDismiss = { opponentLog = false }) {
                    OpponentLogSheet(state.digits, state.opponentRounds, onClose = { opponentLog = false })
                }
            }
            // The memo sheet stays open through reveals (05 §2.4): the round result plays underneath and the keypad
            // marks update live. It is never closed by the match itself.
            if (memoOpen && memoAvailable) {
                BackHandler { memoOpen = false }
                if (large) {
                    // Full window height minus the status bar, so the header stays in one place (D2-52).
                    val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    SheetFrame(onDismiss = { memoOpen = false }, top = statusTop) {
                        LargeMemoSheet(
                            state.memo, onMemoDigit, onMemoCell, actions.onMemoClear,
                            onClose = { memoOpen = false },
                            clock = state.secondsLeft?.let { s -> { PitchClock(s, clockLevel(s, paused = state.reconnectSeconds != null), Modifier.padding(horizontal = 4.dp)) } },
                        )
                    }
                } else {
                    SheetFrame(onDismiss = { memoOpen = false }) {
                        MemoSheet(state.memo, onMemoDigit, onMemoCell, actions.onMemoClear, onClose = { memoOpen = false })
                    }
                }
            }
        },
    ) {
        state.practice?.let { PracticeBand(it, actions.onToRoom) }
        state.reconnectSeconds?.let {
            if (!compact) VSpacer(8.dp)
            ConnectionBanner(stringResource(R.string.reconnecting_title), stringResource(R.string.reconnecting_match), formatClock(it))
        }
        // Large text with the connection banner (D2-37): the opponent row gives way so the log keeps its latest row.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                if (tutorial != null) {
                    CoachBand(tutorial, compact)
                } else if (!compact && !((large || available600()) && state.reconnectSeconds != null)) {
                    OpponentRow(state, actions.onChat)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val logWidth = maxWidth
                    Column(Modifier.fillMaxSize()) {
                        if (secretPhase) {
                            HintLegend(compact, Modifier.weight(1f))
                        } else {
                            // ADAPTIVE §1: the opponent column of the log is hidden below 320 dp width.
                            val showOpponent = logWidth >= 320.dp
                            PitchLog(
                                state.digits, state.myRounds, state.opponentRounds, reveal.progress, compact, Modifier.weight(1f),
                                showOpponent = showOpponent,
                                solvedFlash = reveal.flash,
                                onOpponentLog = if (tutorial == null) ({ opponentLog = true }) else null,
                                highlightLatest = tutorial?.legend != null,
                                latestFooter = tutorial?.legend?.let { legend -> { TutorialLegend(legend, compact) } },
                            )
                        }
                    }
                }
            }
            if (bubble != null && !compact) {
                PhraseBubbleView(
                    bubble,
                    Modifier.align(if (bubble.mine) Alignment.TopStart else Alignment.TopEnd)
                        .padding(start = if (bubble.mine) 16.dp else 0.dp, end = if (bubble.mine) 0.dp else 16.dp)
                        .offset(y = NbLayout.oppRow + 4.dp + 8.dp),
                )
            }
            // V2-02 (DESIGN_SYSTEM 12.17): the toast sits at the bottom of the log region, 8 dp above the entry head, so it
            // never covers the entry head, slots or keypad; compact windows: at the top of the log, 8 dp under the
            // progress line (ADAPTIVE §1).
            ToastHost(toast, bottomOffset = 8.dp, top = compact)
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
            onMemo = if (memoAvailable && !compact) openMemo else null,
            tutorial = tutorial,
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

/**
 * Match top bar (DESIGN_SYSTEM 12.1): `close` · round over "mode · digits" (secret entry: mode over digits) · timer pill ·
 * `more_vert`, then the 3 dp progress line with 16 dp side margins (12 compact). Small windows: the opponent status
 * replaces the subtitle and `edit_note` opens the memo sheet (ADAPTIVE §1).
 */
@Composable
private fun MatchTopBar(
    state: MatchUiState,
    compact: Boolean,
    onClose: () -> Unit,
    onMenu: () -> Unit,
    memoToggle: (() -> Unit)?,
    onTitleClick: (() -> Unit)?,
    menu: @Composable () -> Unit,
    tutorial: app.numbaseball.ui.tutorial.TutorialUi? = null,
) {
    val secretPhase = state.phase == MatchPhase.CHOOSING_SECRET || state.phase == MatchPhase.WAITING_FOR_OPPONENT_SECRET
    val title = when {
        tutorial != null -> stringResource(R.string.menu_how_to)
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
                    Text(title, style = NbType.subtitle, color = NbColors.ink, maxLines = if (isLargeFont() && !compact) 2 else 1, overflow = TextOverflow.Ellipsis, fitMinScale = 0.5f)
                    if (tutorial != null) {
                        Unit
                    } else if (compact && !secretPhase) {
                        // Small window at large text: dot and countdown only; the word is read by TalkBack (DEF-055).
                        StatusLine(state.opponentStatus, showLabel = !isLargeFont())
                    } else if (!isLargeFont()) {
                        // Large text: the mode subtitle is hidden (ADAPTIVE.md §2).
                        // Long translations wrap to a second line rather than lose the digit count (DEF-054).
                        Text(subtitle, style = NbType.caption, color = NbColors.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            actions = {
                if (tutorial != null) {
                    // Tutorial: step "1/4" (`numLabel` `text3`) + "건너뛰기"; no clock, no menu (01 §2).
                    Text("${tutorial.step}/${tutorial.total}", style = NbType.numLabel, color = NbColors.text3, maxLines = 1, softWrap = false)
                    HSpacer(4.dp)
                    if (memoToggle != null) IconButton48(R.drawable.ic_edit_note, stringResource(R.string.memo_open), memoToggle)
                    NbButton(stringResource(R.string.tut_skip), tutorial.onSkip, kind = ButtonKind.TEXT, modifier = Modifier.targetOverflow(if (compact) 48.dp else 56.dp))
                    return@TopBar
                }
                if (memoToggle != null) IconButton48(R.drawable.ic_edit_note, stringResource(R.string.memo_open), memoToggle)
                state.secondsLeft?.let { PitchClock(it, level, Modifier.padding(start = 4.dp)) }
                Box {
                    IconButton48(R.drawable.ic_more_vert, stringResource(R.string.action_more), onMenu)
                    menu()
                }
            },
            bottom = {
                // The tutorial has no pitch clock (01 §2): no progress line either.
                if (tutorial == null) {
                    val total = state.roundSeconds
                    val fraction = if (total != null && state.secondsLeft != null) state.secondsLeft / total.toFloat() else 1f
                    ClockLine(fraction, level, Modifier.padding(horizontal = if (compact) 12.dp else 16.dp))
                }
            },
            titleGap = 8.dp,
            height = 56.dp,
            compactHeight = 48.dp,
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
    onMemo: (() -> Unit)? = null,
    onOpponentLog: (() -> Unit)? = null,
) {
    // Overflow menu (DESIGN_SYSTEM 12.17): `surface3`, radius 12, 1 dp `line` border instead of a shadow.
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = NbColors.surface3,
        shape = RoundedCornerShape(NbRadius.m),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, NbColors.line),
    ) {
        val colors = MenuDefaults.itemColors(textColor = NbColors.ink)
        // Compact secret entry has no shuffle key: random fill lives here (D2-35).
        if (onRandom != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_random_fill), style = NbType.body) }, onClick = { onDismiss(); onRandom() }, colors = colors)
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_how_to), style = NbType.body) }, onClick = { onDismiss(); onHowTo() }, colors = colors)
        if (onMemo != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.memo), style = NbType.body) }, onClick = { onDismiss(); onMemo() }, colors = colors)
        }
        if (onOpponentLog != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_opp_log), style = NbType.body) }, onClick = { onDismiss(); onOpponentLog() }, colors = colors)
        }
        if (compact && state.mySecret != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_show_secret), style = NbType.body) }, onClick = { onDismiss(); onShowSecret() }, colors = colors)
        }
        if (onReport != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_report), style = NbType.body) }, onClick = { onDismiss(); onReport() }, colors = colors)
        }
        if (state.mySecret != null && state.phase != MatchPhase.FINISHED) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_forfeit), style = NbType.body, color = NbColors.danger) },
                onClick = { onDismiss(); onForfeit() },
            )
        }
    }
}

/** Status line under the opponent's name (DESIGN_SYSTEM 12.3): 6 dp dot (`ball` when pitched / ready) + `caption`. */
@Composable
private fun StatusLine(status: OpponentStatus, wrap: Boolean = false, showLabel: Boolean = true) {
    val (lit, text, color) = when (status) {
        OpponentStatus.Picking -> Triple(false, stringResource(R.string.opp_picking), NbColors.text2)
        OpponentStatus.Ready -> Triple(true, stringResource(R.string.opp_ready), NbColors.text2)
        OpponentStatus.Thinking -> Triple(false, stringResource(R.string.opp_thinking), NbColors.text2)
        OpponentStatus.Pitched -> Triple(true, stringResource(R.string.opp_pitched), NbColors.text2)
        // The word may shorten; the countdown never does (DEF-055).
        is OpponentStatus.Disconnected -> Triple(false, stringResource(R.string.opp_disconnected, "").trimEnd(' ', '·', '・'), NbColors.strike)
    }
    val clock = (status as? OpponentStatus.Disconnected)?.let { formatClock(it.secondsLeft) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (!showLabel) Modifier.semantics(mergeDescendants = true) { contentDescription = listOfNotNull(text, clock).joinToString(" ") } else if (clock != null) Modifier.semantics(mergeDescendants = true) {} else Modifier,
    ) {
        Dot(if (lit) NbColors.ball else NbColors.dotOff, NbSize.dotS)
        if (showLabel) {
            HSpacer(6.dp)
            Text(text, style = NbType.caption, color = color, maxLines = if (wrap) 2 else 1, overflow = TextOverflow.Ellipsis, fitMinScale = 0.5f, modifier = Modifier.weight(1f, fill = false))
        }
        if (clock != null) {
            HSpacer(6.dp)
            Text(clock, style = NbType.numTag, color = color, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Opponent row (DESIGN_SYSTEM 12.3, replaces the v1 duel strip): 52 dp min, padding 8 / 4 / 8 / 16, 10 dp gaps: avatar
 * 36 dp (computer: `memory`) · name + tag over status · quick-phrase / chat button (online). Large text: no avatar, the
 * name line and the status line (ADAPTIVE §2.2).
 */
@Composable
private fun OpponentRow(state: MatchUiState, onChat: (() -> Unit)?) {
    val large = isLargeFont()
    val name: @Composable (Modifier) -> Unit = { m ->
        when (val o = state.opponent) {
            is Opponent.Computer -> Text(
                stringResource(R.string.mode_cpu) + " · " + levelLabel(o.level),
                style = NbType.subtitle,
                color = NbColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = m,
            )
            is Opponent.Player -> NameWithTag(o.nickname, o.tag, m)
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (large) 64.dp else NbLayout.oppRow).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!large) {
            when (val o = state.opponent) {
                is Opponent.Computer -> Avatar("", NbSize.avatarM, icon = R.drawable.ic_memory)
                is Opponent.Player -> Avatar(o.nickname, NbSize.avatarM)
            }
            HSpacer(10.dp)
        }
        Column(Modifier.weight(1f)) {
            name(Modifier)
            // Long status words wrap to a second line in narrow windows instead of being cut (DEF-055).
            StatusLine(state.opponentStatus, wrap = true)
        }
        if (onChat != null) {
            HSpacer(10.dp)
            IconButton48(R.drawable.ic_chat, stringResource(R.string.chat_title), onChat, tint = NbColors.text2)
        }
    }
}

@Composable
private fun PhraseBubbleView(bubble: PhraseBubble, modifier: Modifier) {
    Column(
        modifier
            .widthIn(max = 240.dp)
            .background(if (bubble.mine) NbColors.accentTint else NbColors.surface2, RoundedCornerShape(NbRadius.m))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(bubble.sender, style = NbType.micro, color = NbColors.text2)
        Text(bubble.text, style = NbType.body, color = NbColors.ink)
    }
}

/**
 * Practice band (05 §2.11): 48 dp minimum, `surface2`: "방 K7Q2M · 3/6명 기다리는 중" + "방으로" (`accent`, 48 dp target);
 * at large text the band grows and its text wraps to two lines.
 */
@Composable
private fun PracticeBand(info: PracticeInfo, onToRoom: (() -> Unit)?) {
    // Small windows keep a one-line band whatever the font size (the keypad needs the height).
    val compact = isCompactHeight()
    val large = isLargeFont() && !compact
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 40.dp else 48.dp).background(NbColors.surface2).padding(start = if (compact) 12.dp else 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.practice_band, info.roomCode, info.present, info.total),
            style = NbType.caption,
            color = NbColors.ink,
            maxLines = if (large) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = if (large) 6.dp else 0.dp),
        )
        if (onToRoom != null) {
            NbButton(stringResource(R.string.practice_to_room), onToRoom, kind = ButtonKind.TEXT, textColorOverride = NbColors.accent, modifier = if (compact) Modifier.targetOverflow(40.dp) else Modifier)
        }
    }
}

/**
 * Entry head (DESIGN_SYSTEM 12.5): 40 dp: "내 숫자" `caption` `text3` + digits (`numTag` 700, 0.12 em, `text2`) left, the
 * 메모 pill button (36 dp visual, 48 dp target, 1.5 dp `line`) right. Large text: the label goes to TalkBack only and
 * the button shows `open_in_full`; when the line cannot hold both, only the button stays.
 */
@Composable
private fun EntryHead(secret: String?, onMemo: (() -> Unit)?, ring: Boolean) {
    val large = isLargeFont()
    Row(Modifier.fillMaxWidth().heightIn(min = if (large) 48.dp else 40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (secret != null) {
            val spoken = stringResource(R.string.my_secret) + " " + secret.toList().joinToString(" ")
            Row(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = spoken }, verticalAlignment = Alignment.CenterVertically) {
                if (!large) {
                    Text(stringResource(R.string.my_secret), style = NbType.caption, color = NbColors.text3, maxLines = 1, softWrap = false)
                    HSpacer(6.dp)
                }
                Text(secret, style = NbType.numTag.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.12.em), color = NbColors.text2, maxLines = 1, softWrap = false, autoFit = false)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (onMemo != null) MemoButton(onMemo, large, ring)
    }
}

@Composable
private fun MemoButton(onClick: () -> Unit, large: Boolean, ring: Boolean) {
    val description = stringResource(R.string.memo_open)
    Box(
        Modifier
            .targetOverflow(NbSize.memoButtonH)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = NbSize.memoButtonH)
                .border(if (ring) 2.dp else NbSize.border, if (ring) NbColors.accent else NbColors.line, CircleShape)
                .padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NbIcon(if (large) R.drawable.ic_open_in_full else R.drawable.ic_edit_note, null, NbColors.text2, 18.dp)
            HSpacer(4.dp)
            Text(stringResource(R.string.memo), style = NbType.labelSmall, color = NbColors.text2, maxLines = 1, softWrap = false)
        }
    }
}

/** Entry (05 §1): entry head 40 · 4 · slots 64 · 12 · keypad 172 · 12 (compact: 8 · 48 · 8 · 100 · 8, no head). */
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
    onMemo: (() -> Unit)?,
    tutorial: app.numbaseball.ui.tutorial.TutorialUi? = null,
) {
    val margin = if (compact) 12.dp else 16.dp
    val waiting = state.phase == MatchPhase.WAITING_FOR_OPPONENT_GUESS || state.phase == MatchPhase.WAITING_FOR_OPPONENT_SECRET
    Column(Modifier.fillMaxWidth().padding(horizontal = margin)) {
        if (state.phase == MatchPhase.FINISHED) {
            Column(Modifier.fillMaxWidth().padding(vertical = margin), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.duel_over), style = NbType.body, color = NbColors.text2, textAlign = TextAlign.Center)
                if (onTeamBoard != null) {
                    VSpacer(12.dp)
                    NbButton(stringResource(R.string.team_match), onTeamBoard, kind = ButtonKind.TONAL, modifier = Modifier.fillMaxWidth())
                }
            }
            return
        }
        when {
            compact -> VSpacer(8.dp)
            secretPhase -> {
                VSpacer(16.dp)
                Text(stringResource(R.string.secret_title), style = NbType.title, color = NbColors.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    pluralStringResource(R.plurals.secret_rule, state.digits, state.digits),
                    style = NbType.caption,
                    color = NbColors.text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                VSpacer(12.dp)
            }
            else -> {
                EntryHead(state.mySecret?.takeIf { tutorial == null }, onMemo, ring = tutorial != null && onMemo != null)
                VSpacer(4.dp)
            }
        }
        DigitSlots(
            digits = state.digits,
            value = state.input.ifEmpty { if (waiting && state.phase == MatchPhase.WAITING_FOR_OPPONENT_SECRET) state.mySecret.orEmpty() else state.input },
            locked = waiting,
            error = state.inputProblem != null || state.error != null,
            compact = compact,
            showActive = state.acceptsInput,
            placeholder = tutorial?.slotPlaceholder,
            shakeKey = shakeKey,
            onTapSlot = onTapSlot,
        )
        VSpacer(if (compact) 8.dp else 12.dp)
        DigitKeypad(
            mode = if (secretPhase && !compact) KeypadMode.SECRET else KeypadMode.GUESS,
            used = state.input.toSet(),
            locked = keypadLocked,
            actionLabel = tutorial?.actionLabel ?: stringResource(if (secretPhase && (compact || isLargeFont())) R.string.lock_in_short else if (secretPhase) R.string.lock_in else R.string.pitch),
            actionState = when {
                tutorial?.actionLabel != null -> ActionState.ENABLED
                tutorial != null -> if (tutorial.canPitch) ActionState.ENABLED else ActionState.DISABLED
                waiting -> ActionState.WAITING
                state.reconnectSeconds != null -> ActionState.OFFLINE
                keypadLocked || state.input.length < state.digits -> ActionState.DISABLED
                else -> ActionState.ENABLED
            },
            waitingLabel = stringResource(if (secretPhase) R.string.waiting_ready else R.string.waiting_opp),
            onDigit = onDigit,
            onBackspace = onDelete,
            onAction = tutorial?.onAction ?: onSubmit,
            highlight = tutorial?.expectedKey,
            onClear = onClear,
            onRandom = onRandom,
            memo = if (secretPhase) emptyMap() else state.memo.keyMarks,
            compact = compact,
        )
        VSpacer(if (compact) 8.dp else 12.dp)
    }
}

/** State of The Count on the latest log row. */
class RevealHandle(val progress: RevealProgress, val running: Boolean, val locked: Boolean, val flash: Float = 0f)

/**
 * Runs The Count (DESIGN_SYSTEM 8.3 B) when a new round arrives: hint chips step 240 + 110 × i ms after the result
 * (each step 180 ms, hooks `lamp(...)`); the keypad unlocks at `reveal_last`; a solve flashes then hands off to the result (8.3 C, 400 ms). Sound and
 * haptic hooks fire on the same frames. Reduced motion: lamps at once, hooks 60 ms apart (8.4).
 */
@Composable
private fun rememberReveal(state: MatchUiState, audio: GameAudio, haptics: GameHaptics, onFinished: (MatchSummary) -> Unit): RevealHandle {
    val reduce = LocalReduceMotion.current
    var shown by rememberSaveable { mutableIntStateOf(state.myRounds.size) }
    var running by remember { mutableStateOf(false) }
    val t = remember { Animatable(Float.MAX_VALUE) }
    val flash = remember { Animatable(0f) }
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
            // Solve flash: `surface2` → `strikeTint` → `surface2` over 300 ms (8.3 C).
            if (!reduce) {
                flash.animateTo(1f, tween(NbMotion.solveFlash / 2))
                flash.animateTo(0f, tween(NbMotion.solveFlash / 2))
            }
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
            zero = ((time - lead) / NbMotion.short).coerceIn(0f, 1f),
        )
    }
    val revealLast = latest?.let { NbMotion.revealLeadIn + NbMotion.lampStagger * maxOf(it.strikes + it.balls - 1, 0) } ?: 0
    return RevealHandle(progress, running, locked = running && time < revealLast, flash = flash.value)
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

/** Coach box (01 §2): `surface2`, radius 12, 16 dp margins and padding, min 96 dp, `body` with key words `bodyStrong`. */
@Composable
private fun CoachBand(tutorial: app.numbaseball.ui.tutorial.TutorialUi, compact: Boolean) {
    // Small windows (D2-41): no 96 dp minimum, `caption`, two lines visible and the rest scrolls, so the log keeps
    // at least 88 dp for the row the coach is explaining.
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = if (compact) 12.dp else 16.dp, end = if (compact) 12.dp else 16.dp, top = if (compact) 4.dp else 8.dp)
            .background(NbColors.surface2, RoundedCornerShape(NbRadius.m))
            .then(if (compact) Modifier.heightIn(max = 48.dp).verticalScroll(rememberScrollState()) else Modifier.heightIn(min = 96.dp))
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 6.dp else 16.dp)
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(tutorial.coach, style = if (compact) NbType.caption else NbType.body, color = NbColors.text2)
    }
}

/** Step 3: a one-line legend under the highlighted log row, using the same chips (01 §2). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TutorialLegend(text: String, compact: Boolean) {
    // Small windows: TalkBack only; the coach text already explains the row (D2-41).
    if (compact) {
        Box(Modifier.fillMaxWidth().semantics { contentDescription = text })
        return
    }
    // 01 §2 step 3 (V2-12): a 1-line legend using the same chips, 8 dp under the ringed latest row.
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp).clearAndSetSemantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf('S' to R.string.tut_legend_strike, 'B' to R.string.tut_legend_ball).forEach { (letter, caption) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendChip(letter)
                HSpacer(6.dp)
                Text(stringResource(caption), style = NbType.caption, color = NbColors.text2)
            }
        }
    }
}

/** Windows shorter than 600 dp: with the connection banner the duel strip gives way to the log (D2-37, DEF-060). */
@Composable
private fun available600(): Boolean = app.numbaseball.ui.common.rememberWindowHeightDp() < 600
