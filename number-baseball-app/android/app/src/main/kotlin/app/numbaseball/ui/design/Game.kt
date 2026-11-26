package app.numbaseball.ui.design

import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.TextUnit
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numbaseball.R
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbMotion
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.delay

/**
 * Waiting chase (DESIGN_SYSTEM 8.2.4): three flat dots, one [lit] at a time (400 ms each), the others [off]; one static
 * lit dot under reduced motion. The name is kept from v1 (lamps are gone in v2). Decorative for TalkBack.
 */
@Composable
fun ChaseLamps(size: Dp = NbSize.dotS, lit: Color = NbColors.accent, off: Color = NbColors.dotOff) {
    val reduce = LocalReduceMotion.current
    var step by remember { mutableIntStateOf(0) }
    if (!reduce) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(NbMotion.chaseStep.toLong())
                step = (step + 1) % 3
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(if (size >= 8.dp) 6.dp else 4.dp), modifier = Modifier.clearAndSetSemantics {}) {
        repeat(3) { i -> Dot(if (i == step) lit else off, size) }
    }
}

/** Geometry of the hint chips (DESIGN_SYSTEM 12.7; n ≥ 5 `wide`; small windows ADAPTIVE §1). */
data class ChipSize(val height: Dp, val minWidth: Dp, val outMinWidth: Dp, val padding: Dp) {
    companion object {
        val Normal = ChipSize(NbSize.hintChipH, NbSize.hintChipMinW, NbSize.outChipMinW, 8.dp)
        val Wide = ChipSize(NbSize.hintChipH, 40.dp, 86.dp, 6.dp)
        val Compact = ChipSize(24.dp, 38.dp, 82.dp, 6.dp)
        val Legend = Normal
    }
}

private enum class ChipKind { STRIKE, BALL, ZERO, OUT }

/** One hint chip: S solid `strike` / B `bg` + 2 dp `ball` ring / zero plain `text3` / OUT `surface2` + 1.5 dp `out` ring. */
@Composable
private fun HintChip(text: String, kind: ChipKind, size: ChipSize, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(NbRadius.s)
    val cap = LocalDensity.current.fontScale.coerceIn(1f, NUMERAL_SCALE_CAP)
    val base = Modifier
        .heightIn(min = size.height * cap)
        .widthIn(min = if (kind == ChipKind.OUT) size.outMinWidth else size.minWidth)
    val (decor, color) = when (kind) {
        ChipKind.STRIKE -> Modifier.background(NbColors.strike, shape) to NbColors.onAccent
        ChipKind.BALL -> Modifier.background(NbColors.bg, shape).border(2.dp, NbColors.ball, shape) to NbColors.ball
        ChipKind.OUT -> Modifier.background(NbColors.surface2, shape).border(NbSize.border, NbColors.out, shape) to NbColors.out
        ChipKind.ZERO -> Modifier to NbColors.text3
    }
    Box(modifier.then(base).then(decor).padding(horizontal = size.padding), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = when (kind) {
                ChipKind.OUT -> NbType.numHint.copy(letterSpacing = 0.08.em)
                ChipKind.ZERO -> NbType.numHint.copy(fontWeight = FontWeight.SemiBold)
                else -> NbType.numHint
            },
            color = color,
            maxLines = 1,
            softWrap = false,
            autoFit = false,
        )
    }
}

/**
 * Hint chips (DESIGN_SYSTEM 12.7), right-aligned, 6 dp apart: S chip then B chip; a zero count is plain text in the same
 * min-width box; 0S 0B is one wide OUT chip. With reveal progress ([strikeSteps] / [ballSteps]: one 0..1 ignite per
 * `lamp(...)` hook) the chips scale 0.8 → 1 on their first step and count up one per hook (8.2 #1); a zero count and the
 * OUT chip follow [zeroAlpha] / [outIgnite]. Not a focus stop: the log row speaks the hint.
 */
@Composable
fun HintChips(
    strikes: Int,
    balls: Int,
    modifier: Modifier = Modifier,
    size: ChipSize = ChipSize.Normal,
    strikeSteps: List<Float>? = null,
    ballSteps: List<Float>? = null,
    zeroAlpha: Float = 1f,
    outIgnite: Float = 1f,
) {
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(NbSize.hintChipGap), verticalAlignment = Alignment.CenterVertically) {
        if (strikes == 0 && balls == 0) {
            HintChip("OUT", ChipKind.OUT, size, Modifier.graphicsLayer { alpha = outIgnite; val sc = 0.8f + 0.2f * outIgnite; scaleX = sc; scaleY = sc })
            return@Row
        }
        listOf(Triple(strikes, 'S', strikeSteps), Triple(balls, 'B', ballSteps)).forEach { (count, letter, steps) ->
            if (count == 0) {
                HintChip("0$letter", ChipKind.ZERO, size, Modifier.graphicsLayer { alpha = zeroAlpha })
            } else {
                val first = steps?.firstOrNull() ?: 1f
                val shown = steps?.count { it > 0f }?.coerceAtLeast(1) ?: count
                HintChip(
                    "$shown$letter",
                    if (letter == 'S') ChipKind.STRIKE else ChipKind.BALL,
                    size,
                    Modifier.graphicsLayer { alpha = first; val sc = 0.8f + 0.2f * first; scaleX = sc; scaleY = sc },
                )
            }
        }
    }
}

/** A single legend chip ("1S" solid, "1B" outlined, "OUT"), DESIGN_SYSTEM 12.8 legend box. */
@Composable
fun LegendChip(letter: Char?) {
    when (letter) {
        'S' -> HintChip("1S", ChipKind.STRIKE, ChipSize.Legend)
        'B' -> HintChip("1B", ChipKind.BALL, ChipSize.Legend)
        else -> HintChip("OUT", ChipKind.OUT, ChipSize.Legend)
    }
}

/** Opponent hint as plain text (`numLabel` `text3`): "2S 1B" / "OUT" — never chips (DESIGN_SYSTEM 12.7). */
fun hintText(strikes: Int, balls: Int): String = if (strikes == 0 && balls == 0) "OUT" else "${strikes}S ${balls}B"

enum class SlotState { EMPTY, ACTIVE, FILLED, LOCKED, ERROR, PLATE, QUESTION }

/**
 * Digit slots (DESIGN_SYSTEM 12.4): n plates centred, gap 10 dp, width min(60, (content − (n − 1) × 10) ÷ n) (min 44),
 * height 64 (48 compact), radius 12. [plate] draws result / answer plates (`ink`, `onInk`). [shakeKey] changes trigger the
 * ±6 dp, 3-cycle, 280 ms shake. Tapping slot i calls [onTapSlot].
 */
@Composable
fun DigitSlots(
    digits: Int,
    value: String,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    error: Boolean = false,
    plate: Boolean = false,
    compact: Boolean = false,
    plateWidth: Dp? = null,
    plateHeight: Dp? = null,
    gap: Dp = NbSize.slotGap,
    showActive: Boolean = true,
    placeholder: Char? = null,
    shakeKey: Int = 0,
    contentDescription: String? = null,
    /** Per-digit alpha (result reveal, 8.3 C). */
    digitAlpha: (Int) -> Float = { 1f },
    onTapSlot: ((Int) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val shake = remember { Animatable(0f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0 || reduce) return@LaunchedEffect
        val amp = with(density) { 6.dp.toPx() }
        repeat(3) {
            shake.animateTo(amp, tween(NbMotion.shake / 12))
            shake.animateTo(-amp, tween(NbMotion.shake / 6))
            shake.animateTo(0f, tween(NbMotion.shake / 12))
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value }
            .then(if (contentDescription != null) Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Compact slots are 48 × 48 (ADAPTIVE.md §1, D2-05).
        val g = if (compact && gap == NbSize.slotGap) 8.dp else gap
        val w = plateWidth ?: minOf(if (compact) NbSize.slotHeightCompact else NbSize.slotWidth, (maxWidth - g * (digits - 1)) / digits).coerceAtLeast(NbSize.slotWidthMin)
        val h = plateHeight ?: if (compact) NbSize.slotHeightCompact else NbSize.slotHeight
        Row(horizontalArrangement = Arrangement.spacedBy(g)) {
            repeat(digits) { i ->
                val ch = value.getOrNull(i)
                val state = when {
                    plate -> SlotState.PLATE
                    ch == null && placeholder == '?' -> SlotState.QUESTION
                    error -> SlotState.ERROR
                    ch != null && locked -> SlotState.LOCKED
                    ch != null -> SlotState.FILLED
                    showActive && !locked && i == value.length -> SlotState.ACTIVE
                    else -> SlotState.EMPTY
                }
                Slot(
                    char = ch ?: placeholder,
                    state = state,
                    width = w,
                    height = h,
                    compact = compact,
                    alpha = digitAlpha(i),
                    onClick = if (onTapSlot != null && ch != null && !locked) ({ onTapSlot(i) }) else null,
                )
            }
        }
    }
}

@Composable
private fun Slot(char: Char?, state: SlotState, width: Dp, height: Dp, compact: Boolean, alpha: Float, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(NbRadius.m)
    val (fill, border, borderWidth) = when (state) {
        SlotState.EMPTY -> Triple(NbColors.bg, NbColors.outline, NbSize.border)
        SlotState.ACTIVE -> Triple(NbColors.bg, NbColors.accent, 2.dp)
        SlotState.FILLED -> Triple(NbColors.surface2, null, 0.dp)
        SlotState.LOCKED -> Triple(NbColors.surface1, null, 0.dp)
        SlotState.ERROR -> Triple(NbColors.dangerTint, NbColors.danger, 2.dp)
        SlotState.PLATE -> Triple(NbColors.ink, null, 0.dp)
        SlotState.QUESTION -> Triple(NbColors.bg, NbColors.line, NbSize.border)
    }
    val textColor = when (state) {
        SlotState.LOCKED -> NbColors.text2
        SlotState.PLATE -> NbColors.onInk
        SlotState.QUESTION -> NbColors.text3
        SlotState.EMPTY, SlotState.ACTIVE -> NbColors.textDisabled
        else -> NbColors.ink
    }
    val scale = remember { Animatable(1f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(char) {
        if (char != null && !reduce && state == SlotState.FILLED) {
            scale.snapTo(0.92f)
            scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 800f))
        }
    }
    val style = when {
        compact || height <= 48.dp -> NbType.numSlotSm.copy(letterSpacing = 0.sp)
        else -> NbType.numSlot
    }
    Box(
        Modifier
            .width(width)
            .heightIn(min = height)
            .background(fill, shape)
            .then(if (border != null) Modifier.border(borderWidth, border, shape) else Modifier)
            .then(if (onClick != null) Modifier.combinedClickableCompat(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) {
            Text(char.toString(), style = style, color = textColor, maxLines = 1, softWrap = false, autoFit = false, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = combinedClickable(onClick = onClick)

enum class MemoMark { NONE, OUT, IN }

enum class KeypadMode { GUESS, SECRET, AGE }

/** What the action key shows. */
enum class ActionState { DISABLED, ENABLED, WAITING, OFFLINE }

/**
 * Custom digit keypad (DESIGN_SYSTEM 12.5), never the system keyboard. 5 columns (0–4 / 5–9), 8 dp gap, 52 dp keys,
 * action row ⌫ + Pitch (GUESS), ⌫ + shuffle + Lock in (SECRET) or ⌫ + Confirm (AGE, repeated digits allowed).
 * Compact: 2 × 6 with 4 dp gaps and 48 dp keys (`0–4 ⌫` / `5–9 Pitch`, last column 64 dp).
 */
@Composable
fun DigitKeypad(
    mode: KeypadMode,
    used: Set<Char>,
    locked: Boolean,
    actionLabel: String,
    actionState: ActionState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: () -> Unit = {},
    onRandom: () -> Unit = {},
    memo: Map<Char, MemoMark> = emptyMap(),
    compact: Boolean = false,
    waitingLabel: String? = null,
    /** A key the tutorial asks for: 2 dp `accent` inset (01 §2). */
    highlight: Char? = null,
) {
    val gap = if (compact) 4.dp else NbSize.keyGap
    val keyHeight = if (compact) NbSize.keyHeightCompact else NbSize.keyHeight
    val digitKey: @Composable (Char, Modifier) -> Unit = { d, m ->
        val isUsed = mode != KeypadMode.AGE && d in used
        DigitKey(d, isUsed, locked, memo[d] ?: MemoMark.NONE, keyHeight, if (d == highlight) m.border(2.dp, NbColors.accent, RoundedCornerShape(NbRadius.m)) else m) { onDigit(d) }
    }
    val backspace: @Composable (Modifier) -> Unit = { m -> IconKey(R.drawable.ic_backspace, stringResource(R.string.backspace), locked, keyHeight, m, onBackspace, onClear) }
    val action: @Composable (Modifier) -> Unit = { m ->
        ActionKey(actionLabel, actionState, if (mode == KeypadMode.AGE) ButtonKind.PRIMARY else ButtonKind.PRIMARY, keyHeight, compact, waitingLabel, m, onAction)
    }
    if (compact) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                "01234".forEach { digitKey(it, Modifier.weight(1f)) }
                backspace(Modifier.width(64.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                "56789".forEach { digitKey(it, Modifier.weight(1f)) }
                action(Modifier.width(64.dp))
            }
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val key = (maxWidth - gap * 4) / 5
        fun span(columns: Int) = key * columns + gap * (columns - 1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { "01234".forEach { digitKey(it, Modifier.width(key)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { "56789".forEach { digitKey(it, Modifier.width(key)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                backspace(Modifier.width(key))
                if (mode == KeypadMode.SECRET) {
                    IconKey(R.drawable.ic_shuffle, stringResource(R.string.random_number), locked, keyHeight, Modifier.width(key), onRandom, null)
                    action(Modifier.width(span(3)))
                } else {
                    action(Modifier.width(span(4)))
                }
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, used: Boolean, locked: Boolean, memo: MemoMark, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val disabled = used || locked
    val fill = if (disabled) NbColors.surface1 else NbColors.surface2
    val digitColor = when {
        disabled -> NbColors.textDisabled
        memo == MemoMark.OUT -> NbColors.text3
        else -> NbColors.ink
    }
    // Memo marks follow the key: a used or locked key shows its mark in `textDisabled` (DESIGN_SYSTEM 12.6).
    val markColor = if (disabled) NbColors.textDisabled else if (memo == MemoMark.IN) NbColors.ink else NbColors.text3
    Box(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(NbRadius.m))
            .background(fill)
            .testTag("key_$digit")
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (disabled) disabled()
            }
            .then(if (!disabled) Modifier.combinedClickableCompat(onClick) else Modifier)
            .then(if (memo == MemoMark.OUT) Modifier.ruledOut(markColor) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(digit.toString(), style = NbType.numKey, color = digitColor, autoFit = false)
        if (memo == MemoMark.IN) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).size(14.dp, 3.dp).background(markColor, RoundedCornerShape(2.dp)))
        }
    }
}

/** Ruled-out mark (DESIGN_SYSTEM 12.6 / 16): one 22 × 2 dp stroke, round caps, rotated −45°, centred over the content. */
fun Modifier.ruledOut(color: Color, length: Dp = NbSize.ruledStrokeL): Modifier = drawWithContent {
    drawContent()
    val half = length.toPx() / 2f
    rotate(-45f) {
        drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), NbSize.ruledStrokeW.toPx(), StrokeCap.Round)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconKey(icon: Int, description: String, locked: Boolean, height: Dp, modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Box(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(NbRadius.m))
            .background(if (locked) NbColors.surface1 else NbColors.surface2)
            .semantics { if (locked) disabled() }
            .then(if (!locked) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Function keys: `ink` glyphs (DESIGN_SYSTEM 12.6, prototype `.key.fn`).
        NbIcon(icon, description, if (locked) NbColors.textDisabled else NbColors.ink)
    }
}

@Composable
private fun ActionKey(
    label: String,
    state: ActionState,
    @Suppress("UNUSED_PARAMETER") kind: ButtonKind,
    height: Dp,
    compact: Boolean,
    waitingLabel: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val enabled = state == ActionState.ENABLED
    val fill = if (enabled) NbColors.accent else NbColors.surface2
    val textColor = when (state) {
        ActionState.ENABLED -> NbColors.onAccent
        ActionState.WAITING -> NbColors.text2
        else -> NbColors.textDisabled
    }
    Box(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(NbRadius.m))
            .background(fill)
            .semantics(mergeDescendants = true) { role = Role.Button; if (!enabled) disabled() }
            .then(if (enabled) Modifier.combinedClickableCompat(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (state == ActionState.WAITING && !compact && isLargeFont()) {
            // Large text (D2-38, ADAPTIVE §2.2): the key grows to 64 dp, the lamps move above a label of up to two lines.
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                ChaseLamps(NbSize.dotS)
                VSpacer(4.dp)
                Text(waitingLabel ?: label, style = NbType.labelSmall.copy(fontSize = 14.sp), color = textColor, textAlign = TextAlign.Center, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        } else if (state == ActionState.WAITING && !compact) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                ChaseLamps(NbSize.dotS)
                HSpacer(8.dp)
                Text(waitingLabel ?: label, style = NbType.labelSmall.copy(fontSize = 14.sp), color = textColor, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        } else {
            Text(
                label,
                style = if (compact) NbType.labelSmall else NbType.label.copy(fontSize = 17.sp),
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 1 else 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fitMinScale = if (compact) 0.5f else 0.7f,
            )
        }
    }
}

enum class ClockLevel { NORMAL, WARN, CRITICAL, PAUSED }

fun clockLevel(secondsLeft: Int?, paused: Boolean): ClockLevel = when {
    paused -> ClockLevel.PAUSED
    secondsLeft == null -> ClockLevel.NORMAL
    secondsLeft <= 5 -> ClockLevel.CRITICAL
    secondsLeft <= 10 -> ClockLevel.WARN
    else -> ClockLevel.NORMAL
}

private fun ClockLevel.color(): Color = when (this) {
    ClockLevel.NORMAL -> NbColors.ink
    ClockLevel.WARN -> NbColors.strike
    ClockLevel.CRITICAL -> NbColors.danger
    ClockLevel.PAUSED -> NbColors.textDisabled
}

private fun ClockLevel.lineColor(): Color = if (this == ClockLevel.NORMAL) NbColors.accent else color()

fun formatClock(seconds: Int): String = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

/**
 * Pitch clock pill (DESIGN_SYSTEM 12.2): 36 dp, `surface2`, radius full, 12 dp padding: `timer` 18 dp `text2` + 6 dp +
 * `numTimer` m:ss (min width 34 dp, right-aligned). Warn `strike`, critical `danger` (value pulses), paused `textDisabled`.
 */
@Composable
fun PitchClock(secondsLeft: Int, level: ClockLevel, modifier: Modifier = Modifier, pill: Boolean = true) {
    val color = level.color()
    val pulse = remember { Animatable(1f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(secondsLeft, level) {
        if (level == ClockLevel.CRITICAL && !reduce) {
            pulse.animateTo(1.06f, tween(NbMotion.short / 2 + 25))
            pulse.animateTo(1f, tween(NbMotion.short / 2 + 25))
        }
    }
    Row(
        modifier
            .semantics(mergeDescendants = true) {}
            .then(if (pill) Modifier.heightIn(min = NbSize.timerPillH).background(NbColors.surface2, CircleShape).padding(horizontal = 12.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NbIcon(R.drawable.ic_timer, null, if (level == ClockLevel.NORMAL) NbColors.text2 else color, 18.dp)
        HSpacer(6.dp)
        Text(
            formatClock(secondsLeft),
            style = NbType.numTimer,
            color = color,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            autoFit = false,
            modifier = Modifier.widthIn(min = 34.dp).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
        )
    }
}

/** 3 dp progress line under the match top bar (DESIGN_SYSTEM 12.2): radius 2, track `line`, fill `accent`, shrinks right to left. */
@Composable
fun ClockLine(fraction: Float, level: ClockLevel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(modifier.fillMaxWidth().height(NbSize.progressH).clip(shape).background(NbColors.line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(NbSize.progressH).clip(shape).background(level.lineColor()))
    }
}

/** A `?` plate (Home hero, search): question state, `bg` + 1.5 dp `line`, `numSlot` `text3`; [lit] = swept (`ink`). */
@Composable
fun QuestionPlate(lit: Boolean, width: Dp, height: Dp) {
    val shape = RoundedCornerShape(NbRadius.m)
    Box(Modifier.size(width, height).background(NbColors.bg, shape).border(NbSize.border, if (lit) NbColors.outline else NbColors.line, shape), contentAlignment = Alignment.Center) {
        Text("?", style = NbType.numSlot, color = if (lit) NbColors.ink else NbColors.text3, autoFit = false)
    }
}

/** Two bars of [armLength] crossed at ±45° around the centre (memo "out" cell). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.crossBars(color: Color, armLength: Float, stroke: Float) {
    val h = armLength / (2f * kotlin.math.sqrt(2f))
    val c = center
    drawLine(color, Offset(c.x - h, c.y - h), Offset(c.x + h, c.y + h), stroke, StrokeCap.Butt)
    drawLine(color, Offset(c.x + h, c.y - h), Offset(c.x - h, c.y + h), stroke, StrokeCap.Butt)
}
