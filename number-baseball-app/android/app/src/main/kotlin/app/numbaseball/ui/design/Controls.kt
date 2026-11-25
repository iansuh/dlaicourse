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

enum class ButtonKind { PRIMARY, SECONDARY, TONAL, TEXT, DESTRUCTIVE_OUTLINE, DESTRUCTIVE_TEXT }

/**
 * Buttons of DESIGN_SYSTEM 12.13: radius 8, icon 20 dp + 8 dp gap, 24 dp padding (12 small), wraps to 2 lines rather
 * than truncating. Disabled = `surface2` fill, no border, `textDisabled`.
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
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(NbRadius.m)
    val height = when {
        heightOverride != null -> heightOverride
        small -> NbSize.buttonHeightSmall
        kind == ButtonKind.PRIMARY -> NbSize.buttonHeight
        kind == ButtonKind.TEXT || kind == ButtonKind.DESTRUCTIVE_TEXT -> NbSize.buttonHeightSmall
        else -> NbSize.buttonHeightSecondary
    }
    val (fill, border, textColor) = when {
        !enabled && kind != ButtonKind.TEXT && kind != ButtonKind.DESTRUCTIVE_TEXT -> Triple(NbColors.surface2, null, NbColors.textDisabled)
        !enabled -> Triple(Color.Transparent, null, NbColors.textDisabled)
        kind == ButtonKind.PRIMARY -> Triple(if (pressed) NbColors.chalkPressed else NbColors.chalk, null, NbColors.onChalk)
        kind == ButtonKind.SECONDARY -> Triple(if (pressed) NbColors.surface2 else Color.Transparent, NbColors.outline, NbColors.chalk)
        kind == ButtonKind.TONAL -> Triple(if (pressed) NbColors.surface3 else NbColors.surface2, null, NbColors.chalk)
        kind == ButtonKind.DESTRUCTIVE_OUTLINE -> Triple(if (pressed) NbColors.surface2 else Color.Transparent, NbColors.out, NbColors.out)
        kind == ButtonKind.DESTRUCTIVE_TEXT -> Triple(Color.Transparent, null, NbColors.out)
        else -> Triple(Color.Transparent, null, NbColors.text2)
    }
    val textStyle = if (small || kind == ButtonKind.TEXT || kind == ButtonKind.DESTRUCTIVE_TEXT) NbType.labelSmall else NbType.label
    Row(
        modifier = modifier
            .heightIn(min = maxOf(height, if (kind == ButtonKind.TEXT) 48.dp else height))
            .background(fill, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (kind == ButtonKind.TEXT) 8.dp else if (small) 12.dp else 24.dp, vertical = 8.dp),
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
            Text(text, style = textStyle, color = textColor, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Segmented control (DESIGN_SYSTEM 12.12): 48 dp `surface2` track, 4 dp padding and gap, items radius 4. */
@Composable
fun <T> Segmented(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (item: T, selected: Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NbSize.segmented)
            .background(NbColors.surface2, RoundedCornerShape(NbRadius.m))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .background(if (isSelected) NbColors.chalk else Color.Transparent, RoundedCornerShape(NbRadius.s))
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) { label(item, isSelected) }
        }
    }
}

/** Segment label: Barlow 600 20 sp number + `labelSmall` unit ("3자리"), selected `onChalk`, else `text2`. */
@Composable
fun NumberUnitLabel(number: String, unit: String?, selected: Boolean) {
    val color = if (selected) NbColors.onChalk else NbColors.text2
    // Number and unit share a baseline (prototype `.seg button`: align-items baseline, number 600 20 / 40).
    Row {
        Text(number, style = NbType.numHint.copy(fontSize = 20.sp, lineHeight = 40.sp, letterSpacing = 0.sp), color = color, modifier = Modifier.alignByBaseline())
        if (unit != null) {
            HSpacer(2.dp)
            Text(unit, style = NbType.labelSmall, color = color, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
fun TextSegmentLabel(text: String, selected: Boolean) {
    Text(
        text,
        style = NbType.labelSmall,
        color = if (selected) NbColors.onChalk else NbColors.text2,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Switch 52 × 32 dp (DESIGN_SYSTEM 12.14). The whole row is the target, so this draws only. */
@Composable
fun NbSwitchVisual(checked: Boolean) {
    val thumbX by animateDpAsState(if (checked) 24.dp else 8.dp, tween(NbMotion.short), label = "thumb")
    val thumbSize by animateDpAsState(if (checked) 24.dp else 16.dp, tween(NbMotion.short), label = "thumbSize")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .background(if (checked) NbColors.chalk else NbColors.surface2, CircleShape)
            .then(if (checked) Modifier else Modifier.border(2.dp, NbColors.outline, CircleShape)),
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = (32.dp - thumbSize) / 2)
                .size(thumbSize)
                .background(if (checked) NbColors.onChalk else NbColors.text3, CircleShape),
        )
    }
}

/** Volume slider row (DESIGN_SYSTEM 12.14): track 4 dp, thumb 20 dp, value 28 dp box, steps of 5. */
@Composable
fun VolumeSliderRow(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit, onRelease: () -> Unit = {}) {
    val percent = (value * 100).roundToInt()
    val activeColor = if (enabled) NbColors.chalk else NbColors.textDisabled
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
            Box(Modifier.fillMaxWidth().height(4.dp).background(NbColors.surface4, RoundedCornerShape(2.dp)))
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).background(activeColor, RoundedCornerShape(2.dp)))
            Box(
                Modifier
                    .offset(x = (maxWidth - 20.dp) * value.coerceIn(0f, 1f))
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
        Modifier.size(20.dp).border(2.dp, if (selected) NbColors.chalk else NbColors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).background(NbColors.chalk, CircleShape))
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
        Text(text, style = NbType.body, color = NbColors.chalk)
    }
}

/** Section label: 40 dp (first) or 48 dp tall, `labelSmall` `text3`, baseline 8 dp above the next row. */
@Composable
fun SectionHeader(text: String, first: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(if (first) 40.dp else 48.dp).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
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
    /** Settings' "Delete account" has only its title in `out` (shot settings-ko-end); the block rows colour the icon too. */
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
            NbIcon(icon, null, if (destructiveIcon) NbColors.out else if (enabled) NbColors.text2 else NbColors.textDisabled)
            HSpacer(16.dp)
        } else {
            HSpacer(40.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = titleStyle, color = when {
                !enabled -> NbColors.textDisabled
                destructive -> NbColors.out
                else -> NbColors.chalk
            })
            if (subtitle != null) {
                VSpacer(2.dp)
                Text(subtitle, style = NbType.caption, color = if (enabled) subtitleColor else NbColors.textDisabled)
            }
        }
        if (value != null) {
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

/** Text field (DESIGN_SYSTEM 12.14): 56 dp `surface2`, radius 8, 16 dp padding, text 18 sp; focus 2 dp chalk, error 2 dp `out`. */
@Composable
fun NbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    height: Dp = NbSize.textField,
    textStyle: TextStyle = TextStyle(fontFamily = NbFonts.sans, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, color = NbColors.chalk),
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
        cursorBrush = SolidColor(NbColors.chalk),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = height)
                    .background(NbColors.surface2, shape)
                    .then(
                        when {
                            isError -> Modifier.border(2.dp, NbColors.out, shape)
                            focused -> Modifier.border(2.dp, NbColors.chalk, shape)
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
