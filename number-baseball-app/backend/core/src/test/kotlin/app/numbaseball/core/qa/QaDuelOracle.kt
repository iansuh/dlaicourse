package app.numbaseball.core.qa

/**
 * QA reference model of one duel, written from docs/protocol.md section 8.3 (not from Duel.kt):
 * - CHOOSING with deadline start + T (no deadline when T is "none"); at the deadline a player without a secret gets
 *   one assigned and that counts as one missed round;
 * - GUESSING round n resolves when both guessed or at the deadline; no guess = missed entry and misses + 1, a guess
 *   resets misses; next round starts at the resolution time;
 * - resolution order: both solved -> draw both_solved; one solved -> solved; both at the miss limit -> draw
 *   both_timeout; one at the limit -> timeout;
 * - a player disconnected for the grace period loses "disconnect" (both at the same instant -> draw);
 * - forfeit -> loss "forfeit".
 * Tie between a round deadline and a disconnect expiry: the round resolves first (DEF-030 asks the spec to say so).
 *
 * Actions return the set of acceptable rejection codes (empty = must be accepted), because protocol.md does not fix
 * the precedence when several errors apply at once.
 */
class QaDuelOracle(
    val digits: Int,
    val roundMs: Long?,
    val missLimit: Int = 2,
    val graceMs: Long = 60_000L,
) {
    enum class Phase { NOT_STARTED, CHOOSING, GUESSING, FINISHED }

    data class Entry(val round: Int, val guess: String?, val strikes: Int, val balls: Int)

    /** winner: 0 = side A, 1 = side B, null = draw / no winner. */
    data class Result(val winner: Int?, val reason: String)

    var phase = Phase.NOT_STARTED
        private set
    var round = 0
        private set
    var deadline: Long? = null
        private set
    var result: Result? = null
        private set

    val secret = arrayOfNulls<String>(2)
    val pending = arrayOfNulls<String>(2)
    val misses = IntArray(2)
    val rounds: List<MutableList<Entry>> = listOf(ArrayList(), ArrayList())
    val disconnectedAt = arrayOfNulls<Long>(2)

    /** Supplies the secret the engine assigned on a choosing timeout (its RNG cannot be predicted). */
    var assignSecret: (side: Int) -> String = { error("assignSecret not set") }

    fun start(now: Long) {
        check(phase == Phase.NOT_STARTED)
        phase = Phase.CHOOSING
        deadline = roundMs?.let { now + it }
    }

    private val running get() = phase == Phase.CHOOSING || phase == Phase.GUESSING

    fun tick(now: Long) {
        while (running) {
            val d = deadline
            val e0 = disconnectedAt[0]?.plus(graceMs)
            val e1 = disconnectedAt[1]?.plus(graceMs)
            val t = listOfNotNull(d, e0, e1).minOrNull() ?: return
            if (t > now) return
            if (d == t) {
                onDeadline(t)
            } else when {
                e0 == t && e1 == t -> finish(null, "disconnect")
                e0 == t -> finish(1, "disconnect")
                else -> finish(0, "disconnect")
            }
        }
    }

    private fun onDeadline(t: Long) {
        if (phase == Phase.CHOOSING) {
            for (side in 0..1) if (secret[side] == null) {
                val s = assignSecret(side)
                check(QaOracle.isValid(s, digits)) { "engine assigned an invalid secret <$s>" }
                secret[side] = s
                misses[side]++
            }
            if (!finishOnMisses()) startRound(t)
        } else {
            resolve(t)
        }
    }

    private fun startRound(t: Long) {
        phase = Phase.GUESSING
        round++
        deadline = roundMs?.let { t + it }
    }

    private fun resolve(t: Long) {
        val solved = BooleanArray(2)
        for (side in 0..1) {
            val g = pending[side]
            if (g == null) {
                rounds[side] += Entry(round, null, 0, 0)
                misses[side]++
            } else {
                val s = QaOracle.judge(secret[1 - side]!!, g)
                rounds[side] += Entry(round, g, s.strikes, s.balls)
                misses[side] = 0
                solved[side] = s.strikes == digits
            }
        }
        pending[0] = null
        pending[1] = null
        when {
            solved[0] && solved[1] -> finish(null, "both_solved")
            solved[0] -> finish(0, "solved")
            solved[1] -> finish(1, "solved")
            finishOnMisses() -> Unit
            else -> startRound(t)
        }
    }

    private fun finishOnMisses(): Boolean {
        val out0 = misses[0] >= missLimit
        val out1 = misses[1] >= missLimit
        when {
            out0 && out1 -> finish(null, "both_timeout")
            out0 -> finish(1, "timeout")
            out1 -> finish(0, "timeout")
            else -> return false
        }
        return true
    }

    private fun finish(winner: Int?, reason: String) {
        phase = Phase.FINISHED
        result = Result(winner, reason)
        deadline = null
        pending[0] = null
        pending[1] = null
    }

    fun setSecret(side: Int, value: String, now: Long): Set<String> {
        tick(now)
        return when (phase) {
            Phase.NOT_STARTED, Phase.FINISHED -> setOf("wrong_phase")
            Phase.GUESSING -> setOf("secret_already_set", "wrong_phase")
            Phase.CHOOSING -> when {
                secret[side] != null -> setOf("secret_already_set")
                !QaOracle.isValid(value, digits) -> setOf("invalid_secret")
                else -> {
                    secret[side] = value
                    if (secret[0] != null && secret[1] != null) startRound(now)
                    emptySet()
                }
            }
        }
    }

    fun guess(side: Int, value: String, forRound: Int, now: Long): Set<String> {
        tick(now)
        if (phase != Phase.GUESSING) return setOf("wrong_phase")
        val problems = buildSet {
            if (!QaOracle.isValid(value, digits)) add("invalid_guess")
            if (forRound != round) add("stale_round")
            if (pending[side] != null) add("already_guessed")
        }
        if (problems.isNotEmpty()) return problems
        pending[side] = value
        if (pending[0] != null && pending[1] != null) resolve(now)
        return emptySet()
    }

    fun forfeit(side: Int, now: Long): Set<String> {
        tick(now)
        if (!running) return setOf("wrong_phase")
        finish(1 - side, "forfeit")
        return emptySet()
    }

    fun disconnect(side: Int, now: Long) {
        tick(now)
        if (disconnectedAt[side] == null) disconnectedAt[side] = now
    }

    fun reconnect(side: Int, now: Long) {
        tick(now)
        disconnectedAt[side] = null
    }

    /** Earliest pending timer, for scripts that want to hit boundaries exactly. */
    fun nextTimer(): Long? = if (!running) null else listOfNotNull(
        deadline, disconnectedAt[0]?.plus(graceMs), disconnectedAt[1]?.plus(graceMs),
    ).minOrNull()
}
