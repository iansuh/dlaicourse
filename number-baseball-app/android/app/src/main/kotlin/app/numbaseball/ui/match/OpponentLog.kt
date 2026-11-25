package app.numbaseball.ui.match

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.numbaseball.R
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.ui.design.FitText
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.HintColumns
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.SheetBottomSpace
import app.numbaseball.ui.design.SheetTitle
import app.numbaseball.ui.design.Text
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbType

/**
 * "상대 기록 보기" (05 §2.9, D2-43): the opponent's full log in a sheet, newest first. Rows as in the pitch log (round ·
 * guess · S · B), 48 dp. Opened from the duel strip's opponent area, the log's "상대 기록" header button (when the opponent
 * column is hidden, ADAPTIVE §2.2) and the overflow menu. The match keeps running underneath.
 */
@Composable
fun ColumnScope.OpponentLogSheet(digits: Int, rounds: List<RoundEntry>, onClose: () -> Unit) {
    SheetTitle(stringResource(R.string.opp_log_title)) {
        IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), onClose, tint = NbColors.text2)
    }
    val hint = if (digits >= 5) 52.dp else 44.dp
    if (rounds.isEmpty()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.opp_log_empty), style = NbType.caption, color = NbColors.text3, textAlign = TextAlign.Center)
        }
    } else {
        Hairline()
        Row(Modifier.fillMaxWidth().heightIn(min = NbSize.historyHeader).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#", style = NbType.labelSmall, color = NbColors.text3, maxLines = 1, modifier = Modifier.width(28.dp))
            Text(stringResource(R.string.col_opp_guess), style = NbType.labelSmall, color = NbColors.text3, maxLines = 1, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.col_hint), style = NbType.labelSmall, color = NbColors.text3, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(hint * 2))
        }
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(rounds.asReversed(), key = { it.round }) { entry -> OpponentRow(entry, hint) }
        }
    }
    SheetBottomSpace()
}

@Composable
private fun OpponentRow(entry: RoundEntry, hint: androidx.compose.ui.unit.Dp) {
    val spoken = stringResource(R.string.log_row_spoken, entry.round, entry.guess?.toList()?.joinToString(" ") ?: "–", entry.strikes, entry.balls)
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Hairline()
        Row(Modifier.fillMaxWidth().heightIn(min = 47.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(entry.round.toString(), style = NbType.numLabel, color = NbColors.text3, modifier = Modifier.width(28.dp).clearAndSetSemantics {})
            FitText(entry.guess ?: "—", style = NbType.numLog, color = if (entry.guess == null) NbColors.text3 else NbColors.chalk, modifier = Modifier.weight(1f).clearAndSetSemantics {})
            Box(Modifier.clearAndSetSemantics {}) {
                if (entry.guess == null) {
                    Box(Modifier.width(hint * 2), contentAlignment = Alignment.Center) { Text("—", style = NbType.numHint, color = NbColors.text3) }
                } else {
                    HintColumns(strikes = entry.strikes, balls = entry.balls, column = hint)
                }
            }
        }
    }
}
