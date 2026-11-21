package app.numbaseball.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.ads.ComputerMatchFinishedListener
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.rules.Rules
import app.numbaseball.game.offline.OfflineGame
import app.numbaseball.game.offline.OfflineMatch
import app.numbaseball.game.offline.OfflineSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** vs-computer match, also used for the practice game while waiting in a room ([practice]). */
class ComputerMatchViewModel(
    digits: Int,
    level: ComputerLevel,
    private val practice: Boolean,
    private val game: OfflineGame,
    private val finishedListener: ComputerMatchFinishedListener,
) : ViewModel() {
    private val _state = MutableStateFlow(
        MatchUiState(
            digits = digits,
            opponent = Opponent.Computer(level),
            mode = if (practice) MatchMode.PRACTICE else MatchMode.COMPUTER,
        ),
    )
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    private var match: OfflineMatch? = null

    fun onDigit(digit: Char) = _state.update {
        if (it.acceptsInput) it.copy(input = NumberInput.append(it.input, digit, it.digits), inputProblem = null) else it
    }

    fun onDelete() = _state.update {
        if (it.acceptsInput) it.copy(input = NumberInput.deleteLast(it.input), inputProblem = null) else it
    }

    fun onRandomSecret() = _state.update {
        if (it.phase == MatchPhase.CHOOSING_SECRET && !it.busy) it.copy(input = Rules.randomNumber(it.digits), inputProblem = null) else it
    }

    fun onSubmit() {
        val current = _state.value
        if (!current.acceptsInput) return
        val problem = NumberInput.problem(current.input, current.digits)
        if (problem != null) {
            _state.update { it.copy(inputProblem = problem) }
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            if (current.phase == MatchPhase.CHOOSING_SECRET) {
                val started = game.start(current.digits, (current.opponent as Opponent.Computer).level, current.input)
                match = started
                apply(started.current())
            } else {
                apply(checkNotNull(match).play(current.input))
            }
        }
    }

    fun onForfeit() {
        val m = match ?: return
        if (_state.value.phase == MatchPhase.FINISHED) return
        viewModelScope.launch { apply(m.forfeit()) }
    }

    private fun apply(snapshot: OfflineSnapshot) {
        val result = snapshot.result
        _state.update {
            it.copy(
                phase = if (result == null) MatchPhase.GUESSING else MatchPhase.FINISHED,
                input = "",
                busy = false,
                mySecret = snapshot.mySecret,
                round = snapshot.myRounds.size + if (result == null) 1 else 0,
                myRounds = snapshot.myRounds,
                opponentRounds = snapshot.computerRounds,
                summary = result?.let { r -> summaryOf(it, r, snapshot) },
            )
        }
        if (result != null && !practice) finishedListener.onComputerMatchFinished()
    }

    private fun summaryOf(state: MatchUiState, result: DuelResult, snapshot: OfflineSnapshot) = MatchSummary(
        mode = state.mode,
        outcome = when (result.winner) {
            Team.A -> Outcome.WIN
            Team.B -> Outcome.LOSE
            null -> Outcome.DRAW
        },
        reason = result.reason,
        rounds = snapshot.myRounds.size,
        opponentSecret = snapshot.computerSecret.orEmpty(),
        digits = state.digits,
        level = (state.opponent as Opponent.Computer).level,
    )
}
