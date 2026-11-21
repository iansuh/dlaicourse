package app.numbaseball.server

import app.numbaseball.core.protocol.AdminReportList
import app.numbaseball.core.protocol.Chat
import app.numbaseball.core.protocol.ChatEvent
import app.numbaseball.core.protocol.ChatHistory
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.LeaveReason
import app.numbaseball.core.protocol.Phrase
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.ReportResponse
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomLeft
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.Welcome
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModerationTest {
    private val admin = "admin-token-for-tests-0123456789"

    /** A private 2v2 lobby with three players: host (A), b (B), c (A). */
    private suspend fun TestServer.lobby(): List<Player> {
        val ps = listOf("Host", "Bee", "Cee").map { connect(account(it)).also { p -> p.expect<Welcome>() } }
        ps[0].send(RoomCreate(RoomSettings(3, 2, 60)))
        val code = ps[0].state().code!!
        for (p in ps.drop(1)) {
            p.send(RoomJoin(code))
            p.state { s -> s.players.any { it.id == p.id } }
        }
        ps.forEach { it.expect<ChatHistory>() }
        return ps
    }

    @Test fun chatIsFilteredAndDeliveredToEveryone() = serverTest {
        val (a, b, c) = lobby()
        a.send(Chat("  fuck   you​ 😀 "))
        for (p in listOf(a, b, c)) {
            val m = p.expect<ChatEvent>().message
            assertEquals("**** you", m.text)
            assertEquals(a.id, m.senderId)
            assertEquals(ChatKind.TEXT, m.kind)
        }
        b.send(Phrase("good_luck"))
        assertEquals("good_luck", c.expect<ChatEvent>().message.phraseId)
        a.send(Chat("😀", rid = 1))
        assertEquals(ErrorCodes.CHAT_EMPTY, a.expect<ErrorMessage> { it.rid == 1L }.code)
        a.send(Chat("x".repeat(101), rid = 2))
        assertEquals(ErrorCodes.CHAT_TOO_LONG, a.expect<ErrorMessage> { it.rid == 2L }.code)
        a.send(Phrase("HELLO", rid = 3))
        assertEquals(ErrorCodes.UNKNOWN_PHRASE, a.expect<ErrorMessage> { it.rid == 3L }.code)
    }

    @Test fun chatRateLimit() = serverTest {
        val (a, _, _) = lobby()
        repeat(5) { a.send(Chat("hi $it")) }
        a.send(Chat("one too many", rid = 7))
        val e = a.expect<ErrorMessage> { it.rid == 7L }
        assertEquals(ErrorCodes.CHAT_RATE_LIMITED, e.code)
        assertTrue((e.retryAfterMs ?: 0) > 0)
    }

    @Test fun quickMatchAllowsOnlyQuickPhrases() = serverTest {
        val a = connect(account("Mina")); a.expect<Welcome>()
        val b = connect(account("Leo")); b.expect<Welcome>()
        a.send(QueueJoin(3)); b.send(QueueJoin(3))
        a.state { it.phase == RoomPhase.PLAYING }
        a.send(Chat("hello there", rid = 1))
        assertEquals(ErrorCodes.CHAT_NOT_ALLOWED, a.expect<ErrorMessage> { it.rid == 1L }.code)
        a.send(Phrase("hello"))
        assertEquals("hello", b.expect<ChatEvent>().message.phraseId)
    }

    @Test fun quickMatchFreeTextCanBeEnabled() = serverTest(testConfig(quickMatchFreeChat = true)) {
        val a = connect(account("Mina")); a.expect<Welcome>()
        val b = connect(account("Leo")); b.expect<Welcome>()
        a.send(QueueJoin(3)); b.send(QueueJoin(3))
        a.state { it.phase == RoomPhase.PLAYING }
        a.send(Chat("hello there"))
        assertEquals("hello there", b.expect<ChatEvent>().message.text)
    }

    @Test fun blockHidesChatBothWaysLiveAndInHistory() = serverTest {
        val (a, b, c) = lobby()
        a.send(Chat("before"))
        listOf(a, b, c).forEach { it.expect<ChatEvent>() }
        assertEquals(HttpStatusCode.NoContent, http(HttpMethod.Put, "/api/v1/me/blocks/${b.id}", a.account.token).status)
        b.send(Chat("from b"))
        assertEquals("from b", c.expect<ChatEvent>().message.text)
        a.send(Chat("from a"))
        assertEquals("from a", c.expect<ChatEvent>().message.text)
        // b's own echo arrives, a's line never does; a never gets b's line.
        assertEquals("from b", b.expect<ChatEvent>().message.text)
        assertEquals("from a", a.expect<ChatEvent>().message.text)
        c.send(Chat("from c"))
        assertEquals("from c", b.expect<ChatEvent>().message.text) // proves nothing from a was queued before it
        assertEquals("from c", a.expect<ChatEvent>().message.text)
        // History after a reconnect is filtered too.
        a.close()
        val a2 = connect(a.account)
        val history = a2.expect<ChatHistory>().messages.map { it.text }
        assertEquals(listOf("before", "from a", "from c"), history)
        // Block list round trip.
        val list = http(HttpMethod.Get, "/api/v1/me/blocks", a.account.token).bodyAsText()
        assertTrue(list.contains(b.id))
        assertEquals(HttpStatusCode.NoContent, http(HttpMethod.Delete, "/api/v1/me/blocks/${b.id}", a.account.token).status)
        assertEquals(HttpStatusCode.BadRequest, http(HttpMethod.Put, "/api/v1/me/blocks/${a.id}", a.account.token).status)
        assertEquals(HttpStatusCode.NotFound, http(HttpMethod.Put, "/api/v1/me/blocks/nobody000000", a.account.token).status)
    }

    @Test fun reportStoresSnapshotAndThreeReportersRestrictChat() = serverTest {
        val (a, b, c) = lobby()
        b.send(Chat("you are bad"))
        val msg = a.expect<ChatEvent>().message
        val roomId = a.lastRoom!!.id
        val r = http(HttpMethod.Post, "/api/v1/reports", a.account.token,
            """{"targetId":"${b.id}","reason":"harassment","roomId":"$roomId","messageId":${msg.id},"note":"rude"}""")
        assertEquals(r.bodyAsText(), HttpStatusCode.Created, r.status)
        assertTrue(ProtocolJson.decodeFromString(ReportResponse.serializer(), r.bodyAsText()).reportId > 0)
        // Wrong target for that message.
        val wrongTarget = http(HttpMethod.Post, "/api/v1/reports", a.account.token,
            """{"targetId":"${c.id}","reason":"spam","roomId":"$roomId","messageId":${msg.id}}""")
        assertEquals(HttpStatusCode.BadRequest, wrongTarget.status)

        val list = ProtocolJson.decodeFromString(AdminReportList.serializer(), http(HttpMethod.Get, "/admin/v1/reports", admin).bodyAsText())
        val report = list.reports.single()
        assertEquals("you are bad", report.snapshot!!.message!!.text)
        assertEquals(b.id, report.targetId)
        assertNull(me(b.account.token).chatRestrictedUntil)

        // Two more distinct reporters within 24 h restrict b's chat for 24 h.
        val d = account("Dee")
        http(HttpMethod.Post, "/api/v1/reports", c.account.token, """{"targetId":"${b.id}","reason":"spam"}""")
        http(HttpMethod.Post, "/api/v1/reports", d.token, """{"targetId":"${b.id}","reason":"spam"}""")
        assertNotNull(me(b.account.token).chatRestrictedUntil)
        b.send(Phrase("hello", rid = 5))
        assertEquals(ErrorCodes.CHAT_RESTRICTED, b.expect<ErrorMessage> { it.rid == 5L }.code)
        // An admin lifts it.
        val lift = http(HttpMethod.Post, "/admin/v1/accounts/${b.id}/chat-restrict", admin, """{"hours":0,"reason":"ok"}""")
        assertEquals(HttpStatusCode.NoContent, lift.status)
        b.send(Phrase("hello"))
        assertEquals("hello", a.expect<ChatEvent> { it.message.kind == ChatKind.PHRASE }.message.phraseId)
        // Resolve the report.
        assertEquals(HttpStatusCode.NoContent,
            http(HttpMethod.Post, "/admin/v1/reports/${report.id}/resolve", admin, """{"resolution":"warned"}""").status)
        assertEquals(2, ProtocolJson.decodeFromString(AdminReportList.serializer(), http(HttpMethod.Get, "/admin/v1/reports", admin).bodyAsText()).reports.size)
    }

    @Test fun banClosesTheConnectionAndRemovesThePlayer() = serverTest {
        val (a, b, _) = lobby()
        assertEquals(HttpStatusCode.NoContent, http(HttpMethod.Post, "/admin/v1/accounts/${b.id}/ban", admin, """{"reason":"abuse"}""").status)
        assertEquals(LeaveReason.BANNED, b.expect<RoomLeft>().reason)
        assertEquals(4003.toShort(), b.awaitClose()?.code)
        a.state { s -> s.players.none { it.id == b.id } }
        assertEquals(HttpStatusCode.Forbidden, http(HttpMethod.Post, "/api/v1/me/transfer-code", b.account.token).status)
        assertEquals(HttpStatusCode.OK, http(HttpMethod.Get, "/api/v1/me", b.account.token).status) // banned may still see / delete
        val upgrade = runCatching { connect(b.account) }
        assertTrue(upgrade.isFailure)
    }

    @Test fun adminApiNeedsTheAdminToken() = serverTest {
        val user = account("Mina")
        assertEquals(HttpStatusCode.Unauthorized, http(HttpMethod.Get, "/admin/v1/reports").status)
        assertEquals(HttpStatusCode.Unauthorized, http(HttpMethod.Get, "/admin/v1/reports", user.token).status)
        assertEquals(HttpStatusCode.OK, http(HttpMethod.Get, "/admin/v1/reports", admin).status)
    }

    @Test fun adminApiIsOffWithoutToken() = serverTest(testConfig(adminToken = null)) {
        assertEquals(HttpStatusCode.NotFound, http(HttpMethod.Get, "/admin/v1/reports", admin).status)
    }
}
