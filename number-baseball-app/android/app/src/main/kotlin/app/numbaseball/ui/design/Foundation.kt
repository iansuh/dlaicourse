package app.numbaseball.ui.design

import androidx.compose.ui.draw.drawBehind
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numbaseball.core.protocol.Team
import app.numbaseball.ui.common.rememberWindowHeightDp
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbFonts
import app.numbaseball.ui.theme.NbLayout
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbType

/**
 * Reduced motion (DESIGN_SYSTEM 8.4): instant state changes, lamps lit at once, one static chase lamp. MainActivity
 * sets it from the system animator scale; screenshot tests set it so images are deterministic.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Height-compact window (< 480 dp): compact game layout, 12 dp margins, 48 dp top bars, no banner. */
@Composable
fun isCompactHeight(): Boolean = rememberWindowHeightDp() < NbLayout.compactHeightBelow

@Composable
fun screenMargin(): Dp = if (isCompactHeight()) NbLayout.marginCompact else NbLayout.margin

@Composable
fun NbIcon(@DrawableRes icon: Int, contentDescription: String?, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(icon), contentDescription, modifier.size(size), tint = tint)
}

/** 48 dp target, 24 dp glyph, unbounded ripple (DESIGN_SYSTEM 7). */
@Composable
fun IconButton48(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NbColors.chalk,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = 24.dp, color = NbColors.chalk),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        NbIcon(icon, contentDescription, if (enabled) tint else NbColors.textDisabled, iconSize)
    }
}

/** 1 dp hairline (decorative). */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = NbColors.line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Fades the bottom (and optionally top) edge of scrolling content with a DstIn gradient mask (DESIGN_SYSTEM 16). */
fun Modifier.fadingEdges(bottom: Dp = 16.dp, top: Dp = 0.dp, enabled: Boolean = true): Modifier =
    if (!enabled) {
        this
    } else {
        graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
            drawContent()
            val h = size.height
            if (bottom > 0.dp) {
                val b = bottom.toPx()
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = h - b, endY = h),
                    topLeft = Offset(0f, h - b),
                    size = Size(size.width, b),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (top > 0.dp) {
                val t = top.toPx()
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = t),
                    size = Size(size.width, t),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }

/** Horizontal fade at the right edge (scrolling chip rows, 16 dp). */
fun Modifier.fadingEnd(width: Dp = 16.dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val w = width.toPx()
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
            topLeft = Offset(size.width - w, 0f),
            size = Size(w, size.height),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * CJK fallback fonts make the first and last line taller than the style's line height (StaticLayout uses the fallback
 * font's extents there), so a Korean `display` line measures 60 dp instead of 48. This lays the text out at exactly
 * lines × lineHeight, centred, as the design renders it. For the large sans styles (display, headline).
 */
fun Modifier.exactLineHeight(style: TextStyle): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val line = if (style.lineHeight.isSp) lineBoxPx(style.lineHeight, style.fontSize) else 0f
    if (line <= 0f) return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    val lines = (placeable.height / line).toInt().coerceAtLeast(1)
    val height = (lines * line).roundToInt().coerceIn(constraints.minHeight, placeable.height)
    layout(placeable.width, height) { placeable.place(0, -(placeable.height - height) / 2) }
}

/** Horizontal fade at the left edge (line score scrolled to the end, 16 dp). */
fun Modifier.fadingStart(width: Dp = 16.dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val w = width.toPx()
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
            size = Size(w, size.height),
            blendMode = BlendMode.DstIn,
        )
    }

private val MARK_THREE = PathParser().parsePathString(
    "M61.53 54.9Q61.53 56.64 61.38 57.87Q61.06 60.72 59.18 62.36Q57.3 64 54.14 64Q50.86 64 48.8 62.26Q46.74 60.52 46.58 57.63" +
        "Q46.47 56.56 46.47 55.34Q46.47 54.86 46.94 54.86H51.57Q52.04 54.86 52.04 55.34Q52.04 56.25 52.16 57.04Q52.28 58.07 52.79 58.64" +
        "Q53.31 59.21 54.14 59.21Q54.93 59.21 55.44 58.66Q55.96 58.11 56.12 57.16Q56.23 55.77 56.23 54.31Q56.23 52.45 56.04 51.38" +
        "Q55.88 50.47 55.38 49.98Q54.89 49.49 54.14 49.49Q53.55 49.49 52.95 50Q52.71 50.16 52.64 50.16Q52.48 50.16 52.28 49.96L49.91 47.55" +
        "Q49.75 47.39 49.75 47.19Q49.75 47.03 49.87 46.88L54.65 40.98Q54.73 40.9 54.69 40.82Q54.65 40.75 54.53 40.75H47.42" +
        "Q47.22 40.75 47.08 40.61Q46.94 40.47 46.94 40.27V36.47Q46.94 36.28 47.08 36.14Q47.22 36 47.42 36H61.02Q61.22 36 61.36 36.14" +
        "Q61.49 36.28 61.49 36.47V40.43Q61.49 40.75 61.3 41.02L56.87 46.6Q56.83 46.68 56.83 46.76Q56.83 46.84 56.91 46.84" +
        "Q58.65 46.99 59.85 48.42Q61.06 49.84 61.34 51.98Q61.53 53.48 61.53 54.9Z",
).toPath()

/**
 * Brand mark (DESIGN_SYSTEM 15): number plate with a chalk 3 above three lit strike lamps, drawn from
 * design/icon/ic_launcher_foreground.svg. Default 22 × 32 dp.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, width: Dp = 22.dp, height: Dp = 32.dp, alpha: Float = 1f) {
    Canvas(modifier.size(width, height).graphicsLayer { this.alpha = alpha }) {
        // SVG region x 34..74, y 27..85.2 (plate, digit, lamps); scale it into the canvas.
        val s = size.height / 58.2f
        val dx = (size.width - 40f * s) / 2f
        translate(left = dx - 34f * s, top = -27f * s) {
            scale(s, pivot = Offset.Zero) {
                drawRoundRect(NbColors.surface2, Offset(36f, 28f), Size(36f, 44f), CornerRadius(6f))
                drawRoundRect(NbColors.lampOffRing, Offset(36.5f, 28.5f), Size(35f, 43f), CornerRadius(5.5f), style = Stroke(1f))
                drawPath(MARK_THREE, NbColors.chalk)
                listOf(44.5f, 54f, 63.5f).forEach { x ->
                    drawCircle(
                        Brush.radialGradient(listOf(NbColors.strike.copy(alpha = 0.55f), Color.Transparent), Offset(x, 81.5f), 6.6f),
                        radius = 6.6f,
                        center = Offset(x, 81.5f),
                    )
                    drawCircle(NbColors.strike, radius = 2.75f, center = Offset(x, 81.5f))
                }
            }
        }
    }
}

/** Name (sans) + `#1234` (num, text3) with no space; the name ellipsizes, the tag never does (DESIGN_SYSTEM 3.2). */
@Composable
fun NameWithTag(
    name: String,
    tag: String?,
    modifier: Modifier = Modifier,
    nameStyle: TextStyle = NbType.subtitle,
    nameColor: Color = NbColors.chalk,
    tagStyle: TextStyle = NbType.numTag,
    tagColor: Color = NbColors.text3,
) {
    // Name and tag sit on one baseline, like the prototype's inline `.pname` + `.ptag`.
    Row(modifier) {
        Text(name, style = nameStyle, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
        if (tag != null) {
            Spacer(Modifier.width(1.dp))
            Text("#$tag", style = tagStyle, color = tagColor, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        }
    }
}

/** Team badge 24 × 24 dp (16 dp in chat), radius 4, letter Barlow 700 on team A blue / team B chalk. */
@Composable
fun TeamBadge(team: Team, size: Dp = 24.dp) {
    Box(
        Modifier.size(size).background(if (team == Team.A) NbColors.teamA else NbColors.teamB, RoundedCornerShape(NbRadius.s)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (team == Team.A) "A" else "B",
            style = TextStyle(fontFamily = NbFonts.num, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = if (size >= 24.dp) 16.sp else 12.sp),
            color = NbColors.onChalk,
        )
    }
}

/** Tags (`micro`, 20 dp, radius 4): outline ("방장", "오프라인") or solid chalk ("나"). */
@Composable
fun NbTag(text: String, solid: Boolean = false) {
    Box(
        Modifier
            .heightIn(min = 20.dp)
            .then(
                if (solid) {
                    Modifier.background(NbColors.chalk, RoundedCornerShape(NbRadius.s))
                } else {
                    Modifier.border(1.dp, NbColors.outline, RoundedCornerShape(NbRadius.s))
                },
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NbType.micro, color = if (solid) NbColors.onChalk else NbColors.text2, maxLines = 1)
    }
}

/** A row that is one button with a full-width ripple. */
fun Modifier.rowClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = clickable(enabled = enabled, role = Role.Button, onClick = onClick)

@Composable
internal fun HSpacer(width: Dp) = Spacer(Modifier.width(width))

@Composable
internal fun VSpacer(height: Dp) = Spacer(Modifier.height(height))

internal val SpacedBy8 = Arrangement.spacedBy(8.dp)

/**
 * Text link underline like the prototype's `text-decoration: underline; text-underline-offset: 3px;
 * text-decoration-color: outline`: a 1 dp `outline` line 3 dp under the baseline of single-line text.
 */
fun Modifier.underlined(color: Color = NbColors.outline): Modifier = drawBehind {
    val y = size.height / 2 + 4.5.dp.toPx() + 3.dp.toPx()
    drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

/**
 * Lets a control keep its 48 dp touch target inside a row that is visually shorter ([visual], e.g. a 46 dp band between
 * two 1 dp lines): the control is measured at least [target] tall but reports [visual] to the parent, centred, so the
 * row keeps its design height and the target overlaps the lines, not the neighbours (D2-19).
 */
fun Modifier.targetOverflow(visual: Dp, target: Dp = NbLayout.touchMin): Modifier = layout { measurable, constraints ->
    val t = target.roundToPx()
    val v = visual.roundToPx()
    val p = measurable.measure(constraints.copy(minHeight = maxOf(constraints.minHeight, t), maxHeight = maxOf(constraints.maxHeight, t)))
    if (p.height <= v) {
        layout(p.width, p.height) { p.place(0, 0) }
    } else {
        layout(p.width, v) { p.place(0, -(p.height - v) / 2) }
    }
}
