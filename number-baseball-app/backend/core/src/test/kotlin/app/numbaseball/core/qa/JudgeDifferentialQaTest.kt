package app.numbaseball.core.qa

import app.numbaseball.core.rules.NumberProblem
import app.numbaseball.core.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * AC-RUL-*: the core judge and validator against the QA oracle.
 * 3 digits: every (secret, guess) pair (518 400). 4-6 digits: 300 000 random pairs each, seeded.
 */
class JudgeDifferentialQaTest {

    private fun core(secret: String, guess: String): QaOracle.Score =
        Rules.judge(secret, guess).let { QaOracle.Score(it.strikes, it.balls) }

    @Test fun acRul01_everyThreeDigitPairMatchesTheOracle() {
        val all = QaOracle.all(3)
        var checked = 0
        for (s in all) for (g in all) {
            val expected = QaOracle.judge(s, g)
            val actual = core(s, g)
            if (expected != actual) fail("judge($s, $g): expected $expected, core says $actual")
            checked++
        }
        assertEquals(518_400, checked)
    }

    @Test fun acRul02_randomPairsForFourToSixDigits() {
        for (n in 4..6) {
            val rnd = Random(4242 + n)
            repeat(300_000) {
                val s = QaOracle.random(n, rnd)
                val g = if (rnd.nextInt(10) == 0) s else QaOracle.random(n, rnd)
                val expected = QaOracle.judge(s, g)
                val actual = core(s, g)
                if (expected != actual) fail("judge($s, $g): expected $expected, core says $actual")
            }
        }
    }

    @Test fun acRul03_handDerivedDistributionForEverySecret() {
        val all = QaOracle.all(3)
        for (s in all) {
            val dist = all.groupingBy { core(s, it) }.eachCount()
            assertEquals("distribution for secret $s", QaOracle.DISTRIBUTION_3, dist)
        }
    }

    @Test fun acRul04_solvedAndOutFlags() {
        for (n in 3..6) {
            val rnd = Random(n.toLong())
            repeat(5_000) {
                val s = QaOracle.random(n, rnd)
                val g = QaOracle.random(n, rnd)
                val j = Rules.judge(s, g)
                assertEquals(s == g, j.isSolved(n))
                assertEquals(j.strikes == 0 && j.balls == 0, j.isOut)
            }
        }
    }

    @Test fun acRul05_validatorMatchesOracleOnAllShortStringsOverAHostileAlphabet() {
        // Every string of length 0..4 over an alphabet with ASCII digits, a letter, a space and look-alike digits.
        val alphabet = listOf("0", "1", "2", "9", "a", " ", String(Character.toChars(0x661)), String(Character.toChars(0xFF11)),
            String(Character.toChars(0x1D7CF)), String(Character.toChars(0x966)))
        fun rec(prefix: List<String>, depth: Int) {
            val value = prefix.joinToString("")
            for (n in 1..7) {
                val expected = QaOracle.isValid(value, n)
                assertEquals("isValid(<$value>, $n)", expected, Rules.isValid(value, n))
                if (n in 3..6) assertEquals("problem(<$value>, $n)", expected, Rules.problem(value, n) == null)
            }
            if (depth < 4) for (a in alphabet) rec(prefix + a, depth + 1)
        }
        rec(emptyList(), 0)
    }

    @Test fun acRul05_problemCodes() {
        assertEquals(NumberProblem.WRONG_LENGTH, Rules.problem("12", 3))
        assertEquals(NumberProblem.WRONG_LENGTH, Rules.problem("1234", 3))
        assertEquals(NumberProblem.NOT_DIGITS, Rules.problem("12a", 3))
        assertEquals(NumberProblem.NOT_DIGITS, Rules.problem(String(intArrayOf(0x661, 0x662, 0x663), 0, 3), 3))
        assertEquals(NumberProblem.REPEATED_DIGIT, Rules.problem("121", 3))
        assertEquals(null, Rules.problem("012", 3))
        assertFalse(Rules.isValid("012", 2))
        assertFalse(Rules.isValid("0123456", 7))
    }

    @Test fun acRul06_randomNumbersAreValidAndUniform() {
        for (n in 3..6) {
            val rnd = Random(77 + n)
            val first = IntArray(10)
            val samples = 60_000
            repeat(samples) {
                val v = Rules.randomNumber(n, rnd)
                assertTrue("<$v>", QaOracle.isValid(v, n))
                first[v[0] - '0']++
            }
            // Leading digit (0 included) must be uniform: chi-square with 9 dof, p = 0.001 critical value 27.88.
            val e = samples / 10.0
            val chi = first.sumOf { (it - e) * (it - e) / e }
            assertTrue("leading digit not uniform for $n digits: ${first.toList()} chi2=$chi", chi < 27.88)
            assertTrue("0 must be able to lead", first[0] > 0)
        }
    }

    /**
     * DEF-031: Rules.judge does not check its inputs. judge("12a", "12a") returns a "solved" result for a number that
     * is not valid, and repeated digits give nonsense. The KDoc states the precondition, so this is Minor; the test
     * pins the safe behaviour we ask for (reject invalid input).
     */
    @Test fun acRul07_judgeRejectsInvalidInput() {
        val bad = listOf("12a" to "123", "112" to "123", "123" to "1 3", "123" to "12")
        val failures = bad.mapNotNull { (s, g) ->
            runCatching { Rules.judge(s, g) }.getOrNull()?.let { "judge:$s/$g" to "accepted, returned $it" }
        }.toMap()
        KnownDefects.check("Rules.judge input validation", bad.map { (s, g) -> "judge:$s/$g" }, failures)
    }
}
