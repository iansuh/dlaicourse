package app.numbaseball.game.offline

import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.ai.ComputerPlayer
import app.numbaseball.core.match.ActionResult
import app.numbaseball.core.match.Duel
import app.numbaseball.core.match.DuelConfig
import app.numbaseball.core.protocol.DuelPhase
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
 * on [dispatcher], because a hard 6-digit move can take a few milliseconds. The computer submits its guess when a round
 * starts, so the player's pitch resolves the round and an expired round timer counts as the player's miss.
 */
class OfflineGame(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val random: Random = Random.Default,
) {
    /** Player = team A, computer = team B. [roundSeconds] 0 or null = no timer. [now] is any monotonic ms clock. */
    suspend fun start(digits: Int, level: ComputerLevel, secret: String, roundSeconds: Int? = null, now: Long = 0): OfflineMatch =
        withContext(dispatcher) {
            val computer = ComputerPlayer(level, digits, random)
            val duel = Duel(DuelConfig.of(digits, roundSeconds ?: 0), random)
            duel.start(now)
            check(duel.setSecret(Team.A, secret, now) == ActionResult.Ok) { "invalid secret" }
            check(duel.setSecret(Team.B, computer.chooseSecret(), now) == ActionResult.Ok)
            OfflineMatch(duel, computer, dispatcher).also { it.computerPitches(now) }
        }
}

class OfflineMatch internal constructor(
    private val duel: Duel,
    private val computer: ComputerPlayer,
    private val dispatcher: CoroutineDispatcher,
) {
    private val lock = Mutex()
    private var computerRoundsSeen = 0

    val digits: Int get() = duel.config.digits
    val level: ComputerLevel get() = computer.level

    /** The player's pitch for the current round; the computer's pitch is already in, so the round resolves. */
    suspend fun play(guess: String, now: Long = 0): OfflineSnapshot = withContext(dispatcher) {
        lock.withLock {
            duel.advance(now)
            if (!duel.isFinished) {
                val result = duel.guess(Team.A, guess, duel.round, now)
                require(result == ActionResult.Ok) { "rejected: $result" }
            }
            afterResolution(now)
            snapshot(now)
        }
    }

    /** Processes an expired round timer (the player missed); call about once a second while the clock runs. */
    suspend fun tick(now: Long): OfflineSnapshot = withContext(dispatcher) {
        lock.withLock {
            duel.advance(now)
            afterResolution(now)
            snapshot(now)
        }
    }

    suspend fun forfeit(now: Long = 0): OfflineSnapshot = withContext(dispatcher) {
        lock.withLock {
            if (!duel.isFinished) duel.forfeit(Team.A, now)
            snapshot(now)
        }
    }

    suspend fun current(now: Long = 0): OfflineSnapshot = lock.withLock { snapshot(now) }

    internal fun computerPitches(now: Long) {
        if (duel.phase == DuelPhase.GUESSING && duel.pendingGuessOf(Team.B) == null) {
            check(duel.guess(Team.B, computer.nextGuess(), duel.round, now) == ActionResult.Ok)
        }
    }

    /** Teaches the computer its new hints and lets it pitch for the next round. */
    private fun afterResolution(now: Long) {
        val rounds = duel.roundsOf(Team.B)
        while (computerRoundsSeen < rounds.size) {
            val entry = rounds[computerRoundsSeen++]
            entry.guess?.let { computer.onResult(it, entry.strikes, entry.balls) }
        }
        if (!duel.isFinished) computerPitches(now)
    }

    private fun snapshot(now: Long) = OfflineSnapshot(
        mySecret = duel.secretOf(Team.A)!!,
        myRounds = duel.roundsOf(Team.A).toList(),
        computerRounds = duel.roundsOf(Team.B).toList(),
        result = duel.result,
        computerSecret = if (duel.isFinished) duel.secretOf(Team.B) else null,
        secondsLeft = duel.deadline?.let { ((it - now + 999) / 1000).coerceAtLeast(0).toInt() },
        roundSeconds = duel.config.roundMillis?.let { (it / 1000).toInt() },
        round = duel.round,
    )
}

/** Player = team A. [computerSecret] is revealed only after the end. */
data class OfflineSnapshot(
    val mySecret: String,
    val myRounds: List<RoundEntry>,
    val computerRounds: List<RoundEntry>,
    val result: DuelResult?,
    val computerSecret: String?,
    val secondsLeft: Int? = null,
    val roundSeconds: Int? = null,
    val round: Int = 0,
)
