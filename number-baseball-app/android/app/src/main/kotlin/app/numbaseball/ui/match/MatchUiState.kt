package app.numbaseball.ui.match

import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.rules.NumberProblem
import app.numbaseball.ui.common.UiError

enum class MatchPhase { CHOOSING_SECRET, WAITING_FOR_OPPONENT_SECRET, GUESSING, WAITING_FOR_OPPONENT_GUESS, FINISHED }

sealed interface Opponent {
    data class Computer(val level: ComputerLevel) : Opponent
    data class Player(val nickname: String, val tag: String) : Opponent {
        val displayName: String get() = "$nickname#$tag"
    }
}

/** Match variants of 05 §5. */
enum class MatchMode { COMPUTER, PRACTICE, QUICK, ROOM, TEAM;

    val online: Boolean get() = this == QUICK || this == ROOM || this == TEAM
}

enum class Outcome { WIN, LOSE, DRAW }

/** Duel strip status (DESIGN_SYSTEM 12.3). */
sealed interface OpponentStatus {
    data object Picking : OpponentStatus
    data object Ready : OpponentStatus
    data object Thinking : OpponentStatus
    data object Pitched : OpponentStatus
    data class Disconnected(val secondsLeft: Int) : OpponentStatus
}

/** Waiting-room practice band (05 §2.11). */
data class PracticeInfo(val roomCode: String, val present: Int, val total: Int)

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
    val mySecret: String = "",
    val opponentName: String = "",
    /** Strikes per round for the line score (06 §1); null entries = missed rounds. */
    val myStrikes: List<Int?> = emptyList(),
    val opponentStrikes: List<Int?> = emptyList(),
    val durationSeconds: Int? = null,
    /** vs Computer: the timer, so "play again" keeps the same settings. */
    val roundSeconds: Int? = null,
    /** True when I was the one who forfeited / timed out / disconnected (sub line wording). */
    val iEnded: Boolean = false,
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
    /** Length of the current phase or round (clock line fraction). */
    val roundSeconds: Int? = null,
    val busy: Boolean = false,
    val error: UiError? = null,
    val summary: MatchSummary? = null,
    val opponentStatus: OpponentStatus = OpponentStatus.Thinking,
    val memo: MemoState = MemoState(digits),
    val teamScore: Pair<Int, Int>? = null,
    val practice: PracticeInfo? = null,
    /** My connection is lost: seconds left before the duel is forfeited (connection banner). */
    val reconnectSeconds: Int? = null,
) {
    val acceptsInput: Boolean
        get() = !busy && reconnectSeconds == null && (phase == MatchPhase.CHOOSING_SECRET || phase == MatchPhase.GUESSING)
}
