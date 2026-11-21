package app.numbaseball.ui.team

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.numbaseball.R
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelSummary
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.Team
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType

/** Team match overview: score and every duel (other duels only as summaries, docs/protocol.md §7). */
@Composable
fun TeamBoardScreen(room: RoomView?, onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.board_title), onBack = onBack) {
        val match = room?.match
        if (room == null || match == null) {
            Text(stringResource(R.string.board_waiting), color = NbColors.text2)
            return@ScreenScaffold
        }
        Text(stringResource(R.string.score_a_b, match.score.a, match.score.b), style = NbType.numScore)
        val names = room.players.associate { it.id to "${it.nickname}#${it.tag}" }
        match.duels.forEach { duel ->
            Row(Modifier.fillMaxWidth()) {
                Text("${names[duel.playerA].orEmpty()}  vs  ${names[duel.playerB].orEmpty()}", style = NbType.numTag, modifier = Modifier.weight(1f))
                Text(duelStatus(duel), color = NbColors.text2)
            }
        }
    }
}

@Composable
private fun duelStatus(duel: DuelSummary): String {
    val result = duel.result
    return when {
        result != null && result.reason == EndReason.CANCELLED -> stringResource(R.string.board_cancelled)
        result != null -> when (result.winner) {
            Team.A -> stringResource(R.string.board_won_a)
            Team.B -> stringResource(R.string.board_won_b)
            null -> stringResource(R.string.board_draw)
        }
        duel.phase == DuelPhase.CHOOSING -> stringResource(R.string.board_choosing)
        else -> stringResource(R.string.board_round, duel.round)
    }
}
