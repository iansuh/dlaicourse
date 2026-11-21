package app.numbaseball.game.offline

import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.ai.ComputerPlayer
import app.numbaseball.core.match.ActionResult
import app.numbaseball.core.match.Duel
import app.numbaseball.core.match.DuelConfig
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.protocol.Team
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * vs-computer games (also the practice game while waiting in a room). Wraps the core [Duel] engine and [ComputerPlayer]
 * and runs them on [dispatcher], because a hard 6-digit move can take a few milliseconds.
 */
class OfflineGame(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val random: Random = Random.Default,
) {
    /** Starts a match with the player's [secret]; the player is team A, the computer team B. No round timer offline. */
    suspend fun start(digits: Int, level: ComputerLevel, secret: String): OfflineMatch = withContext(dispatcher) {
        val computer = ComputerPlayer(level, digits, random)
        val duel = Duel(DuelConfig(digits, roundMillis = null), random)
        duel.start(now = 0)
        check(duel.setSecret(Team.A, secret, now = 0) == ActionResult.Ok) { "invalid secret" }
        check(duel.setSecret(Team.B, computer.chooseSecret(), now = 0) == ActionResult.Ok)
        OfflineMatch(duel, computer, dispatcher)
    }
}

class OfflineMatch internal constructor(
    private val duel: Duel,
    private val computer: ComputerPlayer,
    private val dispatcher: CoroutineDispatcher,
) {
    private val lock = Mutex()

    val digits: Int get() = duel.config.digits
    val level: ComputerLevel get() = computer.level

    /** Plays one simultaneous round: the player's [guess] and the computer's guess. */
    suspend fun play(guess: String): OfflineSnapshot = withContext(dispatcher) {
        lock.withLock {
            check(!duel.isFinished) { "match over" }
            val round = duel.round
            val result = duel.guess(Team.A, guess, round, now = 0)
            require(result == ActionResult.Ok) { "rejected: $result" }
            val computerGuess = computer.nextGuess()
            check(duel.guess(Team.B, computerGuess, round, now = 0) == ActionResult.Ok)
            val entry = duel.roundsOf(Team.B).last()
            computer.onResult(computerGuess, entry.strikes, entry.balls)
            snapshot()
        }
    }

    suspend fun forfeit(): OfflineSnapshot = withContext(dispatcher) {
        lock.withLock {
            if (!duel.isFinished) duel.forfeit(Team.A, now = 0)
            snapshot()
        }
    }

    suspend fun current(): OfflineSnapshot = lock.withLock { snapshot() }

    private fun snapshot() = OfflineSnapshot(
        mySecret = duel.secretOf(Team.A)!!,
        myRounds = duel.roundsOf(Team.A).toList(),
        computerRounds = duel.roundsOf(Team.B).toList(),
        result = duel.result,
        computerSecret = if (duel.isFinished) duel.secretOf(Team.B) else null,
    )
}

/** Player = team A. [computerSecret] is revealed only after the end. */
data class OfflineSnapshot(
    val mySecret: String,
    val myRounds: List<RoundEntry>,
    val computerRounds: List<RoundEntry>,
    val result: DuelResult?,
    val computerSecret: String?,
)
