package app.numbaseball.ui.results

import androidx.compose.ui.unit.sp
import app.numbaseball.audio.ScreenMusic
import app.numbaseball.audio.MusicTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.numbaseball.R
import app.numbaseball.ads.AdBanner
import app.numbaseball.audio.HapticCue
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.LocalGameHaptics
import app.numbaseball.audio.SoundCue
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.exactLineHeight
import app.numbaseball.ui.design.DigitSlots
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.Lamp
import app.numbaseball.ui.design.LampColor
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.fadingEdges
import app.numbaseball.ui.design.fadingStart
import app.numbaseball.ui.design.formatClock
import app.numbaseball.ui.design.isCompactHeight
import app.numbaseball.ui.design.isLargeFont
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchSummary
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbMotion
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.delay

/**
 * Duel result (06 §1–3): verdict hero, the revealed numbers, the per-round strike line score, then the next game. The
 * banner shows unless this screen was reached through an interstitial ([showBanner] false) and never after practice.
 * [onRemoveAds] non-null shows the quiet "광고 제거" text button in the top bar (06 §1; hidden in small windows).
 */
@Composable
fun ResultsScreen(
    summary: MatchSummary,
    showBanner: Boolean,
    onRemoveAds: (() -> Unit)?,
    onRematch: (() -> Unit)?,
    onHome: () -> Unit,
    onBackToRoom: (() -> Unit)?,
    onReport: (() -> Unit)?,
) {
    val audio = LocalGameAudio.current
    val haptics = LocalGameHaptics.current
    ScreenMusic(MusicTrack.MENU, audio)
    // Buttons accept input no earlier than 1000 ms after the result appears (06 §3).
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        when (summary.outcome) {
            Outcome.WIN -> { audio.play(SoundCue.WIN); haptics.perform(HapticCue.WIN) }
            Outcome.LOSE -> { audio.play(SoundCue.LOSE); haptics.perform(HapticCue.LOSE) }
            Outcome.DRAW -> { audio.play(SoundCue.DRAW); haptics.perform(HapticCue.DRAW) }
        }
        delay(NbMotion.resultButtonMin)
        ready = true
    }
    val guard: (() -> Unit) -> () -> Unit = { action -> { if (ready) action() } }
    val compact = isCompactHeight()
    val banner = showBanner && summary.mode != MatchMode.PRACTICE
    NbScreen(
        wash = if (summary.outcome == Outcome.WIN) NbColors.strikeTint else null,
        statusBarPadding = false,
        topBar = {
            TopBar(
                title = null,
                navIcon = R.drawable.ic_close,
                navDescription = stringResource(R.string.home),
                onNav = guard(onHome),
                actions = {
                    if (onRemoveAds != null && !compact) RemoveAdsLink(onRemoveAds)
                    if (onReport != null) IconButton48(R.drawable.ic_flag, stringResource(R.string.report_player), onReport, tint = NbColors.text2)
                },
            )
        },
        bottomBar = { if (banner) AdBanner() },
    ) {
        val scroll = rememberScrollState()
        val (primaryLabel, primary, secondaryLabel, secondary) = when {
            summary.mode == MatchMode.ROOM && onBackToRoom != null -> Actions(R.string.back_to_room, onBackToRoom, R.string.home, onHome)
            summary.mode == MatchMode.PRACTICE && onBackToRoom != null -> Actions(R.string.rematch, onRematch ?: onBackToRoom, R.string.back_to_room, onBackToRoom)
            onRematch != null -> Actions(R.string.rematch, onRematch, R.string.home, onHome)
            else -> Actions(R.string.home, onHome, null, null)
        }
        if (compact) {
            // Small window (ADAPTIVE.md §1): headline row 56 dp, the opponent's number as 40 × 44 plates, buttons side by
            // side (48 dp); no line score, no banner.
            Column(
                Modifier.weight(1f).fillMaxWidth().fadingEdges(bottom = 16.dp, enabled = scroll.canScrollForward).verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val headlineText = stringResource(headlineRes(summary.outcome))
                val sub = resultSubline(summary)
                // Only the headline row is drawn; TalkBack still reads the sub line (ADAPTIVE.md §1, D2-36).
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = true) { heading(); contentDescription = "$headlineText. $sub" },
                    contentAlignment = Alignment.Center,
                ) {
                    // ADAPTIVE §1: 12 dp lamps + headline `title`, one line.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { HeroLamps(summary) }
                        HSpacer(12.dp)
                        Text(headlineText, style = NbType.title, color = NbColors.chalk, maxLines = 1)
                    }
                }
                val theirs = stringResource(R.string.opp_secret)
                DigitSlots(
                    digits = summary.digits,
                    value = summary.opponentSecret,
                    hit = summary.outcome == Outcome.WIN,
                    showActive = false,
                    plateWidth = 40.dp,
                    plateHeight = 44.dp,
                    compact = true,
                    contentDescription = theirs + " " + summary.opponentSecret.toList().joinToString(" "),
                )
                VSpacer(12.dp)
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (secondaryLabel != null && secondary != null) {
                    NbButton(stringResource(secondaryLabel), guard(secondary), kind = ButtonKind.SECONDARY, heightOverride = 48.dp, modifier = Modifier.weight(1f).testTag("resultSecondary"))
                }
                NbButton(stringResource(primaryLabel), guard(primary), heightOverride = 48.dp, modifier = Modifier.weight(1f).testTag("resultPrimary"))
            }
            return@NbScreen
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().fadingEdges(bottom = 16.dp, enabled = scroll.canScrollForward).verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Hero(summary, compact)
            Reveal(summary, compact)
            VSpacer(28.dp)
            LineScore(summary, isLargeFont(), Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.heightIn(min = 16.dp).weight(1f))
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
            NbButton(stringResource(primaryLabel), guard(primary), modifier = Modifier.fillMaxWidth().testTag("resultPrimary"))
            if (secondaryLabel != null && secondary != null) {
                VSpacer(8.dp)
                NbButton(stringResource(secondaryLabel), guard(secondary), kind = ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth().testTag("resultSecondary"))
            }
        }
    }
}

/**
 * The quiet "광고 제거" / "Remove ads" text button of the result top bars (06 §1, DESIGN_SYSTEM 12.13 Text): `labelSmall`
 * `text2`, 36 dp visual, 48 dp target, 8 dp side padding. It never moves, blinks or carries a badge.
 */
@Composable
fun RemoveAdsLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.remove_ads)
    val spoken = stringResource(R.string.remove_ads_link_a11y)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = 8.dp)
            .testTag(REMOVE_ADS_LINK_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NbType.labelSmall, color = NbColors.text2, maxLines = 1, softWrap = false)
    }
}

/** Test tag of the result top-bar link (QA hook: never on match, waiting, lobby or chat screens). */
const val REMOVE_ADS_LINK_TAG = "removeAdsLink"

private data class Actions(val primaryLabel: Int, val primary: () -> Unit, val secondaryLabel: Int?, val secondary: (() -> Unit)?)

private fun headlineRes(outcome: Outcome) = when (outcome) {
    Outcome.WIN -> R.string.res_win
    Outcome.LOSE -> R.string.res_lose
    Outcome.DRAW -> R.string.res_draw
}

@Composable
private fun Hero(summary: MatchSummary, compact: Boolean) {
    val headline = stringResource(headlineRes(summary.outcome))
    val sub = resultSubline(summary)
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (compact) 12.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { HeroLamps(summary) }
        }
        Text(
            headline,
            style = if (compact) NbType.headline else NbType.display,
            color = NbColors.chalk,
            textAlign = TextAlign.Center,
            modifier = Modifier.exactLineHeight(if (compact) NbType.headline else NbType.display).semantics { heading() },
        )
        Text(sub, style = NbType.body, color = NbColors.text2, textAlign = TextAlign.Center)
    }
}

/** n lamps 12 dp (win: all lit strike; lose: all unlit; draw: one chalk lamp). */
@Composable
private fun HeroLamps(summary: MatchSummary) {
    when (summary.outcome) {
        Outcome.WIN -> repeat(summary.digits) { Lamp(LampColor.STRIKE, lit = true, size = 12.dp) }
        Outcome.LOSE -> repeat(summary.digits) { Lamp(LampColor.STRIKE, lit = false, size = 12.dp) }
        Outcome.DRAW -> Lamp(LampColor.CHALK, lit = true, size = 12.dp)
    }
}

@Composable
private fun resultSubline(summary: MatchSummary): String = when (summary.reason) {
    EndReason.SOLVED -> stringResource(if (summary.outcome == Outcome.WIN) R.string.res_win_sub else R.string.res_lose_sub, summary.rounds)
    EndReason.BOTH_SOLVED -> stringResource(R.string.res_draw_sub, summary.rounds)
    EndReason.FORFEIT -> stringResource(if (summary.outcome == Outcome.LOSE) R.string.res_forfeit_me else R.string.res_forfeit_opp)
    EndReason.TIMEOUT, EndReason.BOTH_TIMEOUT -> stringResource(if (summary.outcome == Outcome.WIN) R.string.res_timeout_opp else R.string.res_timeout_me)
    EndReason.DISCONNECT -> stringResource(if (summary.outcome == Outcome.WIN) R.string.res_disconnect_opp else R.string.res_disconnect_me)
    EndReason.CANCELLED -> stringResource(R.string.res_cancelled)
}

@Composable
private fun Reveal(summary: MatchSummary, compact: Boolean) {
    val theirs = stringResource(R.string.opp_secret)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(theirs, style = NbType.caption, color = NbColors.text3)
        VSpacer(8.dp)
        DigitSlots(
            digits = summary.digits,
            value = summary.opponentSecret,
            hit = summary.outcome == Outcome.WIN,
            showActive = false,
            plateWidth = 52.dp,
            plateHeight = if (compact) 48.dp else 60.dp,
            contentDescription = theirs + " " + summary.opponentSecret.toList().joinToString(" "),
        )
        if (summary.mySecret.isNotEmpty()) {
            VSpacer(16.dp)
            Text(stringResource(R.string.my_secret), style = NbType.caption, color = NbColors.text3)
            VSpacer(8.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = summary.mySecret.toList().joinToString(" ")
                },
            ) {
                summary.mySecret.forEach { ch ->
                    Box(Modifier.size(40.dp, 44.dp).background(NbColors.surface1, RoundedCornerShape(NbRadius.m)), contentAlignment = Alignment.Center) {
                        Text(ch.toString(), style = NbType.numLog.copy(letterSpacing = NbType.numHint.letterSpacing), color = NbColors.text2)
                    }
                }
            }
        }
    }
}

/**
 * Line score (06 §1): 1 dp line top, 12 dp padding, header (title + match duration), label column 80 dp, one column per
 * round with the strike count, "Best" 56 dp. More than 9 rounds scroll horizontally, starting at the end. At large
 * text (06 §5, D2-02) only the last 3 rounds and "Best" show; the full table is behind "회별 기록 보기".
 */
@Composable
private fun LineScore(summary: MatchSummary, large: Boolean, modifier: Modifier = Modifier) {
    val rounds = maxOf(summary.myStrikes.size, summary.opponentStrikes.size)
    if (rounds == 0) return
    val opponentLabel = if (summary.mode == MatchMode.COMPUTER || summary.mode == MatchMode.PRACTICE) {
        stringResource(R.string.cpu_short)
    } else {
        summary.opponentName
    }
    val myBest = summary.myStrikes.filterNotNull().maxOrNull() ?: 0
    val theirBest = summary.opponentStrikes.filterNotNull().maxOrNull() ?: 0
    var showAll by remember { mutableStateOf(false) }
    val first = if (large && !showAll) (rounds - 3).coerceAtLeast(0) else 0
    val shown = rounds - first
    val header = if (large) 44.dp else 32.dp
    val row = if (large) 48.dp else ROW_HEIGHT
    Column(modifier.fillMaxWidth()) {
        Hairline()
        // 10 dp: the header row is 20 dp tall (numTag), so the 16 dp timer icon and the label sit where the shot has them (D2-20).
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.line_score), style = NbType.labelSmall, color = NbColors.text3, modifier = Modifier.weight(1f).semantics { heading() })
            summary.durationSeconds?.let {
                NbIcon(R.drawable.ic_timer, null, NbColors.text3, 16.dp)
                HSpacer(4.dp)
                Text(formatClock(it), style = NbType.numTag, color = NbColors.text3)
            }
        }
        VSpacer(8.dp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val label = if (large) 96.dp else 80.dp
            val best = if (large) 72.dp else 56.dp
            val available = maxWidth - label - best
            val scrolls = shown > 9
            val column = if (scrolls) 32.dp else if (large) minOf(48.dp, available / shown) else minOf(32.dp, available / shown)
            val scroll = rememberScrollState()
            LaunchedEffect(shown) { scroll.scrollTo(scroll.maxValue) }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(label)) {
                    Box(Modifier.height(header))
                    LabelCell(stringResource(R.string.me_short), row)
                    LabelCell(opponentLabel, row)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .then(if (scrolls) Modifier.fadingStart() else Modifier),
                ) {
                    Column(if (scrolls) Modifier.horizontalScroll(scroll) else Modifier) {
                        Row(Modifier.height(header), verticalAlignment = Alignment.CenterVertically) {
                            (first until rounds).forEach { i ->
                                Text((i + 1).toString(), style = NbType.numLabel, color = NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.width(column))
                            }
                        }
                        StrikeRow(summary.myStrikes, first, rounds, column, row, summary.digits, stringResource(R.string.me_short))
                        StrikeRow(summary.opponentStrikes, first, rounds, column, row, summary.digits, opponentLabel)
                    }
                }
                Column(Modifier.width(best), horizontalAlignment = Alignment.End) {
                    Box(Modifier.height(header), contentAlignment = Alignment.CenterEnd) {
                        Text(stringResource(R.string.best), style = NbType.labelSmall, color = NbColors.text3, maxLines = 1, softWrap = false)
                    }
                    BestCell(myBest, summary.digits, row)
                    BestCell(theirBest, summary.digits, row)
                }
            }
            // Row separators span the whole table.
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.height(header))
                Hairline()
                Box(Modifier.height(row))
                Hairline()
            }
        }
        if (large && rounds > 3) {
            NbButton(
                stringResource(if (showAll) R.string.line_score_recent else R.string.line_score_all),
                { showAll = !showAll },
                kind = ButtonKind.TEXT,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private val ROW_HEIGHT = 37.dp

@Composable
private fun LabelCell(text: String, row: Dp) {
    Box(Modifier.height(row + 1.dp).padding(top = 1.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, style = NbType.caption, color = NbColors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StrikeRow(strikes: List<Int?>, first: Int, rounds: Int, column: Dp, row: Dp, digits: Int, who: String) {
    Row(Modifier.height(row + 1.dp).padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        (first until rounds).forEach { i ->
            val value = strikes.getOrNull(i)
            val spoken = stringResource(R.string.line_score_cell, i + 1, who, value ?: 0)
            Text(
                value?.toString() ?: "–",
                style = NbType.numLog.copy(letterSpacing = NbType.numHint.letterSpacing),
                color = when {
                    value == null || value == 0 -> NbColors.textDisabled
                    value == digits -> NbColors.strike
                    else -> NbColors.chalk
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.width(column).clearAndSetSemantics { contentDescription = spoken },
            )
        }
    }
}

@Composable
private fun BestCell(best: Int, digits: Int, row: Dp) {
    Box(Modifier.height(row + 1.dp).padding(top = 1.dp), contentAlignment = Alignment.CenterEnd) {
        Text("${best}S", style = NbType.numHint, color = if (best == digits) NbColors.strike else NbColors.chalk)
    }
}
