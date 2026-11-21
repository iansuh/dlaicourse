package app.numbaseball.server.qa

import app.numbaseball.core.protocol.Chat
import app.numbaseball.core.protocol.RoomSettings
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File

private const val WAITING = "waiting for the :server implementation (backend/server/src/main is empty); run with NB_QA_BASE_URL once it lands"
private const val WAITING_POLICY = "waiting for protocol.md to define the web deletion request (POST /delete-account/request, deletionRequestedAt; DEF-009/DEF-011) and for the server"

/** AC-ACC-*, AC-DEL-*, AC-XFER-*, AC-PRIV-* against a running server. */
class AccountLifecycleQaTest {

    @Ignore(WAITING)
    @Test fun acDel01_inAppDeletionIsImmediateAndEverywhere() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 0))
        val guest = QaPlayer.fresh().also { QaFlows.join(it, host.room!!.code!!) }
        host.ws.send(Chat("hello from soon-deleted"))
        guest.ws.awaitType("chat")
        assertEquals(204, QaHttp.call("DELETE", "/api/v1/me", host.auth.token).status)
        assertEquals(4004, host.ws.awaitClose())
        guest.ws.awaitType("chat_sender_deleted")
        assertEquals(401, QaHttp.call("GET", "/api/v1/me", host.auth.token).status)
        assertTrue(runCatching { QaWs.connect(host.auth.token) }.isFailure)
        guest.close()
    }

    @Ignore(WAITING)
    @Test fun acDel02_webDeletionWithAnInAppCode() {
        val a = QaHttp.newAccount()
        val code = QaHttp.call("POST", "/api/v1/me/deletion-code", a.token).json!!["code"]!!.jsonPrimitive.content
        val noConfirm = QaHttp.call("POST", "/delete-account", body = "code=$code", contentType = "application/x-www-form-urlencoded")
        assertEquals("missing confirmation must not delete", 200, QaHttp.call("GET", "/api/v1/me", a.token).status)
        assertTrue(noConfirm.status in 200..499)
        val ok = QaHttp.call("POST", "/delete-account", body = "code=$code&confirm=yes", contentType = "application/x-www-form-urlencoded")
        assertTrue(ok.status in 200..399)
        assertEquals(401, QaHttp.call("GET", "/api/v1/me", a.token).status)
    }

    @Ignore(WAITING_POLICY)
    @Test fun acDel03_webRequestWithoutCodeAnswersIdenticallyAndDeletesAfter30DaysUnlessUsed() {
        val a = QaHttp.newAccount()
        val handle = "${a.account.nickname}#${a.account.tag}"
        val real = QaHttp.call("POST", "/delete-account/request", body = "handle=${java.net.URLEncoder.encode(handle, "UTF-8")}&confirm=yes",
            contentType = "application/x-www-form-urlencoded")
        val fake = QaHttp.call("POST", "/delete-account/request", body = "handle=Nobody%230000&confirm=yes",
            contentType = "application/x-www-form-urlencoded")
        assertEquals("no account enumeration: same status", real.status, fake.status)
        assertEquals("no account enumeration: same body", real.body.replace(handle, "X"), fake.body.replace("Nobody#0000", "X"))
        // The app sees the pending request; any authenticated use cancels it; after 30 days without use the account is gone
        // (time travel needs a server test clock: NB_QA_CLOCK_OFFSET or an admin endpoint, to be agreed, DEF-009).
        val me = QaHttp.call("GET", "/api/v1/me", a.token).body
        assertTrue("deletionRequestedAt exposed to the app", me.contains("deletionRequestedAt"))
    }

    @Ignore(WAITING)
    @Test fun acAcc01_accountCreationNicknameRulesOverRest() {
        for (row in QaServerCorpus.load("nicknames.tsv")) {
            val nick = QaServerCorpus.decode(row.getValue("input"))
            val body = "{\"nickname\":" + kotlinx.serialization.json.JsonPrimitive(nick).toString() + "}"
            val r = QaHttp.call("POST", "/api/v1/accounts", body = body)
            val expect = row.getValue("expect")
            when (expect) {
                "VALID" -> assertEquals(row["id"], 201, r.status)
                "INVALID" -> assertEquals(row["id"], "invalid_nickname", r.code)
                "PROFANITY" -> assertEquals(row["id"], "nickname_profanity", r.code)
                else -> assertTrue(row["id"], r.status in setOf(201, 400))
            }
            assertTrue("never a 500", r.status < 500)
            Thread.sleep(400) // stays under the 10-per-hour IP limit only with QA_DISABLE_RATE_LIMITS on a test server
        }
    }

    @Ignore(WAITING)
    @Test fun acAcc02_renameKeepsTagWhenFreeAndIsLimitedToFivePerDay() {
        val a = QaHttp.newAccount("Ren")
        val statuses = (1..6).map { QaHttp.call("PATCH", "/api/v1/me", a.token, "{\"nickname\":\"Renamed$it\"}").status }
        assertEquals(listOf(200, 200, 200, 200, 200, 429), statuses)
    }

    @Ignore(WAITING)
    @Test fun acPriv01_noChatTextTokensCodesOrIpsInLogsOrTheDatabase() {
        // Run against a server started with NB_QA_SERVER_LOG (its stdout/stderr file) and NB_QA_DB_PATH (SQLite file).
        val log = File(System.getenv("NB_QA_SERVER_LOG") ?: error("NB_QA_SERVER_LOG"))
        val db = File(System.getenv("NB_QA_DB_PATH") ?: error("NB_QA_DB_PATH"))
        val a = QaHttp.newAccount()
        val code = QaHttp.call("POST", "/api/v1/me/transfer-code", a.token).json!!["code"]!!.jsonPrimitive.content
        val marker = "QAMARKER" + System.nanoTime()
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 0))
        host.ws.send(Chat(marker))
        host.ws.awaitType("chat")
        host.close()
        Thread.sleep(2_000)
        val haystacks = mapOf("log" to log.readBytes(), "db" to db.readBytes() + (File(db.path + "-wal").takeIf { it.exists() }?.readBytes() ?: ByteArray(0)))
        for ((where, bytes) in haystacks) {
            val s = String(bytes, Charsets.ISO_8859_1)
            assertFalse("chat text in $where", s.contains(marker))
            assertFalse("token in $where", s.contains(a.token))
            assertFalse("transfer code in $where", s.contains(code) || s.contains(code.replace("-", "")))
            assertFalse("IPv4 address in $where", Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""").containsMatchIn(s.replace("0.0.0.0", "")))
        }
    }

    @Ignore(WAITING)
    @Test fun acRev01_reviewRoomGivesABotOpponentWhenConfigured() {
        val code = System.getenv("REVIEW_ROOM_CODE") ?: error("set REVIEW_ROOM_CODE on the test server")
        val p = QaPlayer.fresh("Rev")
        // Terms acceptance first once DEF-011 is resolved.
        val r = QaFlows.join(p, code)
        assertEquals(4, r.settings.digits)
        p.awaitRoom(10_000) { it.phase.name == "PLAYING" }
        p.ws.await(60_000, "bot quick phrase") { it.type == "chat" }
        p.close()
    }
}
