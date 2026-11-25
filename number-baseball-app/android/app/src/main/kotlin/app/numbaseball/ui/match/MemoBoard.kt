package app.numbaseball.ui.match

import androidx.compose.ui.unit.sp
import app.numbaseball.ui.design.crossBars
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.SheetTitle
import app.numbaseball.ui.design.isLargeFont
import app.numbaseball.ui.theme.NbLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.numbaseball.R
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.MemoMark
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbType

/** Height of the memo band for [digits] positions (05 §1: 32 + 32 + n × 28 + 8, plus the 1 dp top line). */
fun memoHeight(digits: Int, collapsed: Boolean) =
    if (collapsed) 1.dp + NbSize.memoToolbar + NbSize.memoHeader + 8.dp else 1.dp + NbSize.memoToolbar + NbSize.memoHeader + NbSize.memoCell * digits + 8.dp

/**
 * Memo board (DESIGN_SYSTEM 12.8): `surface1` band, 16 dp margins, 8 dp bottom padding. Toolbar 32 dp, grid of a 24 dp
 * label column + 10 equal columns: digit row 32 dp, n position rows 28 dp.
 */
@Composable
fun MemoBoard(
    memo: MemoState,
    collapsed: Boolean,
    onDigit: (Int) -> Unit,
    onCell: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onCollapse: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    margin: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val large = isLargeFont()
    // At large text the toolbar row is itself 48 dp; otherwise it is 32 dp and its two controls get 48 dp targets that
    // extend upward over the log's bottom fade, never down into the digit row (D2-09).
    val toolbar = if (large) NbLayout.touchMin else NbSize.memoToolbar
    Column(modifier.fillMaxWidth().background(NbColors.surface1)) {
        Hairline()
        Row(Modifier.fillMaxWidth().heightIn(min = toolbar).padding(start = margin, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.memo), style = NbType.labelSmall, color = NbColors.text2, maxLines = 1)
            if (memo.isEmpty && !large) {
                Text(
                    stringResource(R.string.memo_help),
                    style = NbType.caption,
                    color = NbColors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            } else {
                Box(Modifier.weight(1f))
            }
            ToolbarAction(toolbar, enabled = !memo.isEmpty, onClick = onClear, description = null) {
                Text(
                    stringResource(R.string.memo_clear),
                    style = NbType.labelSmall,
                    color = if (memo.isEmpty) NbColors.textDisabled else NbColors.text2,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            if (onCollapse != null) {
                ToolbarAction(toolbar, enabled = true, onClick = { onCollapse(!collapsed) }, description = stringResource(if (collapsed) R.string.memo_expand else R.string.memo_collapse)) {
                    NbIcon(if (collapsed) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down, null, NbColors.text2, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
        Column(Modifier.padding(start = margin, end = margin, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = NbSize.memoHeader), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(24.dp))
                (0..9).forEach { d -> DigitHeaderCell(d, memo.digitMarks[d], Modifier.weight(1f).heightIn(min = NbSize.memoHeader)) { onDigit(d) } }
            }
            if (!collapsed) {
                repeat(memo.digits) { position ->
                    Row(Modifier.fillMaxWidth().height(NbSize.memoCell), verticalAlignment = Alignment.CenterVertically) {
                        // Centred in the 24 dp label column (prototype `.ml { place-items: center }`, D2-08).
                        Text((position + 1).toString(), style = NbType.numLabel, color = NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
                        (0..9).forEach { d ->
                            val columnOut = memo.digitMarks[d] == MemoMark.OUT
                            PositionCell(
                                position = position,
                                digit = d,
                                mark = memo.cells[position][d],
                                modifier = Modifier.weight(1f).fillMaxHeight().alpha(if (columnOut) 0.35f else 1f),
                            ) { onCell(position, d) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A toolbar control: visually [visual] tall, with a 48 dp target. When [visual] is shorter, the extra target sits above
 * the row (laid out upward so the parent row keeps its height).
 */
@Composable
private fun ToolbarAction(visual: Dp, enabled: Boolean, onClick: () -> Unit, description: String?, content: @Composable () -> Unit) {
    val extra = (NbLayout.touchMin - visual).coerceAtLeast(0.dp)
    Box(
        Modifier
            .extendTargetUp(visual, NbLayout.touchMin)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(top = extra),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Lays the content out [target] tall but reports [visual] to the parent, so the extra height reaches up only. */
private fun Modifier.extendTargetUp(visual: Dp, target: Dp): Modifier = layout { measurable, constraints ->
    val t = target.roundToPx()
    val v = visual.roundToPx()
    if (t <= v) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val p = measurable.measure(constraints.copy(minHeight = t, maxHeight = t))
    layout(p.width, v) { p.place(0, v - t) }
}

/**
 * The large memo (DESIGN_SYSTEM 11.1 b, ADAPTIVE.md §2): at fontScale ≥ 1.5 expanding the memo opens this sheet with a
 * transposed grid. Rows are digits 0–9 (the first cell is the digit's status), columns are positions 1..n; every cell is
 * 48 × 48 dp, with the same state cycles as the board.
 */
@Composable
fun ColumnScope.LargeMemoSheet(memo: MemoState, onDigit: (Int) -> Unit, onCell: (Int, Int) -> Unit, onClear: () -> Unit, onClose: () -> Unit) {
    SheetTitle(stringResource(R.string.memo)) {
        NbButton(stringResource(R.string.memo_clear), onClear, kind = ButtonKind.TEXT, enabled = !memo.isEmpty)
        IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), onClose, tint = NbColors.text2)
    }
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(LARGE_CELL))
            repeat(memo.digits) { p ->
                Text((p + 1).toString(), style = NbType.numLabel, color = NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.width(LARGE_CELL))
            }
        }
        (0..9).forEach { d ->
            val columnOut = memo.digitMarks[d] == MemoMark.OUT
            Row(verticalAlignment = Alignment.CenterVertically) {
                DigitHeaderCell(d, memo.digitMarks[d], Modifier.size(LARGE_CELL)) { onDigit(d) }
                repeat(memo.digits) { p ->
                    PositionCell(p, d, memo.cells[p][d], Modifier.size(LARGE_CELL).alpha(if (columnOut) 0.35f else 1f)) { onCell(p, d) }
                }
            }
        }
    }
}

private val LARGE_CELL = 48.dp

@Composable
private fun DigitHeaderCell(digit: Int, mark: MemoMark, modifier: Modifier, onClick: () -> Unit) {
    val state = stringResource(markLabel(mark))
    Box(
        modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$digit, $state" },
        contentAlignment = Alignment.Center,
    ) {
        when (mark) {
            MemoMark.IN -> Box(Modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp).background(NbColors.chalk, RoundedCornerShape(NbRadius.s)), contentAlignment = Alignment.Center) {
                Text(digit.toString(), style = NbType.numMemo, color = NbColors.onChalk)
            }
            MemoMark.OUT -> {
                Text(digit.toString(), style = NbType.numMemo, color = NbColors.textDisabled)
                Canvas(Modifier.size(22.dp)) {
                    // 22 dp diagonal stroke, bottom-left to top-right.
                    val inset = size.width * 0.15f
                    drawLine(NbColors.text3, Offset(inset, size.height - inset), Offset(size.width - inset, inset), 1.5.dp.toPx(), StrokeCap.Butt)
                }
            }
            MemoMark.NONE -> Text(digit.toString(), style = NbType.numMemo, color = NbColors.chalk)
        }
    }
}

@Composable
private fun PositionCell(position: Int, digit: Int, mark: MemoMark, modifier: Modifier, onClick: () -> Unit) {
    val spoken = stringResource(R.string.memo_cell_spoken, position + 1, digit, stringResource(markLabel(mark)))
    val actionLabel = stringResource(R.string.memo_change)
    Box(
        modifier
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = spoken
                customActions = listOf(CustomAccessibilityAction(actionLabel) { onClick(); true })
            },
        contentAlignment = Alignment.Center,
    ) {
        when (mark) {
            MemoMark.NONE -> Box(Modifier.size(4.dp).background(NbColors.lampOffRing, CircleShape))
            // Prototype `.mc.x`: two 10 × 1.5 dp bars crossed at 45°.
            MemoMark.OUT -> Canvas(Modifier.size(10.dp)) { crossBars(NbColors.text3, armLength = 10.dp.toPx(), stroke = 1.5.dp.toPx()) }
            // `.mc > b`: the memo digit at 16 sp inside a cell plate.
            MemoMark.IN -> Box(Modifier.size(24.dp).background(NbColors.chalk, RoundedCornerShape(NbRadius.s)), contentAlignment = Alignment.Center) {
                Text(digit.toString(), style = NbType.numMemo.copy(fontSize = 16.sp), color = NbColors.onChalk)
            }
        }
    }
}

private fun markLabel(mark: MemoMark) = when (mark) {
    MemoMark.NONE -> R.string.memo_state_unknown
    MemoMark.OUT -> R.string.memo_state_out
    MemoMark.IN -> R.string.memo_state_in
}
