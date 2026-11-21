package app.numbaseball.core.qa

import kotlin.random.Random

/**
 * QA reference implementation (oracle) of the game rules, written from README.md and docs/protocol.md only,
 * independently of `:core`. Used for differential testing (JudgeDifferentialQaTest, TeamMatchQaTest, ...).
 *
 * The judging algorithm deliberately differs from the core's bit-mask approach: it uses a digit -> position table.
 * [judgeNaive] is a second, quadratic implementation that cross-checks the oracle itself.
 */
object QaOracle {
    const val MIN_DIGITS = 3
    const val MAX_DIGITS = 6

    data class Score(val strikes: Int, val balls: Int) {
        val isOut: Boolean get() = strikes == 0 && balls == 0
        override fun toString(): String = "${strikes}S${balls}B"
    }

    /** README: 3-6 different digits, 0 may lead. Only ASCII '0'..'9' are digits (protocol 1). */
    fun isValid(value: String, digits: Int): Boolean {
        if (digits !in MIN_DIGITS..MAX_DIGITS || value.length != digits) return false
        val seen = BooleanArray(10)
        for (ch in value) {
            val d = ch.code - '0'.code
            if (d !in 0..9 || seen[d]) return false
            seen[d] = true
        }
        return true
    }

    fun judge(secret: String, guess: String): Score {
        require(secret.length == guess.length && isValid(secret, secret.length) && isValid(guess, guess.length)) {
            "oracle judge needs two valid numbers of equal length: <$secret> <$guess>"
        }
        val position = IntArray(10) { -1 }
        for (i in secret.indices) position[secret[i] - '0'] = i
        var strikes = 0
        var balls = 0
        for (i in guess.indices) {
            val p = position[guess[i] - '0']
            if (p == i) strikes++ else if (p >= 0) balls++
        }
        return Score(strikes, balls)
    }

    fun judgeNaive(secret: String, guess: String): Score {
        var s = 0
        var b = 0
        for (i in guess.indices) for (j in secret.indices) {
            if (guess[i] == secret[j]) {
                if (i == j) s++ else b++
            }
        }
        return Score(s, b)
    }

    /** P(10, digits): 720, 5040, 30240, 151200. */
    fun count(digits: Int): Int = (0 until digits).fold(1) { acc, i -> acc * (10 - i) }

    /** Every valid number of [digits] digits, in lexicographic order. */
    fun all(digits: Int): List<String> {
        val out = ArrayList<String>(count(digits))
        val buf = CharArray(digits)
        val used = BooleanArray(10)
        fun rec(pos: Int) {
            if (pos == digits) {
                out += String(buf)
                return
            }
            for (d in 0..9) if (!used[d]) {
                used[d] = true
                buf[pos] = '0' + d
                rec(pos + 1)
                used[d] = false
            }
        }
        rec(0)
        return out
    }

    fun random(digits: Int, rnd: Random): String = (0..9).shuffled(rnd).take(digits).joinToString("")

    /**
     * Hand-derived distribution of answers for ONE fixed 3-digit secret over all 720 guesses
     * (see TEST_PLAN.md section 12 for the derivation). Independent of any implementation.
     */
    val DISTRIBUTION_3: Map<Score, Int> = mapOf(
        Score(0, 0) to 210, Score(0, 1) to 252, Score(0, 2) to 63, Score(0, 3) to 2,
        Score(1, 0) to 126, Score(1, 1) to 42, Score(1, 2) to 3,
        Score(2, 0) to 21, Score(3, 0) to 1,
    )

    /** Whether [candidate] answers every (guess, score) of [history] exactly as recorded. */
    fun isConsistent(candidate: String, history: List<Pair<String, Score>>): Boolean =
        history.all { (g, s) -> judge(candidate, g) == s }

    // ------------------------------------------------------------------ team match (protocol 8.4)

    /** State of one duel as seen by the team match. */
    enum class DuelState { PENDING, A, B, DRAW }

    sealed interface Verdict {
        data object Undecided : Verdict
        data object Draw : Verdict
        data class Winner(val team: Char, val early: Boolean) : Verdict
    }

    /**
     * protocol 8.4: a duel win is a point; draws give none. Decided as soon as leader > other + unfinished;
     * otherwise when every duel ended: more wins takes it, equal is a team draw.
     */
    fun teamVerdict(duels: List<DuelState>): Verdict {
        val a = duels.count { it == DuelState.A }
        val b = duels.count { it == DuelState.B }
        val open = duels.count { it == DuelState.PENDING }
        return when {
            a > b + open -> Verdict.Winner('A', early = open > 0)
            b > a + open -> Verdict.Winner('B', early = open > 0)
            open == 0 -> Verdict.Draw
            else -> Verdict.Undecided
        }
    }

    /** The rule of the web prototype (wins > N/2), kept to document DEF-001: it misses decisions when draws exist. */
    fun prototypeMajorityDecided(duels: List<DuelState>): Boolean {
        val n = duels.size
        val a = duels.count { it == DuelState.A }
        val b = duels.count { it == DuelState.B }
        return a * 2 > n || b * 2 > n || duels.none { it == DuelState.PENDING }
    }
}
