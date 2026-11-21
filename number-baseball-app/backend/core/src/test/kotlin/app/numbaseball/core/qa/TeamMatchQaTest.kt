package app.numbaseball.core.qa

import app.numbaseball.core.match.ActionResult
import app.numbaseball.core.match.DuelConfig
import app.numbaseball.core.match.TeamMatch
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.MatchResult
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.qa.QaOracle.DuelState
import app.numbaseball.core.qa.QaOracle.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * AC-TEAM-*: TeamMatch against the oracle for EVERY finish order and EVERY combination of duel results
 * (A win / B win / draw) for team sizes 1..5 (5! x 3^5 = 29 160 sequences for 5v5), driven only through the public API.
 */
class TeamMatchQaTest {
    private val secretA = "012"
    private val secretB = "345"
    private val miss = "678"

    private fun match(n: Int, seconds: Int = 0): TeamMatch {
        val m = TeamMatch(DuelConfig.of(3, seconds), (0 until n).map { "a$it" to "b$it" }, Random(n.toLong()))
        m.start(0)
        for (i in 0 until n) {
            assertEquals(ActionResult.Ok, m.setSecret("a$i", secretA, 0))
            assertEquals(ActionResult.Ok, m.setSecret("b$i", secretB, 0))
        }
        return m
    }

    /** Ends duel [i] with [outcome] using real guesses (or a forfeit when [viaForfeit]). Returns the action results. */
    private fun finishDuel(m: TeamMatch, i: Int, outcome: DuelState, now: Long, viaForfeit: Boolean): List<ActionResult> = when {
        viaForfeit && outcome == DuelState.A -> listOf(m.forfeit("b$i", now))
        viaForfeit && outcome == DuelState.B -> listOf(m.forfeit("a$i", now))
        outcome == DuelState.A -> listOf(m.guess("a$i", secretB, 1, now), m.guess("b$i", miss, 1, now))
        outcome == DuelState.B -> listOf(m.guess("a$i", miss, 1, now), m.guess("b$i", secretA, 1, now))
        else -> listOf(m.guess("a$i", secretB, 1, now), m.guess("b$i", secretA, 1, now))
    }

    private fun verdictOf(r: MatchResult?): Verdict = when {
        r == null -> Verdict.Undecided
        r.winner == null -> Verdict.Draw
        else -> Verdict.Winner(if (r.winner == Team.A) 'A' else 'B', r.decidedEarly)
    }

    @Test fun acTeam01_everyOrderAndOutcomeMatchesTheOracle() {
        var sequences = 0
        val rnd = Random(99)
        for (n in 1..5) QaOracleSelfTest.forEachOutcome(n) { outcomes ->
            QaOracleSelfTest.forEachPermutation(n) { order ->
                sequences++
                val m = match(n)
                val seen = MutableList(n) { DuelState.PENDING }
                var decidedAt: Verdict? = null
                var t = 1L
                for (i in order) {
                    val results = finishDuel(m, i, outcomes[i], t++, viaForfeit = rnd.nextInt(4) == 0)
                    val ctx = "n=$n outcomes=$outcomes order=${order.toList()} at duel $i"
                    if (decidedAt == null) {
                        assertTrue("$ctx: action rejected before the decision: $results", results.all { it == ActionResult.Ok })
                        seen[i] = outcomes[i]
                        val expected = QaOracle.teamVerdict(seen)
                        assertEquals(ctx, expected, verdictOf(m.result))
                        if (expected != Verdict.Undecided) decidedAt = expected
                    } else {
                        // After the decision the remaining duels are cancelled; actions on them are refused.
                        assertTrue("$ctx: action accepted after decision: $results", results.all { it == ActionResult.Rejected(ErrorCodes.WRONG_PHASE) })
                        assertEquals("$ctx: result changed after decision", decidedAt, verdictOf(m.result))
                    }
                }
                // Final invariants.
                val result = m.result ?: fail("n=$n $outcomes: no result after every duel ended") as Nothing
                assertTrue(m.isFinished)
                assertNull(m.nextWakeup())
                val score = m.score()
                assertEquals(m.duels.count { it.result?.winner == Team.A }, score.a)
                assertEquals(m.duels.count { it.result?.winner == Team.B }, score.b)
                for ((idx, d) in m.duels.withIndex()) {
                    assertEquals(DuelPhase.FINISHED, d.phase)
                    if (d.result!!.reason == EndReason.CANCELLED) {
                        assertTrue("cancelled duel $idx in a match that was not decided early", result.decidedEarly)
                        assertNull(d.result!!.winner)
                    }
                }
                if (!result.decidedEarly) assertTrue(m.duels.none { it.result!!.reason == EndReason.CANCELLED })
            }
        }
        assertEquals((1..5).sumOf { n -> (1..n).fold(1) { a, k -> a * k } * Math.pow(3.0, n.toDouble()).toInt() }, sequences)
    }

    @Test fun acTeam02_drawExamplesFromTheSpec() {
        // 3v3: 1 win, 1 loss, 1 draw -> team draw (protocol 8.4).
        val m = match(3)
        finishDuel(m, 0, DuelState.A, 1, false)
        finishDuel(m, 1, DuelState.B, 2, false)
        assertNull(m.result)
        finishDuel(m, 2, DuelState.DRAW, 3, false)
        assertEquals(MatchResult(null, decidedEarly = false), m.result)
        // 1v1 draw.
        val one = match(1)
        finishDuel(one, 0, DuelState.DRAW, 1, false)
        assertEquals(MatchResult(null, decidedEarly = false), one.result)
    }

    @Test fun acTeam03_earlyDecisionWithADrawCancelsTheRest() {
        // 4v4: A, A, draw -> A leads 2:0 with 1 open; 2 > 0 + 1 -> decided early (the prototype's majority rule misses this).
        val m = match(4)
        finishDuel(m, 0, DuelState.A, 1, false)
        finishDuel(m, 1, DuelState.A, 2, false)
        assertNull(m.result)
        finishDuel(m, 2, DuelState.DRAW, 3, false)
        assertEquals(MatchResult(Team.A, decidedEarly = true), m.result)
        val last = m.duels[3].result!!
        assertEquals(EndReason.CANCELLED, last.reason)
        assertNull(last.winner)
        assertEquals(3L, m.endedAt)
    }

    @Test fun acTeam04_simultaneousTimerEndingsAreDecidedTogether() {
        // 3v3 with a 30 s timer. Duel 0: A wins at t=1. Duels 1 and 2 both end at t=60 000 by timeouts, one each way.
        // Evaluated together: 2:1 for A with nothing open -> not "early", and duel 2 keeps its real result (DEF-036).
        val m = match(3, seconds = 30)
        finishDuel(m, 0, DuelState.A, 1, false)
        assertEquals(ActionResult.Ok, m.guess("a1", miss, 1, 2)) // b1 misses
        assertEquals(ActionResult.Ok, m.guess("b2", miss, 1, 2)) // a2 misses
        m.advance(30_000)
        assertEquals(ActionResult.Ok, m.guess("a1", "679", 2, 30_001))
        assertEquals(ActionResult.Ok, m.guess("b2", "679", 2, 30_001))
        m.advance(60_000)
        assertEquals(MatchResult(Team.A, decidedEarly = false), m.result)
        assertEquals(EndReason.TIMEOUT, m.duels[1].result!!.reason)
        assertEquals(Team.A, m.duels[1].result!!.winner)
        assertEquals(EndReason.TIMEOUT, m.duels[2].result!!.reason)
        assertEquals(Team.B, m.duels[2].result!!.winner)
        assertEquals(2, m.score().a)
        assertEquals(1, m.score().b)
    }

    @Test fun acTeam05_unknownPlayerAndDuplicatePlayers() {
        val m = match(2)
        assertEquals(ActionResult.Rejected(ErrorCodes.NOT_IN_DUEL), m.guess("zzz", "123", 1, 1))
        val dup = runCatching { TeamMatch(DuelConfig.of(3, 0), listOf("x" to "y", "x" to "z")) }
        assertTrue("a player in two duels must be refused", dup.isFailure)
        val empty = runCatching { TeamMatch(DuelConfig.of(3, 0), emptyList()) }
        assertTrue("a match without duels must be refused", empty.isFailure)
    }

    @Test fun acTeam06_disconnectForfeitCountsForTheOtherTeam() {
        val m = match(2)
        m.disconnected("a0", 10)
        m.advance(60_010)
        assertEquals(Team.B, m.duels[0].result!!.winner)
        assertEquals(EndReason.DISCONNECT, m.duels[0].result!!.reason)
        assertNull(m.result) // 0:1 with one open duel: not decided
        finishDuel(m, 1, DuelState.DRAW, 60_020, false)
        assertEquals(MatchResult(Team.B, decidedEarly = false), m.result)
    }
}
