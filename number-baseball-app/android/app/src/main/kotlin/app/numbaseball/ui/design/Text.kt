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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numbaseball.ui.theme.NbFonts
import kotlin.math.roundToInt
import androidx.compose.material3.Text as MaterialText

/**
 * Material `Text` with the design's exact line boxes (DESIGN_SYSTEM 3.2, 16). Android grows a line to the font's own
 * extents when they exceed the line height (Inter numerals, and Hangul / kana / Han through the fallback fonts), so a
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
    /** Steps the size down (to [fitMinScale]) rather than clip, ellipsize or break a word; off for player content (names). */
    autoFit: Boolean = true,
    fitMinScale: Float = 0.7f,
) {
    val (cappedStyle, cappedSize) = numeralCap(style, fontFamily, fontSize)
    val lines = remember { LineCount() }
    MaterialText(
        text = text,
        autoSize = if (autoFit) wordFit(if (cappedSize.isSpecified()) cappedSize else cappedStyle.fontSize, fitMinScale) else null,
        modifier = modifier.designLineBoxes(if (lineHeight.isSpecified()) lineHeight else cappedStyle.lineHeight, if (cappedSize.isSpecified()) cappedSize else cappedStyle.fontSize, lines),
        color = color, fontSize = cappedSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = { result ->
            lines.value = result.lineCount
            onTextLayout?.invoke(result)
        },
        style = cappedStyle,
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
    autoFit: Boolean = true,
) {
    val (cappedStyle, cappedSize) = numeralCap(style, fontFamily, fontSize)
    val lines = remember { LineCount() }
    MaterialText(
        text = text,
        autoSize = if (autoFit) wordFit(if (cappedSize.isSpecified()) cappedSize else cappedStyle.fontSize) else null,
        modifier = modifier.designLineBoxes(if (lineHeight.isSpecified()) lineHeight else cappedStyle.lineHeight, if (cappedSize.isSpecified()) cappedSize else cappedStyle.fontSize, lines),
        color = color, fontSize = cappedSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = { result ->
            lines.value = result.lineCount
            onTextLayout(result)
        },
        style = cappedStyle,
    )
}

/**
 * DEF-054..061 / AC-FONT-01: a label that does not fit its space (long translations, 200 % font, narrow windows) steps
 * its font size down in 5 % steps to [WordFit.minScale] of the token before it would clip, ellipsize or break inside a
 * word. When it fits at full size nothing changes. Latin, Cyrillic and Vietnamese words never break; Hangul labels
 * (up to three words) break only between 어절 (DESIGN_SYSTEM 3.2); Han and kana may break anywhere.
 */
private fun wordFit(size: TextUnit, minScale: Float = 0.7f): androidx.compose.foundation.text.TextAutoSize? = if (size.isSp && size.value > 0f) WordFit(size, minScale) else null

private data class WordFit(val max: TextUnit, val minScale: Float = 0.7f) : androidx.compose.foundation.text.TextAutoSize {
    override fun androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope.getFontSize(
        constraints: androidx.compose.ui.unit.Constraints,
        text: AnnotatedString,
    ): TextUnit {
        var scale = 1f
        while (true) {
            val size = max * scale
            val result = performLayout(constraints, text, size)
            if (scale <= minScale + 0.001f || fits(result, text.text, size.toPx())) return size
            scale = (scale - 0.05f).coerceAtLeast(minScale)
        }
    }
}

private fun fits(r: TextLayoutResult, text: String, fontPx: Float): Boolean {
    if (r.didOverflowWidth && r.multiParagraph.width - r.size.width > 1f) return false
    if (r.multiParagraph.height - r.size.height > fontPx * 0.25f) return false
    if ((0 until r.lineCount).any { r.isLineEllipsized(it) }) return false
    if (r.lineCount <= 1) return true
    return !brokenInsideWord(r, text)
}

/** True when a line ends inside a word (see [wordFit] for the scripts). */
internal fun brokenInsideWord(r: TextLayoutResult, text: String): Boolean {
    val label = text.count { it == ' ' } <= 2
    return (0 until r.lineCount - 1).any { i ->
        val end = r.getLineEnd(i, visibleEnd = false)
        if (end !in 1 until text.length) return@any false
        val a = text[end - 1]
        val b = text[end]
        a.isLetterOrDigit() && b.isLetterOrDigit() && wordScript(a, label) && wordScript(b, label)
    }
}

private fun wordScript(c: Char, label: Boolean): Boolean = when (Character.UnicodeScript.of(c.code)) {
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> false
    Character.UnicodeScript.HANGUL -> label
    else -> true
}

/** Fonts scale above this factor only for `sans` text (DESIGN_SYSTEM 3.3). */
const val NUMERAL_SCALE_CAP = 1.3f

/** True at the large-text breakpoint (fontScale ≥ 1.5, ADAPTIVE.md §2). */
@Composable
@ReadOnlyComposable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale >= 1.5f

/**
 * DESIGN_SYSTEM 3.3: numerals (`num` family) sit in fixed controls (slots, keys, memo cells, plates, tags, badges, log
 * columns), so they grow with the font scale only up to `size × min(fontScale, 1.3)`. Works with Android 14's non-linear
 * scaling: the rendered size is the smaller of the platform's scaled size and 1.3 × the token, and the line height keeps
 * the token's ratio. Every `num` text goes through the shared [Text], so the cap applies everywhere automatically;
 * [capped] gives the same style for measuring.
 */
@Composable
@ReadOnlyComposable
fun TextStyle.capped(): TextStyle = numeralCap(this, null, TextUnit.Unspecified).first

@Composable
@ReadOnlyComposable
private fun numeralCap(style: TextStyle, fontFamily: FontFamily?, fontSize: TextUnit): Pair<TextStyle, TextUnit> {
    val density = LocalDensity.current
    if (density.fontScale <= NUMERAL_SCALE_CAP || (fontFamily ?: style.fontFamily) != NbFonts.num) return style to fontSize
    val size = if (fontSize.isSpecified()) fontSize else style.fontSize
    if (!size.isSp) return style to fontSize
    val cappedSize = density.capSp(size)
    val line = style.lineHeight
    val cappedLine = if (line.isSp && size.value > 0f) (cappedSize.value * line.value / size.value).sp else line
    return style.copy(fontSize = cappedSize, lineHeight = cappedLine) to (if (fontSize.isSpecified()) cappedSize else fontSize)
}

private fun Density.capSp(size: TextUnit): TextUnit {
    val scaled = size.toDp()
    val limit = (size.value * NUMERAL_SCALE_CAP).dp
    return if (scaled <= limit) size else limit.toSp()
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
            // The glyph extents (Inter numerals, Hangul / kana fallbacks) may exceed the line box by up to about a third
            // of the font size; give the text that room so a container sized to the line boxes does not clip it.
            val slack = if (constraints.hasBoundedHeight && fontSize.isSp) (fontSize.toPx() * 0.35f).roundToInt() else 0
            val inner = if (slack > 0) constraints.copy(maxHeight = constraints.maxHeight + slack) else constraints
            val placeable = measurable.measure(inner)
            val target = (lines.value.coerceAtLeast(1) * lineBoxPx(lineHeight, fontSize)).roundToInt().coerceAtLeast(constraints.minHeight)
            if (target >= placeable.height) {
                val h = placeable.height.coerceAtMost(constraints.maxHeight)
                layout(placeable.width, h) { placeable.place(0, -((placeable.height - h) / 2f).roundToInt()) }
            } else {
                val h = target.coerceAtMost(constraints.maxHeight)
                layout(placeable.width, h) { placeable.place(0, -((placeable.height - h) / 2f).roundToInt()) }
            }
        }
    }

/**
 * One line box in px as Compose lays it out: with non-linear font scaling (Android 14, large font sizes) the line
 * height keeps its ratio to the scaled font size instead of being scaled on its own.
 */
internal fun androidx.compose.ui.unit.Density.lineBoxPx(lineHeight: TextUnit, fontSize: TextUnit): Float =
    if (fontSize.isSp && fontSize.value > 0f) fontSize.toPx() * (lineHeight.value / fontSize.value) else lineHeight.toPx()

/**
 * One line that never truncates or wraps: when [text] in [style] (numeral-capped) is wider than the space it gets, the
 * font size steps down (to 60 % at most) until it fits. Used for the log's guess digits (D2-01: "319620" lost its last
 * digit at 200 % font).
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val base = style.capped()
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val maxPx = constraints.maxWidth
        val fitted = remember(text, base, maxPx) {
            if (maxPx == androidx.compose.ui.unit.Constraints.Infinity) return@remember base
            var scale = 1f
            var candidate = base
            while (scale > 0.6f) {
                candidate = base.copy(fontSize = base.fontSize * scale, lineHeight = base.lineHeight, letterSpacing = base.letterSpacing)
                val width = measurer.measure(AnnotatedString(text), candidate, softWrap = false, maxLines = 1).size.width
                if (width <= maxPx) break
                scale -= 0.05f
            }
            candidate
        }
        androidx.compose.material3.Text(
            text,
            style = fitted,
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = textAlign,
            modifier = Modifier.designLineBoxes(fitted.lineHeight, fitted.fontSize, remember { LineCount() }),
        )
    }
}
