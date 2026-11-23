package app.numbaseball.ui.match

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
    Column(modifier.fillMaxWidth().background(NbColors.surface1)) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(NbSize.memoToolbar).padding(start = margin, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.memo), style = NbType.labelSmall, color = NbColors.text2)
            if (memo.isEmpty) {
                Text(
                    stringResource(R.string.memo_help),
                    style = NbType.caption,
                    color = NbColors.text3,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            } else {
                Box(Modifier.weight(1f))
            }
            NbButton(stringResource(R.string.memo_clear), onClear, kind = ButtonKind.TEXT, enabled = !memo.isEmpty)
            if (onCollapse != null) {
                IconButton48(
                    if (collapsed) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down,
                    stringResource(if (collapsed) R.string.memo_expand else R.string.memo_collapse),
                    { onCollapse(!collapsed) },
                    tint = NbColors.text2,
                )
            }
        }
        Column(Modifier.padding(start = margin, end = margin, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(NbSize.memoHeader)) {
                Box(Modifier.width(24.dp))
                (0..9).forEach { d -> DigitHeaderCell(d, memo.digitMarks[d], Modifier.weight(1f).fillMaxHeight()) { onDigit(d) } }
            }
            if (!collapsed) {
                repeat(memo.digits) { position ->
                    Row(Modifier.fillMaxWidth().height(NbSize.memoCell), verticalAlignment = Alignment.CenterVertically) {
                        Text((position + 1).toString(), style = NbType.numLabel, color = NbColors.text3, modifier = Modifier.width(24.dp))
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
            MemoMark.IN -> Box(Modifier.size(24.dp).background(NbColors.chalk, RoundedCornerShape(NbRadius.s)), contentAlignment = Alignment.Center) {
                Text(digit.toString(), style = NbType.numMemo, color = NbColors.onChalk)
            }
            MemoMark.OUT -> {
                Text(digit.toString(), style = NbType.numMemo, color = NbColors.textDisabled)
                Canvas(Modifier.size(22.dp)) {
                    // 22 dp diagonal stroke, bottom-left to top-right.
                    val inset = size.width * 0.15f
                    drawLine(NbColors.text3, Offset(inset, size.height - inset), Offset(size.width - inset, inset), 1.5.dp.toPx(), StrokeCap.Round)
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
            MemoMark.OUT -> Canvas(Modifier.size(10.dp)) {
                val w = 1.5.dp.toPx()
                drawLine(NbColors.text3, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
                drawLine(NbColors.text3, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
            }
            MemoMark.IN -> Box(Modifier.size(24.dp).background(NbColors.chalk, RoundedCornerShape(NbRadius.s)), contentAlignment = Alignment.Center) {
                Text(digit.toString(), style = NbType.numMemo, color = NbColors.onChalk)
            }
        }
    }
}

private fun markLabel(mark: MemoMark) = when (mark) {
    MemoMark.NONE -> R.string.memo_state_unknown
    MemoMark.OUT -> R.string.memo_state_out
    MemoMark.IN -> R.string.memo_state_in
}
