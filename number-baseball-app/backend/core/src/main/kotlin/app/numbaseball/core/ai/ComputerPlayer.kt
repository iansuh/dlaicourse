package app.numbaseball.core.ai

import app.numbaseball.core.rules.Rules
import kotlin.math.ln
import kotlin.random.Random

enum class ComputerLevel { EASY, NORMAL, HARD }

/**
 * The offline opponent. Call [nextGuess], judge it against the player's secret, then report with [onResult].
 *
 * - EASY remembers only its last few hints and sometimes guesses at random.
 * - NORMAL uses every hint but picks any number that fits, and now and then throws a wild guess.
 * - HARD keeps every number that fits all hints and picks the guess whose possible answers split those numbers
 *   most evenly (expected information), sampling when the set is large so a move stays within a few milliseconds.
 *
 * All randomness comes from [random], so a seeded instance plays the same game every time.
 * Not thread-safe; use one instance per game.
 */
class ComputerPlayer(
    val level: ComputerLevel,
    val digits: Int,
    private val random: Random = Random.Default,
) {
    private val space = CodeSpace.of(digits)
    private val hints = ArrayList<Hint>()
    private val tried = HashSet<Int>()

    // Candidates consistent with every hint (NORMAL / HARD), kept as parallel code/mask arrays.
    private var candCodes: IntArray = space.codes
    private var candMasks: IntArray = space.masks
    private var candSize: Int = space.size

    private class Hint(val code: Int, val mask: Int, val fb: Int)

    /** A random valid secret for this computer player. */
    fun chooseSecret(): String = Rules.randomNumber(digits, random)

    /** Number of codes still consistent with every hint (diagnostics/tests). */
    val remainingCandidates: Int get() = candSize

    fun nextGuess(): String {
        val code = when (level) {
            ComputerLevel.EASY -> easyGuess()
            ComputerLevel.NORMAL -> normalGuess()
            ComputerLevel.HARD -> hardGuess()
        }
        return space.format(code)
    }

    /** Reports the result of [guess] (which should come from [nextGuess]). */
    fun onResult(guess: String, strikes: Int, balls: Int) {
        require(Rules.isValid(guess, digits)) { "invalid guess" }
        val hint = Hint(CodeSpace.pack(guess), CodeSpace.maskOf(guess), CodeSpace.feedback(strikes, balls))
        hints += hint
        tried += hint.code
        if (level != ComputerLevel.EASY) filterCandidates(hint)
    }

    private fun filterCandidates(h: Hint) {
        // Copy on first write so the shared CodeSpace arrays are never modified.
        val codes = if (candCodes === space.codes) candCodes.copyOf() else candCodes
        val masks = if (candMasks === space.masks) candMasks.copyOf() else candMasks
        var n = 0
        for (i in 0 until candSize) {
            if (CodeSpace.feedback(codes[i], masks[i], h.code, h.mask, digits) == h.fb) {
                codes[n] = codes[i]; masks[n] = masks[i]; n++
            }
        }
        candCodes = codes; candMasks = masks; candSize = n
    }

    private fun randomUntried(): Int {
        while (true) {
            val c = space.codes[random.nextInt(space.size)]
            if (c !in tried || tried.size >= space.size) return c
        }
    }

    private fun randomCandidate(exclude: Set<Int>): Int? {
        if (candSize == 0) return null
        // Most of the time the candidates are all untried (a tried code only fits if it was the answer).
        repeat(8) {
            val c = candCodes[random.nextInt(candSize)]
            if (c !in exclude) return c
        }
        val left = (0 until candSize).map { candCodes[it] }.filter { it !in exclude }
        return if (left.isEmpty()) null else left[random.nextInt(left.size)]
    }

    // ---- EASY ----

    private fun easyGuess(): Int {
        if (hints.isEmpty() || random.nextDouble() < EASY_RANDOM) return randomUntried()
        val memory = hints.takeLast(easyMemory())
        val codes = space.codes
        val masks = space.masks
        // Reservoir-sample one code that fits the remembered hints and was not tried yet.
        var chosen = -1
        var seen = 0
        for (i in codes.indices) {
            val c = codes[i]
            var ok = true
            for (h in memory) {
                if (CodeSpace.feedback(c, masks[i], h.code, h.mask, digits) != h.fb) { ok = false; break }
            }
            if (ok && c !in tried) {
                seen++
                if (random.nextInt(seen) == 0) chosen = c
            }
        }
        return if (chosen >= 0) chosen else randomUntried()
    }

    /** Easy remembers a little more for long numbers so a game does not drag on forever. */
    private fun easyMemory(): Int = if (digits <= 4) 3 else 4

    // ---- NORMAL ----

    private fun normalGuess(): Int {
        if (hints.isEmpty()) return randomUntried()
        if (candSize > 1 && random.nextDouble() < NORMAL_WILD) return randomUntried()
        return randomCandidate(tried) ?: randomUntried()
    }

    // ---- HARD ----

    private fun hardGuess(): Int {
        // Every first guess is equivalent by symmetry.
        if (hints.isEmpty()) return randomUntried()
        if (candSize <= 2) return randomCandidate(tried) ?: randomUntried()

        val m = candSize
        // Guess pool: all candidates when affordable, otherwise a random sample.
        val poolSize = minOf(m, MAX_POOL)
        val pool = sampleIndices(m, poolSize)
        // Evaluation set: as many candidates as the budget allows.
        val evalSize = minOf(m, maxOf(MIN_EVAL, BUDGET / poolSize))
        val eval = sampleIndices(m, evalSize)

        val counts = IntArray(64)
        val win = CodeSpace.feedback(digits, 0)
        var bestScore = Double.MAX_VALUE
        var best = candCodes[pool[0]]
        var ties = 0
        for (gi in pool) {
            val g = candCodes[gi]
            val gm = candMasks[gi]
            java.util.Arrays.fill(counts, 0)
            for (ei in eval) counts[CodeSpace.feedback(candCodes[ei], candMasks[ei], g, gm, digits)]++
            // Expected size of the remaining set (log-weighted); the winning cell costs nothing.
            var score = 0.0
            for (fb in counts.indices) {
                val c = counts[fb]
                if (c > 1 && fb != win) score += c * ln(c.toDouble())
                if (c >= 1 && fb != win) score += c * SINGLE_COST
            }
            if (score < bestScore - 1e-9) {
                bestScore = score; best = g; ties = 1
            } else if (score <= bestScore + 1e-9) {
                ties++
                if (random.nextInt(ties) == 0) best = g
            }
        }
        // Also consider non-candidate probes when only a handful remain; they can split candidates
        // that every candidate guess would leave tied.
        if (m in 3..PROBE_LIMIT) {
            val probe = bestProbe(win)
            if (probe != null && probe.second + PROBE_MARGIN < bestScore) best = probe.first
        }
        return best
    }

    private fun bestProbe(win: Int): Pair<Int, Double>? {
        val counts = IntArray(64)
        var best: Pair<Int, Double>? = null
        val all = space.codes
        val allMasks = space.masks
        val step = maxOf(1, all.size / PROBE_SAMPLE)
        val offset = random.nextInt(step)
        var i = offset
        while (i < all.size) {
            val g = all[i]
            val gm = allMasks[i]
            java.util.Arrays.fill(counts, 0)
            for (e in 0 until candSize) counts[CodeSpace.feedback(candCodes[e], candMasks[e], g, gm, digits)]++
            var score = 0.0
            for (fb in counts.indices) {
                val c = counts[fb]
                if (c > 1 && fb != win) score += c * ln(c.toDouble())
                if (c >= 1 && fb != win) score += c * SINGLE_COST
            }
            if (best == null || score < best.second) best = g to score
            i += step
        }
        return best
    }

    /** [k] distinct indices from 0 until [n], in random order when sampling. */
    private fun sampleIndices(n: Int, k: Int): IntArray {
        if (k >= n) return IntArray(n) { it }
        // Partial Fisher–Yates over a scratch array.
        val idx = IntArray(n) { it }
        for (i in 0 until k) {
            val j = i + random.nextInt(n - i)
            val t = idx[i]; idx[i] = idx[j]; idx[j] = t
        }
        return idx.copyOf(k)
    }

    private companion object {
        const val EASY_RANDOM = 0.3
        const val NORMAL_WILD = 0.3
        /** Judge operations per hard move: ~300k keeps a 6-digit move at a few ms on a desktop JVM. */
        const val BUDGET = 300_000
        const val MAX_POOL = 400
        const val MIN_EVAL = 400
        /** Cost of a non-winning singleton cell: one more guess is needed. */
        const val SINGLE_COST = 1.0
        const val PROBE_LIMIT = 40
        const val PROBE_SAMPLE = 2000
        const val PROBE_MARGIN = 0.5
    }
}
