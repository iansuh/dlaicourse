package app.numbaseball.core.qa

import app.numbaseball.core.match.ActionResult
import app.numbaseball.core.match.Duel
import app.numbaseball.core.match.DuelConfig
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/** AC-TIME-*, AC-RECON-* (engine level) and a random differential of Duel against QaDuelOracle. */
class DuelEngineQaTest {
    private val A = Team.A
    private val B = Team.B
    private val ok = ActionResult.Ok
    private fun rejected(code: String) = ActionResult.Rejected(code)

    private fun duel(digits: Int = 3, seconds: Int = 60, seed: Long = 1): Duel =
        Duel(DuelConfig.of(digits, seconds), Random(seed)).also { it.start(0) }

    /** Both secrets set at t=0: A has 012, B has 345. Round 1 deadline = 60 s. */
    private fun guessing(seconds: Int = 60): Duel = duel(seconds = seconds).also {
        assertEquals(ok, it.setSecret(A, "012", 0))
        assertEquals(ok, it.setSecret(B, "345", 0))
        assertEquals(DuelPhase.GUESSING, it.phase)
    }

    @Test fun acTime01_roundResolvesWhenBothGuessed() {
        val d = guessing()
        assertEquals(60_000L, d.deadline)
        assertEquals(ok, d.guess(A, "345".reversed(), 1, 1_000))
        assertEquals(DuelPhase.GUESSING, d.phase)
        assertEquals(1, d.round)
        assertEquals(ok, d.guess(B, "021", 1, 2_000))
        assertEquals(2, d.round)
        assertEquals(62_000L, d.deadline) // next round starts at the resolution time
        assertEquals(QaOracle.judge("345", "543").strikes, d.roundsOf(A).single().strikes)
    }

    @Test fun acTime02_solveBeatsOpponentTimeoutInTheSameRound() {
        val d = guessing()
        d.advance(60_000) // round 1: both miss
        assertEquals(1, d.missesOf(A))
        assertEquals(ok, d.guess(A, "345", 2, 61_000)) // A solves in round 2, B misses its 2nd round
        d.advance(120_000)
        assertEquals(DuelResult(A, EndReason.SOLVED), d.result)
    }

    @Test fun acTime03_bothSolveSameRoundIsADraw() {
        val d = guessing()
        assertEquals(ok, d.guess(A, "345", 1, 10))
        assertEquals(ok, d.guess(B, "012", 1, 20))
        assertEquals(DuelResult(null, EndReason.BOTH_SOLVED), d.result)
    }

    @Test fun acTime04_twoMissedRoundsInARowForfeit() {
        val d = guessing()
        assertEquals(ok, d.guess(B, "678", 1, 10))
        d.advance(60_000) // A misses round 1
        assertEquals(ok, d.guess(B, "679", 2, 60_010))
        d.advance(120_000) // A misses round 2
        assertEquals(DuelResult(B, EndReason.TIMEOUT), d.result)
        assertEquals(listOf(null, null), d.roundsOf(A).map { it.guess })
    }

    @Test fun acTime05_bothMissTwiceIsADraw() {
        val d = guessing()
        d.advance(120_000)
        assertEquals(DuelResult(null, EndReason.BOTH_TIMEOUT), d.result)
    }

    @Test fun acTime06_aGuessResetsTheMissCounter() {
        val d = guessing()
        d.advance(60_000) // miss
        assertEquals(ok, d.guess(A, "678", 2, 60_001))
        d.advance(120_000) // A guessed, B missed twice -> B loses
        assertEquals(0, d.missesOf(A))
        assertEquals(DuelResult(A, EndReason.TIMEOUT), d.result)
    }

    @Test fun acTime07_choosingTimeoutAssignsASecretAndCountsAsAMiss() {
        val d = duel()
        assertEquals(ok, d.setSecret(A, "012", 1))
        d.advance(60_000)
        val assigned = d.secretOf(B)
        assertNotNull(assigned)
        assertTrue(QaOracle.isValid(assigned!!, 3))
        assertEquals(1, d.missesOf(B))
        assertEquals(0, d.missesOf(A))
        assertEquals(DuelPhase.GUESSING, d.phase)
        // A missed choosing never, B missed choosing + round 1 -> B loses at the round-1 deadline.
        val miss = if (assigned == "678") "679" else "678"
        assertEquals(ok, d.guess(A, miss, 1, 60_001))
        d.advance(120_000)
        assertEquals(DuelResult(A, EndReason.TIMEOUT), d.result)
    }

    @Test fun acTime08_noTimerMeansNoDeadlineEver() {
        val d = duel(seconds = 0)
        assertNull(d.deadline)
        assertNull(d.nextWakeup())
        d.advance(Long.MAX_VALUE / 2)
        assertEquals(DuelPhase.CHOOSING, d.phase)
        // DEF-003: a connected but idle opponent can stall an untimed duel forever; the only exit is a forfeit.
    }

    @Test fun acTime09_errorCodes() {
        val d = duel()
        assertEquals(rejected(ErrorCodes.INVALID_SECRET), d.setSecret(A, "011", 1))
        assertEquals(rejected(ErrorCodes.WRONG_PHASE), d.guess(A, "012", 0, 1))
        assertEquals(ok, d.setSecret(A, "012", 2))
        assertEquals(rejected(ErrorCodes.SECRET_ALREADY_SET), d.setSecret(A, "013", 3))
        assertEquals(ok, d.setSecret(B, "345", 4))
        assertEquals(rejected(ErrorCodes.SECRET_ALREADY_SET), d.setSecret(A, "013", 5))
        assertEquals(rejected(ErrorCodes.INVALID_GUESS), d.guess(A, "33", 1, 6))
        assertEquals(rejected(ErrorCodes.STALE_ROUND), d.guess(A, "678", 2, 7))
        assertEquals(rejected(ErrorCodes.STALE_ROUND), d.guess(A, "678", 0, 7))
        assertEquals(ok, d.guess(A, "678", 1, 8))
        assertEquals(rejected(ErrorCodes.ALREADY_GUESSED), d.guess(A, "679", 1, 9))
        assertEquals(ok, d.forfeit(B, 10))
        assertEquals(DuelResult(A, EndReason.FORFEIT), d.result)
        assertEquals(rejected(ErrorCodes.WRONG_PHASE), d.forfeit(A, 11))
        assertEquals(rejected(ErrorCodes.WRONG_PHASE), d.guess(A, "679", 1, 12))
        assertNull(d.deadline)
    }

    @Test fun acRecon01_reconnectJustBeforeGraceKeepsTheDuel() {
        val d = duel(seconds = 0)
        d.disconnected(A, 1_000)
        d.reconnected(A, 60_999)
        d.advance(500_000)
        assertNull(d.result)
    }

    @Test fun acRecon02_sixtySecondsDisconnectedLoses() {
        val d = duel(seconds = 0)
        d.disconnected(A, 1_000)
        d.advance(60_999)
        assertNull(d.result)
        d.advance(61_000)
        assertEquals(DuelResult(B, EndReason.DISCONNECT), d.result)
        assertEquals(61_000L, d.endedAt)
    }

    @Test fun acRecon03_bothDisconnectedAtTheSameInstantIsADraw() {
        val d = duel(seconds = 0)
        d.disconnected(A, 5_000)
        d.disconnected(B, 5_000)
        d.advance(65_000)
        assertEquals(DuelResult(null, EndReason.DISCONNECT), d.result)
    }

    @Test fun acRecon04_missedRoundsKeepCountingWhileDisconnected() {
        // protocol 8.3: with a 30 s timer two missed rounds can forfeit before the 60 s grace is over.
        val d = guessing(seconds = 30)
        d.disconnected(A, 0)
        assertEquals(ok, d.guess(B, "678", 1, 1))
        d.advance(30_000)
        assertEquals(ok, d.guess(B, "679", 2, 30_001))
        d.advance(60_000)
        assertEquals(DuelResult(B, EndReason.TIMEOUT), d.result)
    }

    @Test fun acRecon05_roundDeadlineWinsATieWithDisconnectExpiry() {
        val d = guessing(seconds = 60)
        d.disconnected(B, 0) // expiry 60 000 == round-1 deadline
        assertEquals(ok, d.guess(A, "345", 1, 10))
        d.advance(60_000)
        assertEquals(DuelResult(A, EndReason.SOLVED), d.result)
    }

    @Test fun acRecon06_reconnectAtExactlySixtySecondsIsTooLate() {
        val d = duel(seconds = 0)
        d.disconnected(A, 0)
        d.reconnected(A, 60_000)
        assertEquals(DuelResult(B, EndReason.DISCONNECT), d.result)
    }

    @Test fun acTime10_cancelGivesNoWinner() {
        val d = guessing()
        d.cancel(5)
        assertEquals(DuelResult(null, EndReason.CANCELLED), d.result)
        assertEquals(rejected(ErrorCodes.WRONG_PHASE), d.guess(A, "345", 1, 6))
    }

    // ------------------------------------------------------------------ random differential

    @Test fun acTime11_randomScriptsAgreeWithTheOracle() {
        var steps = 0
        var finished = 0
        for (seed in 0 until 12_000) {
            val r = runScript(seed.toLong())
            steps += r.first
            if (r.second) finished++
        }
        assertTrue("too few steps exercised: $steps", steps > 100_000)
        assertTrue("too few duels reached an end: $finished", finished > 8_000)
    }

    private fun team(side: Int) = if (side == 0) A else B

    private fun codesOf(r: ActionResult): String? = (r as? ActionResult.Rejected)?.code

    /** Returns (steps, finished). Fails with a reproducible log on the first divergence. */
    private fun runScript(seed: Long): Pair<Int, Boolean> {
        val rnd = Random(seed)
        val digits = rnd.nextInt(3, 7)
        val roundMs = listOf(null, 1_000L, 30_000L, 60_000L, 120_000L).random(rnd)
        val core = Duel(DuelConfig(digits, roundMs), Random(seed * 31 + 7))
        val oracle = QaDuelOracle(digits, roundMs)
        oracle.assignSecret = { side -> core.secretOf(team(side)) ?: error("core did not assign a secret") }
        val log = ArrayList<String>()
        var now = 1_000_000L
        core.start(now)
        oracle.start(now)
        log += "seed=$seed digits=$digits roundMs=$roundMs start@$now"
        var steps = 0
        var after = 0
        while (steps < 80 && after < 3) {
            steps++
            if (oracle.phase == QaDuelOracle.Phase.FINISHED) after++
            // time step: same instant, small, exactly on the next timer, one ms before it, or big
            val next = oracle.nextTimer()
            now = when (rnd.nextInt(10)) {
                0, 1 -> now
                2 -> if (next != null && next >= now) next else now + 1
                3 -> if (next != null && next - 1 >= now) next - 1 else now
                4 -> now + rnd.nextLong(0, 200_000)
                else -> now + rnd.nextLong(0, 5_000)
            }
            val side = rnd.nextInt(2)
            val desc: String
            val expected: Set<String>?
            val actual: ActionResult?
            when (rnd.nextInt(100)) {
                in 0..17 -> {
                    val v = if (rnd.nextInt(8) == 0) "1" + "1".repeat(digits - 1) else QaOracle.random(digits, rnd)
                    desc = "secret(${team(side)}, $v)@$now"
                    actual = core.setSecret(team(side), v, now)
                    expected = oracle.setSecret(side, v, now)
                }
                in 18..64 -> {
                    val opp = oracle.secret[1 - side]
                    val v = when (rnd.nextInt(40)) {
                        in 0..1 -> opp ?: QaOracle.random(digits, rnd)
                        4 -> "9".repeat(digits)
                        5 -> QaOracle.random(digits, rnd).dropLast(1)
                        else -> QaOracle.random(digits, rnd)
                    }
                    val rd = when (rnd.nextInt(12)) {
                        0 -> oracle.round + 1
                        1 -> oracle.round - 1
                        else -> oracle.round
                    }
                    desc = "guess(${team(side)}, $v, round $rd)@$now"
                    actual = core.guess(team(side), v, rd, now)
                    expected = oracle.guess(side, v, rd, now)
                }
                in 65..74 -> {
                    desc = "disconnect(${team(side)})@$now"
                    core.disconnected(team(side), now); oracle.disconnect(side, now)
                    actual = null; expected = null
                }
                in 75..86 -> {
                    desc = "reconnect(${team(side)})@$now"
                    core.reconnected(team(side), now); oracle.reconnect(side, now)
                    actual = null; expected = null
                }
                in 87..88 -> {
                    desc = "forfeit(${team(side)})@$now"
                    actual = core.forfeit(team(side), now)
                    expected = oracle.forfeit(side, now)
                }
                else -> {
                    desc = "advance@$now"
                    core.advance(now); oracle.tick(now)
                    actual = null; expected = null
                }
            }
            log += desc + (actual?.let { " -> $it" } ?: "")
            fun diverge(what: String): Nothing = fail("Duel diverges from oracle ($what)\n" + log.joinToString("\n")) as Nothing
            if (actual != null && expected != null) {
                val code = codesOf(actual)
                if (expected.isEmpty() && code != null) diverge("expected accept, got $code")
                if (expected.isNotEmpty() && (code == null || code !in expected)) diverge("expected one of $expected, got ${code ?: "Ok"}")
            }
            compare(core, oracle, ::diverge)
        }
        return steps to (oracle.phase == QaDuelOracle.Phase.FINISHED)
    }

    private fun compare(core: Duel, o: QaDuelOracle, diverge: (String) -> Nothing) {
        val phase = when (o.phase) {
            QaDuelOracle.Phase.CHOOSING -> DuelPhase.CHOOSING
            QaDuelOracle.Phase.GUESSING -> DuelPhase.GUESSING
            QaDuelOracle.Phase.FINISHED -> DuelPhase.FINISHED
            QaDuelOracle.Phase.NOT_STARTED -> DuelPhase.CHOOSING
        }
        if (core.phase != phase) diverge("phase ${core.phase} vs ${o.phase}")
        if (core.round != o.round) diverge("round ${core.round} vs ${o.round}")
        if (core.deadline != o.deadline) diverge("deadline ${core.deadline} vs ${o.deadline}")
        val expectedResult = o.result?.let { DuelResult(it.winner?.let(::team), EndReason.entries.first { e -> e.name.lowercase() == it.reason }) }
        if (core.result != expectedResult) diverge("result ${core.result} vs $expectedResult")
        for (side in 0..1) {
            val t = team(side)
            if (core.missesOf(t) != o.misses[side]) diverge("misses $t ${core.missesOf(t)} vs ${o.misses[side]}")
            if (core.pendingGuessOf(t) != o.pending[side]) diverge("pending $t ${core.pendingGuessOf(t)} vs ${o.pending[side]}")
            val coreRounds = core.roundsOf(t).map { QaDuelOracle.Entry(it.round, it.guess, it.strikes, it.balls) }
            if (coreRounds != o.rounds[side]) diverge("rounds $t $coreRounds vs ${o.rounds[side]}")
            if ((core.secretOf(t) == null) != (o.secret[side] == null)) diverge("secret presence $t")
        }
    }
}
