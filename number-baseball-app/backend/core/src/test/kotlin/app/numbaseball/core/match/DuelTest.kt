package app.numbaseball.core.match

import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DuelTest {
    private val ok = ActionResult.Ok
    private fun rejected(code: String) = ActionResult.Rejected(code)

    private fun duel(roundSeconds: Int = 60, digits: Int = 3): Duel =
        Duel(DuelConfig.of(digits, roundSeconds), Random(1)).also { it.start(0) }

    private fun ready(d: Duel, a: String = "123", b: String = "456", now: Long = 0) {
        assertEquals(ok, d.setSecret(Team.A, a, now))
        assertEquals(ok, d.setSecret(Team.B, b, now))
    }

    @Test fun choosingThenGuessing() {
        val d = duel()
        assertEquals(DuelPhase.CHOOSING, d.phase)
        assertEquals(60_000L, d.deadline)
        assertEquals(rejected(ErrorCodes.WRONG_PHASE), d.guess(Team.A, "456", 1, 10))
        assertEquals(rejected(ErrorCodes.INVALID_SECRET), d.setSecret(Team.A, "112", 10))
        assertEquals(ok, d.setSecret(Team.A, "123", 10))
        assertEquals(rejected(ErrorCodes.SECRET_ALREADY_SET), d.setSecret(Team.A, "789", 11))
        assertEquals(DuelPhase.CHOOSING, d.phase)
        assertEquals(ok, d.setSecret(Team.B, "456", 20))
        assertEquals(DuelPhase.GUESSING, d.phase)
        assertEquals(1, d.round)
        assertEquals(60_020L, d.deadline)
    }

    @Test fun simultaneousRoundResolvesWhenBothGuessed() {
        val d = duel()
        ready(d)
        assertEquals(ok, d.guess(Team.A, "465", 1, 100))
        assertEquals("465", d.pendingGuessOf(Team.A))
        assertEquals(rejected(ErrorCodes.ALREADY_GUESSED), d.guess(Team.A, "789", 1, 101))
        assertEquals(1, d.round)
        assertEquals(ok, d.guess(Team.B, "120", 1, 200))
        assertEquals(2, d.round)
        assertEquals(listOf(RoundEntry(1, "465", 1, 2)), d.roundsOf(Team.A))
        assertEquals(listOf(RoundEntry(1, "120", 2, 0)), d.roundsOf(Team.B))
        assertNull(d.pendingGuessOf(Team.A))
        assertEquals(60_200L, d.deadline)
        assertEquals(rejected(ErrorCodes.STALE_ROUND), d.guess(Team.A, "789", 1, 300))
        assertEquals(rejected(ErrorCodes.INVALID_GUESS), d.guess(Team.A, "78", 2, 300))
    }

    @Test fun firstSolverWins() {
        val d = duel()
        ready(d)
        d.guess(Team.A, "456", 1, 1)
        d.guess(Team.B, "789", 1, 2)
        assertEquals(DuelPhase.FINISHED, d.phase)
        assertEquals(DuelResult(Team.A, EndReason.SOLVED), d.result)
        assertNull(d.deadline)
        assertEquals(rejected(ErrorCodes.WRONG_PHASE), d.guess(Team.B, "123", 2, 3))
    }

    @Test fun bothSolveInSameRoundIsADraw() {
        val d = duel()
        ready(d)
        d.guess(Team.A, "456", 1, 1)
        d.guess(Team.B, "123", 1, 2)
        assertEquals(DuelResult(null, EndReason.BOTH_SOLVED), d.result)
    }

    @Test fun missedRoundIsRecordedAndTwoInARowForfeit() {
        val d = duel(roundSeconds = 30)
        ready(d)
        d.guess(Team.A, "789", 1, 1_000)
        d.advance(30_000)
        assertEquals(2, d.round)
        assertEquals(RoundEntry(1, null, 0, 0), d.roundsOf(Team.B).single())
        assertEquals(1, d.missesOf(Team.B))
        assertEquals(0, d.missesOf(Team.A))
        assertEquals(60_000L, d.deadline)
        d.guess(Team.A, "780", 2, 31_000)
        d.advance(60_000)
        assertEquals(DuelResult(Team.A, EndReason.TIMEOUT), d.result)
        assertEquals(2, d.missesOf(Team.B))
    }

    @Test fun aGuessResetsTheMissCounter() {
        val d = duel(roundSeconds = 30)
        ready(d)
        d.guess(Team.A, "789", 1, 1)
        d.advance(30_000) // B misses round 1
        d.guess(Team.A, "780", 2, 30_001)
        d.guess(Team.B, "789", 2, 30_002) // B guesses round 2 -> reset
        assertEquals(0, d.missesOf(Team.B))
        d.guess(Team.A, "781", 3, 30_003)
        d.advance(60_002) // B misses round 3: only one in a row
        assertEquals(DuelPhase.GUESSING, d.phase)
        assertEquals(1, d.missesOf(Team.B))
    }

    @Test fun solvingBeatsAnOpponentTimeoutInTheSameRound() {
        val d = duel(roundSeconds = 30)
        ready(d)
        d.guess(Team.A, "789", 1, 1)
        d.advance(30_000)
        d.guess(Team.A, "456", 2, 30_001) // solves while B times out a second time
        d.advance(60_000)
        assertEquals(DuelResult(Team.A, EndReason.SOLVED), d.result)
    }

    @Test fun bothTimingOutIsADraw() {
        val d = duel(roundSeconds = 30)
        ready(d)
        d.advance(60_000)
        assertEquals(DuelResult(null, EndReason.BOTH_TIMEOUT), d.result)
        assertEquals(2, d.roundsOf(Team.A).size)
    }

    @Test fun choiceTimeoutAssignsRandomSecretAndCountsAsMiss() {
        val d = duel(roundSeconds = 30)
        d.setSecret(Team.A, "123", 1)
        d.advance(30_000)
        assertEquals(DuelPhase.GUESSING, d.phase)
        val assigned = d.secretOf(Team.B)
        assertNotNull(assigned)
        assertTrue(Rules.isValid(assigned!!, 3))
        assertEquals(1, d.missesOf(Team.B))
        // Missing round 1 as well is the second miss in a row.
        d.guess(Team.A, "789", 1, 30_001)
        d.advance(60_000)
        assertEquals(DuelResult(Team.A, EndReason.TIMEOUT), d.result)
    }

    @Test fun noTimerMeansNoDeadline() {
        val d = duel(roundSeconds = 0)
        assertNull(d.deadline)
        ready(d)
        assertNull(d.deadline)
        d.advance(10_000_000)
        assertEquals(DuelPhase.GUESSING, d.phase)
    }

    @Test fun disconnectForfeitsAfterGrace() {
        val d = duel(roundSeconds = 0)
        ready(d)
        d.disconnected(Team.B, 1_000)
        d.advance(60_999)
        assertEquals(DuelPhase.GUESSING, d.phase)
        d.advance(61_000)
        assertEquals(DuelResult(Team.A, EndReason.DISCONNECT), d.result)
        assertEquals(61_000L, d.endedAt)
    }

    @Test fun reconnectWithinGraceCancelsTheForfeit() {
        val d = duel(roundSeconds = 0)
        ready(d)
        d.disconnected(Team.A, 1_000)
        d.reconnected(Team.A, 50_000)
        d.advance(200_000)
        assertEquals(DuelPhase.GUESSING, d.phase)
        // A second disconnect starts a fresh grace period.
        d.disconnected(Team.A, 200_000)
        assertEquals(260_000L, d.nextWakeup())
    }

    @Test fun bothDisconnectedAtOnceIsADraw() {
        val d = duel(roundSeconds = 0)
        ready(d)
        d.disconnected(Team.A, 5)
        d.disconnected(Team.B, 5)
        d.advance(60_005)
        assertEquals(DuelResult(null, EndReason.DISCONNECT), d.result)
    }

    @Test fun eventsAreProcessedInTimeOrderAcrossALargeJump() {
        // Round timer 30 s; B disconnects at 10 s. Rounds expire at 30 s (B misses, A guessed) and 60 s;
        // the disconnect expires at 70 s, but B's second miss at 60 s ends the duel first.
        val d = duel(roundSeconds = 30)
        ready(d)
        d.guess(Team.A, "789", 1, 1)
        d.disconnected(Team.B, 10_000)
        d.advance(500_000)
        assertEquals(DuelResult(Team.A, EndReason.TIMEOUT), d.result)
        assertEquals(60_000L, d.endedAt)
    }

    @Test fun forfeit() {
        val d = duel()
        ready(d)
        assertEquals(ok, d.forfeit(Team.B, 5))
        assertEquals(DuelResult(Team.A, EndReason.FORFEIT), d.result)
        assertEquals(rejected(ErrorCodes.WRONG_PHASE), d.forfeit(Team.A, 6))
    }
}
