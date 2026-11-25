package app.numbaseball.server.qa

import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.QueueLeave
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomLeave
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.SetSecret
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AC-QM-*, AC-ROOM-*, AC-TEAM-* (server), AC-TIME-* (server), AC-RECON-*, AC-SRV-*, AC-STAT-*: real server module, real sockets.
 * Anything that depends on time (round timers, the 60 s reconnect window, lobby clean-up) uses a private instance and the
 * TEST_HOOKS clock instead of waiting.
 */
class MatchFlowQaTest {

    private fun stats(inst: QaInstance, token: String): String {
        val s = inst.call("GET", "/api/v1/me", token).json!!["stats"]!!.jsonObject
        return "${s["wins"]}/${s["losses"]}/${s["draws"]}"
    }

    // ------------------------------------------------------------------ quick match

    @Test fun acQm01_twoPlayersSameDigitsArePairedIntoAQuickRoomWith60SecondRounds() {
        val (a, b) = QaFlows.quickPair(4)
        val ra = a.room!!
        assertEquals("QUICK", ra.kind.name)
        assertEquals(1, ra.settings.teamSize)
        assertEquals(60, ra.settings.roundSeconds)
        assertEquals(4, ra.settings.digits)
        assertNull(ra.code)
        assertNull(ra.hostId)
        assertEquals(ra.id, b.room!!.id)
        assertEquals("earlier player is team A", a.id, ra.match!!.duels[0].playerA)
        val d = ra.match!!.myDuel!!
        assertEquals("CHOOSING", d.phase.name)
        assertEquals(60_000L, d.deadline!! - ra.serverTime)
        val cfg = QaServer.main.call("GET", "/api/v1/config").json!!
        assertEquals(60, cfg["quickRoundSeconds"]!!.jsonPrimitive.content.toInt())
        assertEquals(listOf(30, 60, 120, 600), cfg["roundSeconds"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() })
        a.close(); b.close()
    }

    @Test fun acQm02_differentDigitCountsAreNeverPaired() {
        val a = QaPlayer.fresh(); val b = QaPlayer.fresh()
        a.ws.send(QueueJoin(3)); a.ws.awaitType("queued")
        b.ws.send(QueueJoin(5)); b.ws.awaitType("queued")
        assertTrue(runCatching { a.awaitRoom(2_000) }.isFailure)
        a.close(); b.close()
    }

    @Test fun acQm03_blockedPlayersAreNeverPairedEitherDirection() {
        val inst = QaServer.main
        val a = inst.newAccount(); val b = inst.newAccount()
        assertEquals(204, inst.call("PUT", "/api/v1/me/blocks/${b.account.id}", a.token).status)
        val pa = QaPlayer(a); val pb = QaPlayer(b)
        pb.ws.send(QueueJoin(3)); pb.ws.awaitType("queued")
        pa.ws.send(QueueJoin(3)); pa.ws.awaitType("queued")
        assertTrue("blocked pair was matched", runCatching { pa.awaitRoom(2_000) }.isFailure)
        // A third player is paired with the earlier waiting one (first come, first served, skipping the blocked pair).
        val c = QaPlayer.fresh()
        c.ws.send(QueueJoin(3))
        val room = c.awaitRoom(5_000) { it.kind.name == "QUICK" }
        assertTrue(room.players.any { it.id == pb.id })
        pa.close(); pb.close(); c.close()
    }

    @Test fun acQm04_queueEntryIsDroppedOnDisconnect() {
        val a = QaPlayer.fresh()
        a.ws.send(QueueJoin(3)); a.ws.awaitType("queued")
        a.close()
        Thread.sleep(500)
        val b = QaPlayer.fresh()
        b.ws.send(QueueJoin(3)); b.ws.awaitType("queued")
        assertTrue("paired with a disconnected player", runCatching { b.awaitRoom(2_000) }.isFailure)
        b.close()
    }

    @Test fun acQm05_queueStatesAndResync() {
        val p = QaPlayer.fresh()
        p.ws.send(QueueLeave()); p.ws.awaitError("not_queued")
        p.ws.send(QueueJoin(3)); p.ws.awaitType("queued")
        p.ws.send(QueueJoin(4)); p.ws.awaitError("already_queued")
        p.ws.send(app.numbaseball.core.protocol.Resync())
        assertEquals("queued", p.ws.awaitType("queued").type)
        p.ws.send(RoomCreate(RoomSettings(3, 1, 60))); p.ws.awaitError("already_in_room")
        assertEquals("ack", p.ws.request("{\"type\":\"queue_leave\"}").type)
        p.ws.send(app.numbaseball.core.protocol.Resync())
        assertTrue("nothing but an ack when idle", p.ws.drain(300).none { it.type == "room_state" || it.type == "queued" })
        p.close()
    }

    @Test fun acQm06_queueJoinWhileSeatedIsRefusedAndAReconnectDropsTheQueueEntry() {
        val (a, b) = QaFlows.quickPair()
        a.ws.send(QueueJoin(3)); a.ws.awaitError("already_in_room")
        a.close(); b.close()
        val q = QaPlayer.fresh()
        q.ws.send(QueueJoin(3)); q.ws.awaitType("queued")
        val again = QaWs.connect(q.auth.token) // newer connection replaces the old one and drops the queue entry
        again.awaitType("welcome")
        again.send(app.numbaseball.core.protocol.Resync())
        assertTrue(again.drain(300).none { it.type == "queued" })
        again.close(); q.close()
    }

    // ------------------------------------------------------------------ private rooms

    @Test fun acRoom01_roomFillsAndStartsAutomaticallyCodeIsCaseAndDashInsensitive() {
        val host = QaFlows.privateLobby(RoomSettings(3, 1, 30))
        val code = host.room!!.code!!
        assertTrue(code.matches(Regex("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{5}")))
        val guest = QaPlayer.fresh()
        val typed = code.lowercase().let { it.substring(0, 2) + "-" + it.substring(2, 3) + " " + it.substring(3) }
        QaFlows.join(guest, typed)
        val started = host.awaitRoom { it.phase.name == "PLAYING" }
        val left = started.match!!.myDuel!!.deadline!! - started.serverTime
        assertTrue("first deadline is 30 s after the start, was $left", left in 29_000..30_000)
        host.close(); guest.close()
    }

    @Test fun acRoom02_hostLeavesEarliestJoinerBecomesHost() {
        val host = QaFlows.privateLobby(RoomSettings(3, 3, 60))
        val code = host.room!!.code!!
        val second = QaPlayer.fresh().also { QaFlows.join(it, code) }
        val third = QaPlayer.fresh().also { QaFlows.join(it, code) }
        host.ws.send(RoomLeave())
        host.ws.awaitType("room_left")
        val r = second.awaitRoom { it.hostId != host.id }
        assertEquals(second.id, r.hostId)
        assertEquals("seat freed in the lobby", 2, r.players.size)
        second.close(); third.close(); host.close()
    }

    @Test fun acRoom03_roundTimerChoicesAre30_60_120_600AndNothingElse() {
        for (seconds in listOf(30, 60, 120, 600)) {
            val host = QaFlows.privateLobby(RoomSettings(3, 1, seconds))
            val guest = QaPlayer.fresh().also { QaFlows.join(it, host.room!!.code!!) }
            val started = host.awaitRoom { it.phase.name == "PLAYING" }
            assertEquals(seconds, started.settings.roundSeconds)
            assertEquals("deadline for ${seconds}s", seconds * 1000L, started.match!!.myDuel!!.deadline!! - started.serverTime)
            host.close(); guest.close()
        }
        val p = QaPlayer.fresh()
        for (bad in listOf(0, 45, 59, 61, 300, 601, -1)) {
            p.ws.send(RoomCreate(RoomSettings(3, 1, bad)))
            p.ws.awaitError("invalid_settings") // there is no untimed or free-form online room
        }
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 60))
        host.ws.send(app.numbaseball.core.protocol.RoomSettingsUpdate(RoomSettings(4, 2, 600)))
        assertEquals(600, host.awaitRoom { it.settings.roundSeconds == 600 }.settings.roundSeconds)
        host.ws.send(app.numbaseball.core.protocol.RoomSettingsUpdate(RoomSettings(4, 2, 0)))
        host.ws.awaitError("invalid_settings")
        p.close(); host.close()
    }

    @Test fun acRoom04_aPrivateLobbyThatDoesNotStartIsClosedAfter30Minutes() {
        QaServer.start().use { inst ->
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 60), inst)
            val code = host.room!!.code!!
            inst.advance(29 * 60_000L)
            host.ws.send(app.numbaseball.core.protocol.Resync())
            assertEquals("still there after 29 min", code, host.awaitRoom().code)
            // A join resets the idle clock ("last join").
            val guest = QaPlayer.fresh(inst = inst).also { QaFlows.join(it, code) }
            inst.advance(29 * 60_000L)
            guest.ws.send(app.numbaseball.core.protocol.Resync())
            guest.awaitRoom()
            inst.advance(61_000L)
            assertEquals("closed", host.ws.awaitType("room_left")["reason"]!!.jsonPrimitive.content)
            assertEquals("closed", guest.ws.awaitType("room_left")["reason"]!!.jsonPrimitive.content)
            host.ws.send(RoomJoin(code)); host.ws.awaitError("room_not_found")
            host.close(); guest.close()
        }
    }

    @Test fun acRoom05_joiningARunningMatchAndRoomStateRules() {
        val host = QaFlows.privateLobby(RoomSettings(3, 1, 60))
        val code = host.room!!.code!!
        val guest = QaPlayer.fresh().also { QaFlows.join(it, code) }
        host.awaitRoom { it.phase.name == "PLAYING" }
        val late = QaPlayer.fresh()
        late.ws.send(RoomJoin(code)); late.ws.awaitError("match_in_progress")
        host.ws.send(RoomJoin(code)) // joining the room you are in just re-sends the state
        assertEquals(code, host.awaitRoom { true }.code)
        host.ws.send(RoomJoin("ZZZZZ")); host.ws.awaitError("already_in_room")
        host.ws.send(app.numbaseball.core.protocol.RoomSettingsUpdate(RoomSettings(3, 1, 60))); host.ws.awaitError("wrong_phase")
        guest.ws.send(app.numbaseball.core.protocol.RoomSettingsUpdate(RoomSettings(3, 1, 60))); guest.ws.awaitError("not_host")
        host.close(); guest.close(); late.close()
    }

    @Test fun acRoom06_hostMovesPlayersBetweenTeamsAndResetsAFinishedRoom() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 60))
        val code = host.room!!.code!!
        val g1 = QaPlayer.fresh().also { QaFlows.join(it, code) } // team B (smaller team)
        host.awaitRoom { it.players.size == 2 }
        assertEquals("B", host.room!!.players.first { it.id == g1.id }.team.name)
        g1.ws.send(app.numbaseball.core.protocol.TeamSet(app.numbaseball.core.protocol.Team.A, host.id)); g1.ws.awaitError("not_host")
        host.ws.send(app.numbaseball.core.protocol.TeamSet(app.numbaseball.core.protocol.Team.A, g1.id))
        host.awaitRoom { r -> r.players.first { it.id == g1.id }.team.name == "A" }
        host.ws.send(app.numbaseball.core.protocol.TeamSet(app.numbaseball.core.protocol.Team.B, "nobody000000")); host.ws.awaitError("not_found")
        host.ws.send(app.numbaseball.core.protocol.RoomSettingsUpdate(RoomSettings(3, 1, 60)))
        host.ws.awaitError("invalid_settings") // team A already has 2 players
        host.ws.send(app.numbaseball.core.protocol.TeamSet(app.numbaseball.core.protocol.Team.B, g1.id))
        host.awaitRoom { r -> r.players.first { it.id == g1.id }.team.name == "B" }
        host.ws.send(app.numbaseball.core.protocol.RoomSettingsUpdate(RoomSettings(3, 1, 60)))
        host.awaitRoom { it.phase.name == "PLAYING" } // 1 v 1 after the settings change: starts at once
        // Finish the match, then reset.
        host.ws.send(app.numbaseball.core.protocol.Forfeit())
        host.awaitRoom { it.phase.name == "FINISHED" }
        g1.ws.send(app.numbaseball.core.protocol.RoomReset()); g1.ws.awaitError("not_host")
        host.ws.send(app.numbaseball.core.protocol.RoomReset())
        val r = host.awaitRoom { it.phase.name == "PLAYING" && it.match?.number == 2 }
        assertEquals(2, r.match!!.number)
        host.close(); g1.close()
    }

    // ------------------------------------------------------------------ timers (protocol 8.3), test clock

    @Test fun acTime12_bothIdleQuickDuelEndsAsBothTimeoutAfterTwoMissedRounds() {
        QaServer.start().use { inst ->
            val (a, b) = QaFlows.guessingPair(inst = inst)
            val d = a.room!!.match!!.myDuel!!
            assertTrue("deadline 60 s after the snapshot", d.deadline!! - a.room!!.serverTime in 59_000..60_000)
            inst.advance(61_000)
            val one = a.awaitRoom { it.match?.myDuel?.round == 2 }
            assertEquals("a missed round: guess null, one miss each", 1, one.match!!.myDuel!!.myMisses)
            assertEquals(1, one.match!!.myDuel!!.opponentMisses)
            assertNull(one.match!!.myDuel!!.myRounds.single().guess)
            assertEquals("next round: a fresh 60 s", true, one.match!!.myDuel!!.deadline!! - one.serverTime in 55_000..60_000)
            inst.advance(61_000)
            val end = a.awaitRoom { it.match?.myDuel?.phase?.name == "FINISHED" }
            assertEquals("BOTH_TIMEOUT", end.match!!.myDuel!!.result!!.reason.name)
            assertNull("draw", end.match!!.myDuel!!.result!!.winner)
            assertEquals("secrets are revealed at the end", "345", end.match!!.myDuel!!.opponentSecret)
            assertNull(end.match!!.myDuel!!.deadline)
            assertEquals("0/0/1", stats(inst, a.auth.token))
            assertEquals("0/0/1", stats(inst, b.auth.token))
            a.close(); b.close()
        }
    }

    @Test fun acTime13_twoMissedRoundsForfeitAGuessResetsTheCount() {
        QaServer.start().use { inst ->
            val (a, b) = QaFlows.guessingPair("012", "345", inst)
            fun round(n: Int, bGuesses: Boolean) {
                a.ws.send(Guess("678", n))
                if (bGuesses) b.ws.send(Guess("678", n))
                if (!bGuesses) { a.awaitRoom { it.match?.myDuel?.myPendingGuess == "678" }; inst.advance(61_000) }
            }
            round(1, bGuesses = false)
            val r2 = b.awaitRoom { it.match?.myDuel?.round == 2 }
            assertEquals(1, r2.match!!.myDuel!!.myMisses)
            round(2, bGuesses = true)
            val r3 = b.awaitRoom { it.match?.myDuel?.round == 3 }
            assertEquals("a guess resets the misses", 0, r3.match!!.myDuel!!.myMisses)
            round(3, bGuesses = false)
            val r4 = b.awaitRoom { it.match?.myDuel?.round == 4 }
            assertEquals(1, r4.match!!.myDuel!!.myMisses)
            round(4, bGuesses = false)
            val end = a.awaitRoom { it.match?.myDuel?.phase?.name == "FINISHED" }
            assertEquals("TIMEOUT", end.match!!.myDuel!!.result!!.reason.name)
            assertEquals("A", end.match!!.myDuel!!.result!!.winner!!.name)
            assertEquals(0, end.match!!.myDuel!!.myMisses)
            assertEquals(2, end.match!!.myDuel!!.opponentMisses)
            assertEquals("1/0/0", stats(inst, a.auth.token))
            assertEquals("0/1/0", stats(inst, b.auth.token))
            a.close(); b.close()
        }
    }

    @Test fun acTime14_aPlayerWhoNeverChoosesGetsARandomSecretAndOneMiss() {
        QaServer.start().use { inst ->
            val (a, b) = QaFlows.quickPair(3, inst)
            a.ws.send(SetSecret("012"))
            a.awaitRoom { it.match?.myDuel?.mySecret == "012" }
            inst.advance(61_000)
            val guessing = b.awaitRoom { it.match?.myDuel?.phase?.name == "GUESSING" }
            val mine = guessing.match!!.myDuel!!
            assertNotNull("random secret assigned and visible", mine.mySecret)
            assertEquals(3, mine.mySecret!!.length)
            assertEquals("counts as one missed round", 1, mine.myMisses)
            assertEquals(1, mine.round)
            inst.advance(61_000) // B misses again -> two in a row; A (secret set, no miss yet) misses once -> B forfeits
            val end = a.awaitRoom { it.match?.myDuel?.phase?.name == "FINISHED" }
            assertEquals("TIMEOUT", end.match!!.myDuel!!.result!!.reason.name)
            assertEquals("A", end.match!!.myDuel!!.result!!.winner!!.name)
            a.close(); b.close()
        }
    }

    @Test fun acTime15_timersAre30sInA30sRoomAndTheDeadlineIsNotEarly() {
        QaServer.start().use { inst ->
            val host = QaFlows.privateLobby(RoomSettings(3, 1, 30), inst)
            val guest = QaPlayer.fresh(inst = inst).also { QaFlows.join(it, host.room!!.code!!) }
            host.awaitRoom { it.phase.name == "PLAYING" }
            host.ws.send(SetSecret("012")); guest.ws.send(SetSecret("345"))
            host.awaitRoom { it.match?.myDuel?.phase?.name == "GUESSING" }
            inst.advance(25_000)
            host.ws.send(app.numbaseball.core.protocol.Resync())
            assertEquals("no miss before the deadline", 1, host.awaitRoom().match!!.myDuel!!.round)
            inst.advance(6_000)
            assertEquals(2, host.awaitRoom { it.match?.myDuel?.round == 2 }.match!!.myDuel!!.round)
            host.close(); guest.close()
        }
    }

    @Test fun acTime16_solvingBeatsTheOpponentsTimeoutInTheSameRound() {
        QaServer.start().use { inst ->
            val (a, b) = QaFlows.guessingPair("012", "345", inst)
            a.ws.send(Guess("345", 1)) // A solves, B never guesses; the round ends by itself as soon as... B's deadline
            a.awaitRoom { it.match?.myDuel?.myPendingGuess == "345" }
            inst.advance(61_000)
            val end = b.awaitRoom { it.match?.myDuel?.phase?.name == "FINISHED" }
            assertEquals("SOLVED", end.match!!.myDuel!!.result!!.reason.name)
            assertEquals("A", end.match!!.myDuel!!.result!!.winner!!.name)
            a.close(); b.close()
        }
    }

    // ------------------------------------------------------------------ reconnect (protocol 8.3, 10)

    @Test fun acRecon07_reconnectWithin60sRestoresTheSeatWithAFullSnapshot() {
        QaServer.start().use { inst ->
            val (a, b) = QaFlows.guessingPair(inst = inst)
            a.ws.send(Guess("456", 1))
            a.awaitRoom { it.match?.myDuel?.myPendingGuess == "456" }
            val token = a.auth.token
            a.close()
            Thread.sleep(500)
            inst.advance(20_000)
            val again = QaWs.connect(token, inst)
            val welcome = again.awaitType("welcome")
            assertNotNull("seat kept", welcome["roomId"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content)
            val snap = again.await { it.type == "room_state" }
            assertEquals("456", QaPlayer.roomOf(snap).match!!.myDuel!!.myPendingGuess)
            again.awaitType("chat_history")
            assertTrue("the opponent saw A go away and come back", b.snapshot().players.all { it.connected })
            again.close(); b.close()
        }
    }

    @Test fun acRecon08_aNewerConnectionReplacesTheOldOneWith4000() {
        val a = QaServer.main.newAccount()
        val first = QaWs.connect(a.token).also { it.awaitType("welcome") }
        val second = QaWs.connect(a.token).also { it.awaitType("welcome") }
        first.awaitError("session_replaced")
        assertEquals(4000, first.awaitClose())
        second.assertAlive()
        second.close()
    }

    @Test fun acRecon09_disconnectFor60sForfeitsTheDuelAndTheOpponentWins() {
        QaServer.start().use { inst ->
            val (a, b) = QaFlows.guessingPair(inst = inst)
            b.ws.send(Guess("678", 1)) // B plays on, so only A can lose by time
            b.awaitRoom { it.match?.myDuel?.myPendingGuess == "678" }
            a.close()
            Thread.sleep(500)
            inst.advance(60_000)
            val end = b.awaitRoom { it.match?.myDuel?.phase?.name == "FINISHED" }
            assertEquals("DISCONNECT", end.match!!.myDuel!!.result!!.reason.name)
            assertEquals("B", end.match!!.myDuel!!.result!!.winner!!.name)
            assertEquals("1/0/0".replace("1/0/0", "1/0/0"), stats(inst, b.auth.token))
            // The forfeiting player is not a leaver: they get the team result (a loss here), and the seat is kept until freed.
            assertEquals("0/1/0", stats(inst, a.auth.token))
            val back = QaWs.connect(a.auth.token, inst)
            val welcome = back.awaitType("welcome")
            assertNotNull("finished room keeps the seat for 60 s", welcome["roomId"]?.takeIf { it !is JsonNull })
            back.close(); b.close()
        }
    }

    @Test fun acRecon10_reconnectJustBefore60sKeepsTheSeatAndAfter60sTheSeatIsGone() {
        QaServer.start().use { inst ->
            val (a, b) = QaFlows.guessingPair(inst = inst)
            b.ws.send(Guess("678", 1)); b.awaitRoom { it.match?.myDuel?.myPendingGuess == "678" }
            val token = a.auth.token
            a.close(); Thread.sleep(500)
            inst.advance(58_000)
            QaWs.connect(token, inst).use { again ->
                assertNotNull("58 s: still seated", again.awaitType("welcome")["roomId"]?.takeIf { it !is JsonNull })
                again.awaitType("room_state")
            }
            Thread.sleep(500)
            // Gone for good: an idle lobby seat is freed after 60 s (protocol 8.2).
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 600), inst)
            val guest = QaPlayer.fresh(inst = inst).also { QaFlows.join(it, host.room!!.code!!) }
            val gtoken = guest.auth.token
            guest.close(); Thread.sleep(500)
            inst.advance(61_000)
            host.ws.send(app.numbaseball.core.protocol.Resync())
            assertEquals("seat freed", 1, host.awaitRoom().players.size)
            QaWs.connect(gtoken, inst).use { late ->
                val w = late.awaitType("welcome")
                assertTrue("no seat any more", w["roomId"] == null || w["roomId"] is JsonNull)
            }
            host.close(); b.close()
        }
    }

    // ------------------------------------------------------------------ team matches

    @Test fun acTeam10_fiveVersusFiveDecidesEarlyAndRevealsCancelledSecrets() {
        QaServer.start().use { inst ->
            val room = QaFlows.startedTeamRoom(5, 3, 60, inst)
            assertEquals(5, room.teamA.size); assertEquals(5, room.teamB.size)
            QaFlows.everybodySecret(room, "123")
            val duels = room.teamA.map { a -> a to room.opponentOf(a) }
            // Three A players solve their duel in round 1 (their opponents miss with a wrong guess): 3:0 with 2 open.
            for ((a, b) in duels.take(3)) QaFlows.guessBoth(a, b, "123", "456", 1)
            val end = duels[3].first.awaitRoom(10_000) { it.match?.result != null }
            assertTrue("decided early", end.match!!.result!!.decidedEarly)
            assertEquals("A", end.match!!.result!!.winner!!.name)
            assertEquals(3, end.match!!.score.a); assertEquals(0, end.match!!.score.b)
            assertEquals("FINISHED", end.phase.name)
            for ((a, b) in duels.drop(3)) {
                val ra = a.awaitRoom { it.match?.myDuel?.phase?.name == "FINISHED" }.match!!.myDuel!!
                assertEquals("CANCELLED", ra.result!!.reason.name)
                assertNull(ra.result!!.winner)
                assertEquals("cancelled duel shows both secrets to its players", "123", ra.opponentSecret)
                assertEquals("123", b.awaitRoom { it.match?.myDuel?.phase?.name == "FINISHED" }.match!!.myDuel!!.opponentSecret)
            }
            room.teamA.forEach { assertEquals("0 losses, 1 win", "1/0/0", stats(inst, it.auth.token)) }
            room.teamB.forEach { assertEquals("0/1/0", stats(inst, it.auth.token)) }
            room.close()
        }
    }

    @Test fun acTeam11_aPlayerWhoLeftGetsALossEvenWhenTheTeamWins() {
        QaServer.start().use { inst ->
            val room = QaFlows.startedTeamRoom(3, 3, 60, inst)
            QaFlows.everybodySecret(room, "123")
            val duels = room.teamA.map { a -> a to room.opponentOf(a) }
            val (leaver, _) = duels[0]
            leaver.ws.send(RoomLeave())
            leaver.ws.awaitType("room_left")
            for ((a, b) in duels.drop(1)) QaFlows.guessBoth(a, b, "123", "456", 1)
            val end = duels[1].first.awaitRoom(10_000) { it.match?.result != null }
            assertEquals("A", end.match!!.result!!.winner!!.name)
            assertEquals(2, end.match!!.score.a); assertEquals(1, end.match!!.score.b)
            assertEquals("the leaver gets a loss although the team won (protocol 3.1)", "0/1/0", stats(inst, leaver.auth.token))
            duels.drop(1).forEach { assertEquals("1/0/0", stats(inst, it.first.auth.token)) }
            room.teamB.forEach { assertEquals("0/1/0", stats(inst, it.auth.token)) }
            room.close()
        }
    }

    @Test fun acTeam12_aDisconnectForfeitIsNotLeavingSoTheTeamResultCounts() {
        QaServer.start().use { inst ->
            val room = QaFlows.startedTeamRoom(2, 3, 60, inst)
            QaFlows.everybodySecret(room, "123")
            val (a1, b1) = room.teamA[0] to room.opponentOf(room.teamA[0])
            val (a2, b2) = room.teamA[1] to room.opponentOf(room.teamA[1])
            // B1's duel is lost by disconnect, A2 beats B2: A wins 2:0.
            b1.close(); Thread.sleep(500)
            QaFlows.guessBoth(a1, b1, "456", "456", 1).let { }
            a2.ws.send(Guess("123", 1)); b2.ws.send(Guess("456", 1))
            a1.ws.send(Guess("456", 1))
            inst.advance(60_000)
            val end = a1.awaitRoom(10_000) { it.match?.result != null }
            assertEquals("A", end.match!!.result!!.winner!!.name)
            assertEquals("the disconnected player gets the team result (a loss), not a leaver's extra penalty", "0/1/0", stats(inst, b1.auth.token))
            assertEquals("1/0/0", stats(inst, a1.auth.token))
            room.close()
        }
    }

    // ------------------------------------------------------------------ server restart

    @Test fun acSrv01_gracefulRestartTellsPlayersAndRecordsNothingForInterruptedMatches() {
        val dir = java.nio.file.Files.createTempDirectory("nb-qa-restart").toFile()
        val first = QaServer.start(dataDir = dir)
        val (a, b) = QaFlows.guessingPair(inst = first)
        val queued = QaPlayer.fresh("Que", first).also { it.ws.send(QueueJoin(5)); it.ws.awaitType("queued") }
        val lobby = QaFlows.privateLobby(RoomSettings(3, 2, 60), first)
        first.stop()
        for (p in listOf(a, b, lobby)) {
            assertEquals("closed", p.ws.awaitType("room_left")["reason"]!!.jsonPrimitive.content)
            assertEquals(1001, p.ws.awaitClose())
        }
        assertEquals(1001, queued.ws.awaitClose())
        QaServer.start(dataDir = dir).use { second ->
            for (p in listOf(a, b, lobby, queued)) {
                QaWs.connect(p.auth.token, second).use { ws ->
                    val w = ws.awaitType("welcome")
                    assertTrue("roomId is null after a restart", w["roomId"] == null || w["roomId"] is JsonNull)
                }
            }
            assertEquals("unfinished match records nothing", "0/0/0", stats(second, a.auth.token))
            assertEquals("0/0/0", stats(second, b.auth.token))
            // Accounts and the terms acceptance survive (persistent database).
            assertNotNull(second.call("GET", "/api/v1/me", a.auth.token).json!!["termsVersion"])
        }
    }

    // ------------------------------------------------------------------ stats

    @Test fun acStat01_statsCountOneResultPerMatch() {
        val inst = QaServer.main
        val (a, b) = QaFlows.guessingPair()
        a.ws.send(Guess("345", 1)); b.ws.send(Guess("678", 1))
        a.awaitRoom { it.match?.result != null }
        assertEquals("1/0/0", stats(inst, a.auth.token))
        assertEquals("0/1/0", stats(inst, b.auth.token))
        // Leaving a finished room later changes nothing.
        a.ws.send(RoomLeave()); a.ws.awaitType("room_left")
        b.ws.send(RoomLeave()); b.ws.awaitType("room_left")
        assertEquals("1/0/0", stats(inst, a.auth.token))
        assertEquals("0/1/0", stats(inst, b.auth.token))
        a.close(); b.close()
    }

    @Test fun acStat02_bothSolvingInTheSameRoundIsADrawForBoth() {
        val inst = QaServer.main
        val (a, b) = QaFlows.guessingPair()
        a.ws.send(Guess("345", 1)); b.ws.send(Guess("012", 1))
        val end = a.awaitRoom { it.match?.result != null }
        assertEquals("BOTH_SOLVED", end.match!!.myDuel!!.result!!.reason.name)
        assertNull(end.match!!.result!!.winner)
        assertEquals("0/0/1", stats(inst, a.auth.token))
        assertEquals("0/0/1", stats(inst, b.auth.token))
        a.close(); b.close()
    }

    @Test fun acStat03_forfeitCountsAsALossAndLeavingARunningDuelToo() {
        val inst = QaServer.main
        val (a, b) = QaFlows.guessingPair()
        a.ws.send(app.numbaseball.core.protocol.Forfeit())
        val end = b.awaitRoom { it.match?.result != null }
        assertEquals("FORFEIT", end.match!!.myDuel!!.result!!.reason.name)
        assertEquals("0/1/0", stats(inst, a.auth.token))
        assertEquals("1/0/0", stats(inst, b.auth.token))
        val (c, d) = QaFlows.guessingPair()
        c.ws.send(RoomLeave())
        c.ws.awaitType("room_left")
        val e2 = d.awaitRoom { it.match?.result != null }
        assertEquals("FORFEIT", e2.match!!.myDuel!!.result!!.reason.name)
        assertEquals("0/1/0", stats(inst, c.auth.token))
        assertEquals("1/0/0", stats(inst, d.auth.token))
        a.close(); b.close(); c.close(); d.close()
    }

    @Test fun acRoom07_afterAMatchTheSeatsOfDisconnectedPlayersAreFreedAfter60sAndRoomsCloseWhenEmpty() {
        QaServer.start().use { inst ->
            val (a, b) = QaFlows.guessingPair(inst = inst)
            a.ws.send(app.numbaseball.core.protocol.Forfeit())
            b.awaitRoom { it.match?.result != null }
            val tokenA = a.auth.token
            a.close(); Thread.sleep(500)
            inst.advance(61_000)
            b.ws.send(app.numbaseball.core.protocol.Resync())
            val seen = b.awaitRoom()
            assertTrue("a's seat is freed or marked gone", seen.players.none { it.id == a.id && it.connected })
            QaWs.connect(tokenA, inst).use { ws ->
                val w = ws.awaitType("welcome")
                assertTrue(w["roomId"] == null || w["roomId"] is JsonNull)
            }
            b.close()
        }
    }

    @Test fun acRoom08_roomIdsAndCodesAreNotReusedAndLeaversCanRequeue() {
        val (a, b) = QaFlows.quickPair()
        val first = a.room!!.id
        a.ws.send(RoomLeave()); a.ws.awaitType("room_left")
        b.ws.send(RoomLeave()); b.ws.awaitType("room_left")
        a.ws.send(QueueJoin(3)); a.ws.awaitType("queued")
        b.ws.send(QueueJoin(3))
        val again = a.awaitRoom(10_000) { it.kind.name == "QUICK" && it.id != first }
        assertNotEquals(first, again.id)
        a.close(); b.close()
    }
}
