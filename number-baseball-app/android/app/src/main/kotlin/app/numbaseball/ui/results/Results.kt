package app.numbaseball.ui.results

import app.numbaseball.audio.ScreenMusic
import app.numbaseball.audio.MusicTrack
import androidx.compose.foundation.background
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
import app.numbaseball.ui.design.IconNote
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
 * banner shows only when no interstitial is due ([adDue] shows the notice instead) and never after practice.
 */
@Composable
fun ResultsScreen(
    summary: MatchSummary,
    adDue: Boolean,
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
    val showBanner = !adDue && summary.mode != MatchMode.PRACTICE
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
                    if (onReport != null) IconButton48(R.drawable.ic_flag, stringResource(R.string.report_player), onReport, tint = NbColors.text2)
                },
            )
        },
        bottomBar = { if (showBanner) AdBanner() },
    ) {
        val scroll = rememberScrollState()
        Column(
            Modifier.weight(1f).fillMaxWidth().fadingEdges(bottom = 16.dp, enabled = scroll.canScrollForward).verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Hero(summary, compact)
            Reveal(summary, compact)
            VSpacer(if (compact) 16.dp else 28.dp)
            LineScore(summary, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.heightIn(min = 16.dp).weight(1f))
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (compact) 12.dp else 24.dp)) {
            if (adDue) {
                Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                    IconNote(R.drawable.ic_info, stringResource(R.string.ad_notice))
                }
            }
            val (primaryLabel, primary, secondaryLabel, secondary) = when {
                summary.mode == MatchMode.ROOM && onBackToRoom != null -> Actions(R.string.back_to_room, onBackToRoom, R.string.home, onHome)
                summary.mode == MatchMode.PRACTICE && onBackToRoom != null -> Actions(R.string.rematch, onRematch ?: onBackToRoom, R.string.back_to_room, onBackToRoom)
                onRematch != null -> Actions(R.string.rematch, onRematch, R.string.home, onHome)
                else -> Actions(R.string.home, onHome, null, null)
            }
            NbButton(stringResource(primaryLabel), guard(primary), modifier = Modifier.fillMaxWidth().testTag("resultPrimary"))
            if (secondaryLabel != null && secondary != null) {
                VSpacer(8.dp)
                NbButton(stringResource(secondaryLabel), guard(secondary), kind = ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth().testTag("resultSecondary"))
            }
        }
    }
}

private data class Actions(val primaryLabel: Int, val primary: () -> Unit, val secondaryLabel: Int?, val secondary: (() -> Unit)?)

@Composable
private fun Hero(summary: MatchSummary, compact: Boolean) {
    val headline = stringResource(
        when (summary.outcome) {
            Outcome.WIN -> R.string.res_win
            Outcome.LOSE -> R.string.res_lose
            Outcome.DRAW -> R.string.res_draw
        },
    )
    val sub = resultSubline(summary)
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (compact) 12.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (summary.outcome) {
                    Outcome.WIN -> repeat(summary.digits) { Lamp(LampColor.STRIKE, lit = true, size = 12.dp) }
                    Outcome.LOSE -> repeat(summary.digits) { Lamp(LampColor.STRIKE, lit = false, size = 12.dp) }
                    Outcome.DRAW -> Lamp(LampColor.CHALK, lit = true, size = 12.dp)
                }
            }
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
 * round with the strike count, "Best" 56 dp. More than 9 rounds scroll horizontally, starting at the end.
 */
@Composable
private fun LineScore(summary: MatchSummary, modifier: Modifier = Modifier) {
    val rounds = maxOf(summary.myStrikes.size, summary.opponentStrikes.size)
    if (rounds == 0) return
    val opponentLabel = if (summary.mode == MatchMode.COMPUTER || summary.mode == MatchMode.PRACTICE) {
        stringResource(R.string.cpu_short)
    } else {
        summary.opponentName
    }
    val myBest = summary.myStrikes.filterNotNull().maxOrNull() ?: 0
    val theirBest = summary.opponentStrikes.filterNotNull().maxOrNull() ?: 0
    Column(modifier.fillMaxWidth()) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.line_score), style = NbType.labelSmall, color = NbColors.text3, modifier = Modifier.weight(1f).semantics { heading() })
            summary.durationSeconds?.let {
                NbIcon(R.drawable.ic_timer, null, NbColors.text3, 16.dp)
                HSpacer(4.dp)
                Text(formatClock(it), style = NbType.numTag, color = NbColors.text3)
            }
        }
        VSpacer(8.dp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val label = 80.dp
            val best = 56.dp
            val available = maxWidth - label - best
            val scrolls = rounds > 9
            val column = if (scrolls) 32.dp else minOf(32.dp, available / rounds)
            val scroll = rememberScrollState()
            LaunchedEffect(rounds) { scroll.scrollTo(scroll.maxValue) }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(label)) {
                    Box(Modifier.height(32.dp))
                    LabelCell(stringResource(R.string.me_short))
                    LabelCell(opponentLabel)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .then(if (scrolls) Modifier.fadingStart() else Modifier),
                ) {
                    Column(if (scrolls) Modifier.horizontalScroll(scroll) else Modifier) {
                        Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                            repeat(rounds) { i ->
                                Text((i + 1).toString(), style = NbType.numLabel, color = NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.width(column))
                            }
                        }
                        StrikeRow(summary.myStrikes, rounds, column, summary.digits, stringResource(R.string.me_short))
                        StrikeRow(summary.opponentStrikes, rounds, column, summary.digits, opponentLabel)
                    }
                }
                Column(Modifier.width(best), horizontalAlignment = Alignment.End) {
                    Box(Modifier.height(32.dp), contentAlignment = Alignment.CenterEnd) {
                        Text(stringResource(R.string.best), style = NbType.labelSmall, color = NbColors.text3)
                    }
                    BestCell(myBest, summary.digits)
                    BestCell(theirBest, summary.digits)
                }
            }
            // Row separators span the whole table.
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.height(32.dp))
                Hairline()
                Box(Modifier.height(ROW_HEIGHT))
                Hairline()
            }
        }
    }
}

private val ROW_HEIGHT = 37.dp

@Composable
private fun LabelCell(text: String) {
    Box(Modifier.height(ROW_HEIGHT + 1.dp).padding(top = 1.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, style = NbType.caption, color = NbColors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StrikeRow(strikes: List<Int?>, rounds: Int, column: Dp, digits: Int, who: String) {
    Row(Modifier.height(ROW_HEIGHT + 1.dp).padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(rounds) { i ->
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
private fun BestCell(best: Int, digits: Int) {
    Box(Modifier.height(ROW_HEIGHT + 1.dp).padding(top = 1.dp), contentAlignment = Alignment.CenterEnd) {
        Text("${best}S", style = NbType.numHint, color = if (best == digits) NbColors.strike else NbColors.chalk)
    }
}
