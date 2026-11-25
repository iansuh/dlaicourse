package app.numbaseball.server.qa

import app.numbaseball.core.protocol.RoomLeave
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomSettings
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protocol 1.1 additions (team board hints, player card, system chat lines) and the small hardening fixes (DEF-015/018/042). */
class ProtocolV11Test {
    private val inst get() = QaServer.main

    // ------------------------------------------------------------------ team board: best hint of every duel

    @Test fun bestHintOfEveryDuelIsVisibleToEveryoneWithoutDigits() {
        QaServer.start().use { server ->
            val room = QaFlows.startedTeamRoom(2, inst = server)
            QaFlows.everybodySecret(room, "123")
            val watcher = room.players.first()
            // A duel the watcher is NOT in: guess "125" (2 strikes) vs "123" and "321" (1 strike 2 balls... 3 balls here).
            val other = watcher.room!!.match!!.duels.first { it.index != watcher.room!!.match!!.myDuel!!.index }
            val a = room.player(other.playerA); val b = room.player(other.playerB)
            QaFlows.guessBoth(a, b, "125", "456", 1)
            val seen = watcher.awaitRoom(10_000) { r -> r.match!!.duels.first { it.index == other.index }.bestHintA != null }
            val duel = seen.match!!.duels.first { it.index == other.index }
            assertEquals(2, duel.bestHintA!!.strikes); assertEquals(0, duel.bestHintA!!.balls)
            assertEquals(0, duel.bestHintB!!.strikes); assertEquals(0, duel.bestHintB!!.balls)
            // Digits of the other duel never reach the watcher (frames of that duel's guesses are not in its snapshot).
            val raw = watcher.ws.drain(300).map { it.toString() }
            assertFalse(QaFlows.leaks(raw, "125")); assertFalse(QaFlows.leaks(raw, "456"))
            // Second round: the best hint keeps the better of the rounds (A: 3 balls is worse than 2 strikes).
            QaFlows.guessBoth(a, b, "312", "126", 2)
            val later = watcher.awaitRoom(10_000) { r -> r.match!!.duels.first { it.index == other.index }.bestHintB!!.strikes == 2 }
            val d2 = later.match!!.duels.first { it.index == other.index }
            assertEquals(2, d2.bestHintA!!.strikes)
            room.close()
        }
    }

    @Test fun bestHintIsNullBeforeTheFirstResolvedGuess() {
        val (a, b) = QaFlows.guessingPair("012", "345")
        val d = a.snapshot().match!!.duels.single()
        assertNull(d.bestHintA); assertNull(d.bestHintB)
        a.close(); b.close()
    }

    // ------------------------------------------------------------------ player card

    @Test fun playerCardByIdAndByHandleShowsOnlyPublicRecord() {
        val me = inst.newAccount("CardMe")
        val other = inst.newAccount("CardOt")
        val byId = inst.call("GET", "/api/v1/players/${other.account.id}", me.token)
        assertEquals(200, byId.status)
        assertEquals(setOf("id", "nickname", "tag", "stats"), byId.json!!.keys)
        assertEquals(setOf("wins", "losses", "draws"), byId.json!!["stats"]!!.jsonObject.keys)
        assertEquals(other.account.nickname, byId.json!!["nickname"]!!.jsonPrimitive.content)
        val handle = inst.handleQuery(other.account.nickname, other.account.tag)
        val byHandle = inst.call("GET", "/api/v1/players/$handle", me.token)
        assertEquals(200, byHandle.status)
        assertEquals(other.account.id, byHandle.json!!["id"]!!.jsonPrimitive.content)
        assertEquals(200, inst.call("GET", "/api/v1/players/${me.account.id}", me.token).status)
    }

    @Test fun playerCardNeedsAuthAndHidesUnknownBlockedAndBotPlayers() {
        val me = inst.newAccount("CardMe")
        val other = inst.newAccount("CardOt")
        assertEquals(401, inst.call("GET", "/api/v1/players/${other.account.id}").status)
        assertEquals(404, inst.call("GET", "/api/v1/players/nobody000000", me.token).status)
        assertEquals(404, inst.call("GET", "/api/v1/players/${inst.handleQuery("Nobody", "0001")}", me.token).status)
        assertEquals(404, inst.call("GET", "/api/v1/players/reviewbot0001", me.token).status)
        // Blocks hide the card in both directions.
        assertEquals(204, inst.call("PUT", "/api/v1/me/blocks/${other.account.id}", me.token).status)
        assertEquals(404, inst.call("GET", "/api/v1/players/${other.account.id}", me.token).status)
        assertEquals(404, inst.call("GET", "/api/v1/players/${me.account.id}", other.token).status)
        assertEquals(204, inst.call("DELETE", "/api/v1/me/blocks/${other.account.id}", me.token).status)
        assertEquals(200, inst.call("GET", "/api/v1/players/${other.account.id}", me.token).status)
    }

    @Test fun playerCardLookupsAreRateLimited() {
        QaServer.start(relaxed = false).use { s ->
            val me = s.newAccount("CardMe")
            val statuses = (1..35).map { s.call("GET", "/api/v1/players/nobody000000", me.token).status }
            assertEquals(30, statuses.count { it == 404 })
            assertTrue(statuses.drop(30).all { it == 429 })
        }
    }

    // ------------------------------------------------------------------ system chat lines

    private fun systemLines(history: List<JsonObject>) = history.filter { it.str("kind") == "system" }

    @Test fun joiningAndLeavingAPrivateRoomPostsLocalizableSystemLines() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 60))
        val code = host.room!!.code!!
        val guest = QaPlayer.fresh("QaG")
        QaFlows.join(guest, code)
        val joined = host.ws.await(5_000, "system join") { it.type == "chat" && it["message"]!!.jsonObject.str("kind") == "system" }["message"]!!.jsonObject
        assertEquals("player_joined", joined.str("systemCode"))
        assertEquals(guest.id, joined.str("subjectId"))
        assertEquals(guest.auth.account.nickname, joined.str("subjectNickname"))
        assertEquals(guest.auth.account.tag, joined.str("subjectTag"))
        assertTrue(joined["senderId"] is JsonNull); assertTrue(joined["text"] is JsonNull)
        // The joiner sees the line about themselves in the history; a later joiner sees both... but only the join of earlier players.
        val hist = guest.ws.await(5_000, "history") { it.type == "chat_history" }["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("player_joined"), systemLines(hist).map { it.str("systemCode") })
        guest.ws.send(RoomLeave())
        val left = host.ws.await(5_000, "system leave") { it.type == "chat" && it["message"]!!.jsonObject.str("systemCode") == "player_left" }["message"]!!.jsonObject
        assertEquals(guest.id, left.str("subjectId"))
        host.close(); guest.close()
    }

    @Test fun systemLinesRespectBlocksAndAreAnonymisedWhenTheAccountIsDeleted() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 60))
        val code = host.room!!.code!!
        val guest = QaPlayer.fresh("QaG")
        QaFlows.join(guest, code)
        host.ws.await(5_000, "join line") { it.type == "chat" && it["message"]!!.jsonObject.str("kind") == "system" }
        assertEquals(204, inst.call("DELETE", "/api/v1/me", guest.auth.token).status)
        host.ws.await(5_000, "anonymised") { it.type == "chat_sender_deleted" }
        val hist = host.let { it.ws.send(app.numbaseball.core.protocol.Resync()); it.ws.await(5_000, "history") { m -> m.type == "chat_history" } }
        val lines = systemLines(hist["messages"]!!.jsonArray.map { it.jsonObject })
        assertTrue(lines.all { it["subjectId"] is JsonNull && it["subjectNickname"] is JsonNull })
        host.close()
    }

    @Test fun quickAndReviewRoomsHaveNoSystemLines() {
        val (a, b) = QaFlows.quickPair()
        a.ws.send(app.numbaseball.core.protocol.Resync())
        val hist = a.ws.await(5_000, "history") { it.type == "chat_history" }["messages"]!!.jsonArray
        assertTrue(hist.none { it.jsonObject.str("kind") == "system" })
        a.close(); b.close()
        val p = QaPlayer.fresh("Rev")
        QaFlows.join(p, QaServer.REVIEW_CODE)
        p.ws.send(app.numbaseball.core.protocol.Resync())
        val h2 = p.ws.await(5_000, "history") { it.type == "chat_history" }["messages"]!!.jsonArray
        assertTrue(h2.none { it.jsonObject.str("kind") == "system" })
        p.close()
    }

    // ------------------------------------------------------------------ DEF-015 / DEF-018 / DEF-042

    @Test fun def015_aBlockedPlayerCannotJoinMyPrivateRoomAndTheCodeLooksWrong() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 60))
        val code = host.room!!.code!!
        val blocked = QaPlayer.fresh("QaB")
        assertEquals(204, inst.call("PUT", "/api/v1/me/blocks/${blocked.id}", host.auth.token).status)
        blocked.ws.send(RoomJoin(code))
        blocked.ws.awaitError("room_not_found")
        // The other direction: the joiner blocked the host.
        val other = QaPlayer.fresh("QaO")
        assertEquals(204, inst.call("PUT", "/api/v1/me/blocks/${host.id}", other.auth.token).status)
        other.ws.send(RoomJoin(code))
        other.ws.awaitError("room_not_found")
        // After unblocking the join works.
        assertEquals(204, inst.call("DELETE", "/api/v1/me/blocks/${blocked.id}", host.auth.token).status)
        assertNotNull(QaFlows.join(blocked, code))
        host.close(); blocked.close(); other.close()
    }

    @Test fun def018_wrongRoomCodesAreLimitedPerAccount() {
        QaServer.start(relaxed = false).use { s ->
            val p = QaPlayer.fresh("QaW", s)
            repeat(20) { p.ws.send(RoomJoin("ZZZZ$it".take(5))); p.ws.awaitError("room_not_found") }
            p.ws.send(RoomJoin("YYYYY"))
            val e = p.ws.awaitError("too_many_attempts")
            assertNotNull(e["retryAfterMs"])
            // A real code is refused too while limited (no oracle), and the limit is per account.
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 60), s)
            p.ws.send(RoomJoin(host.room!!.code!!)); p.ws.awaitError("too_many_attempts")
            val fresh = QaPlayer.fresh("QaF", s)
            assertNotNull(QaFlows.join(fresh, host.room!!.code!!))
            p.close(); host.close(); fresh.close()
        }
    }

    @Test fun def042_failedAdminAuthIsRateLimited() {
        QaServer.start(relaxed = false).use { s ->
            repeat(10) { assertEquals(401, s.call("GET", "/admin/v1/reports", authHeader = "Bearer wrong-$it").status) }
            assertEquals(429, s.call("GET", "/admin/v1/reports", authHeader = "Bearer wrong-x").status)
            // Locked out even with the right token until the window passes.
            assertEquals(429, s.admin("GET", "/admin/v1/reports").status)
        }
    }
}
