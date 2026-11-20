package app.numbaseball.core.match

import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.rules.Rules
import kotlin.random.Random

data class DuelConfig(
    val digits: Int,
    /** Round (and secret-choice) timer; null = no timer. */
    val roundMillis: Long?,
    val missLimit: Int = Limits.MISS_LIMIT,
    val disconnectGraceMillis: Long = Limits.DISCONNECT_GRACE_MS,
) {
    init {
        require(Rules.isValidDigitCount(digits)) { "digits" }
        require(roundMillis == null || roundMillis > 0) { "roundMillis" }
        require(missLimit >= 1) { "missLimit" }
    }

    companion object {
        /** [roundSeconds] 0 means no timer. */
        fun of(digits: Int, roundSeconds: Int): DuelConfig =
            DuelConfig(digits, if (roundSeconds <= 0) null else roundSeconds * 1000L)
    }
}

/** Outcome of a player action on the engine. */
sealed interface ActionResult {
    data object Ok : ActionResult
    data class Rejected(val code: String) : ActionResult
}

/**
 * One duel: two players (team A and team B side), simultaneous rounds.
 *
 * Pure state machine: no clock and no threads inside. Every call takes the current time `now` (any monotonic
 * millisecond clock), and [advance] processes deadlines that have passed, in time order. Actions call
 * [advance] first, so callers only need to call it periodically (or at [nextWakeup]) to fire timers.
 * Randomness (secrets assigned on a choice timeout) comes from [random].
 */
class Duel(val config: DuelConfig, private val random: Random = Random.Default) {
    var phase: DuelPhase = DuelPhase.CHOOSING
        private set
    /** 0 while choosing, then the current round (1-based); after the end, the last round played. */
    var round: Int = 0
        private set
    /** When the current phase or round times out; null = no timer, not started, or finished. */
    var deadline: Long? = null
        private set
    var result: DuelResult? = null
        private set
    var started: Boolean = false
        private set
    var endedAt: Long? = null
        private set

    private val secrets = arrayOfNulls<String>(2)
    private val pending = arrayOfNulls<String>(2)
    private val rounds = arrayOf(ArrayList<RoundEntry>(), ArrayList<RoundEntry>())
    private val misses = IntArray(2)
    private val disconnectedAt = arrayOfNulls<Long>(2)

    val isFinished: Boolean get() = phase == DuelPhase.FINISHED

    fun secretOf(team: Team): String? = secrets[team.ordinal]
    fun pendingGuessOf(team: Team): String? = pending[team.ordinal]
    /** Guesses made by [team] (against the other side's secret), oldest first. */
    fun roundsOf(team: Team): List<RoundEntry> = rounds[team.ordinal]
    fun missesOf(team: Team): Int = misses[team.ordinal]
    fun disconnectedSince(team: Team): Long? = disconnectedAt[team.ordinal]

    fun start(now: Long) {
        check(!started) { "already started" }
        started = true
        phase = DuelPhase.CHOOSING
        deadline = config.roundMillis?.let { now + it }
    }

    fun setSecret(team: Team, secret: String, now: Long): ActionResult {
        advance(now)
        val i = team.ordinal
        if (phase != DuelPhase.CHOOSING || !started) {
            return reject(if (secrets[i] != null && phase != DuelPhase.FINISHED) ErrorCodes.SECRET_ALREADY_SET else ErrorCodes.WRONG_PHASE)
        }
        if (secrets[i] != null) return reject(ErrorCodes.SECRET_ALREADY_SET)
        if (!Rules.isValid(secret, config.digits)) return reject(ErrorCodes.INVALID_SECRET)
        secrets[i] = secret
        if (secrets[0] != null && secrets[1] != null) startRound(now)
        return ActionResult.Ok
    }

    fun guess(team: Team, guess: String, round: Int, now: Long): ActionResult {
        advance(now)
        val i = team.ordinal
        if (phase != DuelPhase.GUESSING) return reject(ErrorCodes.WRONG_PHASE)
        if (!Rules.isValid(guess, config.digits)) return reject(ErrorCodes.INVALID_GUESS)
        if (round != this.round) return reject(ErrorCodes.STALE_ROUND)
        if (pending[i] != null) return reject(ErrorCodes.ALREADY_GUESSED)
        pending[i] = guess
        if (pending[0] != null && pending[1] != null) resolveRound(now)
        return ActionResult.Ok
    }

    /** [team] gives up (explicit forfeit or leaving the room). */
    fun forfeit(team: Team, now: Long): ActionResult {
        advance(now)
        if (!started || phase == DuelPhase.FINISHED) return reject(ErrorCodes.WRONG_PHASE)
        finish(team.other, EndReason.FORFEIT, now)
        return ActionResult.Ok
    }

    fun disconnected(team: Team, now: Long) {
        advance(now)
        if (disconnectedAt[team.ordinal] == null) disconnectedAt[team.ordinal] = now
    }

    fun reconnected(team: Team, now: Long) {
        advance(now)
        disconnectedAt[team.ordinal] = null
    }

    /** Ends the duel without a winner (team match already decided). */
    fun cancel(now: Long) {
        if (phase == DuelPhase.FINISHED) return
        finish(null, EndReason.CANCELLED, now)
    }

    /** The earliest time at which [advance] would change something, or null. */
    fun nextWakeup(): Long? {
        if (!started || phase == DuelPhase.FINISHED) return null
        var t = deadline
        for (d in disconnectedAt) if (d != null) {
            val expiry = d + config.disconnectGraceMillis
            if (t == null || expiry < t) t = expiry
        }
        return t
    }

    /** Processes every deadline up to and including [now], oldest first. Returns true if the state changed. */
    fun advance(now: Long): Boolean {
        var changed = false
        while (started && phase != DuelPhase.FINISHED) {
            val t = nextWakeup() ?: break
            if (t > now) break
            changed = true
            if (deadline == t) {
                // A round deadline wins a tie with a disconnect expiry: a solve in that round still counts.
                onDeadline(t)
            } else {
                val expA = disconnectedAt[0]?.plus(config.disconnectGraceMillis)
                val expB = disconnectedAt[1]?.plus(config.disconnectGraceMillis)
                when {
                    expA == t && expB == t -> finish(null, EndReason.DISCONNECT, t)
                    expA == t -> finish(Team.B, EndReason.DISCONNECT, t)
                    else -> finish(Team.A, EndReason.DISCONNECT, t)
                }
            }
        }
        return changed
    }

    private fun onDeadline(t: Long) {
        when (phase) {
            DuelPhase.CHOOSING -> {
                for (i in 0..1) if (secrets[i] == null) {
                    secrets[i] = Rules.randomNumber(config.digits, random)
                    misses[i]++
                }
                if (!finishOnMisses(t)) startRound(t)
            }
            DuelPhase.GUESSING -> resolveRound(t)
            DuelPhase.FINISHED -> Unit
        }
    }

    private fun startRound(t: Long) {
        phase = DuelPhase.GUESSING
        round++
        deadline = config.roundMillis?.let { t + it }
    }

    private fun resolveRound(t: Long) {
        val solved = BooleanArray(2)
        for (i in 0..1) {
            val g = pending[i]
            if (g != null) {
                val j = Rules.judge(secrets[1 - i]!!, g)
                rounds[i] += RoundEntry(round, g, j.strikes, j.balls)
                misses[i] = 0
                solved[i] = j.isSolved(config.digits)
            } else {
                rounds[i] += RoundEntry(round, null, 0, 0)
                misses[i]++
            }
        }
        pending[0] = null
        pending[1] = null
        when {
            solved[0] && solved[1] -> finish(null, EndReason.BOTH_SOLVED, t)
            solved[0] -> finish(Team.A, EndReason.SOLVED, t)
            solved[1] -> finish(Team.B, EndReason.SOLVED, t)
            finishOnMisses(t) -> Unit
            else -> startRound(t)
        }
    }

    private fun finishOnMisses(t: Long): Boolean {
        val outA = misses[0] >= config.missLimit
        val outB = misses[1] >= config.missLimit
        when {
            outA && outB -> finish(null, EndReason.BOTH_TIMEOUT, t)
            outA -> finish(Team.B, EndReason.TIMEOUT, t)
            outB -> finish(Team.A, EndReason.TIMEOUT, t)
            else -> return false
        }
        return true
    }

    private fun finish(winner: Team?, reason: EndReason, t: Long) {
        phase = DuelPhase.FINISHED
        result = DuelResult(winner, reason)
        deadline = null
        endedAt = t
        pending[0] = null
        pending[1] = null
    }

    private fun reject(code: String) = ActionResult.Rejected(code)
}
