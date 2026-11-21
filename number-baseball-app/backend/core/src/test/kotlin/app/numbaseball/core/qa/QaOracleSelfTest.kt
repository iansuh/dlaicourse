package app.numbaseball.core.qa

import app.numbaseball.core.qa.QaOracle.DuelState
import app.numbaseball.core.qa.QaOracle.Score
import app.numbaseball.core.qa.QaOracle.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The oracle must be right before it can judge anybody else. */
class QaOracleSelfTest {

    @Test fun countsOfValidNumbers() {
        assertEquals(listOf(720, 5040, 30240, 151200), (3..6).map { QaOracle.count(it) })
        for (n in 3..5) {
            val all = QaOracle.all(n)
            assertEquals(QaOracle.count(n), all.size)
            assertEquals(all.size, all.toSet().size)
            assertTrue(all.all { QaOracle.isValid(it, n) })
        }
    }

    @Test fun validity() {
        assertTrue(QaOracle.isValid("012", 3))
        assertTrue(QaOracle.isValid("9876", 4))
        assertTrue(QaOracle.isValid("012345", 6))
        assertFalse(QaOracle.isValid("01", 2))
        assertFalse(QaOracle.isValid("0123456", 7))
        assertFalse(QaOracle.isValid("112", 3))
        assertFalse(QaOracle.isValid("12a", 3))
        assertFalse(QaOracle.isValid("123", 4))
        // Unicode digits are not digits for this game.
        val arabicIndic = String(intArrayOf(0x661, 0x662, 0x663), 0, 3)
        val fullWidth = String(intArrayOf(0xFF11, 0xFF12, 0xFF13), 0, 3)
        assertFalse(QaOracle.isValid(arabicIndic, 3))
        assertFalse(QaOracle.isValid(fullWidth, 3))
    }

    @Test fun twoOracleImplementationsAgreeOnEveryThreeDigitPair() {
        val all = QaOracle.all(3)
        for (s in all) for (g in all) assertEquals("$s/$g", QaOracle.judgeNaive(s, g), QaOracle.judge(s, g))
    }

    @Test fun handDerivedDistributionMatches() {
        assertEquals(720, QaOracle.DISTRIBUTION_3.values.sum())
        for (secret in listOf("012", "987", "406")) {
            val dist = QaOracle.all(3).groupingBy { QaOracle.judge(secret, it) }.eachCount()
            assertEquals(QaOracle.DISTRIBUTION_3, dist)
        }
    }

    @Test fun judgeProperties() {
        val rnd = Random(1)
        repeat(20_000) {
            val n = rnd.nextInt(3, 7)
            val s = QaOracle.random(n, rnd)
            val g = QaOracle.random(n, rnd)
            val r = QaOracle.judge(s, g)
            assertTrue(r.strikes + r.balls <= n)
            assertEquals(s == g, r.strikes == n)
            assertEquals(r, QaOracle.judge(g, s)) // symmetric for distinct-digit numbers
            assertEquals(r, QaOracle.judgeNaive(s, g))
        }
        assertEquals(Score(0, 0), QaOracle.judge("012", "345"))
        assertTrue(QaOracle.judge("012", "345").isOut)
        assertEquals(Score(1, 2), QaOracle.judge("012", "021"))
        assertEquals(Score(0, 3), QaOracle.judge("012", "120"))
    }

    @Test fun teamVerdictExamplesFromProtocol() {
        val (A, B, D, P) = listOf(DuelState.A, DuelState.B, DuelState.DRAW, DuelState.PENDING)
        assertEquals(Verdict.Draw, QaOracle.teamVerdict(listOf(A, B, D))) // protocol 8.4 example
        assertEquals(Verdict.Draw, QaOracle.teamVerdict(listOf(D)))
        assertEquals(Verdict.Winner('A', early = false), QaOracle.teamVerdict(listOf(A)))
        assertEquals(Verdict.Winner('B', early = true), QaOracle.teamVerdict(listOf(B, B, P)))
        assertEquals(Verdict.Undecided, QaOracle.teamVerdict(listOf(A, P)))
        assertEquals(Verdict.Undecided, QaOracle.teamVerdict(listOf(A, A, P, P)))
        // DEF-001: with a draw, 2-0 + draw + pending is already decided; the prototype's majority rule says no.
        val case = listOf(A, A, D, P)
        assertEquals(Verdict.Winner('A', early = true), QaOracle.teamVerdict(case))
        assertFalse(QaOracle.prototypeMajorityDecided(case))
    }

    @Test fun teamVerdictIsMonotoneOverEveryFinishOrder() {
        // Once decided, no further duel result may change the verdict; the final verdict equals "more wins".
        for (n in 1..5) forEachOutcome(n) { outcomes ->
            forEachPermutation(n) { order ->
                val state = MutableList(n) { DuelState.PENDING }
                var decided: Verdict? = null
                for (i in order) {
                    state[i] = outcomes[i]
                    val v = QaOracle.teamVerdict(state)
                    if (decided != null) {
                        val w = v as? Verdict.Winner
                        val d = decided as? Verdict.Winner
                        if (d != null) assertEquals(d.team, w?.team)
                    } else if (v != Verdict.Undecided) decided = v
                }
                val a = outcomes.count { it == DuelState.A }
                val b = outcomes.count { it == DuelState.B }
                val final = QaOracle.teamVerdict(outcomes)
                when {
                    a > b -> assertEquals('A', (final as Verdict.Winner).team)
                    b > a -> assertEquals('B', (final as Verdict.Winner).team)
                    else -> assertEquals(Verdict.Draw, final)
                }
            }
        }
    }

    @Test fun duelOracleBasicScenario() {
        val o = QaDuelOracle(3, 60_000L)
        o.start(0)
        assertEquals(emptySet<String>(), o.setSecret(0, "012", 10))
        assertEquals(setOf("invalid_secret"), o.setSecret(1, "011", 20))
        assertEquals(emptySet<String>(), o.setSecret(1, "345", 30))
        assertEquals(QaDuelOracle.Phase.GUESSING, o.phase)
        assertEquals(60_030L, o.deadline)
        assertEquals(emptySet<String>(), o.guess(0, "345", 1, 100)) // A solves
        assertEquals(setOf("already_guessed"), o.guess(0, "346", 1, 110))
        o.tick(60_030) // B misses, A solved: solve wins
        assertEquals(QaDuelOracle.Result(0, "solved"), o.result)
        assertNull(o.deadline)
    }

    companion object {
        fun forEachOutcome(n: Int, block: (List<DuelState>) -> Unit) {
            val values = listOf(DuelState.A, DuelState.B, DuelState.DRAW)
            val idx = IntArray(n)
            while (true) {
                block(idx.map { values[it] })
                var k = 0
                while (k < n && idx[k] == 2) { idx[k] = 0; k++ }
                if (k == n) return
                idx[k]++
            }
        }

        fun forEachPermutation(n: Int, block: (IntArray) -> Unit) {
            val a = IntArray(n) { it }
            fun rec(k: Int) {
                if (k == n) { block(a.copyOf()); return }
                for (i in k until n) {
                    a[k] = a[i].also { a[i] = a[k] }
                    rec(k + 1)
                    a[k] = a[i].also { a[i] = a[k] }
                }
            }
            rec(0)
        }
    }
}
