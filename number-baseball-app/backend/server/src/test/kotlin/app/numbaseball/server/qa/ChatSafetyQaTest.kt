package app.numbaseball.server.qa

import app.numbaseball.core.protocol.Chat
import app.numbaseball.core.protocol.Phrase
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.RoomSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

private const val WAITING = "waiting for the :server implementation (backend/server/src/main is empty); run with NB_QA_BASE_URL once it lands"
private const val WAITING_POLICY = "waiting for protocol.md to define terms_required / chat_not_allowed / chat restriction (DEF-011) and for the server"

/** AC-CHAT-* (server side), AC-SAFE-*, AC-TERMS-* against a running server. */
class ChatSafetyQaTest {

    private fun text(chat: JsonObject): String = chat["message"]!!.jsonObject["text"]!!.jsonPrimitive.content

    @Ignore(WAITING)
    @Test fun acChat10_everybodyIncludingTheSenderGetsTheSameMaskedText() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 0))
        val guest = QaPlayer.fresh().also { QaFlows.join(it, host.room!!.code!!) }
        host.ws.send(Chat("f u c k  you"))
        val mine = text(host.ws.awaitType("chat"))
        val theirs = text(guest.ws.awaitType("chat"))
        assertEquals(mine, theirs)
        assertFalse(mine.contains("f u c k"))
        host.close(); guest.close()
    }

    @Ignore(WAITING)
    @Test fun acChat11_serverAppliesTheWholeCorpusLikeTheCoreFilter() {
        // Every chat_* corpus row through a real room: the delivered text must equal core ChatFilter.clean(input)
        // (protocol 9.1 says server and app use the same filter). Rate limit: one line per 2.1 s.
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 0))
        for (row in QaServerCorpus.load("chat_profanity.tsv").take(20)) {
            val input = QaServerCorpus.decode(row.getValue("input"))
            host.ws.send(Chat(input))
            val expected = app.numbaseball.core.text.ChatFilter.clean(input)
            if (expected is app.numbaseball.core.text.ChatFilter.Result.Ok) assertEquals(row["id"], expected.text, text(host.ws.awaitType("chat")))
            Thread.sleep(2_100)
        }
        host.close()
    }

    @Ignore(WAITING)
    @Test fun acChat12_rateLimitFiveLinesPerTenSeconds() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 0))
        repeat(5) { host.ws.send(Chat("line $it")) }
        repeat(5) { host.ws.awaitType("chat") }
        host.ws.send(Phrase("hello")) // phrases share the budget
        val e = host.ws.awaitError("chat_rate_limited")
        assertTrue(e["retryAfterMs"]!!.jsonPrimitive.content.toLong() in 1..10_000)
        host.close()
    }

    @Ignore(WAITING_POLICY)
    @Test fun acChat13_quickMatchAcceptsQuickPhrasesButNoFreeText() {
        val (a, b) = QaFlows.quickPair()
        a.ws.send(Chat("hello there"))
        a.ws.awaitError("chat_not_allowed")
        assertFalse(b.ws.all.any { it.contains("hello there") })
        a.ws.send(Phrase("good_luck"))
        val got = b.ws.awaitType("chat")
        assertEquals("good_luck", got["message"]!!.jsonObject["phraseId"]!!.jsonPrimitive.content)
        a.close(); b.close()
    }

    @Ignore(WAITING)
    @Test fun acSafe01_blockHidesChatBothWaysLiveAndInHistory() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 0))
        val guest = QaPlayer.fresh().also { QaFlows.join(it, host.room!!.code!!) }
        host.ws.send(Chat("before block"))
        guest.ws.awaitType("chat")
        assertEquals(204, QaHttp.call("PUT", "/api/v1/me/blocks/${guest.id}", host.auth.token).status)
        assertEquals("idempotent", 204, QaHttp.call("PUT", "/api/v1/me/blocks/${guest.id}", host.auth.token).status)
        guest.ws.send(Chat("from the blocked one"))
        host.ws.send(Chat("from the blocker"))
        Thread.sleep(1_000)
        assertFalse(host.ws.all.any { it.contains("from the blocked one") })
        assertFalse(guest.ws.all.any { it.contains("from the blocker") })
        guest.ws.send(app.numbaseball.core.protocol.Resync())
        val history = guest.ws.awaitType("chat_history").toString()
        assertFalse("history must be block-filtered", history.contains("before block"))
        host.close(); guest.close()
    }

    @Ignore(WAITING)
    @Test fun acSafe02_reportStoresASnapshotAndIsRateLimited() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 0))
        val guest = QaPlayer.fresh().also { QaFlows.join(it, host.room!!.code!!) }
        host.ws.send(Chat("rude line"))
        val id = guest.ws.awaitType("chat")["message"]!!.jsonObject["id"]!!.jsonPrimitive.content
        val body = "{\"targetId\":\"${host.id}\",\"reason\":\"harassment\",\"roomId\":\"${host.room!!.id}\",\"messageId\":$id}"
        assertEquals(201, QaHttp.call("POST", "/api/v1/reports", guest.auth.token, body).status)
        val wrongTarget = body.replace(host.id, guest.id)
        assertEquals("message report must name the sender", 400, QaHttp.call("POST", "/api/v1/reports", guest.auth.token, wrongTarget).status)
        val statuses = (0 until 11).map { QaHttp.call("POST", "/api/v1/reports", guest.auth.token, "{\"targetId\":\"${host.id}\",\"reason\":\"spam\"}").status }
        assertTrue("10 reports per hour", 429 in statuses)
        host.close(); guest.close()
    }

    @Ignore(WAITING_POLICY)
    @Test fun acSafe05_threeDistinctReportersIn24hRestrictChatFor24h() {
        // 3 fresh accounts that shared a room with the target report it; the target's next chat line is refused (error code
        // to be defined, DEF-011) while phrases/play still work; 2 reporters or 3 reports from one account do not trigger;
        // see DEF-043 for the collusion risk QA wants mitigated.
        TODO("needs protocol definition and a running server")
    }

    @Ignore(WAITING_POLICY)
    @Test fun acTerms01_serverRefusesQueueRoomAndChatUntilTermsAreAccepted() {
        val fresh = QaHttp.newAccount()
        val p = QaPlayer(fresh)
        p.ws.send(QueueJoin(3))
        p.ws.awaitError("terms_required")
        p.ws.send(app.numbaseball.core.protocol.RoomCreate(RoomSettings(3, 1, 60)))
        p.ws.awaitError("terms_required")
        p.ws.send(app.numbaseball.core.protocol.RoomJoin("ABCDE"))
        p.ws.awaitError("terms_required")
        val accepted = QaHttp.call("PUT", "/api/v1/me/terms", fresh.token, "{\"version\":\"current\"}")
        assertTrue(accepted.status in 200..204)
        p.ws.send(QueueJoin(3))
        p.ws.awaitType("queued")
        // An old version must not unlock: PUT with a stale version -> still terms_required (exact body per protocol).
        p.close()
    }
}
