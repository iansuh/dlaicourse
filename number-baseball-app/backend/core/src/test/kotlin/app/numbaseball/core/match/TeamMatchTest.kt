package app.numbaseball.core.match

import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.MatchResult
import app.numbaseball.core.protocol.Score
import app.numbaseball.core.protocol.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TeamMatchTest {
    private val secretA = "123"
    private val secretB = "456"

    private fun match(n: Int, roundSeconds: Int = 60): TeamMatch {
        val m = TeamMatch(DuelConfig.of(3, roundSeconds), (1..n).map { "a$it" to "b$it" }, Random(3))
        m.start(0)
        for (i in 1..n) {
            m.setSecret("a$i", secretA, 0)
            m.setSecret("b$i", secretB, 0)
        }
        return m
    }

    /** Duel i: winner A, B, or draw (null) in round 1. */
    private fun play(m: TeamMatch, i: Int, winner: Team?, now: Long = 10) {
        val r = m.duels[i - 1].round
        m.guess("a$i", if (winner == Team.A || winner == null) secretB else "789", r, now)
        m.guess("b$i", if (winner == Team.B || winner == null) secretA else "789", r, now)
    }

    @Test fun oneVsOne() {
        val m = match(1)
        assertEquals(0 to Team.A, m.locate("a1"))
        play(m, 1, Team.B)
        assertEquals(MatchResult(Team.B, decidedEarly = false), m.result)
        assertEquals(Score(0, 1), m.score())
    }

    @Test fun majorityWinsEarlyAndCancelsTheRest() {
        val m = match(3)
        play(m, 1, Team.A)
        assertNull(m.result)
        play(m, 2, Team.A)
        assertEquals(MatchResult(Team.A, decidedEarly = true), m.result)
        assertEquals(DuelResult(null, EndReason.CANCELLED), m.duels[2].result)
        assertEquals(DuelPhase.FINISHED, m.duels[2].phase)
        assertEquals(ActionResult.Rejected(ErrorCodes.WRONG_PHASE), m.guess("a3", "456", 1, 20))
        assertEquals(Score(2, 0), m.score())
        assertNull(m.nextWakeup())
    }

    @Test fun duelDrawAndTeamDraw() {
        // 3v3: A wins one, B wins one, one duel is a draw -> team draw.
        val m = match(3)
        play(m, 1, Team.A)
        play(m, 2, null)
        assertEquals(DuelResult(null, EndReason.BOTH_SOLVED), m.duels[1].result)
        assertNull(m.result)
        play(m, 3, Team.B)
        assertEquals(MatchResult(null, decidedEarly = false), m.result)
        assertEquals(Score(1, 1), m.score())
    }

    @Test fun drawsDoNotCountSoOneWinCanTakeTheMatch() {
        val m = match(2)
        play(m, 1, null)
        assertNull(m.result) // 0:0 with one open duel
        play(m, 2, Team.A)
        assertEquals(MatchResult(Team.A, decidedEarly = false), m.result)
    }

    @Test fun earlyDecisionOnlyWhenTheLeaderCannotBeCaught() {
        val m = match(4)
        play(m, 1, Team.A)
        play(m, 2, Team.A)
        assertNull(m.result) // 2:0 with two open duels: B can still tie
        play(m, 3, null)
        assertEquals(MatchResult(Team.A, decidedEarly = true), m.result) // 2:0 with one open
    }

    @Test fun timeoutsAcrossDuelsAreProcessedInOrder() {
        val m = match(3, roundSeconds = 30)
        // Duel 1: B never guesses -> A wins at 60 s. Duel 2: A never guesses -> B wins at 60 s.
        // Duel 3: both keep guessing wrong (its rounds resolve as soon as both have guessed).
        fun duel3(now: Long) {
            val r = m.duels[2].round
            m.guess("a3", "789", r, now)
            m.guess("b3", "789", r, now)
        }
        m.guess("a1", "789", 1, 1)
        m.guess("b2", "789", 1, 1)
        duel3(1)
        duel3(20_000)
        m.guess("a1", "789", 2, 30_001)
        m.guess("b2", "789", 2, 30_001)
        duel3(40_000)
        m.advance(60_000)
        assertEquals(DuelResult(Team.A, EndReason.TIMEOUT), m.duels[0].result)
        assertEquals(DuelResult(Team.B, EndReason.TIMEOUT), m.duels[1].result)
        assertFalse(m.isFinished)
        m.forfeit("b3", 61_000)
        assertEquals(MatchResult(Team.A, decidedEarly = false), m.result)
        assertEquals(61_000L, m.endedAt)
    }

    @Test fun disconnectForfeitDecidesTheMatch() {
        val m = match(1, roundSeconds = 0)
        m.disconnected("a1", 100)
        assertTrue(m.advance(60_100))
        assertEquals(MatchResult(Team.B, decidedEarly = false), m.result)
        assertEquals(DuelResult(Team.B, EndReason.DISCONNECT), m.duels[0].result)
    }

    @Test fun unknownPlayerIsRejected() {
        val m = match(1)
        assertEquals(ActionResult.Rejected(ErrorCodes.NOT_IN_DUEL), m.guess("zz", "456", 1, 1))
    }
}
