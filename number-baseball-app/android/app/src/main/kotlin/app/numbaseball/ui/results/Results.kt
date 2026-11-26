package app.numbaseball.ui.results

import androidx.compose.foundation.layout.offset
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
import app.numbaseball.ui.design.LocalReduceMotion
import app.numbaseball.ui.design.SheetBottomSpace
import app.numbaseball.ui.design.SheetFrame
import app.numbaseball.ui.design.SheetTitle
import app.numbaseball.ui.computer.levelLabel
import app.numbaseball.ui.theme.NbFonts
import app.numbaseball.ui.theme.NbLayout
import app.numbaseball.ui.theme.NbSize
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
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
 * Duel result (06 v2): badge, headline, sub line, the opponent's number on `ink` plates, my number, three summary numbers,
 * "회별 기록 보기" (innings sheet), then the pinned actions and the quiet "광고 제거" line, whose 48 dp target ends 24 dp
 * above the banner. The banner shows unless this screen was reached through an interstitial ([showBanner] false) and
 * never after practice. [onRemoveAds] non-null shows the line (hidden in small windows).
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
    /** My online wins after this match ("내 기록 13승"); null = unknown (`–`). */
    myWins: Int? = null,
    initialInnings: Boolean = false,
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
    var innings by rememberSaveable { mutableStateOf(initialInnings) }
    NbScreen(
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
        bottomBar = { if (banner) AdBanner() },
        overlay = {
            if (innings && !compact) {
                BackHandler { innings = false }
                SheetFrame(onDismiss = { innings = false }) { InningsSheet(summary, onClose = { innings = false }) }
            }
        },
    ) {
        val scroll = rememberScrollState()
        val (primaryLabel, primary, secondaryLabel, secondary) = when {
            summary.mode == MatchMode.ROOM && onBackToRoom != null -> Actions(R.string.back_to_room, onBackToRoom, R.string.home, onHome)
            summary.mode == MatchMode.PRACTICE && onBackToRoom != null -> Actions(R.string.rematch, onRematch ?: onBackToRoom, R.string.back_to_room, onBackToRoom)
            onRematch != null -> Actions(R.string.rematch, onRematch, R.string.home, onHome)
            else -> Actions(R.string.home, onHome, null, null)
        }
        val summaryA11y = summarySpoken(summary, myWins)
        if (compact) {
            // Small window (ADAPTIVE.md §1): headline row 56 dp (32 dp badge + `title`), the opponent's number as 40 × 44
            // plates, buttons side by side (48 dp); no sub line, summary, innings link, 광고 제거 line or banner.
            Column(
                Modifier.weight(1f).fillMaxWidth().fadingEdges(enabled = scroll.canScrollForward).verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val headlineText = stringResource(headlineRes(summary.outcome))
                val sub = resultSubline(summary)
                // Only the headline row is drawn; TalkBack still reads the sub line and the summary (ADAPTIVE.md §1, D2-36).
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = true) { heading(); contentDescription = "$headlineText. $sub. $summaryA11y" },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ResultBadge(summary.outcome, 32.dp)
                        HSpacer(12.dp)
                        Text(headlineText, style = NbType.title, color = NbColors.ink, maxLines = 1)
                    }
                }
                val theirs = stringResource(R.string.opp_secret)
                DigitSlots(
                    digits = summary.digits,
                    value = summary.opponentSecret,
                    plate = true,
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
            Modifier.weight(1f).fillMaxWidth().fadingEdges(enabled = scroll.canScrollForward).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VSpacer(4.dp)
            ResultBadge(summary.outcome, 64.dp)
            VSpacer(12.dp)
            Text(
                stringResource(headlineRes(summary.outcome)),
                style = NbType.display,
                color = NbColors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.exactLineHeight(NbType.display).semantics { heading() },
            )
            VSpacer(4.dp)
            Text(resultSubline(summary), style = NbType.body, color = NbColors.text2, textAlign = TextAlign.Center)
            VSpacer(16.dp)
            Reveal(summary)
            VSpacer(16.dp)
            ResultSummary(summary, myWins, summaryA11y)
            if (maxOf(summary.myStrikes.size, summary.opponentStrikes.size) > 0) {
                NbButton(stringResource(R.string.innings_link), { innings = true }, kind = ButtonKind.LINK)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            NbButton(stringResource(primaryLabel), guard(primary), modifier = Modifier.fillMaxWidth().testTag("resultPrimary"))
            if (secondaryLabel != null && secondary != null) {
                VSpacer(8.dp)
                NbButton(stringResource(secondaryLabel), guard(secondary), kind = ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth().testTag("resultSecondary"))
            }
            RemoveAdsLine(onRemoveAds, banner)
        }
    }
}

/**
 * The quiet "광고 제거" / "Remove ads" line above the banner (06 §1.2, DESIGN_SYSTEM 13): `caption` `text3`, centred, 48 dp
 * target; 24 dp clear space below its target when the banner is present (0 dp without). Hidden (null) leaves only the
 * clear space. It never moves, blinks or carries a badge, icon or colour.
 */
@Composable
fun RemoveAdsLine(onClick: (() -> Unit)?, banner: Boolean) {
    if (onClick != null && !isCompactHeight()) {
        val label = stringResource(R.string.remove_ads)
        val spoken = stringResource(R.string.remove_ads_link_a11y)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .testTag(REMOVE_ADS_LINK_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = NbType.caption, color = NbColors.text3, textAlign = TextAlign.Center, maxLines = 2)
        }
    } else if (!banner) {
        VSpacer(16.dp)
    }
    if (banner) VSpacer(NbLayout.bannerClearSpace)
}

/** Test tag of the result 광고 제거 line (QA hook: never on match, waiting, lobby or chat screens). */
const val REMOVE_ADS_LINK_TAG = "removeAdsLink"

private data class Actions(val primaryLabel: Int, val primary: () -> Unit, val secondaryLabel: Int?, val secondary: (() -> Unit)?)

private fun headlineRes(outcome: Outcome) = when (outcome) {
    Outcome.WIN -> R.string.res_win
    Outcome.LOSE -> R.string.res_lose
    Outcome.DRAW -> R.string.res_draw
}

/**
 * Result badge (06 §1): circle; win `accentTint` + `check` (`accent`); lose / draw `surface2` + `sports_baseball`
 * (`text2`). Icon 32 dp at 64 dp, 20 dp at 32 dp. Scales in with `slot_pop`. Decorative.
 */
@Composable
fun ResultBadge(outcome: Outcome, size: Dp) {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(if (reduce) 1f else 0.8f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 800f)) }
    val win = outcome == Outcome.WIN
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(if (win) NbColors.accentTint else NbColors.surface2, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        NbIcon(if (win) R.drawable.ic_check else R.drawable.ic_sports_baseball, null, if (win) NbColors.accent else NbColors.text2, if (size >= 64.dp) 32.dp else 20.dp)
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

/** "상대 숫자" + n `ink` answer plates 60 × 68 dp (10 dp gap), then "내 숫자 0472" on one line (06 §1). */
@Composable
private fun Reveal(summary: MatchSummary) {
    val theirs = stringResource(R.string.opp_secret)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(theirs, style = NbType.caption, color = NbColors.text3)
        VSpacer(8.dp)
        DigitSlots(
            digits = summary.digits,
            value = summary.opponentSecret,
            plate = true,
            showActive = false,
            plateHeight = NbSize.plateResultH,
            contentDescription = theirs + " " + summary.opponentSecret.toList().joinToString(" "),
        )
        if (summary.mySecret.isNotEmpty()) {
            VSpacer(12.dp)
            val mine = stringResource(R.string.my_secret)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = mine + " " + summary.mySecret.toList().joinToString(" ") },
            ) {
                Text(mine, style = NbType.caption, color = NbColors.text3, maxLines = 1)
                HSpacer(6.dp)
                Text(summary.mySecret, style = NbType.numTag.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.12.em), color = NbColors.text2, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun summaryCells(summary: MatchSummary, myWins: Int?): List<Pair<String, String>> {
    val cpu = summary.mode == MatchMode.COMPUTER || summary.mode == MatchMode.PRACTICE
    return listOf(
        stringResource(R.string.result_rounds_value, summary.rounds) to stringResource(R.string.result_taken),
        (summary.durationSeconds?.let { formatClock(it) } ?: "–") to stringResource(R.string.result_time),
        if (cpu) {
            (summary.level?.let { levelLabel(it) } ?: "–") to stringResource(R.string.result_level)
        } else {
            (myWins?.let { stringResource(R.string.result_wins_value, it) } ?: "–") to stringResource(R.string.record_label)
        },
    )
}

@Composable
private fun summarySpoken(summary: MatchSummary, myWins: Int?): String {
    val c = summaryCells(summary, myWins)
    return stringResource(R.string.result_summary_a11y, c[0].second, c[0].first, c[1].second, c[1].first, c[2].second, c[2].first)
}

/**
 * Result summary (DESIGN_SYSTEM 12.20): 1 dp `line` top and bottom, 14 dp vertical padding, 3 equal columns split by
 * 1 dp `line`: value `numSummary` `ink` over label `caption` `text3`. Large text: one row per value (label left, value
 * right, 48 dp min).
 */
@Composable
private fun ResultSummary(summary: MatchSummary, myWins: Int?, spoken: String) {
    val cells = summaryCells(summary, myWins)
    // Values mix Inter digits and translated units ("6회", "13승"): the num family is only applied to pure digits.
    val valueStyle: (String) -> androidx.compose.ui.text.TextStyle = { v -> if (v.all { it.isDigit() || it == ':' || it == '–' }) NbType.numSummary else NbType.numSummary.copy(fontFamily = NbFonts.sans) }
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        Hairline()
        if (isLargeFont()) {
            cells.forEachIndexed { i, (value, label) ->
                if (i > 0) Hairline()
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = NbType.caption, color = NbColors.text3, modifier = Modifier.weight(1f))
                    SummaryValue(value, valueStyle(value))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
                cells.forEachIndexed { i, (value, label) ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(NbColors.line))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        SummaryValue(value, valueStyle(value))
                        Text(label, style = NbType.caption, color = NbColors.text3, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
        Hairline()
    }
}

/** A summary value: the digits in Inter, the unit ("회", "승") in the system face at the same size (DESIGN_SYSTEM 3.1). */
@Composable
private fun SummaryValue(value: String, @Suppress("UNUSED_PARAMETER") style: androidx.compose.ui.text.TextStyle) {
    val digits = value.takeWhile { it.isDigit() || it == ':' }
    val unit = value.drop(digits.length)
    Row {
        if (digits.isNotEmpty()) Text(digits, style = NbType.numSummary, color = NbColors.ink, maxLines = 1, softWrap = false, autoFit = false, modifier = Modifier.alignByBaseline())
        if (unit.isNotEmpty()) {
            Text(
                unit.trimStart(),
                style = if (unit == "–") NbType.numSummary else NbType.numSummary.copy(fontFamily = NbFonts.sans, letterSpacing = 0.sp),
                color = NbColors.ink,
                maxLines = 1,
                softWrap = false,
                fitMinScale = 0.6f,
                modifier = Modifier.alignByBaseline().padding(start = if (unit.startsWith(" ")) 4.dp else 0.dp),
            )
        }
    }
}

/**
 * Innings sheet "회별 기록" (06 §1.1, shot `result-innings-ko`): header 56 dp · `close`; label column 80 dp, one 40 dp
 * row per side, round header `numLabel` `text3`, strike counts Inter 600 18 sp (0 `text3` 500, the solving n `strike`
 * 700), "최고" 56 dp; under it `timer` + duration. More than 9 rounds scroll horizontally from the end (16 dp left fade).
 */
@Composable
fun ColumnScope.InningsSheet(summary: MatchSummary, onClose: () -> Unit) {
    SheetTitle(stringResource(R.string.innings_title)) {
        IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), onClose)
    }
    LineScore(summary, isLargeFont(), Modifier.padding(horizontal = 16.dp))
    VSpacer(24.dp)
}

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
    val header = if (large) 44.dp else 40.dp
    val row = if (large) 48.dp else 40.dp
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val label = if (large) 96.dp else 80.dp
            val best = if (large) 72.dp else 56.dp
            val available = maxWidth - label - best
            val scrolls = rounds > 9 || (large && available / rounds < 32.dp)
            val column = if (scrolls) 32.dp else minOf(if (large) 48.dp else 32.dp, available / rounds)
            val scroll = rememberScrollState()
            LaunchedEffect(rounds) { scroll.scrollTo(scroll.maxValue) }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(label)) {
                    Box(Modifier.height(header))
                    LabelCell(stringResource(R.string.me_short), row)
                    LabelCell(opponentLabel, row)
                }
                Box(Modifier.weight(1f).then(if (scrolls) Modifier.fadingStart() else Modifier)) {
                    Column(if (scrolls) Modifier.horizontalScroll(scroll) else Modifier) {
                        Row(Modifier.height(header), verticalAlignment = Alignment.CenterVertically) {
                            (0 until rounds).forEach { i ->
                                Text((i + 1).toString(), style = NbType.numLabel, color = NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.width(column))
                            }
                        }
                        StrikeRow(summary.myStrikes, rounds, column, row, summary.digits, stringResource(R.string.me_short))
                        StrikeRow(summary.opponentStrikes, rounds, column, row, summary.digits, opponentLabel)
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
        summary.durationSeconds?.let {
            VSpacer(8.dp)
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                NbIcon(R.drawable.ic_timer, null, NbColors.text3, 16.dp)
                HSpacer(4.dp)
                Text(formatClock(it), style = NbType.numTag, color = NbColors.text3)
            }
        }
    }
}

@Composable
private fun LabelCell(text: String, row: Dp) {
    Box(Modifier.height(row + 1.dp).padding(top = 1.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, style = NbType.caption, color = NbColors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, fitMinScale = 0.5f)
    }
}

@Composable
private fun StrikeRow(strikes: List<Int?>, rounds: Int, column: Dp, row: Dp, digits: Int, who: String) {
    Row(Modifier.height(row + 1.dp).padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        (0 until rounds).forEach { i ->
            val value = strikes.getOrNull(i)
            val spoken = stringResource(R.string.line_score_cell, i + 1, who, value ?: 0)
            val solved = value == digits
            Text(
                value?.toString() ?: "–",
                style = NbType.numMemo.copy(fontWeight = when {
                    solved -> FontWeight.Bold
                    value == null || value == 0 -> FontWeight.Medium
                    else -> FontWeight.SemiBold
                }),
                color = when {
                    value == null || value == 0 -> NbColors.text3
                    solved -> NbColors.strike
                    else -> NbColors.ink
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
        Text("${best}S", style = NbType.numMemo.copy(fontWeight = FontWeight.Bold), color = if (best == digits) NbColors.strike else NbColors.ink)
    }
}
