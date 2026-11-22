package app.numbaseball.server

import app.numbaseball.core.protocol.Ack
import app.numbaseball.core.protocol.ChatHistory
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.LeaveReason
import app.numbaseball.core.protocol.MatchResult
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.Queued
import app.numbaseball.core.protocol.Resync
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.RoomLeave
import app.numbaseball.core.protocol.RoomLeft
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.SetSecret
import app.numbaseball.core.protocol.Stats
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.protocol.Welcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFlowTest {
    /** Two players queue for 3 digits and land in the same quick room. */
    private suspend fun TestServer.quickPair(digits: Int = 3): Pair<Player, Player> {
        val a = connect(account("Mina"))
        val b = connect(account("Leo"))
        a.expect<Welcome>()
        b.expect<Welcome>()
        a.send(QueueJoin(digits, rid = 1))
        a.expect<Queued> { it.digits == digits }
        a.expect<Ack> { it.rid == 1L }
        b.send(QueueJoin(digits))
        val ra = a.state { it.phase == RoomPhase.PLAYING }
        val rb = b.state { it.phase == RoomPhase.PLAYING }
        assertEquals(ra.id, rb.id)
        assertEquals(RoomKind.QUICK, ra.kind)
        assertEquals(60, ra.settings.roundSeconds)
        assertEquals(Team.A, ra.players.first { it.id == a.id }.team)
        return a to b
    }

    private fun RoomView.duel() = match!!.myDuel!!

    @Test fun fullOneVsOneGame() = serverTest {
        val (a, b) = quickPair()
        a.send(SetSecret("012"))
        b.send(SetSecret("789"))
        val s = a.state { it.duel().phase == DuelPhase.GUESSING }
        assertEquals(1, s.duel().round)
        assertEquals("012", s.duel().mySecret)
        assertNull("secret must never leak before the end", s.duel().opponentSecret)
        assertEquals(s.serverTime + 60_000, s.duel().deadline)

        // Round 1: nobody solves.
        a.send(Guess("123", 1))
        val pending = b.state { it.duel().opponentGuessed }
        assertNull(pending.duel().myPendingGuess)
        b.send(Guess("102", 1))
        val r2 = a.state { it.duel().round == 2 }
        assertEquals(listOf(0 to 0), r2.duel().myRounds.map { it.strikes to it.balls }) // 123 vs 789
        assertEquals(1 to 2, r2.duel().opponentRounds.single().let { it.strikes to it.balls }) // 102 vs 012

        // Stale round is rejected, then A solves in round 2.
        a.send(Guess("456", 1, rid = 9))
        assertEquals(ErrorCodes.STALE_ROUND, a.expect<ErrorMessage> { it.rid == 9L }.code)
        a.send(Guess("789", 2))
        b.send(Guess("013", 2))
        val end = a.state { it.phase == RoomPhase.FINISHED }
        assertEquals(DuelResult(Team.A, EndReason.SOLVED), end.duel().result)
        assertEquals(MatchResult(Team.A, decidedEarly = false), end.match!!.result)
        assertEquals("789", end.duel().opponentSecret)
        assertEquals("012", b.state { it.phase == RoomPhase.FINISHED }.duel().opponentSecret)

        assertEquals(Stats(1, 0, 0), me(a.account.token).stats)
        assertEquals(Stats(0, 1, 0), me(b.account.token).stats)

        a.send(RoomLeave())
        assertEquals(LeaveReason.LEFT, a.expect<RoomLeft>().reason)
        b.send(RoomLeave())
        b.expect<RoomLeft>()
        assertEquals(0, services.hub.counts()["rooms"])
    }

    @Test fun twoMissedRoundsForfeit() = serverTest {
        val (a, b) = quickPair()
        a.send(SetSecret("012"))
        b.send(SetSecret("789"))
        a.state { it.duel().phase == DuelPhase.GUESSING }
        a.send(Guess("123", 1))
        a.state { it.duel().myPendingGuess == "123" }
        advance(60_000)
        val r2 = a.state { it.duel().round == 2 }
        assertEquals(1, r2.duel().opponentMisses)
        assertNull(r2.duel().opponentRounds.single().guess)
        a.send(Guess("124", 2))
        a.state { it.duel().myPendingGuess == "124" }
        advance(60_000)
        val end = b.state { it.phase == RoomPhase.FINISHED }
        assertEquals(DuelResult(Team.A, EndReason.TIMEOUT), end.duel().result)
        assertEquals(Stats(0, 1, 0), me(b.account.token).stats)
    }

    @Test fun choiceTimeoutGivesRandomSecretAndCountsAsMiss() = serverTest {
        val (a, b) = quickPair()
        a.send(SetSecret("012"))
        a.state { it.duel().mySecret == "012" }
        advance(60_000)
        val s = b.state { it.duel().phase == DuelPhase.GUESSING }
        assertNotNull(s.duel().mySecret)
        assertEquals(1, s.duel().myMisses)
    }

    @Test fun disconnectAndReconnectResyncs() = serverTest {
        val (a, b) = quickPair()
        a.send(SetSecret("012"))
        b.send(SetSecret("789"))
        a.state { it.duel().phase == DuelPhase.GUESSING }
        a.send(Guess("123", 1))
        a.state { it.duel().myPendingGuess == "123" }

        b.close()
        assertFalse(a.state { s -> s.players.any { it.id == b.id && !it.connected } }.players.first { it.id == b.id }.connected)
        advance(30_000) // within the grace period (and the round has not timed out yet)

        val b2 = connect(b.account)
        val welcome = b2.expect<Welcome>()
        assertEquals(a.lastRoom!!.id, welcome.roomId)
        val resync = b2.state()
        assertEquals("789", resync.duel().mySecret)
        assertTrue(resync.duel().opponentGuessed)
        assertEquals(1, resync.duel().round)
        b2.expect<ChatHistory>()
        assertTrue(a.state { s -> s.players.first { it.id == b.id }.connected }.players.isNotEmpty())

        // An explicit resync returns the same state.
        b2.send(Resync(rid = 3))
        assertEquals(resync.duel().round, b2.state().duel().round)
        b2.expect<Ack> { it.rid == 3L }

        // Gone for good: the duel is forfeited after 60 s (rounds keep counting meanwhile).
        b2.close()
        a.state { s -> s.players.first { it.id == b.id }.connected.not() }
        advance(59_000) // round 1 times out at 60 s after it began -> B missed once
        advance(1_100)
        val end = a.state(timeoutMs = 5_000) { it.phase == RoomPhase.FINISHED }
        assertEquals(Team.A, end.duel().result!!.winner)
        assertTrue(end.duel().result!!.reason in setOf(EndReason.DISCONNECT, EndReason.TIMEOUT))
    }

    @Test fun gracefulShutdownClosesRoomsAndRecordsNothing() = serverTest {
        val (a, b) = quickPair()
        val roomId = a.lastRoom!!.id
        services.hub.shutdown()
        assertEquals(LeaveReason.CLOSED, a.expect<RoomLeft> { it.roomId == roomId }.reason)
        assertEquals(1001.toShort(), a.awaitClose()?.code)
        assertEquals(1001.toShort(), b.awaitClose()?.code)
        assertEquals(Stats(0, 0, 0), me(a.account.token).stats)
        val again = connect(a.account)
        assertNull(again.expect<Welcome>().roomId)
    }

    @Test fun idlePrivateLobbyIsClosedAfter30Minutes() = serverTest {
        val a = connect(account("Mina")); a.expect<Welcome>()
        val b = connect(account("Leo")); b.expect<Welcome>()
        a.send(app.numbaseball.core.protocol.RoomCreate(app.numbaseball.core.protocol.RoomSettings(3, 2, 60)))
        val code = a.state().code!!
        advance(29 * 60_000L)
        b.send(app.numbaseball.core.protocol.RoomJoin(code)) // a join restarts the 30 minutes
        b.state { s -> s.players.size == 2 }
        advance(29 * 60_000L)
        assertEquals(1, services.hub.counts()["rooms"])
        advance(60_000L)
        assertEquals(LeaveReason.CLOSED, a.expect<RoomLeft>().reason)
        assertEquals(LeaveReason.CLOSED, b.expect<RoomLeft>().reason)
        assertEquals(0, services.hub.counts()["rooms"])
        a.send(Resync(rid = 2))
        a.expect<Ack> { it.rid == 2L }
        assertNull(connect(a.account).expect<Welcome>().roomId)
    }

    @Test fun newConnectionReplacesOldOne() = serverTest {
        val acc = account("Mina")
        val first = connect(acc)
        first.expect<Welcome>()
        val second = connect(acc)
        second.expect<Welcome>()
        assertEquals(ErrorCodes.SESSION_REPLACED, first.expect<ErrorMessage>().code)
        assertEquals(4000.toShort(), first.awaitClose()?.code)
    }

    @Test fun quickMatchNeverPairsBlockedPlayers() = serverTest {
        val a = account("Mina")
        val b = account("Leo")
        val c = account("Ann")
        assertEquals(204, http(io.ktor.http.HttpMethod.Put, "/api/v1/me/blocks/${b.id}", a.token).status.value)
        val pa = connect(a); pa.expect<Welcome>()
        val pb = connect(b); pb.expect<Welcome>()
        val pc = connect(c); pc.expect<Welcome>()
        pa.send(QueueJoin(4)); pa.expect<Queued>()
        pb.send(QueueJoin(4)); pb.expect<Queued>()
        assertEquals(2, services.hub.counts()["queued"])
        pc.send(QueueJoin(4))
        val room = pc.state { it.phase == RoomPhase.PLAYING }
        assertEquals(setOf(a.id, c.id), room.players.map { it.id }.toSet())
        assertEquals(1, services.hub.counts()["queued"])
    }
}
