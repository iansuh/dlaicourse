package app.numbaseball.ui.design

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt
import androidx.compose.material3.Text as MaterialText

/**
 * Material `Text` with the design's exact line boxes (DESIGN_SYSTEM 3.2, 16). Android grows a line to the font's own
 * extents when they exceed the line height (Barlow numerals, and Hangul / kana / Han through the fallback fonts), so a
 * 16 / 22 Korean title measured 24 dp and every block of text drifted down. Like CSS, each line box here is exactly the
 * style's line height; the glyphs stay centred in it and may overflow it visually, never the layout. Every screen uses
 * this `Text` instead of the Material one.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val lines = remember { LineCount() }
    MaterialText(
        text = text,
        modifier = modifier.designLineBoxes(if (lineHeight.isSpecified()) lineHeight else style.lineHeight, if (fontSize.isSpecified()) fontSize else style.fontSize, lines),
        color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = { result ->
            lines.value = result.lineCount
            onTextLayout?.invoke(result)
        },
        style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    val lines = remember { LineCount() }
    MaterialText(
        text = text,
        modifier = modifier.designLineBoxes(if (lineHeight.isSpecified()) lineHeight else style.lineHeight, if (fontSize.isSpecified()) fontSize else style.fontSize, lines),
        color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = { result ->
            lines.value = result.lineCount
            onTextLayout(result)
        },
        style = style,
    )
}

/** Written by the text's own measure pass, read right after it in the same pass (not state on purpose). */
private class LineCount {
    var value = 1
}

private fun TextUnit.isSpecified() = this != TextUnit.Unspecified

/** Crops the measured text to lines × [lineHeight], centred, when Android made it taller (never grows it). */
private fun Modifier.designLineBoxes(lineHeight: TextUnit, fontSize: TextUnit, lines: LineCount): Modifier =
    if (!lineHeight.isSp) {
        this
    } else {
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val target = (lines.value.coerceAtLeast(1) * lineBoxPx(lineHeight, fontSize)).roundToInt().coerceAtLeast(constraints.minHeight)
            if (target >= placeable.height) {
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            } else {
                layout(placeable.width, target) { placeable.place(0, -((placeable.height - target) / 2f).roundToInt()) }
            }
        }
    }

/**
 * One line box in px as Compose lays it out: with non-linear font scaling (Android 14, large font sizes) the line
 * height keeps its ratio to the scaled font size instead of being scaled on its own.
 */
internal fun androidx.compose.ui.unit.Density.lineBoxPx(lineHeight: TextUnit, fontSize: TextUnit): Float =
    if (fontSize.isSp && fontSize.value > 0f) fontSize.toPx() * (lineHeight.value / fontSize.value) else lineHeight.toPx()
