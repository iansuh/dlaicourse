package app.numbaseball.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.Team
import app.numbaseball.game.offline.OfflineGame
import app.numbaseball.game.offline.OfflineMatch
import app.numbaseball.game.offline.OfflineSnapshot
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * vs-computer match (03, 05), also the practice game while waiting in a room ([practice]). The computer "pitches" after
 * a short random delay so the waiting state is visible (03: 0.6–1.8 s, hard 1.2–2.4 s).
 */
class ComputerMatchViewModel(
    digits: Int,
    level: ComputerLevel,
    private val practice: Boolean,
    private val game: OfflineGame,
    private val roundSeconds: Int? = RELAXED_SECONDS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val pitchDelayMillis: (ComputerLevel) -> Long = { lvl ->
        if (lvl == ComputerLevel.HARD) Random.nextLong(1_200, 2_400) else Random.nextLong(600, 1_800)
    },
) : ViewModel() {
    private val _state = MutableStateFlow(
        MatchUiState(
            digits = digits,
            opponent = Opponent.Computer(level),
            mode = if (practice) MatchMode.PRACTICE else MatchMode.COMPUTER,
            opponentStatus = OpponentStatus.Ready,
            roundSeconds = roundSeconds,
            secondsLeft = roundSeconds,
        ),
    )
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    private var match: OfflineMatch? = null
    private var ticker: Job? = null
    private val startedAt = clock()

    fun onDigit(digit: Char) = _state.update {
        if (it.acceptsInput) it.copy(input = NumberInput.append(it.input, digit, it.digits), inputProblem = null) else it
    }

    fun onDelete() = _state.update {
        if (it.acceptsInput) it.copy(input = NumberInput.deleteLast(it.input), inputProblem = null) else it
    }

    fun onClear() = _state.update { if (it.acceptsInput) it.copy(input = "", inputProblem = null) else it }

    /** Tapping a filled slot clears it and every slot after it (DESIGN_SYSTEM 12.4). */
    fun onTapSlot(index: Int) = _state.update { if (it.acceptsInput) it.copy(input = it.input.take(index), inputProblem = null) else it }

    /** Fills the remaining slots with random unused digits (secret entry `shuffle`). */
    fun onRandomSecret() = _state.update {
        if (it.phase == MatchPhase.CHOOSING_SECRET && !it.busy) it.copy(input = NumberInput.fillRandom(it.input, it.digits), inputProblem = null) else it
    }

    fun onMemoDigit(digit: Int) = _state.update { it.copy(memo = Memo.cycleDigit(it.memo, digit)) }
    fun onMemoCell(position: Int, digit: Int) = _state.update { it.copy(memo = Memo.cycleCell(it.memo, position, digit)) }
    fun onMemoClear() = _state.update { it.copy(memo = Memo.clear(it.memo)) }
    fun onMemoCollapse(collapsed: Boolean) = _state.update { it.copy(memo = Memo.setCollapsed(it.memo, collapsed)) }

    fun onSubmit() {
        val current = _state.value
        if (!current.acceptsInput) return
        val problem = NumberInput.problem(current.input, current.digits)
        if (problem != null) {
            _state.update { it.copy(inputProblem = problem) }
            return
        }
        if (current.phase == MatchPhase.CHOOSING_SECRET) {
            _state.update { it.copy(busy = true) }
            viewModelScope.launch {
                val started = game.start(current.digits, (current.opponent as Opponent.Computer).level, current.input, roundSeconds, clock())
                match = started
                apply(started.current(clock()), clearInput = true)
                startTicker()
            }
        } else {
            val m = match ?: return
            _state.update { it.copy(busy = true, phase = MatchPhase.WAITING_FOR_OPPONENT_GUESS, opponentStatus = OpponentStatus.Thinking) }
            viewModelScope.launch {
                // The engine resolves at once (the timer must not hit a pitched round); the reveal waits for the "pitch".
                val snapshot = m.play(current.input, clock())
                delay(pitchDelayMillis((current.opponent as Opponent.Computer).level))
                apply(snapshot, clearInput = true)
            }
        }
    }

    fun onForfeit() {
        val m = match ?: return
        if (_state.value.phase == MatchPhase.FINISHED) return
        viewModelScope.launch { apply(m.forfeit(clock()), clearInput = true) }
    }

    private fun startTicker() {
        if (roundSeconds == null || roundSeconds <= 0) return
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive && _state.value.phase != MatchPhase.FINISHED) {
                delay(TICK_MILLIS)
                val m = match ?: continue
                if (_state.value.phase == MatchPhase.WAITING_FOR_OPPONENT_GUESS) continue
                val snapshot = m.tick(clock())
                val before = _state.value.myRounds.size
                if (snapshot.myRounds.size != before || snapshot.result != null) {
                    apply(snapshot, clearInput = true)
                } else {
                    _state.update { it.copy(secondsLeft = snapshot.secondsLeft) }
                }
            }
        }
    }

    private fun apply(snapshot: OfflineSnapshot, clearInput: Boolean) {
        val result = snapshot.result
        _state.update {
            it.copy(
                phase = if (result == null) MatchPhase.GUESSING else MatchPhase.FINISHED,
                input = if (clearInput) "" else it.input,
                busy = false,
                mySecret = snapshot.mySecret,
                round = snapshot.round,
                myRounds = snapshot.myRounds,
                opponentRounds = snapshot.computerRounds,
                secondsLeft = snapshot.secondsLeft,
                roundSeconds = snapshot.roundSeconds,
                opponentStatus = if (result == null) OpponentStatus.Thinking else OpponentStatus.Pitched,
                summary = result?.let { r -> summaryOf(it, r, snapshot) },
                memo = if (result != null) Memo.clear(it.memo) else it.memo,
            )
        }
        if (result != null) {
            ticker?.cancel()
        }
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
        mySecret = snapshot.mySecret,
        myStrikes = snapshot.myRounds.map { r -> r.guess?.let { r.strikes } },
        opponentStrikes = snapshot.computerRounds.map { r -> r.guess?.let { r.strikes } },
        durationSeconds = ((clock() - startedAt) / 1000).toInt(),
        roundSeconds = roundSeconds,
        iEnded = result.winner == Team.B && result.reason in setOf(EndReason.FORFEIT, EndReason.TIMEOUT, EndReason.DISCONNECT),
    )

    companion object {
        /** 느긋하게 / Relaxed = 10 minutes, the vs-computer default (03). */
        const val RELAXED_SECONDS = app.numbaseball.ui.computer.RELAXED_SECONDS
        private const val TICK_MILLIS = 1_000L
    }
}
