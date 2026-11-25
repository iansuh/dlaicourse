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

enum class LampColor(val lit: Color, val glow: Color) {
    STRIKE(NbColors.strike, NbColors.strikeGlow),
    BALL(NbColors.ball, NbColors.ballGlow),
    OUT(NbColors.out, NbColors.outGlow),
    CHALK(NbColors.chalk, Color(0x40ECEFE6)),
}

/**
 * Count lamp (DESIGN_SYSTEM 12.6): disc + radial glow drawn behind (radius 1.25 × size, no blur). [ignite] 0..1 animates
 * opacity and glow radius. Unlit: `lampOff` + 1 dp `lampOffRing`. Decorative for TalkBack.
 */
@Composable
fun Lamp(color: LampColor, lit: Boolean, size: Dp = NbSize.lampS, ignite: Float = 1f, glow: Boolean = true) {
    Canvas(Modifier.size(size).clearAndSetSemantics {}) {
        val r = this.size.minDimension / 2
        val c = center
        if (!lit || ignite <= 0f) {
            drawCircle(NbColors.lampOff, r, c)
            drawCircle(NbColors.lampOffRing, r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        }
        if (lit && ignite > 0f) {
            if (glow) {
                val gr = this.size.minDimension * 1.25f * ignite
                drawCircle(Brush.radialGradient(listOf(color.glow, Color.Transparent), c, gr.coerceAtLeast(0.1f)), gr, c)
            }
            drawCircle(color.lit.copy(alpha = ignite), r, c)
        }
    }
}

/** A row of [total] lamps of which the first [lit] are on; gap 6 / 3 dp (2 dp for 5+ lamps). */
@Composable
fun LampRow(color: LampColor, total: Int, lit: Int, size: Dp = NbSize.lampS, gap: Dp? = null, ignites: List<Float>? = null) {
    val g = gap ?: if (total >= 5) 2.dp else if (size >= NbSize.lampL) 6.dp else 3.dp
    Row(horizontalArrangement = Arrangement.spacedBy(g), modifier = Modifier.clearAndSetSemantics {}) {
        repeat(total) { i -> Lamp(color, i < lit, size, ignites?.getOrNull(i) ?: 1f) }
    }
}

/** Waiting chase (DESIGN_SYSTEM 8.2.4): three chalk lamps, one lit at a time, 400 ms each; static under reduced motion. */
@Composable
fun ChaseLamps(size: Dp = NbSize.lampM, color: LampColor = LampColor.CHALK) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(if (size >= 8.dp) 6.dp else 3.dp), modifier = Modifier.clearAndSetSemantics {}) {
        repeat(3) { i -> Lamp(color, lit = i == step, size = size, glow = false) }
    }
}

/** `OUT` tag (DESIGN_SYSTEM 12.6): `numLabel` 14 sp, `out` text, 1 dp `out` border, radius 4, 22 dp tall, 6 dp padding. */
@Composable
fun OutTag(modifier: Modifier = Modifier, height: Dp = 22.dp, fontSize: TextUnit = 14.sp, padding: Dp = 6.dp) {
    Box(
        modifier
            .heightIn(min = height)
            .border(1.dp, NbColors.out, RoundedCornerShape(NbRadius.s))
            .padding(horizontal = padding),
        contentAlignment = Alignment.Center,
    ) {
        Text("OUT", style = NbType.numLabel.copy(fontSize = fontSize, letterSpacing = 0.08.em), color = NbColors.out)
    }
}

/** One hint count: `2S` / `1B` in `numHint`; a zero count in `textDisabled` so the columns stay aligned. */
@Composable
fun HintCountText(count: Int, letter: Char, color: Color, style: androidx.compose.ui.text.TextStyle = NbType.numHint) {
    Text("$count$letter", style = style, color = if (count == 0) NbColors.textDisabled else color, maxLines = 1, softWrap = false)
}

/**
 * The two fixed hint columns (S then B, [column] wide each); 0S 0B is the `OUT` tag spanning both. With [digits] set,
 * n lamps are drawn under each count (latest log row) and [ignites] animates them.
 */
@Composable
fun HintColumns(
    strikes: Int,
    balls: Int,
    column: Dp,
    digits: Int? = null,
    strikeIgnites: List<Float>? = null,
    ballIgnites: List<Float>? = null,
    outIgnite: Float = 1f,
    textAlpha: Pair<Float, Float> = 1f to 1f,
) {
    Box(Modifier.width(column * 2), contentAlignment = Alignment.Center) {
        if (strikes == 0 && balls == 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutTag(Modifier.graphicsLayer { alpha = outIgnite })
                if (digits != null) {
                    VSpacer(6.dp)
                    Lamp(LampColor.OUT, lit = true, size = NbSize.lampS, ignite = outIgnite)
                }
            }
        } else {
            Row {
                listOf(Triple(strikes, 'S', LampColor.STRIKE), Triple(balls, 'B', LampColor.BALL)).forEachIndexed { index, (count, letter, lamp) ->
                    Column(Modifier.width(column), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.graphicsLayer { alpha = if (index == 0) textAlpha.first else textAlpha.second }) {
                            HintCountText(count, letter, lamp.lit)
                        }
                        if (digits != null) {
                            VSpacer(6.dp)
                            LampRow(lamp, digits, count, ignites = if (index == 0) strikeIgnites else ballIgnites)
                        }
                    }
                }
            }
        }
    }
}

enum class SlotState { EMPTY, ACTIVE, FILLED, LOCKED, ERROR, HIT }

/**
 * Digit slots (DESIGN_SYSTEM 12.4): n plates centred, gap 8 dp, width min(56, (content − (n − 1) × 8) ÷ n), height 64
 * (48 compact), radius 8. [shakeKey] changes trigger the ±6 dp, 3-cycle, 280 ms shake. Tapping slot i calls [onTapSlot].
 */
@Composable
fun DigitSlots(
    digits: Int,
    value: String,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    error: Boolean = false,
    hit: Boolean = false,
    compact: Boolean = false,
    plateWidth: Dp? = null,
    plateHeight: Dp? = null,
    showActive: Boolean = true,
    placeholder: Char? = null,
    shakeKey: Int = 0,
    contentDescription: String? = null,
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
        val gap = NbSize.slotGap
        // Compact slots are 48 × 48 (ADAPTIVE.md §1, D2-05).
        val w = plateWidth ?: minOf(if (compact) NbSize.slotHeightCompact else NbSize.slotWidth, (maxWidth - gap * (digits - 1)) / digits).coerceAtLeast(NbSize.slotWidthMin)
        val h = plateHeight ?: if (compact) NbSize.slotHeightCompact else NbSize.slotHeight
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(digits) { i ->
                val ch = value.getOrNull(i)
                val state = when {
                    hit && ch != null -> SlotState.HIT
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
                    onClick = if (onTapSlot != null && ch != null && !locked) ({ onTapSlot(i) }) else null,
                )
            }
        }
    }
}

@Composable
private fun Slot(char: Char?, state: SlotState, width: Dp, height: Dp, compact: Boolean, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(NbRadius.m)
    val (fill, border, borderWidth) = when (state) {
        SlotState.EMPTY -> Triple(NbColors.bg, NbColors.outline, 1.dp)
        SlotState.ACTIVE -> Triple(NbColors.bg, NbColors.chalk, 2.dp)
        SlotState.FILLED -> Triple(NbColors.surface2, null, 0.dp)
        SlotState.LOCKED -> Triple(NbColors.surface1, null, 0.dp)
        SlotState.ERROR -> Triple(NbColors.outTint, NbColors.out, 2.dp)
        SlotState.HIT -> Triple(NbColors.strike, null, 0.dp)
    }
    val textColor = when (state) {
        SlotState.LOCKED -> NbColors.text2
        SlotState.HIT -> NbColors.onChalk
        SlotState.EMPTY, SlotState.ACTIVE -> NbColors.textDisabled
        else -> NbColors.chalk
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
        height >= 60.dp && width >= 52.dp -> NbType.numSlot.copy(fontSize = if (height == 60.dp) 32.sp else 40.sp)
        compact || height <= 48.dp -> NbType.numSlotSm
        else -> NbType.numSlot
    }
    Box(
        Modifier
            .size(width, height)
            .background(fill, shape)
            .then(if (border != null) Modifier.border(borderWidth, border, shape) else Modifier)
            .then(if (onClick != null) Modifier.combinedClickableCompat(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) {
            Text(char.toString(), style = style, color = textColor, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
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
) {
    val gap = if (compact) 4.dp else NbSize.keyGap
    val keyHeight = if (compact) NbSize.keyHeightCompact else NbSize.keyHeight
    val digitKey: @Composable (Char, Modifier) -> Unit = { d, m ->
        val isUsed = mode != KeypadMode.AGE && d in used
        DigitKey(d, isUsed, locked, memo[d] ?: MemoMark.NONE, keyHeight, m) { onDigit(d) }
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
        else -> NbColors.chalk
    }
    // Memo marks follow the key: a used or locked key shows its mark in `textDisabled` (DESIGN_SYSTEM 12.5).
    val markColor = if (disabled) NbColors.textDisabled else if (memo == MemoMark.IN) NbColors.chalk else NbColors.text3
    Box(
        modifier
            .height(height)
            .background(fill, RoundedCornerShape(NbRadius.m))
            .testTag("key_$digit")
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (disabled) disabled()
            }
            .then(if (!disabled) Modifier.combinedClickableCompat(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(digit.toString(), style = NbType.numKey, color = digitColor)
        if (memo == MemoMark.OUT) {
            // Prototype `.key.mx`: two 9 × 1.5 dp bars crossed at 45° in a 9 dp box 8 dp from the right, 11 dp from the top.
            Canvas(Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 8.dp).size(9.dp, 1.5.dp)) {
                crossBars(markColor, armLength = 9.dp.toPx(), stroke = 1.5.dp.toPx())
            }
        }
        if (memo == MemoMark.IN) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).size(14.dp, 3.dp).background(markColor, RoundedCornerShape(1.dp)))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconKey(icon: Int, description: String, locked: Boolean, height: Dp, modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Box(
        modifier
            .height(height)
            .background(if (locked) NbColors.surface1 else NbColors.surface2, RoundedCornerShape(NbRadius.m))
            .semantics { if (locked) disabled() }
            .then(if (!locked) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Function keys are secondary: `text2` glyphs (prototype `.key.fn`, D2-06).
        NbIcon(icon, description, if (locked) NbColors.textDisabled else NbColors.text2)
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
    val fill = if (enabled) NbColors.chalk else NbColors.surface2
    val textColor = when (state) {
        ActionState.ENABLED -> NbColors.onChalk
        ActionState.WAITING -> NbColors.text2
        else -> NbColors.textDisabled
    }
    Box(
        modifier
            .heightIn(min = height)
            .background(fill, RoundedCornerShape(NbRadius.m))
            .semantics(mergeDescendants = true) { role = Role.Button; if (!enabled) disabled() }
            .then(if (enabled) Modifier.combinedClickableCompat(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (state == ActionState.WAITING && !compact) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                ChaseLamps(NbSize.lampS)
                HSpacer(8.dp)
                Text(waitingLabel ?: label, style = NbType.label, color = textColor, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        } else {
            Text(label, style = if (compact) NbType.labelSmall else NbType.label, color = textColor, textAlign = TextAlign.Center, maxLines = if (compact) 1 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
    ClockLevel.NORMAL -> NbColors.chalk
    ClockLevel.WARN -> NbColors.strike
    ClockLevel.CRITICAL -> NbColors.out
    ClockLevel.PAUSED -> NbColors.textDisabled
}

fun formatClock(seconds: Int): String = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

/** Pitch clock (DESIGN_SYSTEM 12.2): `timer` 18 dp + `numTimer` m:ss, right-aligned, min width 38 dp. */
@Composable
fun PitchClock(secondsLeft: Int, level: ClockLevel, modifier: Modifier = Modifier) {
    val color = level.color()
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        NbIcon(R.drawable.ic_timer, null, if (level == ClockLevel.NORMAL) NbColors.text3 else color, 18.dp)
        HSpacer(4.dp)
        Text(formatClock(secondsLeft), style = NbType.numTimer, color = color, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 38.dp))
    }
}

/** 2 dp clock line at the bottom edge of the top bar: track `line`, fill shrinks right to left. */
@Composable
fun ClockLine(fraction: Float, level: ClockLevel, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).background(NbColors.line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(2.dp).background(level.color()))
    }
}

/** Mini plates of my secret in the duel strip: 18 × 22 dp, `surface2`, radius 4, Barlow 600 16 sp `text2`, 2 dp apart. */
@Composable
fun MiniPlates(value: String, plate: Dp = 18.dp, height: Dp = 22.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        value.forEach { ch ->
            // Plates grow with the capped numeral at large text instead of cutting it (D2-01).
            Box(Modifier.widthIn(min = plate).heightIn(min = height).background(NbColors.surface2, RoundedCornerShape(NbRadius.s)).padding(horizontal = 1.dp), contentAlignment = Alignment.Center) {
                Text(ch.toString(), style = NbType.numTag.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 0.sp), color = NbColors.text2)
            }
        }
    }
}

/** A `?` plate (search screen): `surface2`, radius 8, `numSlot`, chalk when swept, else `textDisabled`. */
@Composable
fun QuestionPlate(lit: Boolean, width: Dp, height: Dp) {
    Box(Modifier.size(width, height).background(NbColors.surface2, RoundedCornerShape(NbRadius.m)), contentAlignment = Alignment.Center) {
        Text("?", style = NbType.numSlot, color = if (lit) NbColors.chalk else NbColors.textDisabled)
    }
}

/** Two bars of [armLength] crossed at ±45° around the centre (the prototype's rotated pseudo-element bars). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.crossBars(color: Color, armLength: Float, stroke: Float) {
    val h = armLength / (2f * kotlin.math.sqrt(2f))
    val c = center
    drawLine(color, Offset(c.x - h, c.y - h), Offset(c.x + h, c.y + h), stroke, StrokeCap.Butt)
    drawLine(color, Offset(c.x + h, c.y - h), Offset(c.x - h, c.y + h), stroke, StrokeCap.Butt)
}
