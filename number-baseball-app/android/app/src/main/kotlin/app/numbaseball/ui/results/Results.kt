package app.numbaseball.ui.results

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.numbaseball.R
import app.numbaseball.ads.AdBanner
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.SoundCue
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SecondaryButton
import app.numbaseball.ui.common.SectionLabel
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchSummary
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType

/**
 * Result of a duel or team match. A calm screen, so it may carry the banner — except after waiting-room practice, which
 * belongs to the lobby. Interstitials are handled by the route when the player leaves (vs computer only).
 */
@Composable
fun ResultsScreen(
    summary: MatchSummary,
    onPlayAgain: (() -> Unit)?,
    onHome: () -> Unit,
    onBackToRoom: (() -> Unit)?,
) {
    val audio = LocalGameAudio.current
    LaunchedEffect(summary) {
        audio.play(
            when (summary.outcome) {
                Outcome.WIN -> SoundCue.WIN
                Outcome.LOSE -> SoundCue.LOSE
                Outcome.DRAW -> SoundCue.DRAW
            },
        )
    }
    ScreenScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        bottomBar = { if (summary.mode != MatchMode.PRACTICE) AdBanner() },
    ) {
        Text(outcomeText(summary), style = NbType.display, color = if (summary.outcome == Outcome.WIN) NbColors.strike else NbColors.chalk)
        if (summary.teamMatch) Text(stringResource(R.string.score_a_b, summary.scoreA, summary.scoreB), style = NbType.numScore)
        Text(reasonText(summary.reason), color = NbColors.text2)
        Text(pluralStringResource(R.plurals.result_rounds, summary.rounds, summary.rounds), style = MaterialTheme.typography.bodyLarge)
        if (summary.opponentSecret.isNotEmpty()) {
            SectionLabel(stringResource(R.string.result_opponent_secret))
            Text(summary.opponentSecret, style = NbType.numHero)
        }
        onPlayAgain?.let { PrimaryButton(stringResource(R.string.result_play_again), it) }
        onBackToRoom?.let { SecondaryButton(stringResource(R.string.result_back_to_room), it) }
        SecondaryButton(stringResource(R.string.result_home), onHome)
    }
}

@Composable
private fun outcomeText(summary: MatchSummary): String = stringResource(
    when (summary.outcome) {
        Outcome.WIN -> if (summary.teamMatch) R.string.result_team_win else R.string.result_win
        Outcome.LOSE -> if (summary.teamMatch) R.string.result_team_lose else R.string.result_lose
        Outcome.DRAW -> if (summary.teamMatch) R.string.result_team_draw else R.string.result_draw
    },
)

@Composable
private fun reasonText(reason: EndReason): String = stringResource(
    when (reason) {
        EndReason.SOLVED -> R.string.result_reason_solved
        EndReason.BOTH_SOLVED -> R.string.result_reason_both_solved
        EndReason.TIMEOUT -> R.string.result_reason_timeout
        EndReason.BOTH_TIMEOUT -> R.string.result_reason_both_timeout
        EndReason.DISCONNECT -> R.string.result_reason_disconnect
        EndReason.FORFEIT -> R.string.result_reason_forfeit
        EndReason.CANCELLED -> R.string.result_reason_cancelled
    },
)
