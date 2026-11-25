package app.numbaseball.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.numbaseball.R
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.HintColumns
import app.numbaseball.ui.design.HintCountText
import app.numbaseball.ui.design.Lamp
import app.numbaseball.ui.design.LampColor
import app.numbaseball.ui.design.OutTag
import app.numbaseball.ui.design.fadingEdges
import app.numbaseball.ui.design.FitText
import app.numbaseball.ui.design.isLargeFont
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbType

/** Column grid of the log (05 §1): 28 round · flex guess · 44 S · 44 B · 72 opponent; S/B 52 dp when n ≥ 5. */
private class LogColumns(val round: Dp, val hint: Dp, val opponent: Dp, val padding: Dp)

private fun columns(digits: Int, compact: Boolean) =
    if (compact) LogColumns(24.dp, 40.dp, 64.dp, 12.dp) else LogColumns(28.dp, if (digits >= 5) 52.dp else 44.dp, 72.dp, 16.dp)

/** Log header 28 dp: `#` · 내 추측 · 판정 (centred over S and B) · 상대, `labelSmall` `text3`, 1 dp `line` top. */
@Composable
fun PitchLogHeader(digits: Int, showOpponent: Boolean = true, onOpponentLog: (() -> Unit)? = null) {
    val c = columns(digits, compact = false)
    Column {
        Hairline()
        // Grows with large text instead of clipping the header labels (D2-01).
        Row(Modifier.fillMaxWidth().heightIn(min = NbSize.historyHeader).padding(horizontal = c.padding), verticalAlignment = Alignment.CenterVertically) {
            // One line each; long translations at large text step down to half size rather than clip (DEF-061).
            Text("#", style = NbType.labelSmall, color = NbColors.text3, maxLines = 1, softWrap = false, fitMinScale = 0.5f, modifier = Modifier.width(c.round))
            Text(stringResource(R.string.col_guess), style = NbType.labelSmall, color = NbColors.text3, maxLines = 1, softWrap = false, fitMinScale = 0.5f, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.col_hint), style = NbType.labelSmall, color = NbColors.text3, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, fitMinScale = 0.5f, modifier = Modifier.width(c.hint * 2))
            if (showOpponent) {
                Text(stringResource(R.string.col_opp), style = NbType.labelSmall, color = NbColors.text3, textAlign = TextAlign.End, maxLines = 1, softWrap = false, fitMinScale = 0.5f, modifier = Modifier.width(c.opponent))
            } else if (onOpponentLog != null) {
                // The hidden opponent column's header becomes the "상대 기록" button (ADAPTIVE §2.2, D2-43).
                app.numbaseball.ui.design.NbButton(stringResource(R.string.opp_log_title), onOpponentLog, kind = app.numbaseball.ui.design.ButtonKind.TEXT, modifier = Modifier.widthIn(max = 128.dp))
            }
        }
    }
}

/**
 * The pitch log (DESIGN_SYSTEM 12.7): newest first, latest row 64 dp on `surface1` with lamps under the counts, older rows
 * 44 dp, 1 dp `line` tops, 16 dp bottom fade while more rows continue below. [reveal] animates the latest row.
 */
@Composable
fun PitchLog(
    digits: Int,
    myRounds: List<RoundEntry>,
    opponentRounds: List<RoundEntry>,
    reveal: RevealProgress,
    compact: Boolean,
    modifier: Modifier = Modifier,
    showOpponent: Boolean = true,
) {
    val c = columns(digits, compact)
    val rows = myRounds.asReversed()
    val listState = rememberLazyListState()
    val canScroll by remember { derivedStateOf { listState.canScrollForward } }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize().fadingEdges(bottom = 16.dp, enabled = canScroll)) {
        itemsIndexed(rows, key = { _, r -> r.round }) { index, entry ->
            val opponentEntry = opponentRounds.firstOrNull { it.round == entry.round }
            LogRow(entry, opponentEntry, digits, latest = index == 0, compact = compact, columns = c, reveal = if (index == 0) reveal else RevealProgress.Done, showOpponent = showOpponent)
        }
    }
}

@Composable
private fun LogRow(
    entry: RoundEntry,
    opponentEntry: RoundEntry?,
    digits: Int,
    latest: Boolean,
    compact: Boolean,
    columns: LogColumns,
    reveal: RevealProgress,
    showOpponent: Boolean = true,
) {
    val large = isLargeFont()
    val height = when {
        compact && latest -> 48.dp
        compact -> 40.dp
        large && latest -> 72.dp // ADAPTIVE.md §2
        large -> 52.dp
        latest -> NbSize.historyRowLatest
        else -> NbSize.historyRow
    }
    val spoken = stringResource(
        R.string.log_row_spoken,
        entry.round,
        entry.guess?.toList()?.joinToString(" ") ?: "–",
        entry.strikes,
        entry.balls,
    )
    Column(Modifier.fillMaxWidth().background(if (latest) NbColors.surface1 else NbColors.bg).semantics(mergeDescendants = true) { contentDescription = spoken }) {
        // Row heights include the 1 dp top line (prototype `.log-row`, border-box).
        Hairline()
        // Minimum heights: the row grows rather than clip (DESIGN_SYSTEM 11.3).
        Row(Modifier.fillMaxWidth().heightIn(min = height - 1.dp).padding(horizontal = columns.padding), verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.round.toString(),
                style = NbType.numLabel,
                color = if (latest) NbColors.chalk else NbColors.text3,
                modifier = Modifier.width(columns.round),
            )
            // The guess never truncates (D2-01): it takes the flexible column and steps its size down if it must.
            FitText(
                entry.guess ?: "—",
                // Compact rows centre the digits in the line box like the normal rows (D2-46).
                style = if (latest && !compact) {
                    NbType.numSlotSm.copy(letterSpacing = 0.12.em)
                } else if (compact) {
                    NbType.numLog.copy(lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None))
                } else {
                    NbType.numLog
                },
                color = if (entry.guess == null) NbColors.text3 else NbColors.chalk,
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
            )
            Box(Modifier.clearAndSetSemantics {}) {
                if (entry.guess == null) {
                    Box(Modifier.width(columns.hint * 2), contentAlignment = Alignment.Center) {
                        Text("—", style = NbType.numHint, color = NbColors.text3)
                    }
                } else {
                    HintColumns(
                        strikes = entry.strikes,
                        balls = entry.balls,
                        column = columns.hint,
                        digits = if (latest && !compact) digits else null,
                        strikeIgnites = reveal.strikes,
                        ballIgnites = reveal.balls,
                        outIgnite = reveal.out,
                        textAlpha = reveal.strikeText to reveal.ballText,
                    )
                }
            }
            if (showOpponent) {
                Box(Modifier.width(columns.opponent).clearAndSetSemantics {}, contentAlignment = Alignment.CenterEnd) {
                    if (opponentEntry != null) OpponentHint(opponentEntry)
                }
            }
        }
    }
}

/** Opponent column: only the hint they got on my number, `numTag` `text2` (zero counts `textDisabled`). */
@Composable
private fun OpponentHint(entry: RoundEntry) {
    if (entry.guess == null) {
        Text("—", style = NbType.numTag, color = NbColors.text3)
        return
    }
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = if (entry.strikes == 0) NbColors.textDisabled else NbColors.text2)) { append("${entry.strikes}S") }
        append(" ")
        withStyle(SpanStyle(color = if (entry.balls == 0) NbColors.textDisabled else NbColors.text2)) { append("${entry.balls}B") }
    }
    // `numTag` 15 sp like the prototype's opponent column (D2-45).
    Text(text, style = NbType.numTag, maxLines = 1, softWrap = false)
}

/** Empty log during secret entry (DESIGN_SYSTEM 12.7): "판정 읽는 법" + three 44 dp legend rows. */
@Composable
fun HintLegend(compact: Boolean, modifier: Modifier = Modifier, scrollable: Boolean = true) {
    val pad = if (compact) 12.dp else 16.dp
    val rowHeight = if (compact) 40.dp else 44.dp
    // Scrolls when large text makes the rows taller than the region (D2-25).
    val scroll = androidx.compose.foundation.rememberScrollState()
    Column(modifier.fillMaxWidth().then(if (scrollable) Modifier.fadingEdges(bottom = 16.dp, enabled = scroll.canScrollForward).verticalScroll(scroll) else Modifier)) {
        if (!compact) {
            Hairline()
            Box(Modifier.fillMaxWidth().heightIn(min = NbSize.historyHeader).padding(horizontal = pad), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.legend_title), style = NbType.labelSmall, color = NbColors.text3)
            }
        }
        LegendRow(pad, rowHeight, { LegendCount(LampColor.STRIKE, 'S') }, stringResource(R.string.legend_strike_name), stringResource(R.string.legend_strike))
        LegendRow(pad, rowHeight, { LegendCount(LampColor.BALL, 'B') }, stringResource(R.string.legend_ball_name), stringResource(R.string.legend_ball))
        LegendRow(pad, rowHeight, { OutTag() }, stringResource(R.string.legend_out_name), stringResource(R.string.legend_out))
    }
}

@Composable
private fun LegendCount(color: LampColor, letter: Char) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lamp(color, lit = true, size = NbSize.lampM)
        HSpacer(6.dp)
        HintCountText(1, letter, color.lit, NbType.numHint)
    }
}

@Composable
private fun LegendRow(padding: Dp, height: Dp, lead: @Composable () -> Unit, name: String, line: String) {
    // 44 dp including its 1 dp top line (prototype `.lg-row`, border-box).
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        // Wraps to more lines at large text instead of truncating (D2-25).
        Row(Modifier.fillMaxWidth().heightIn(min = height - 1.dp).padding(horizontal = padding, vertical = if (isLargeFont()) 6.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (isLargeFont()) 64.dp else 56.dp)) { lead() }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = NbType.bodyStrong.fontWeight, color = NbColors.chalk)) { append(name) }
                    append(" · ")
                    append(line)
                },
                style = NbType.caption,
                color = NbColors.text2,
            )
        }
    }
}

/** Progress of The Count on the latest row: per-lamp ignite 0..1 and the count text alphas (DESIGN_SYSTEM 8.3 B). */
data class RevealProgress(
    val strikes: List<Float>? = null,
    val balls: List<Float>? = null,
    val out: Float = 1f,
    val strikeText: Float = 1f,
    val ballText: Float = 1f,
) {
    companion object {
        val Done = RevealProgress()
    }
}
