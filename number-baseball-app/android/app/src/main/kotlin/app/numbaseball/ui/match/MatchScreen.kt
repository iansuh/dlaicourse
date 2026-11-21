package app.numbaseball.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.numbaseball.R
import app.numbaseball.audio.GameAudio
import app.numbaseball.audio.HapticCue
import app.numbaseball.audio.LocalGameHaptics
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.MusicTrack
import app.numbaseball.audio.ScreenMusic
import app.numbaseball.audio.SoundCue
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.rules.NumberProblem
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.computer.levelLabel
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SecondaryButton
import app.numbaseball.ui.common.SectionLabel
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbSpace
import app.numbaseball.ui.theme.NbType

/**
 * The duel board: status line, my number, both histories and the keypad. No ads may appear on this screen (README).
 * Phase 1 layout; phase 2 implements design/screens exactly (slots, lamps, memo board).
 */
@Composable
fun MatchScreen(
    state: MatchUiState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onRandomSecret: (() -> Unit)?,
    onForfeit: () -> Unit,
    onBack: () -> Unit,
    banner: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    var confirmForfeit by rememberSaveable { mutableStateOf(false) }
    val audio = LocalGameAudio.current
    val haptics = LocalGameHaptics.current
    ScreenMusic(MusicTrack.MATCH, audio)
    MatchSounds(state, audio)
    ScreenScaffold(
        title = title(state),
        onBack = onBack,
        actions = {
            if (state.phase != MatchPhase.FINISHED && state.mySecret != null) {
                TextButton(onClick = { confirmForfeit = true }) { Text(stringResource(R.string.match_forfeit)) }
            }
        },
    ) {
        banner()
        Text(statusText(state), style = MaterialTheme.typography.bodyLarge)
        state.secondsLeft?.let {
            Text(pluralStringResource(R.plurals.match_seconds_left, it, it), style = NbType.numTimer, color = NbColors.strike)
        }
        state.mySecret?.let {
            Text(stringResource(R.string.match_my_secret, it), style = NbType.numTag, color = NbColors.text2)
        }
        if (state.acceptsInput || state.input.isNotEmpty()) {
            InputSlots(state.input, state.digits)
            state.inputProblem?.let { Text(problemText(it, state.digits), color = NbColors.out, style = MaterialTheme.typography.bodySmall) }
            ErrorText(state.error)
            DigitKeypad(
                input = state.input,
                enabled = state.acceptsInput,
                onDigit = {
                    audio.play(SoundCue.KEYPAD)
                    haptics.perform(HapticCue.KEY_TAP)
                    onDigit(it)
                },
                onDelete = {
                    audio.play(SoundCue.KEYPAD)
                    haptics.perform(HapticCue.KEY_TAP)
                    onDelete()
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s8), modifier = Modifier.fillMaxWidth()) {
                if (state.phase == MatchPhase.CHOOSING_SECRET && onRandomSecret != null) {
                    SecondaryButton(stringResource(R.string.match_random), onRandomSecret, Modifier.weight(1f), enabled = !state.busy)
                }
                PrimaryButton(
                    text = stringResource(if (state.phase == MatchPhase.CHOOSING_SECRET) R.string.match_set_secret else R.string.match_guess),
                    onClick = {
                        audio.play(SoundCue.GUESS_SENT)
                        haptics.perform(HapticCue.CONFIRM)
                        onSubmit()
                    },
                    enabled = state.acceptsInput && state.input.length == state.digits,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        History(state.myRounds, state.opponentRounds)
        footer()
    }
    if (confirmForfeit) {
        AlertDialog(
            onDismissRequest = { confirmForfeit = false },
            text = { Text(stringResource(R.string.match_forfeit_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForfeit = false
                    onForfeit()
                }) { Text(stringResource(R.string.match_forfeit)) }
            },
            dismissButton = { TextButton(onClick = { confirmForfeit = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Hint reveal for each new round and the 10-second warning (phase 2 replaces this with the designed lamp sequence). */
@Composable
private fun MatchSounds(state: MatchUiState, audio: GameAudio) {
    val latest = state.myRounds.lastOrNull()
    LaunchedEffect(state.myRounds.size) {
        if (latest?.guess == null) return@LaunchedEffect
        audio.play(
            when {
                latest.strikes == state.digits -> SoundCue.SOLVED
                latest.strikes > 0 -> SoundCue.HINT_STRIKE
                latest.balls > 0 -> SoundCue.HINT_BALL
                else -> SoundCue.HINT_OUT
            },
        )
    }
    LaunchedEffect(state.secondsLeft == TIMER_WARNING_SECONDS) {
        if (state.secondsLeft == TIMER_WARNING_SECONDS) audio.play(SoundCue.TIMER_WARNING)
    }
}

private const val TIMER_WARNING_SECONDS = 10

@Composable
private fun title(state: MatchUiState): String = when (val o = state.opponent) {
    is Opponent.Computer -> stringResource(
        if (state.mode == MatchMode.PRACTICE) R.string.match_title_practice else R.string.match_title_computer,
        levelLabel(o.level),
    )
    is Opponent.Player -> stringResource(R.string.match_title_online, o.displayName)
}

@Composable
private fun statusText(state: MatchUiState): String = when (state.phase) {
    MatchPhase.CHOOSING_SECRET -> pluralStringResource(R.plurals.match_choose_secret, state.digits, state.digits)
    MatchPhase.WAITING_FOR_OPPONENT_SECRET -> stringResource(R.string.match_waiting_secret)
    MatchPhase.GUESSING -> if (state.busy && state.opponent is Opponent.Computer) {
        stringResource(R.string.match_computer_thinking)
    } else {
        stringResource(R.string.match_round, state.round) + " · " + stringResource(R.string.match_your_turn)
    }
    MatchPhase.WAITING_FOR_OPPONENT_GUESS -> stringResource(R.string.match_waiting_guess)
    MatchPhase.FINISHED -> stringResource(R.string.match_duel_over)
}

@Composable
private fun problemText(problem: NumberProblem, digits: Int): String = when (problem) {
    NumberProblem.REPEATED_DIGIT -> stringResource(R.string.number_error_repeated)
    NumberProblem.WRONG_LENGTH, NumberProblem.NOT_DIGITS -> pluralStringResource(R.plurals.number_error_length, digits, digits)
}

@Composable
private fun InputSlots(input: String, digits: Int) {
    val shown = (0 until digits).joinToString(" ") { i -> input.getOrNull(i)?.toString() ?: "_" }
    Text(
        text = shown,
        style = NbType.numSlot,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = input },
    )
}

/** 3 x 4 keypad: 1-9, delete, 0. Digits already used are disabled because numbers have different digits. */
@Composable
fun DigitKeypad(input: String, enabled: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(NbSize.keyGap), modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(NbSize.keyGap), modifier = Modifier.fillMaxWidth()) {
                row.forEach { d -> Key(d.toString(), enabled && d !in input, Modifier.weight(1f)) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NbSize.keyGap), modifier = Modifier.fillMaxWidth()) {
            Key("⌫", enabled && input.isNotEmpty(), Modifier.weight(1f), stringResource(R.string.match_delete), onDelete)
            Key("0", enabled && '0' !in input, Modifier.weight(1f)) { onDigit('0') }
            Row(Modifier.weight(1f)) {}
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, modifier: Modifier, description: String? = null, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = NbSize.keyHeight)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) { Text(label, style = NbType.numKey) }
}

@Composable
private fun History(myRounds: List<RoundEntry>, opponentRounds: List<RoundEntry>) {
    if (myRounds.isEmpty() && opponentRounds.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s16), modifier = Modifier.fillMaxWidth().padding(top = NbSpace.s8)) {
        HistoryColumn(stringResource(R.string.match_me), myRounds, Modifier.weight(1f))
        HistoryColumn(stringResource(R.string.match_opponent), opponentRounds, Modifier.weight(1f))
    }
}

@Composable
private fun HistoryColumn(title: String, rounds: List<RoundEntry>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NbSpace.s4)) {
        SectionLabel(title)
        rounds.asReversed().forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${entry.round}", style = NbType.numLabel, color = NbColors.text3, modifier = Modifier.width(24.dp))
                Text(entry.guess ?: stringResource(R.string.match_missed), style = NbType.numLog, modifier = Modifier.weight(1f))
                Text(hintText(entry), style = NbType.numHint, color = hintColor(entry))
            }
        }
    }
}

@Composable
fun hintText(entry: RoundEntry): String = when {
    entry.guess == null -> "–"
    entry.strikes == 0 && entry.balls == 0 -> stringResource(R.string.hint_out)
    else -> stringResource(R.string.hint_strikes_balls, entry.strikes, entry.balls)
}

private fun hintColor(entry: RoundEntry) = when {
    entry.guess == null -> NbColors.text3
    entry.strikes == 0 && entry.balls == 0 -> NbColors.out
    entry.strikes > 0 -> NbColors.strike
    else -> NbColors.ball
}
