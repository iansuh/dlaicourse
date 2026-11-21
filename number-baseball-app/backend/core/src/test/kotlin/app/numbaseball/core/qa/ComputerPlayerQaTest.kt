package app.numbaseball.core.qa

import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.ai.ComputerPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * AC-CPU-*: the offline opponent. Every guess is valid and new, every game ends, levels are ordered by strength,
 * seeded games repeat exactly, inconsistent answers never crash it, and a move stays inside the time budget.
 * Budgets (TEST_PLAN.md section 6) were set from measurements of this implementation with head-room; a weaker
 * computer is a regression, not a reason to relax them.
 */
class ComputerPlayerQaTest {

    data class Game(val guesses: Int, val maxMoveNanos: Long)

    /** Plays the computer against [secret] with the oracle as referee. */
    private fun play(level: ComputerLevel, digits: Int, secret: String, seed: Long, limit: Int = QaOracle.count(digits)): Game {
        val cpu = ComputerPlayer(level, digits, Random(seed))
        val tried = HashSet<String>()
        var maxNanos = 0L
        repeat(limit) { i ->
            val t0 = System.nanoTime()
            val g = cpu.nextGuess()
            maxNanos = maxOf(maxNanos, System.nanoTime() - t0)
            if (!QaOracle.isValid(g, digits)) fail("$level/$digits seed $seed: invalid guess <$g>")
            if (!tried.add(g)) fail("$level/$digits seed $seed: repeated guess <$g> at move ${i + 1}")
            val s = QaOracle.judge(secret, g)
            if (s.strikes == digits) return Game(i + 1, maxNanos)
            cpu.onResult(g, s.strikes, s.balls)
        }
        fail("$level/$digits seed $seed: did not solve <$secret> within $limit guesses")
        throw AssertionError()
    }

    private fun stats(level: ComputerLevel, digits: Int, games: Int, seed: Long): List<Game> {
        val rnd = Random(seed)
        return (0 until games).map { play(level, digits, QaOracle.random(digits, rnd), seed * 1000 + it) }
    }

    private fun List<Game>.mean() = sumOf { it.guesses }.toDouble() / size
    private fun List<Game>.worst() = maxOf { it.guesses }

    @Test fun acCpu01_hardSolvesEveryThreeDigitSecret() {
        val games = QaOracle.all(3).mapIndexed { i, s -> play(ComputerLevel.HARD, 3, s, i.toLong()) }
        println("HARD 3 digits over all 720 secrets: mean ${"%.2f".format(games.mean())}, worst ${games.worst()}")
        assertTrue("HARD 3-digit worst case ${games.worst()} > 7", games.worst() <= 7)
        assertTrue("HARD 3-digit mean ${games.mean()} > 5.3", games.mean() <= 5.3)
    }

    @Test fun acCpu02_strengthBudgetsPerLevelAndDigits() {
        // mean guesses (upper bound) / worst case (upper bound) per level and digit count.
        val budget = mapOf(
            (ComputerLevel.HARD to 4) to (6.2 to 9), (ComputerLevel.HARD to 5) to (7.2 to 11), (ComputerLevel.HARD to 6) to (8.2 to 13),
            (ComputerLevel.NORMAL to 4) to (7.5 to 14), (ComputerLevel.NORMAL to 6) to (10.0 to 20),
            (ComputerLevel.EASY to 3) to (12.0 to 40), (ComputerLevel.EASY to 4) to (20.0 to 70), (ComputerLevel.EASY to 6) to (40.0 to 150),
        )
        for ((key, limits) in budget) {
            val (level, digits) = key
            val n = if (level == ComputerLevel.EASY) 150 else 200
            val games = stats(level, digits, n, 31L * digits + level.ordinal)
            println("$level $digits digits: mean ${"%.2f".format(games.mean())}, worst ${games.worst()} over $n games")
            assertTrue("$level/$digits mean ${games.mean()} > ${limits.first}", games.mean() <= limits.first)
            assertTrue("$level/$digits worst ${games.worst()} > ${limits.second}", games.worst() <= limits.second)
        }
    }

    @Test fun acCpu03_levelsAreOrderedByStrength() {
        for (digits in listOf(3, 4, 5)) {
            val easy = stats(ComputerLevel.EASY, digits, 150, 7L + digits).mean()
            val normal = stats(ComputerLevel.NORMAL, digits, 150, 8L + digits).mean()
            val hard = stats(ComputerLevel.HARD, digits, 150, 9L + digits).mean()
            println("$digits digits: easy %.2f normal %.2f hard %.2f".format(easy, normal, hard))
            assertTrue("$digits digits: EASY ($easy) must need clearly more guesses than NORMAL ($normal)", easy > normal + 1.0)
            assertTrue("$digits digits: NORMAL ($normal) must need more guesses than HARD ($hard)", normal > hard + 0.3)
        }
    }

    @Test fun acCpu04_seededGamesRepeatExactly() {
        for (level in ComputerLevel.entries) for (digits in 3..6) {
            fun transcript(): List<String> {
                val cpu = ComputerPlayer(level, digits, Random(1234))
                val secret = "0123456".take(digits).reversed()
                val out = ArrayList<String>()
                out += cpu.chooseSecret()
                repeat(8) {
                    val g = cpu.nextGuess()
                    out += g
                    val s = QaOracle.judge(secret, g)
                    if (s.strikes == digits) return out
                    cpu.onResult(g, s.strikes, s.balls)
                }
                return out
            }
            assertEquals("$level/$digits", transcript(), transcript())
        }
    }

    @Test fun acCpu05_chosenSecretsAreValidAndVaried() {
        for (level in ComputerLevel.entries) for (digits in 3..6) {
            val cpu = ComputerPlayer(level, digits, Random(digits.toLong()))
            val secrets = (0 until 300).map { cpu.chooseSecret() }
            assertTrue(secrets.all { QaOracle.isValid(it, digits) })
            assertTrue("$level/$digits secrets not varied", secrets.toSet().size > 250)
            assertTrue("$level/$digits: 0 must be able to lead", secrets.any { it[0] == '0' })
        }
    }

    @Test fun acCpu06_inconsistentAnswersNeverCrashOrRepeat() {
        // A buggy referee (or a cheating client in practice mode) gives random answers: the computer must keep making
        // valid, new guesses without throwing.
        for (level in ComputerLevel.entries) for (digits in 3..6) {
            val rnd = Random(55L + digits)
            val cpu = ComputerPlayer(level, digits, Random(66L + digits))
            val tried = HashSet<String>()
            repeat(25) {
                val g = cpu.nextGuess()
                assertTrue("$level/$digits invalid <$g>", QaOracle.isValid(g, digits))
                assertTrue("$level/$digits repeated <$g> under inconsistent answers", tried.add(g))
                val s = rnd.nextInt(0, digits)
                val b = rnd.nextInt(0, digits - s + 1)
                cpu.onResult(g, s, b)
            }
        }
    }

    @Test fun acCpu07_onResultRejectsAGuessThatIsNotANumber() {
        val cpu = ComputerPlayer(ComputerLevel.HARD, 4, Random(1))
        assertTrue(runCatching { cpu.onResult("12a4", 0, 0) }.isFailure)
        assertTrue(runCatching { cpu.onResult("1123", 0, 0) }.isFailure)
        assertTrue(runCatching { cpu.onResult("123", 0, 0) }.isFailure)
    }

    @Test fun nfrCpu01_moveTimeBudgetSixDigitsHard() {
        // Budget: worst single move <= 150 ms on a low-end phone (TEST_PLAN NFR-01). JVM proxy on CI: <= 60 ms after
        // warm-up, measured over 40 full games. Device numbers come from the Android macrobenchmark later.
        repeat(3) { play(ComputerLevel.HARD, 6, QaOracle.random(6, Random(it)), it.toLong()) } // warm-up
        val rnd = Random(2026)
        val games = (0 until 40).map { play(ComputerLevel.HARD, 6, QaOracle.random(6, rnd), 500L + it) }
        val worstMs = games.maxOf { it.maxMoveNanos } / 1_000_000.0
        println("HARD 6 digits: worst move %.1f ms, mean guesses %.2f".format(worstMs, games.mean()))
        assertTrue("worst HARD 6-digit move took $worstMs ms (> 60 ms JVM budget)", worstMs <= 60.0)
    }
}
