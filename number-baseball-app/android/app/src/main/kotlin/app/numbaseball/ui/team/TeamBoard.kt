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
import app.numbaseball.ui.design.Lamp
import app.numbaseball.ui.design.LampColor
import app.numbaseball.ui.design.NameWithTag
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.OutTag
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
) {
    val match = room.match ?: return
    val compact = isCompactHeight()
    val me = room.players.firstOrNull { it.id == room.youId }
    val myTeam = me?.team ?: Team.A
    val myWins = if (myTeam == Team.A) match.score.a else match.score.b
    val won = mode == BoardMode.FINAL && match.result?.winner == myTeam
    NbScreen(
        wash = if (won) NbColors.strikeTint else null,
        topBar = {
            TopBar(
                title = stringResource(R.string.team_match),
                navIcon = if (mode == BoardMode.OVERLAY) R.drawable.ic_arrow_back else R.drawable.ic_close,
                navDescription = stringResource(if (mode == BoardMode.OVERLAY) R.string.back_to_duel else R.string.action_close),
                onNav = onBack,
                actions = { IconButton48(R.drawable.ic_chat, stringResource(R.string.chat_title), onChat, tint = NbColors.chalk) },
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
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
                    NbButton(stringResource(R.string.back_to_room), onBackToRoom, modifier = Modifier.fillMaxWidth())
                    VSpacer(8.dp)
                    NbButton(stringResource(R.string.home), onHome, kind = ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
                AdBanner()
            }
        },
    ) {
        if (mode == BoardMode.FINAL && compact) {
            // Small window (D2-26): one 56 dp headline row with the score inline; the duel rows scroll below.
            CompactFinalHeadline(match.result?.winner, myTeam, match.score.a, match.score.b)
        } else {
            if (mode == BoardMode.FINAL) {
                FinalHero(match.result?.winner, myTeam, myWins, room.settings.teamSize)
            }
            ScoreBlock(match.score.a, match.score.b, room.settings.teamSize)
        }
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).fillMaxWidth().fadingEdges(bottom = 16.dp, enabled = scroll.canScrollForward).verticalScroll(scroll)) {
            // Prototype `.duels`: 1 dp top border + each row's own 1 dp top border inside its 72 dp.
            Hairline(Modifier.height(2.dp))
            val players = room.players.associateBy { it.id }
            match.duels.forEachIndexed { i, duel ->
                if (i > 0) Hairline()
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
            Hairline()
            if (mode == BoardMode.LIVE) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    NbIcon(R.drawable.ic_visibility, null, NbColors.text3, 20.dp)
                    HSpacer(8.dp)
                    Text(stringResource(R.string.watching), style = NbType.caption, color = NbColors.text3, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.heightIn(min = 16.dp))
        }
    }
}

@Composable
private fun CompactFinalHeadline(winner: Team?, myTeam: Team, scoreA: Int, scoreB: Int) {
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
        Text(headline, style = NbType.headline, color = NbColors.chalk, maxLines = 1)
        HSpacer(12.dp)
        Text("·", style = NbType.headline, color = NbColors.text3)
        HSpacer(12.dp)
        TeamBadge(Team.A)
        HSpacer(8.dp)
        Text(scoreA.toString(), style = NbType.numStat, color = if (scoreA >= scoreB) NbColors.chalk else NbColors.text3)
        HSpacer(8.dp)
        Text(":", style = NbType.numStat, color = NbColors.text3)
        HSpacer(8.dp)
        Text(scoreB.toString(), style = NbType.numStat, color = if (scoreB >= scoreA) NbColors.chalk else NbColors.text3)
        HSpacer(8.dp)
        TeamBadge(Team.B)
    }
}

@Composable
private fun FinalHero(winner: Team?, myTeam: Team, myWins: Int, teamSize: Int) {
    // One lamp per duel, lit for each duel my team won (06 §4: "2 of 3 lit"); prototype `.final`: 20 / 16 / 8.
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(teamSize) { Lamp(LampColor.STRIKE, lit = it < myWins, size = 12.dp) }
        }
        Text(
            stringResource(
                when (winner) {
                    null -> R.string.res_team_draw
                    myTeam -> R.string.res_win
                    else -> R.string.res_lose
                },
            ),
            style = NbType.display,
            color = NbColors.chalk,
            modifier = Modifier.exactLineHeight(NbType.display).semantics { heading() },
        )
    }
}

/** Score block (08 §2): badge + name · `numScore` a : b (leading or tied in chalk) · name + badge, then the rule line. */
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
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(Team.A)
                HSpacer(10.dp)
                Text(teamA, style = NbType.subtitle, color = NbColors.chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HSpacer(12.dp)
            Text(a.toString(), style = NbType.numScore, color = if (scoreA >= scoreB) NbColors.chalk else NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 32.dp))
            HSpacer(12.dp)
            Text(":", style = TextStyle(fontFamily = NbFonts.num, fontWeight = FontWeight.SemiBold, fontSize = 32.sp), color = NbColors.text3)
            HSpacer(12.dp)
            Text(b.toString(), style = NbType.numScore, color = if (scoreB >= scoreA) NbColors.chalk else NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 32.dp))
            HSpacer(12.dp)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(teamB, style = NbType.subtitle, color = NbColors.chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
 * 12 dp; my row `surface1`. Best hints come from the duel summary (protocol 1.1 `bestHintA` / `bestHintB`); my own duel
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
    val spoken = buildString {
        append(duel.index + 1).append(", ")
        append(a?.let { "${it.nickname} ${it.tag}" }.orEmpty())
        bestA?.let { append(" ").append(it.first).append("S ").append(it.second).append("B") }
        append(", ")
        append(b?.let { "${it.nickname} ${it.tag}" }.orEmpty())
        bestB?.let { append(" ").append(it.first).append("S ").append(it.second).append("B") }
        if (duel.round > 0) append(", R").append(duel.round)
    }
    // 72 dp minimum including the 1 dp hairline above: the row grows with large text instead of clipping (D2-03).
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 71.dp)
            .background(if (mine) NbColors.surface1 else NbColors.bg)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().padding(vertical = 12.dp).background(NbColors.teamA, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
        Column(Modifier.weight(1f).padding(start = 13.dp, top = if (large) 8.dp else 0.dp, bottom = if (large) 8.dp else 0.dp)) {
            PlayerCell(a, winner == Team.A, finished && winner != null && winner != Team.A, bestA, end = false, stacked = large)
        }
        Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                    Lamp(LampColor.CHALK, lit = true, size = 6.dp, glow = false)
                    HSpacer(6.dp)
                    Text(stringResource(R.string.status_playing), style = NbType.caption, color = NbColors.text2)
                }
            }
        }
        Column(Modifier.weight(1f).padding(end = 13.dp, top = if (large) 8.dp else 0.dp, bottom = if (large) 8.dp else 0.dp), horizontalAlignment = Alignment.End) {
            PlayerCell(b, winner == Team.B, finished && winner != null && winner != Team.B, bestB, end = true, stacked = large)
        }
        Box(Modifier.width(3.dp).fillMaxHeight().padding(vertical = 12.dp).background(NbColors.teamB, RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)))
    }
}

/**
 * "재접속 대기 0:41" in `strike`: the 60 s grace counted from the server time the session first saw the player offline
 * ([since]), so opening the board late still shows the real time left (D2-14). Without a sighting it counts from now.
 */
@Composable
private fun ReconnectStatus(since: Long?, serverNow: () -> Long) {
    val start = remember(since) { since ?: serverNow() }
    var now by remember { mutableLongStateOf(serverNow()) }
    LaunchedEffect(start) {
        while (true) {
            now = serverNow()
            delay(1000)
        }
    }
    val left = ((Limits.DISCONNECT_GRACE_MS - (now - start)) / 1000).toInt().coerceIn(0, (Limits.DISCONNECT_GRACE_MS / 1000).toInt())
    Text(stringResource(R.string.status_reconnecting, formatClock(left)), style = NbType.caption, color = NbColors.strike, textAlign = TextAlign.Center, maxLines = 2)
}

@Composable
private fun PlayerCell(player: PlayerView?, winner: Boolean, loser: Boolean, best: Pair<Int, Int>?, end: Boolean, stacked: Boolean) {
    val name = when {
        player == null -> ""
        player.deleted -> stringResource(R.string.chat_deleted_player)
        else -> player.nickname
    }
    val tag = player?.takeIf { !it.deleted }?.tag
    val nameColor = if (loser) NbColors.text3 else NbColors.chalk
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
            winner -> {
                Lamp(LampColor.STRIKE, lit = true, size = 6.dp)
                HSpacer(4.dp)
                Text(stringResource(R.string.tag_won), style = NbType.micro, color = NbColors.strike)
            }
            best != null -> {
                Text(stringResource(R.string.best), style = NbType.caption, color = NbColors.text3)
                HSpacer(6.dp)
                if (best.first == 0 && best.second == 0) {
                    OutTag(height = 18.dp, fontSize = 11.sp, padding = 4.dp)
                } else {
                    Text("${best.first}S ${best.second}B", style = NbType.numTag, color = if (loser) NbColors.text3 else NbColors.text2)
                }
            }
        }
    }
}
