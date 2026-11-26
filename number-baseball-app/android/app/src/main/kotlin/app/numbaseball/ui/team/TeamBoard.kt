package app.numbaseball.ui.team

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numbaseball.R
import app.numbaseball.ads.AdBanner
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelSummary
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.PlayerView
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.protocol.Team
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.exactLineHeight
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.Dot
import app.numbaseball.ui.design.FitText
import app.numbaseball.ui.design.hintText
import app.numbaseball.ui.results.ResultBadge
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.design.NameWithTag
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbDialog
import app.numbaseball.ui.results.RemoveAdsLine
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.TeamBadge
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.fadingEdges
import app.numbaseball.ui.design.formatClock
import app.numbaseball.ui.design.isCompactHeight
import app.numbaseball.ui.design.isLargeFont
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbFonts
import app.numbaseball.ui.theme.NbMotion
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.delay

/** How the board is shown (08 §1): over my running duel, replacing it after my duel ended, or as the team result (06 §4). */
enum class BoardMode { OVERLAY, LIVE, FINAL }

/** Duel wins needed to take the match: ceil(N / 2) for odd N, N / 2 + 1 otherwise. */
fun winsNeeded(teamSize: Int): Int = if (teamSize % 2 == 1) (teamSize + 1) / 2 else teamSize / 2 + 1

@Composable
fun TeamBoardScreen(
    room: RoomView,
    mode: BoardMode,
    onBack: () -> Unit,
    onChat: () -> Unit,
    onBackToRoom: () -> Unit = {},
    onHome: () -> Unit = {},
    /** Server time each offline player was first seen offline ([app.numbaseball.data.online.OnlineSession.offlineSince]). */
    offlineSince: Map<String, Long> = emptyMap(),
    serverNow: () -> Long = System::currentTimeMillis,
    /** Live board after my duel: `close` / back asks first, then leaves the match (08 §1–2). */
    onLeave: () -> Unit = {},
    /** Final board only: false when it was reached through an interstitial (06 §4). */
    showBanner: Boolean = true,
    /** Final board only: the quiet "광고 제거" link (06 §1 rules), null = hidden. */
    onRemoveAds: (() -> Unit)? = null,
) {
    val match = room.match ?: return
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    if (mode == BoardMode.LIVE) BackHandler { confirmLeave = true }
    val compact = isCompactHeight()
    val me = room.players.firstOrNull { it.id == room.youId }
    val myTeam = me?.team ?: Team.A
    val myWins = if (myTeam == Team.A) match.score.a else match.score.b
    NbScreen(
        topBar = {
            // 08 §2: my duel running → `arrow_back` (back to it); finished, others playing → `close` = leave dialog;
            // all finished → team result, `close` = Home. The remove-ads link only on the final result (06 §4).
            TopBar(
                title = stringResource(R.string.team_match),
                navIcon = if (mode == BoardMode.OVERLAY) R.drawable.ic_arrow_back else R.drawable.ic_close,
                navDescription = stringResource(
                    when (mode) {
                        BoardMode.OVERLAY -> R.string.back_to_duel
                        BoardMode.LIVE -> R.string.leave
                        BoardMode.FINAL -> R.string.home
                    },
                ),
                onNav = when (mode) {
                    BoardMode.OVERLAY -> onBack
                    BoardMode.LIVE -> ({ confirmLeave = true })
                    BoardMode.FINAL -> onHome
                },
                actions = {
                    IconButton48(R.drawable.ic_chat, stringResource(R.string.chat_title), onChat)
                },
            )
        },
        bottomBar = {
            if (mode == BoardMode.FINAL && compact) {
                // Small window (ADAPTIVE.md §1): buttons side by side, 48 dp.
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NbButton(stringResource(R.string.home), onHome, kind = ButtonKind.SECONDARY, heightOverride = 48.dp, modifier = Modifier.weight(1f))
                    NbButton(stringResource(R.string.back_to_room), onBackToRoom, heightOverride = 48.dp, modifier = Modifier.weight(1f))
                }
            } else if (mode == BoardMode.FINAL) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    NbButton(stringResource(R.string.back_to_room), onBackToRoom, modifier = Modifier.fillMaxWidth())
                    VSpacer(8.dp)
                    NbButton(stringResource(R.string.home), onHome, kind = ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth())
                    // The quiet 광고 제거 line with its 24 dp clear space above the band (06 §4, §1.2).
                    RemoveAdsLine(onRemoveAds, banner = showBanner)
                }
                if (showBanner) AdBanner()
            }
        },
    ) {
        if (mode == BoardMode.FINAL && compact) {
            // Small window (ADAPTIVE §1, D2-26): one 56 dp headline row with the score inline; the duel rows scroll below.
            CompactFinalHeadline(match.result?.winner, myTeam, match.score.a, match.score.b, myWins, room.settings.teamSize)
        } else if (compact) {
            // Small window, live board: a 56 dp score block, no rule line (ADAPTIVE §1).
            CompactScoreBlock(match.score.a, match.score.b)
        } else {
            if (mode == BoardMode.FINAL) {
                FinalHero(match.result?.winner, myTeam, myWins, room.settings.teamSize)
            }
            ScoreBlock(match.score.a, match.score.b, room.settings.teamSize)
        }
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).fillMaxWidth().fadingEdges(enabled = scroll.canScrollForward).verticalScroll(scroll).padding(horizontal = if (compact) 12.dp else 16.dp)) {
            // Duel rows (DESIGN_SYSTEM 12.12): 1 dp `line` bottom border; my row `surface2`, radius 12, no border.
            val players = room.players.associateBy { it.id }
            match.duels.forEachIndexed { i, duel ->
                if (compact) {
                    CompactDuelRow(
                        duel = duel,
                        a = players[duel.playerA],
                        b = players[duel.playerB],
                        mine = duel.index == match.myDuel?.index,
                        offlineSince = offlineSince,
                        serverNow = serverNow,
                    )
                    return@forEachIndexed
                }
                DuelRow(
                    duel = duel,
                    a = players[duel.playerA],
                    b = players[duel.playerB],
                    mine = duel.index == match.myDuel?.index,
                    myRounds = match.myDuel?.takeIf { it.index == duel.index }?.myRounds,
                    opponentRounds = match.myDuel?.takeIf { it.index == duel.index }?.opponentRounds,
                    myTeam = match.myDuel?.myTeam,
                    offlineSince = offlineSince,
                    serverNow = serverNow,
                )
            }
            if (mode == BoardMode.LIVE) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    NbIcon(R.drawable.ic_visibility, null, NbColors.text3, 20.dp)
                    HSpacer(8.dp)
                    Text(stringResource(R.string.watching), style = NbType.caption, color = NbColors.text3, textAlign = TextAlign.Center, maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.heightIn(min = 16.dp))
        }
    }
    if (confirmLeave) {
        NbDialog(
            title = stringResource(R.string.leave_room_title),
            body = stringResource(R.string.board_leave_body),
            confirmLabel = stringResource(R.string.leave),
            onConfirm = {
                confirmLeave = false
                onLeave()
            },
            onDismiss = { confirmLeave = false },
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

/** Small-window team result headline (ADAPTIVE §1): badge 32 dp · "승리" (`title`) · "A 2 : 1 B" (letters `numLabel`, score Inter 700 28 sp). */
@Composable
private fun CompactFinalHeadline(winner: Team?, myTeam: Team, scoreA: Int, scoreB: Int, myWins: Int, teamSize: Int) {
    val headline = stringResource(
        when (winner) {
            null -> R.string.res_team_draw
            myTeam -> R.string.res_win
            else -> R.string.res_lose
        },
    )
    val teamA = stringResource(R.string.team_a)
    val teamB = stringResource(R.string.team_b)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) { heading(); contentDescription = "$headline. $teamA $scoreA, $teamB $scoreB" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResultBadge(if (winner == null) Outcome.DRAW else if (winner == myTeam) Outcome.WIN else Outcome.LOSE, 32.dp)
        HSpacer(12.dp)
        Text(headline, style = NbType.title, color = NbColors.ink, maxLines = 1)
        HSpacer(16.dp)
        InlineScore(scoreA, scoreB)
    }
}

/** "A 2 : 1 B": team letters `numLabel`, score Inter 700 28 sp (capped like every `num` style). */
@Composable
private fun InlineScore(scoreA: Int, scoreB: Int) {
    val score = NbType.numStat.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("A", style = NbType.numLabel, color = NbColors.teamA)
        HSpacer(8.dp)
        Text(scoreA.toString(), style = score, color = if (scoreA >= scoreB) NbColors.ink else NbColors.text3)
        HSpacer(8.dp)
        Text(":", style = score, color = NbColors.text3)
        HSpacer(8.dp)
        Text(scoreB.toString(), style = score, color = if (scoreB >= scoreA) NbColors.ink else NbColors.text3)
        HSpacer(8.dp)
        Text("B", style = NbType.numLabel, color = NbColors.teamB)
    }
}

/** Small-window live board score block (ADAPTIVE §1): 56 dp, badges · score · badges, no rule line. */
@Composable
private fun CompactScoreBlock(scoreA: Int, scoreB: Int) {
    val teamA = stringResource(R.string.team_a)
    val teamB = stringResource(R.string.team_b)
    val score = NbType.numStat.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp).semantics(mergeDescendants = true) { contentDescription = "$teamA $scoreA, $teamB $scoreB" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamBadge(Team.A)
        HSpacer(12.dp)
        Text(scoreA.toString(), style = score, color = if (scoreA >= scoreB) NbColors.ink else NbColors.text3)
        HSpacer(8.dp)
        Text(":", style = score, color = NbColors.text3)
        HSpacer(8.dp)
        Text(scoreB.toString(), style = score, color = if (scoreB >= scoreA) NbColors.ink else NbColors.text3)
        HSpacer(12.dp)
        TeamBadge(Team.B)
    }
}

/**
 * Small-window duel row (ADAPTIVE §1): 56 dp, one name line each side, a 72 dp status column holding the round and either
 * the winner ("승 A"), a status word (reconnecting, draw, forfeit, decided) or both best hints ("2S 1B · 1S 2B").
 */
@Composable
private fun CompactDuelRow(
    duel: DuelSummary,
    a: PlayerView?,
    b: PlayerView?,
    mine: Boolean,
    offlineSince: Map<String, Long>,
    serverNow: () -> Long,
) {
    val finished = duel.phase == DuelPhase.FINISHED
    val result = duel.result
    val cancelled = result?.reason == EndReason.CANCELLED
    val winner = result?.winner?.takeIf { !cancelled }
    val disconnected = !finished && listOfNotNull(a, b).any { !it.connected }
    fun hint(best: app.numbaseball.core.protocol.BestHint?) = best?.let { "${it.strikes}S ${it.balls}B" } ?: "–"
    val wonLabel = stringResource(R.string.tag_won)
    val spoken = buildString {
        append(duel.index + 1).append(", ")
        append(a?.let { "${it.nickname} ${it.tag}" }.orEmpty()).append(", ")
        append(b?.let { "${it.nickname} ${it.tag}" }.orEmpty())
        if (duel.round > 0) append(", R").append(duel.round)
        if (winner != null) append(", ").append(wonLabel).append(" ").append(winner.name) else append(", ").append(hint(duel.bestHintA)).append(" · ").append(hint(duel.bestHintB))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .duelRowBackground(mine)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactName(a, finished && winner != null && winner != Team.A, Modifier.weight(1f).padding(start = 12.dp))
        Column(Modifier.width(72.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (duel.round > 0) Text(stringResource(R.string.duel_round, duel.round), style = NbType.numLabel, color = NbColors.text3)
            when {
                winner != null -> WonTag("$wonLabel ${winner.name}")
                cancelled -> Text(stringResource(R.string.status_decided), style = NbType.caption, color = NbColors.text2, maxLines = 1)
                finished && result?.winner == null -> Text(stringResource(R.string.status_draw), style = NbType.caption, color = NbColors.text2, maxLines = 1)
                disconnected -> ReconnectStatus(listOfNotNull(a, b).filter { !it.connected }.mapNotNull { offlineSince[it.id] }.minOrNull(), serverNow, short = true)
                // 72 dp column: numLabel without tracking fits "2S 1B · 1S 2B".
                // 72 dp column: Inter is wider than the v1 face, so the pair steps its size down to fit.
                else -> FitText(
                    "${hint(duel.bestHintA)} · ${hint(duel.bestHintB)}",
                    style = NbType.numLabel.copy(letterSpacing = 0.sp),
                    color = NbColors.text2,
                    textAlign = TextAlign.Center,
                )
            }
        }
        CompactName(b, finished && winner != null && winner != Team.B, Modifier.weight(1f).padding(end = 12.dp), end = true)
    }
}

@Composable
private fun CompactName(player: PlayerView?, loser: Boolean, modifier: Modifier, end: Boolean = false) {
    val name = when {
        player == null -> ""
        player.deleted -> stringResource(R.string.chat_deleted_player)
        else -> player.nickname
    }
    Box(modifier, contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) {
        NameWithTag(name, player?.takeIf { !it.deleted }?.tag, nameStyle = NbType.bodyStrong, nameColor = if (loser) NbColors.text3 else NbColors.ink)
    }
}

@Composable
private fun FinalHero(winner: Team?, myTeam: Team, @Suppress("UNUSED_PARAMETER") myWins: Int, @Suppress("UNUSED_PARAMETER") teamSize: Int) {
    // Team result (06 §4 v2): 64 dp badge, 12 dp, headline; the score row below says the rest (no lamp row).
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ResultBadge(if (winner == null) Outcome.DRAW else if (winner == myTeam) Outcome.WIN else Outcome.LOSE, 64.dp)
        VSpacer(12.dp)
        Text(
            stringResource(
                when (winner) {
                    null -> R.string.res_team_draw
                    myTeam -> R.string.res_win
                    else -> R.string.res_lose
                },
            ),
            style = NbType.display,
            color = NbColors.ink,
            modifier = Modifier.exactLineHeight(NbType.display).semantics { heading() },
        )
    }
}

/** 승 tag (DESIGN_SYSTEM 12.12): `micro` white on `strike`, 20 dp, radius 6, 6 dp padding. */
@Composable
private fun WonTag(text: String) {
    Box(Modifier.heightIn(min = 20.dp).background(NbColors.strike, RoundedCornerShape(NbRadius.s)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, style = NbType.micro, color = NbColors.onAccent, maxLines = 1)
    }
}

/** My duel row: `surface2`, radius 12; the others a 1 dp `line` bottom border. */
private fun Modifier.duelRowBackground(mine: Boolean): Modifier =
    if (mine) {
        background(NbColors.surface2, RoundedCornerShape(NbRadius.m))
    } else {
        drawBehind { drawLine(NbColors.line, androidx.compose.ui.geometry.Offset(0f, size.height - 0.5.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
    }

/** Score block (08 §2): badge + name · `numScore` a : b (leading or tied in `ink`) · name + badge, then the rule line. */
@Composable
private fun ScoreBlock(scoreA: Int, scoreB: Int, teamSize: Int) {
    val a by animateIntAsState(scoreA, tween(NbMotion.short), label = "a")
    val b by animateIntAsState(scoreB, tween(NbMotion.short), label = "b")
    val teamA = stringResource(R.string.team_a)
    val teamB = stringResource(R.string.team_b)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$teamA $scoreA, $teamB $scoreB" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Large text: badge over name, so the name keeps the full side width (ADAPTIVE §2.4).
            if (isLargeFont()) {
                Column(Modifier.weight(1f)) {
                    TeamBadge(Team.A)
                    Text(teamA, style = NbType.subtitle, color = NbColors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, fitMinScale = 0.5f)
                }
            } else Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(Team.A)
                HSpacer(10.dp)
                Text(teamA, style = NbType.subtitle, color = NbColors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, fitMinScale = 0.5f)
            }
            HSpacer(12.dp)
            Text(a.toString(), style = NbType.numScore, color = if (scoreA >= scoreB) NbColors.ink else NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 32.dp))
            HSpacer(12.dp)
            Text(":", style = TextStyle(fontFamily = NbFonts.num, fontWeight = FontWeight.Bold, fontSize = 32.sp), color = NbColors.text3)
            HSpacer(12.dp)
            Text(b.toString(), style = NbType.numScore, color = if (scoreB >= scoreA) NbColors.ink else NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 32.dp))
            HSpacer(12.dp)
            if (isLargeFont()) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    TeamBadge(Team.B)
                    Text(teamB, style = NbType.subtitle, color = NbColors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, fitMinScale = 0.5f)
                }
            } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(teamB, style = NbType.subtitle, color = NbColors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, fitMinScale = 0.5f)
                HSpacer(10.dp)
                TeamBadge(Team.B)
            }
        }
        // Prototype: `.score` 20 dp bottom padding, `.score-sub` −12 dp margin and 16 dp bottom padding.
        VSpacer(8.dp)
        val needed = winsNeeded(teamSize)
        Text(
            if (teamSize % 2 == 0) stringResource(R.string.team_rule_even, teamSize, needed, teamSize / 2) else stringResource(R.string.team_rule, teamSize, needed),
            style = NbType.caption,
            color = NbColors.text3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Best hint so far from my own rounds: most strikes, then most balls; (0, 0) = OUT; null when none yet. */
private fun bestHint(rounds: List<RoundEntry>): Pair<Int, Int>? =
    rounds.filter { it.guess != null }.maxWithOrNull(compareBy({ it.strikes }, { it.balls }))?.let { it.strikes to it.balls }

/**
 * Duel row (DESIGN_SYSTEM 12.11): 72 dp; A player (flex) · status 88 dp · B player (right-aligned); 3 dp team stripes inset
 * my row `surface2`, radius 12. Best hints come from the duel summary (protocol 1.1 `bestHintA` / `bestHintB`); my own duel
 * falls back to my rounds for servers that do not send them yet.
 */
@Composable
private fun DuelRow(
    duel: DuelSummary,
    a: PlayerView?,
    b: PlayerView?,
    mine: Boolean,
    myRounds: List<RoundEntry>?,
    opponentRounds: List<RoundEntry>?,
    myTeam: Team?,
    offlineSince: Map<String, Long>,
    serverNow: () -> Long,
) {
    val large = isLargeFont()
    val finished = duel.phase == DuelPhase.FINISHED
    val result = duel.result
    val cancelled = result?.reason == EndReason.CANCELLED
    val winner = result?.winner?.takeIf { !cancelled }
    // My rounds are guesses at the opponent's number: they are the hints of my side.
    val hintA = when (myTeam) { Team.A -> myRounds; Team.B -> opponentRounds; null -> null }
    val hintB = when (myTeam) { Team.B -> myRounds; Team.A -> opponentRounds; null -> null }
    val bestA = duel.bestHintA?.let { it.strikes to it.balls } ?: hintA?.let(::bestHint)
    val bestB = duel.bestHintB?.let { it.strikes to it.balls } ?: hintB?.let(::bestHint)
    val disconnected = !finished && listOfNotNull(a, b).any { !it.connected }
    val wonLabel = stringResource(R.string.tag_won)
    val noBest = stringResource(R.string.best_none_a11y)
    val bestSpoken: @Composable (Pair<Int, Int>?) -> String = { best ->
        best?.let { stringResource(R.string.best_hint_a11y, it.first, it.second) } ?: noBest
    }
    val spokenA = if (winner == Team.A) wonLabel else bestSpoken(bestA)
    val spokenB = if (winner == Team.B) wonLabel else bestSpoken(bestB)
    // "1, 새벽투수 4821 승, 민트초코 0912 최고 2스트라이크 1볼, R7"; null best: "최고 기록 없음" (08 §5).
    val spoken = buildString {
        append(duel.index + 1).append(", ")
        append(a?.let { "${it.nickname} ${it.tag}" }.orEmpty()).append(" ").append(spokenA)
        append(", ")
        append(b?.let { "${it.nickname} ${it.tag}" }.orEmpty()).append(" ").append(spokenB)
        if (duel.round > 0) append(", R").append(duel.round)
    }
    // 72 dp minimum including the 1 dp hairline above: the row grows with large text instead of clipping (D2-03).
    // No intrinsic measurement (D2-03, round 2): the stripes are drawn behind the row, so a tall status column grows it.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .duelRowBackground(mine)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(start = 12.dp, top = if (large) 8.dp else 0.dp, bottom = if (large) 8.dp else 0.dp)) {
            PlayerCell(a, winner == Team.A, finished && winner != null && winner != Team.A, bestA, end = false, stacked = large)
        }
        Column(Modifier.width(88.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (duel.round > 0) Text(stringResource(R.string.duel_round, duel.round), style = NbType.numLabel, color = NbColors.text3)
            if (duel.round > 0 && !(finished && !cancelled && result?.winner != null && result.reason != EndReason.FORFEIT && result.reason != EndReason.DISCONNECT)) VSpacer(2.dp)
            when {
                cancelled -> Text(stringResource(R.string.status_decided), style = NbType.caption, color = NbColors.text2)
                finished && result?.winner == null -> Text(stringResource(R.string.status_draw), style = NbType.caption, color = NbColors.text2)
                finished && (result?.reason == EndReason.FORFEIT || result?.reason == EndReason.DISCONNECT) ->
                    Text(stringResource(R.string.status_forfeit), style = NbType.caption, color = NbColors.text2)
                finished -> Unit
                disconnected -> ReconnectStatus(listOfNotNull(a, b).filter { !it.connected }.mapNotNull { offlineSince[it.id] }.minOrNull(), serverNow)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(NbColors.accent, NbSize.dotS)
                    HSpacer(6.dp)
                    Text(stringResource(R.string.status_playing), style = NbType.caption, color = NbColors.text2)
                }
            }
        }
        Column(Modifier.weight(1f).padding(end = 12.dp, top = if (large) 8.dp else 0.dp, bottom = if (large) 8.dp else 0.dp), horizontalAlignment = Alignment.End) {
            PlayerCell(b, winner == Team.B, finished && winner != null && winner != Team.B, bestB, end = true, stacked = large)
        }
    }
}

/**
 * "재접속 대기 0:41" in `strike`: the 60 s grace counted from the server time the session first saw the player offline
 * ([since]), so opening the board late still shows the real time left (D2-14). Without a sighting it counts from now.
 */
@Composable
private fun ReconnectStatus(since: Long?, serverNow: () -> Long, short: Boolean = false) {
    val start = remember(since) { since ?: serverNow() }
    var now by remember { mutableLongStateOf(serverNow()) }
    LaunchedEffect(start) {
        while (true) {
            now = serverNow()
            delay(1000)
        }
    }
    val left = ((Limits.DISCONNECT_GRACE_MS - (now - start)) / 1000).toInt().coerceIn(0, (Limits.DISCONNECT_GRACE_MS / 1000).toInt())
    // The countdown sits on its own line under the word (D2-03, DEF-056), so it is never clipped or split; small windows
    // use the short word (D2-44: "재접속" / "Away").
    val label = stringResource(if (short || isLargeFont()) R.string.status_reconnecting_short else R.string.status_reconnecting, "").trim()
    // One word per line at most: a long word ("Переподключение") steps down instead of breaking.
    val spoken = stringResource(R.string.status_reconnecting, formatClock(left))
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = NbType.caption, color = NbColors.strike, textAlign = TextAlign.Center, maxLines = 2, fitMinScale = 0.5f)
        Text(formatClock(left), style = NbType.numTag, color = NbColors.strike, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PlayerCell(player: PlayerView?, winner: Boolean, loser: Boolean, best: Pair<Int, Int>?, end: Boolean, stacked: Boolean) {
    val name = when {
        player == null -> ""
        player.deleted -> stringResource(R.string.chat_deleted_player)
        else -> player.nickname
    }
    val tag = player?.takeIf { !it.deleted }?.tag
    val nameColor = if (loser) NbColors.text3 else NbColors.ink
    if (stacked) {
        // Large text (D2-03): name over tag, so the name keeps the full column width and stays legible.
        Text(name, style = NbType.bodyStrong, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (end) TextAlign.End else TextAlign.Start)
        if (tag != null) Text("#$tag", style = NbType.numTag, color = NbColors.text3, maxLines = 1, softWrap = false)
    } else {
        NameWithTag(name, tag, nameStyle = NbType.bodyStrong, nameColor = nameColor)
    }
    VSpacer(4.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (end) Arrangement.End else Arrangement.Start) {
        when {
            winner -> WonTag(stringResource(R.string.tag_won))
            best != null -> {
                // The hint never shrinks or clips; a long "best" label steps down first (DEF-059).
                Text(stringResource(R.string.best), style = NbType.caption, color = NbColors.text3, maxLines = 1, fitMinScale = 0.5f, modifier = Modifier.weight(1f, fill = false))
                HSpacer(6.dp)
                // 08 §2: a new best hint cross-fades in over 150 ms.
                androidx.compose.animation.Crossfade(best, animationSpec = androidx.compose.animation.core.tween(150), label = "bestHint") { b ->
                    // Best hints as text, OUT as text too (08 v2).
                    Text(hintText(b.first, b.second), style = NbType.numTag, color = if (loser) NbColors.text3 else NbColors.text2, maxLines = 1, softWrap = false, autoFit = false)
                }
            }
            // No resolved guess yet (or a 1.0 server without best hints): "최고 –", so every row keeps its two lines (08 §2).
            player != null -> {
                Text(stringResource(R.string.best), style = NbType.caption, color = NbColors.text3)
                HSpacer(6.dp)
                Text("–", style = NbType.numTag, color = NbColors.text3)
            }
        }
    }
}
