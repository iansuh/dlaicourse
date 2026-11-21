package app.numbaseball.core.ai

import app.numbaseball.core.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ComputerPlayerTest {
    /** Plays one game and returns the number of guesses (each guess judged against a seeded secret). */
    private fun play(level: ComputerLevel, digits: Int, seed: Long, onGuess: (String) -> Unit = {}): Int {
        val secret = Rules.randomNumber(digits, Random(seed))
        val ai = ComputerPlayer(level, digits, Random(seed * 31 + 7))
        var n = 0
        while (true) {
            val g = ai.nextGuess()
            onGuess(g)
            n++
            val j = Rules.judge(secret, g)
            if (j.isSolved(digits)) return n
            ai.onResult(g, j.strikes, j.balls)
            check(n < 1000) { "did not finish" }
        }
    }

    @Test fun guessesAreValidAndNeverRepeated() {
        for (level in ComputerLevel.entries) for (d in 3..6) repeat(5) { seed ->
            val seen = HashSet<String>()
            play(level, d, seed.toLong()) { g ->
                assertTrue("$level $d $g", Rules.isValid(g, d))
                assertTrue("$level repeated $g", seen.add(g))
            }
        }
    }

    @Test fun seededGamesAreReproducible() {
        for (level in ComputerLevel.entries) {
            val a = mutableListOf<String>()
            val b = mutableListOf<String>()
            play(level, 4, 42) { a += it }
            play(level, 4, 42) { b += it }
            assertEquals(a, b)
        }
    }

    @Test fun hardNeverGuessesAgainstItsHints() {
        // Every hard guess after the first must be consistent with all earlier feedback, or a deliberate probe while
        // only a handful of candidates remain.
        val digits = 4
        repeat(20) { seed ->
            val secret = Rules.randomNumber(digits, Random(seed))
            val ai = ComputerPlayer(ComputerLevel.HARD, digits, Random(seed.toLong()))
            val hist = mutableListOf<Triple<String, Int, Int>>()
            while (true) {
                val remaining = ai.remainingCandidates
                val g = ai.nextGuess()
                val consistent = hist.all { (h, s, b) -> Rules.judge(g, h).let { it.strikes == s && it.balls == b } }
                assertTrue("seed $seed guess $g", consistent || remaining <= 40)
                val j = Rules.judge(secret, g)
                if (j.isSolved(digits)) break
                ai.onResult(g, j.strikes, j.balls)
                hist += Triple(g, j.strikes, j.balls)
            }
        }
    }

    /** Average guesses per level; printed for the report, asserted against sane ranges. */
    @Test fun simulationAverages() {
        val games = 300
        val results = mutableMapOf<Pair<ComputerLevel, Int>, Double>()
        for (d in listOf(3, 4)) for (level in ComputerLevel.entries) {
            val avg = (1..games).map { play(level, d, it.toLong() + 1000) }.average()
            results[level to d] = avg
            println("AI simulation: digits=$d level=$level games=$games average guesses=${"%.2f".format(avg)}")
        }
        // Hard is near optimal (best known averages: ~4.9 for 3 digits, ~5.2 for 4 digits).
        assertTrue(results.getValue(ComputerLevel.HARD to 3) in 4.6..5.3)
        assertTrue(results.getValue(ComputerLevel.HARD to 4) in 5.0..5.6)
        for (d in listOf(3, 4)) {
            val easy = results.getValue(ComputerLevel.EASY to d)
            val normal = results.getValue(ComputerLevel.NORMAL to d)
            val hard = results.getValue(ComputerLevel.HARD to d)
            assertTrue("normal weaker than hard ($d)", normal > hard + 0.2)
            assertTrue("easy clearly weaker than normal ($d)", easy > normal + 1.5)
            assertTrue("normal sane ($d)", normal < 7.5)
            assertTrue("easy still finishes in reasonable time ($d)", easy < 14.0)
        }
    }

    /** Budget: under 50 ms per move on a desktop JVM for 6 digits (after warm-up). */
    @Test fun hardSixDigitMoveTime() {
        repeat(3) { play(ComputerLevel.HARD, 6, 900L + it) } // JIT warm-up
        var maxNanos = 0L
        var total = 0L
        var moves = 0
        repeat(20) { game ->
            val secret = Rules.randomNumber(6, Random(game.toLong()))
            val ai = ComputerPlayer(ComputerLevel.HARD, 6, Random(game + 77L))
            while (true) {
                val t0 = System.nanoTime()
                val g = ai.nextGuess()
                val dt = System.nanoTime() - t0
                maxNanos = maxOf(maxNanos, dt); total += dt; moves++
                val j = Rules.judge(secret, g)
                if (j.isSolved(6)) break
                val t1 = System.nanoTime()
                ai.onResult(g, j.strikes, j.balls)
                val dt2 = System.nanoTime() - t1
                maxNanos = maxOf(maxNanos, dt2); total += dt2
            }
        }
        println("AI timing: hard 6 digits, $moves moves, average ${"%.2f".format(total / 1e6 / moves)} ms, max ${"%.2f".format(maxNanos / 1e6)} ms per move")
        assertTrue("max move ${maxNanos / 1e6} ms", maxNanos < 50_000_000L)
    }
}
