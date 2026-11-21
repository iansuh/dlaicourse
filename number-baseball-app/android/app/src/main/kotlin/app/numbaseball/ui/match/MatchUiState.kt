package app.numbaseball.ui.match

import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.rules.NumberProblem
import app.numbaseball.ui.common.UiError

enum class MatchPhase { CHOOSING_SECRET, WAITING_FOR_OPPONENT_SECRET, GUESSING, WAITING_FOR_OPPONENT_GUESS, FINISHED }

sealed interface Opponent {
    data class Computer(val level: ComputerLevel) : Opponent
    data class Player(val displayName: String) : Opponent
}

enum class MatchMode { COMPUTER, PRACTICE, ONLINE }

enum class Outcome { WIN, LOSE, DRAW }

/** What the results screen needs; emitted once when a duel (or a team match) is over. */
data class MatchSummary(
    val mode: MatchMode,
    val outcome: Outcome,
    val reason: EndReason,
    val rounds: Int,
    val opponentSecret: String,
    val digits: Int,
    val level: ComputerLevel? = null,
    val teamMatch: Boolean = false,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
)

/** Shared by vs-computer, waiting-room practice and online duels; the screen does not know which engine is behind it. */
data class MatchUiState(
    val digits: Int,
    val opponent: Opponent,
    val mode: MatchMode,
    val phase: MatchPhase = MatchPhase.CHOOSING_SECRET,
    val input: String = "",
    val inputProblem: NumberProblem? = null,
    val mySecret: String? = null,
    val round: Int = 0,
    /** My guesses against the opponent's secret, oldest first. */
    val myRounds: List<RoundEntry> = emptyList(),
    /** The opponent's guesses against my secret. */
    val opponentRounds: List<RoundEntry> = emptyList(),
    val secondsLeft: Int? = null,
    val busy: Boolean = false,
    val error: UiError? = null,
    val summary: MatchSummary? = null,
) {
    val acceptsInput: Boolean
        get() = !busy && (phase == MatchPhase.CHOOSING_SECRET || phase == MatchPhase.GUESSING)
}
