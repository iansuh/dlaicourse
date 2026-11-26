package app.numbaseball.ui.match

import androidx.compose.ui.unit.sp
import app.numbaseball.ui.design.crossBars
import app.numbaseball.ui.design.ruledOut
import app.numbaseball.ui.design.isCompactHeight
import app.numbaseball.ui.design.SheetBottomSpace
import androidx.compose.ui.semantics.heading
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

/**
 * Memo sheet content (DESIGN_SYSTEM 12.9, shot `match-memo-ko`): header 56 dp "메모" · 지우기 · `close`; help line; grid
 * with 16 dp margins: 24 dp label column + 10 equal columns, digit status row 44 dp, n position rows 40 dp with a 1 dp
 * `line` above each. The match keeps running underneath; changes show on the keypad marks at once. Small windows:
 * 48 dp header and the position rows scroll inside the sheet (ADAPTIVE §1).
 */
@Composable
fun ColumnScope.MemoSheet(
    memo: MemoState,
    onDigit: (Int) -> Unit,
    onCell: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val compact = isCompactHeight()
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 56.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.memo), style = NbType.title, color = NbColors.ink, maxLines = 1, modifier = Modifier.weight(1f).semantics { heading() })
        NbButton(stringResource(R.string.memo_clear), onClear, kind = ButtonKind.TEXT, enabled = !memo.isEmpty, textColorOverride = NbColors.ink)
        IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), onClose)
    }
    Text(
        stringResource(R.string.memo_help),
        style = NbType.caption,
        color = NbColors.text3,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = NbSize.memoHeader), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(NbSize.memoLabelColW))
            (0..9).forEach { d -> DigitHeaderCell(d, memo.digitMarks[d], Modifier.weight(1f).heightIn(min = NbSize.memoHeader)) { onDigit(d) } }
        }
    }
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        repeat(memo.digits) { position ->
            Hairline()
            Row(Modifier.fillMaxWidth().height(NbSize.memoCell - 1.dp), verticalAlignment = Alignment.CenterVertically) {
                // Centred in the 24 dp label column (prototype `.ml { place-items: center }`).
                Text((position + 1).toString(), style = NbType.numLabel, color = NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.width(NbSize.memoLabelColW))
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
        SheetBottomSpace(24.dp)
    }
}

/**
 * The large memo (DESIGN_SYSTEM 11.1 b, ADAPTIVE.md §2): at fontScale ≥ 1.5 expanding the memo opens this sheet with a
 * transposed grid. Rows are digits 0–9 (the first cell is the digit's status), columns are positions 1..n; every cell is
 * 48 × 48 dp, with the same state cycles as the board.
 */
@Composable
fun ColumnScope.LargeMemoSheet(
    memo: MemoState,
    onDigit: (Int) -> Unit,
    onCell: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    /** The pitch clock stays visible while the sheet covers the match (D2-34 a). */
    clock: (@Composable () -> Unit)? = null,
) {
    SheetTitle(stringResource(R.string.memo)) {
        clock?.invoke()
        NbButton(stringResource(R.string.memo_clear), onClear, kind = ButtonKind.TEXT, enabled = !memo.isEmpty, textColorOverride = NbColors.ink)
        IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), onClose)
    }
    val gridWidth = LARGE_CELL * (memo.digits + 1)
    // Position row: 40 dp, pinned above the scrolling digit rows (D2-34 d); the grid is centred (b).
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.width(gridWidth).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(LARGE_CELL))
            repeat(memo.digits) { p ->
                Text((p + 1).toString(), style = NbType.numLabel, color = NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.width(LARGE_CELL))
            }
        }
    }
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        (0..9).forEach { d ->
            val columnOut = memo.digitMarks[d] == MemoMark.OUT
            // 1 dp `line` between digit rows (D2-34 c).
            Hairline(Modifier.width(gridWidth))
            Row(Modifier.width(gridWidth), verticalAlignment = Alignment.CenterVertically) {
                DigitHeaderCell(d, memo.digitMarks[d], Modifier.size(LARGE_CELL)) { onDigit(d) }
                repeat(memo.digits) { p ->
                    PositionCell(p, d, memo.cells[p][d], Modifier.size(LARGE_CELL).alpha(if (columnOut) 0.35f else 1f), plate = 32.dp) { onCell(p, d) }
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
            // Digit cycle (12.9): unknown `ink` → out `textDisabled` + 22 × 2 dp stroke → in 28 dp `ink` plate.
            MemoMark.IN -> Box(Modifier.sizeIn(minWidth = 28.dp, minHeight = 28.dp).background(NbColors.ink, RoundedCornerShape(NbRadius.s)), contentAlignment = Alignment.Center) {
                Text(digit.toString(), style = NbType.numMemo, color = NbColors.onInk, autoFit = false)
            }
            MemoMark.OUT -> Box(Modifier.sizeIn(minWidth = 28.dp, minHeight = 28.dp).ruledOut(NbColors.text3), contentAlignment = Alignment.Center) {
                Text(digit.toString(), style = NbType.numMemo, color = NbColors.textDisabled, autoFit = false)
            }
            MemoMark.NONE -> Text(digit.toString(), style = NbType.numMemo, color = NbColors.ink, autoFit = false)
        }
    }
}

@Composable
private fun PositionCell(position: Int, digit: Int, mark: MemoMark, modifier: Modifier, plate: Dp = 28.dp, onClick: () -> Unit) {
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
            MemoMark.NONE -> Box(Modifier.size(4.dp).background(NbColors.dotOff, CircleShape))
            // `.mc.x`: two 10 × 1.5 dp bars crossed at 45°.
            MemoMark.OUT -> Canvas(Modifier.size(10.dp)) { crossBars(NbColors.text3, armLength = 10.dp.toPx(), stroke = 1.5.dp.toPx()) }
            // `.mc > b`: 28 dp `ink` plate with the digit at 16 sp (32 dp plates in the large sheet).
            MemoMark.IN -> Box(Modifier.size(plate).background(NbColors.ink, RoundedCornerShape(NbRadius.s)), contentAlignment = Alignment.Center) {
                Text(digit.toString(), style = NbType.numMemo.copy(fontSize = 16.sp), color = NbColors.onInk, autoFit = false)
            }
        }
    }
}

private fun markLabel(mark: MemoMark) = when (mark) {
    MemoMark.NONE -> R.string.memo_state_unknown
    MemoMark.OUT -> R.string.memo_state_out
    MemoMark.IN -> R.string.memo_state_in
}
