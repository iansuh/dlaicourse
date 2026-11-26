package app.numbaseball.ui.design

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numbaseball.R
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbFonts
import app.numbaseball.ui.theme.NbMotion
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbType
import kotlin.math.roundToInt

enum class ButtonKind { PRIMARY, SECONDARY, TONAL, TEXT, LINK, DESTRUCTIVE_OUTLINE, DESTRUCTIVE_TEXT }

/**
 * Buttons of DESIGN_SYSTEM 12.14: radius 12, icon 20 dp + 8 dp gap, 24 dp padding (12 small), wraps to 2 lines rather
 * than truncating. Primary 56 `accent`; secondary 56 `bg` + 1.5 dp `line`; tonal 48 `surface2`; text 36 (48 target)
 * `labelSmall` `text2`; link `labelSmall` 14 sp `accent` + chevron. Disabled = `surface2`, no border, `textDisabled`.
 */
@Composable
fun NbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    small: Boolean = false,
    /** Overrides the variant's height (buttons side by side in small windows are 48 dp, ADAPTIVE.md §1). */
    heightOverride: Dp? = null,
    /** Text colour override for text buttons (dialog-style accent actions). */
    textColorOverride: Color? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(NbRadius.m)
    val textLike = kind == ButtonKind.TEXT || kind == ButtonKind.DESTRUCTIVE_TEXT || kind == ButtonKind.LINK
    val height = when {
        heightOverride != null -> heightOverride
        small -> NbSize.buttonHeightSmall
        kind == ButtonKind.PRIMARY -> NbSize.buttonHeight
        kind == ButtonKind.TONAL -> NbSize.buttonHeightTonal
        textLike -> NbSize.buttonHeightSmall
        else -> NbSize.buttonHeightSecondary
    }
    val (fill, border, textColor) = when {
        !enabled && !textLike -> Triple(NbColors.surface2, null, NbColors.textDisabled)
        !enabled -> Triple(Color.Transparent, null, NbColors.textDisabled)
        kind == ButtonKind.PRIMARY -> Triple(if (pressed) NbColors.accentPressed else NbColors.accent, null, NbColors.onAccent)
        kind == ButtonKind.SECONDARY -> Triple(if (pressed) NbColors.surface2 else NbColors.bg, NbColors.line, NbColors.ink)
        kind == ButtonKind.TONAL -> Triple(if (pressed) NbColors.surface4 else NbColors.surface2, null, NbColors.ink)
        kind == ButtonKind.DESTRUCTIVE_OUTLINE -> Triple(if (pressed) NbColors.dangerTint else NbColors.bg, NbColors.danger, NbColors.danger)
        kind == ButtonKind.DESTRUCTIVE_TEXT -> Triple(Color.Transparent, null, NbColors.danger)
        kind == ButtonKind.LINK -> Triple(Color.Transparent, null, NbColors.accent)
        else -> Triple(Color.Transparent, null, textColorOverride ?: NbColors.text2)
    }
    val textStyle = when {
        kind == ButtonKind.LINK -> NbType.labelSmall.copy(fontSize = 14.sp)
        small || textLike -> NbType.labelSmall
        else -> NbType.label
    }
    Row(
        modifier = modifier
            .heightIn(min = maxOf(height, if (textLike) 48.dp else height))
            .clip(shape)
            .background(fill, shape)
            .then(if (border != null) Modifier.border(NbSize.border, border, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = if (textLike) ripple(color = NbColors.ink) else null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = when {
                    kind == ButtonKind.LINK -> 4.dp
                    textLike -> 8.dp
                    small -> 12.dp
                    else -> 24.dp
                },
                // Large text: buttons grow with 16 dp vertical padding (DESIGN_SYSTEM 11.3), unless their height is fixed.
                vertical = if (isLargeFont() && !textLike && !small && heightOverride == null) 16.dp else 8.dp,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (content != null) {
            content()
        } else {
            if (icon != null) {
                NbIcon(icon, null, textColor, 20.dp)
                HSpacer(8.dp)
            }
            // Two lines at most; a long word steps the size down instead of breaking (DEF-059).
            Text(text, style = textStyle, color = textColor, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, fitMinScale = 0.55f, modifier = Modifier.weight(1f, fill = false))
            if (kind == ButtonKind.LINK) {
                HSpacer(2.dp)
                NbIcon(R.drawable.ic_chevron_right, null, textColor, 18.dp)
            }
        }
    }
}

/**
 * Equal tiles in a row: each child gets (width − gaps) ÷ n, all take the tallest child's height (at least [minHeight]).
 * Used instead of `IntrinsicSize.Min`, whose intrinsic pass under-measures auto-sizing text (two-line labels at 200 %).
 */
@Composable
fun EqualTilesRow(gap: Dp, minHeight: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val gapPx = gap.roundToPx()
        val minH = minHeight.roundToPx()
        var w = 0
        // Probe pass (no semantics, never placed): the tallest tile at the shared width.
        val probe = subcompose("probe") {
            androidx.compose.ui.layout.Layout(content, Modifier.clearAndSetSemantics {}) { ms, _ ->
                val n = ms.size.coerceAtLeast(1)
                w = ((constraints.maxWidth - gapPx * (n - 1)) / n).coerceAtLeast(0)
                val h = ms.maxOfOrNull { it.measure(androidx.compose.ui.unit.Constraints(minWidth = w, maxWidth = w, minHeight = minH)).height } ?: minH
                layout(w, h) {}
            }
        }.first().measure(androidx.compose.ui.unit.Constraints())
        val h = probe.height
        val placeables = subcompose("tiles", content).map { it.measure(androidx.compose.ui.unit.Constraints.fixed(w, h)) }
        layout(constraints.maxWidth, h) {
            placeables.forEachIndexed { i, p -> p.place(i * (w + gapPx), 0) }
        }
    }
}

/**
 * Segmented control = tiles (DESIGN_SYSTEM 12.13): no track, equal `surface2` tiles, 8 dp gap, 48 dp min ([tileHeight]
 * 52 on Home), radius 12; selected tile `ink`.
 */
@Composable
fun <T> Segmented(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tileHeight: Dp = NbSize.segmented,
    label: @Composable (item: T, selected: Boolean) -> Unit,
) {
    EqualTilesRow(8.dp, tileHeight, modifier) {
        items.forEach { item ->
            val isSelected = item == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(NbRadius.m))
                    .background(if (isSelected) NbColors.ink else NbColors.surface2)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) { label(item, isSelected) }
        }
    }
}

/** Segment label: number `numSummary` `ink` + unit `labelSmall` `text2` ("4자리"); selected: both `onInk`. */
@Composable
fun NumberUnitLabel(number: String, unit: String?, selected: Boolean) {
    if (isLargeFont() && unit != null) {
        // Large text: the unit goes under the number so neither is cut in a narrow tile (ADAPTIVE §2.5).
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)) {
            Text(number, style = NbType.numSummary, color = if (selected) NbColors.onInk else NbColors.ink, maxLines = 1, softWrap = false)
            Text(unit, style = NbType.labelSmall, color = if (selected) NbColors.onInk else NbColors.text2, maxLines = 1, softWrap = false, fitMinScale = 0.5f)
        }
        return
    }
    // Number and unit share a baseline (prototype `.seg button`: align-items baseline, gap 2).
    Row(Modifier.padding(horizontal = 2.dp)) {
        Text(number, style = NbType.numSummary, color = if (selected) NbColors.onInk else NbColors.ink, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        if (unit != null) {
            HSpacer(2.dp)
            Text(unit, style = NbType.labelSmall, color = if (selected) NbColors.onInk else NbColors.text2, maxLines = 1, softWrap = false, fitMinScale = 0.5f, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
        }
    }
}

@Composable
fun TextSegmentLabel(text: String, selected: Boolean) {
    Text(
        text,
        style = NbType.labelSmall,
        color = if (selected) NbColors.onInk else NbColors.text2,
        textAlign = TextAlign.Center,
        // One line, never broken inside a word (D2-44): the shared Text steps the size down when it does not fit.
        maxLines = 1,
        softWrap = false,
        fitMinScale = 0.5f,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Switch 52 × 32 dp (DESIGN_SYSTEM 12.15): off `bg` + 2 dp `outline` ring, 16 dp `outline` thumb; on `accent`, 24 dp white thumb. */
@Composable
fun NbSwitchVisual(checked: Boolean) {
    val thumbX by animateDpAsState(if (checked) 24.dp else 8.dp, tween(NbMotion.short), label = "thumb")
    val thumbSize by animateDpAsState(if (checked) 24.dp else 16.dp, tween(NbMotion.short), label = "thumbSize")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .background(if (checked) NbColors.accent else NbColors.bg, CircleShape)
            .then(if (checked) Modifier else Modifier.border(2.dp, NbColors.outline, CircleShape)),
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = (32.dp - thumbSize) / 2)
                .size(thumbSize)
                .background(if (checked) NbColors.onAccent else NbColors.outline, CircleShape),
        )
    }
}

/** Volume slider row (DESIGN_SYSTEM 12.14): track 4 dp, thumb 20 dp, value 28 dp box, steps of 5. */
@Composable
fun VolumeSliderRow(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit, onRelease: () -> Unit = {}) {
    val percent = (value * 100).roundToInt()
    val activeColor = if (enabled) NbColors.accent else NbColors.textDisabled
    var widthPx by remember { mutableStateOf(1f) }
    val density = LocalDensity.current
    fun valueAt(x: Float): Float = ((x / widthPx).coerceIn(0f, 1f) * 20f).roundToInt() / 20f
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            // The track sits 16 dp under the row above it (reference render), not centred in the 48 dp target.
            .padding(start = 56.dp, end = 16.dp, bottom = 16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, steps = 19)
                if (enabled) {
                    setProgress { target ->
                        onChange((target * 20f).roundToInt() / 20f)
                        true
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onTap = { onChange(valueAt(it.x)); onRelease() })
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(onDragEnd = onRelease) { change, _ -> onChange(valueAt(change.position.x)) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            widthPx = with(density) { maxWidth.toPx() }
            Box(Modifier.fillMaxWidth().height(4.dp).background(NbColors.line, RoundedCornerShape(2.dp)))
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).background(activeColor, RoundedCornerShape(2.dp)))
            Box(
                Modifier
                    // Thumb centre = track start + value × track width; it overhangs the track ends by 10 dp (D2-21).
                    .offset(x = maxWidth * value.coerceIn(0f, 1f) - 10.dp)
                    .size(20.dp)
                    .background(activeColor, CircleShape),
            )
        }
        HSpacer(16.dp)
        Text(
            percent.toString(),
            style = NbType.numTag,
            color = if (enabled) NbColors.text2 else NbColors.textDisabled,
            textAlign = TextAlign.End,
            modifier = Modifier.width(28.dp),
        )
    }
}

/** Radio 20 dp (DESIGN_SYSTEM 12.14). */
@Composable
fun NbRadioVisual(selected: Boolean) {
    Box(
        Modifier.size(20.dp).border(2.dp, if (selected) NbColors.accent else NbColors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).background(NbColors.accent, CircleShape))
    }
}

@Composable
fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NbRadioVisual(selected)
        HSpacer(16.dp)
        Text(text, style = NbType.body, color = NbColors.ink)
    }
}

/** Section label: 40 dp (first) or 48 dp tall, `labelSmall` `text3`, baseline 8 dp above the next row. */
@Composable
fun SectionHeader(text: String, first: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().heightIn(min = if (first) 40.dp else 48.dp).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(text, style = NbType.labelSmall, color = NbColors.text3)
    }
}

/**
 * List row (DESIGN_SYSTEM 4 / 12.14): icon 24 dp at x = 16 (`text2`), text at x = 56, 64 dp with a subtitle, 56 dp single
 * line, value `body` `text2`, chevron 20 dp `text3`.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    subtitleColor: Color = NbColors.text3,
    value: String? = null,
    chevron: Boolean = false,
    destructive: Boolean = false,
    /** Settings' "Delete account" has only its title in `danger` (shot settings-ko-end); the block rows colour the icon too. */
    destructiveIcon: Boolean = destructive,
    enabled: Boolean = true,
    titleStyle: TextStyle = NbType.subtitle,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle != null) 64.dp else 56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = role, onClick = onClick) else Modifier)
            // Prototype `.row`: 10 / 16 dp padding, 16 dp gaps, 2 dp between title and subtitle.
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            NbIcon(icon, null, if (destructiveIcon) NbColors.danger else if (enabled) NbColors.text2 else NbColors.textDisabled)
            HSpacer(16.dp)
        } else {
            HSpacer(40.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = titleStyle, color = when {
                !enabled -> NbColors.textDisabled
                destructive -> NbColors.danger
                else -> NbColors.ink
            })
            if (subtitle != null) {
                VSpacer(2.dp)
                Text(subtitle, style = NbType.caption, color = if (enabled) subtitleColor else NbColors.textDisabled)
            }
            // Large text: the value moves under the title so the title keeps the row's width (DEF-061).
            if (value != null && isLargeFont()) {
                VSpacer(2.dp)
                Text(value, style = NbType.body, color = NbColors.text2)
            }
        }
        if (value != null && !isLargeFont()) {
            HSpacer(16.dp)
            Text(value, style = NbType.body, color = NbColors.text2, maxLines = 1)
        }
        if (trailing != null) {
            HSpacer(16.dp)
            trailing()
        }
        if (chevron) {
            HSpacer(16.dp)
            NbIcon(R.drawable.ic_chevron_right, null, NbColors.text3, 20.dp)
        }
    }
}

/** Divider inset to x = [inset] (56 dp for icon rows, 72 dp on Home rows with a 40 dp plate). */
@Composable
fun InsetDivider(inset: Dp = 56.dp) {
    // Takes no height: like the prototype's `.row + .row::before`, the line is drawn over the top 1 dp of the next row,
    // so a list of 64 dp rows stays exactly 64 dp per row.
    Box(
        Modifier
            .zIndex(1f)
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, 0) { placeable.place(0, 0) }
            }
            .padding(start = inset)
            .height(1.dp)
            .background(NbColors.line),
    )
}

/** Text field (DESIGN_SYSTEM 12.15): 56 dp `surface2`, radius 12, 16 dp padding, text 18 sp; focus 2 dp `accent`, error 2 dp `danger`. */
@Composable
fun NbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    height: Dp = NbSize.textField,
    textStyle: TextStyle = TextStyle(fontFamily = NbFonts.sans, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, color = NbColors.ink),
    horizontalPadding: Dp = 16.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NbRadius.m)
    // The caret sits after the text when the value is set from outside (restored state, filtered input).
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        value = field,
        onValueChange = {
            field = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        textStyle = textStyle,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        cursorBrush = SolidColor(NbColors.accent),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = height)
                    .background(NbColors.surface2, shape)
                    .then(
                        when {
                            isError -> Modifier.border(2.dp, NbColors.danger, shape)
                            focused -> Modifier.border(2.dp, NbColors.accent, shape)
                            else -> Modifier
                        },
                    )
                    .padding(start = horizontalPadding, end = if (trailing != null) 4.dp else horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = NbType.body, color = NbColors.text3)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}
