package app.numbaseball.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.numbaseball.R
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.ui.design.ChipSize
import app.numbaseball.ui.design.FitText
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.HintChips
import app.numbaseball.ui.design.LegendChip
import app.numbaseball.ui.design.Text
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.fadingEdges
import app.numbaseball.ui.design.hintText
import app.numbaseball.ui.design.isLargeFont
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbType

/** Log grid (DESIGN_SYSTEM 12.8): round 22 · guess flex · chips auto · opponent hint ≥ 44, 8 dp gaps; 16 dp side padding (12 compact). */
private class LogColumns(val round: Dp, val opponent: Dp, val margin: Dp, val chips: ChipSize)

private fun columns(digits: Int, compact: Boolean) = LogColumns(
    round = NbSize.roundColW,
    opponent = NbSize.oppColMinW,
    margin = if (compact) 12.dp else 16.dp,
    chips = when {
        compact -> ChipSize.Compact
        digits >= 5 -> ChipSize.Wide
        else -> ChipSize.Normal
    },
)

/**
 * The pitch log (DESIGN_SYSTEM 12.8): no header, newest first. Latest row 64 dp `surface2` radius 12 (48 compact), older
 * rows 46 dp (40 compact) with a 1 dp `line` bottom border; 20 dp bottom fade while more rows continue. [reveal]
 * animates the chips of the latest row.
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
    /** The latest row flashes `strikeTint` (solve, DESIGN_SYSTEM 8.3 C). */
    solvedFlash: Float = 0f,
    /** Tap on an opponent hint opens the opponent's full log (05 §2.8 / 12.8). */
    onOpponentLog: (() -> Unit)? = null,
    /** Tutorial step 3: 2 dp `accent` ring on the latest row. */
    highlightLatest: Boolean = false,
) {
    val c = columns(digits, compact)
    val rows = myRounds.asReversed()
    val listState = rememberLazyListState()
    val canScroll by remember { derivedStateOf { listState.canScrollForward } }
    // A new round is inserted at the top; LazyColumn would keep the previous first row anchored and leave the new
    // result just above the visible area (owner feedback 2026-11-26). Bring the newest row into view every round.
    val newestRound = rows.firstOrNull()?.round
    androidx.compose.runtime.LaunchedEffect(newestRound) { if (newestRound != null) listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().fadingEdges(bottom = NbSize.fadeEdge, enabled = canScroll),
        contentPadding = PaddingValues(start = c.margin, end = c.margin, top = if (compact) 4.dp else 8.dp),
    ) {
        itemsIndexed(rows, key = { _, r -> r.round }) { index, entry ->
            val opponentEntry = opponentRounds.firstOrNull { it.round == entry.round }
            LogRow(
                entry, opponentEntry, digits,
                latest = index == 0,
                compact = compact,
                columns = c,
                reveal = if (index == 0) reveal else RevealProgress.Done,
                showOpponent = showOpponent,
                flash = if (index == 0) solvedFlash else 0f,
                highlight = index == 0 && highlightLatest,
                onOpponentLog = onOpponentLog,
            )
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
    flash: Float = 0f,
    highlight: Boolean = false,
    onOpponentLog: (() -> Unit)? = null,
) {
    val large = isLargeFont() && !compact
    val height = when {
        compact && latest -> 48.dp
        compact -> 40.dp
        large && latest -> 72.dp // ADAPTIVE.md §2.2
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
    ) + (opponentEntry?.takeIf { it.guess != null }?.let { " " + stringResource(R.string.log_row_opp_spoken, it.strikes, it.balls) } ?: "")
    val shape = RoundedCornerShape(NbRadius.m)
    val fill = if (latest) androidx.compose.ui.graphics.lerp(NbColors.surface2, NbColors.strikeTint, flash.coerceIn(0f, 1f)) else NbColors.bg
    val opponent: (@Composable () -> Unit)? = if (showOpponent && opponentEntry != null) ({
        OpponentHint(opponentEntry, label = latest && !compact && !large, onClick = onOpponentLog)
    }) else null
    val guessStyle = when {
        latest && !compact -> NbType.numSlotSm
        else -> NbType.numLog
    }.let { if (digits >= 5) it.copy(letterSpacing = 0.08.em) else it }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (latest) 4.dp else 0.dp)
            .then(if (latest) Modifier.background(fill, shape) else Modifier.drawBehind { drawLine(NbColors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) })
            .then(if (highlight) Modifier.border(2.dp, NbColors.accent, shape) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        // Minimum heights: the row grows rather than clip (DESIGN_SYSTEM 11.3).
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .padding(
                    start = 8.dp,
                    end = if (latest && !compact) 10.dp else 8.dp,
                    top = if (latest && !compact) 6.dp else 4.dp,
                    bottom = if (latest && !compact) 6.dp else 4.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.round.toString(),
                style = NbType.numLabel,
                color = NbColors.text3,
                maxLines = 1,
                softWrap = false,
                autoFit = false,
                modifier = Modifier.width(columns.round).clearAndSetSemantics {},
            )
            HSpacer(8.dp)
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                // The guess never truncates (D2-01): it takes the flexible column and steps its size down if it must.
                FitText(entry.guess ?: "—", style = guessStyle, color = if (entry.guess == null) NbColors.text3 else NbColors.ink)
                // Large text (ADAPTIVE §2.2): the opponent hint moves to a second line under the guess.
                if (large && opponent != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (latest) {
                            Text(stringResource(R.string.col_opp), style = NbType.caption, color = NbColors.text3, maxLines = 1)
                            HSpacer(4.dp)
                        }
                        opponent()
                    }
                }
            }
            HSpacer(8.dp)
            if (entry.guess == null) {
                Box(Modifier.widthIn(min = columns.chips.outMinWidth), contentAlignment = Alignment.Center) {
                    Text("—", style = NbType.numHint, color = NbColors.text3)
                }
            } else {
                HintChips(
                    strikes = entry.strikes,
                    balls = entry.balls,
                    size = columns.chips,
                    strikeSteps = reveal.strikes,
                    ballSteps = reveal.balls,
                    zeroAlpha = reveal.zero,
                    outIgnite = reveal.out,
                )
            }
            if (opponent != null && !large) {
                HSpacer(8.dp)
                Box(Modifier.widthIn(min = columns.opponent).clearAndSetSemantics {}, contentAlignment = Alignment.CenterEnd) { opponent() }
            } else if (showOpponent && !large) {
                HSpacer(8.dp)
                Box(Modifier.width(columns.opponent))
            }
        }
    }
}

/** Opponent hint: plain `numLabel` `text3` "1S 3B" / "OUT"; the latest row adds a small "상대" label above (12.8). */
@Composable
private fun OpponentHint(entry: RoundEntry, label: Boolean, onClick: (() -> Unit)?) {
    val text = if (entry.guess == null) "—" else hintText(entry.strikes, entry.balls)
    Column(
        horizontalAlignment = Alignment.End,
        modifier = if (onClick != null) Modifier.clickable(onClickLabel = stringResource(R.string.menu_opp_log), onClick = onClick) else Modifier,
    ) {
        if (label) {
            Text(stringResource(R.string.col_opp), style = NbType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp), color = NbColors.text3, maxLines = 1, softWrap = false)
        }
        Text(text, style = NbType.numLabel, color = NbColors.text3, maxLines = 1, softWrap = false, autoFit = false)
    }
}

/**
 * Empty log during secret entry: the legend box (DESIGN_SYSTEM 12.8): `surface2`, radius 12, 16 dp padding, "판정 읽는 법"
 * then three rows (12 dp gaps, 96 dp key column): `1S` · `1B` · `OUT` chips with their explanation.
 */
@Composable
fun HintLegend(compact: Boolean, modifier: Modifier = Modifier, scrollable: Boolean = true) {
    val margin = if (compact) 12.dp else 16.dp
    val pad = if (compact) 12.dp else 16.dp
    // Scrolls when large text makes the rows taller than the region (D2-25).
    val scroll = androidx.compose.foundation.rememberScrollState()
    Column(
        modifier
            .fillMaxWidth()
            .then(if (scrollable) Modifier.fadingEdges(bottom = NbSize.fadeEdge, enabled = scroll.canScrollForward).verticalScroll(scroll) else Modifier)
            .padding(start = margin, end = margin, top = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth().background(NbColors.surface2, RoundedCornerShape(NbRadius.m)).padding(pad)) {
            if (!compact) {
                Text(stringResource(R.string.legend_title), style = NbType.labelSmall, color = NbColors.text3)
                VSpacer(12.dp)
            }
            LegendRow(compact, 'S', stringResource(R.string.legend_strike_name), stringResource(R.string.legend_strike))
            VSpacer(if (compact) 4.dp else 12.dp)
            LegendRow(compact, 'B', stringResource(R.string.legend_ball_name), stringResource(R.string.legend_ball))
            VSpacer(if (compact) 4.dp else 12.dp)
            LegendRow(compact, null, stringResource(R.string.legend_out_name), stringResource(R.string.legend_out))
        }
    }
}

@Composable
private fun LegendRow(compact: Boolean, letter: Char?, name: String, line: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 40.dp else if (isLargeFont()) 44.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(if (compact) 104.dp else 96.dp).clearAndSetSemantics {}) { LegendChip(letter) }
        HSpacer(12.dp)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = NbType.bodyStrong.fontWeight, color = NbColors.ink)) { append(name) }
                append(" · ")
                append(line)
            },
            style = NbType.caption,
            color = NbColors.text2,
        )
    }
}

/** Progress of The Count on the latest row: per-lamp ignite 0..1 and the count text alphas (DESIGN_SYSTEM 8.3 B). */
data class RevealProgress(
    val strikes: List<Float>? = null,
    val balls: List<Float>? = null,
    val out: Float = 1f,
    val strikeText: Float = 1f,
    val ballText: Float = 1f,
    /** A zero count fades in with the first chip step (8.3 B). */
    val zero: Float = 1f,
) {
    companion object {
        val Done = RevealProgress()
    }
}
