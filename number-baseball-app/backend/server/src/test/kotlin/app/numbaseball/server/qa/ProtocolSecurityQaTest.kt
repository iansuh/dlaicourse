package app.numbaseball.server.qa

import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.RoomSettings
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

private const val WAITING = "waiting for the :server implementation (backend/server/src/main is empty); run with NB_QA_BASE_URL once it lands"

/** AC-SEC-* and AC-PROTO-* against a running server (protocol sections 3, 4, 5, 11, 12, 14). */
class ProtocolSecurityQaTest {

    @Ignore(WAITING)
    @Test fun acSec05_opponentSecretNeverReachesThePlayerBeforeTheDuelEnds() {
        // Secrets chosen so that no other field can contain them by accident (no "0", ids are lowercase letters+digits).
        val (a, b) = QaFlows.guessingPair("987", "654")
        var round = 1
        // Play 6 wrong rounds, reconnect A once in the middle (snapshot after reconnect must not leak either).
        repeat(6) {
            a.ws.send(Guess("123", round)); b.ws.send(Guess("123", round))
            a.awaitRoom { (it.match?.myDuel?.round ?: 0) > round }
            round++
        }
        val before = a.ws.all.toList()
        assertTrue("B's secret leaked to A before the duel ended", before.none { it.contains("654") })
        // Finish: A solves; now the secret must be revealed in opponentSecret.
        a.ws.send(Guess("654", round)); b.ws.send(Guess("123", round))
        val end = a.awaitRoom { it.match?.myDuel?.phase?.name == "FINISHED" }
        assertEquals("654", end.match!!.myDuel!!.opponentSecret)
        a.close(); b.close()
    }

    @Ignore(WAITING)
    @Test fun acSec05_teamMatesNeverSeeOtherDuelsGuessesOrSecrets() {
        // 2v2: each player's frames may contain only their own duel's guesses and secrets (DuelSummary has none).
        // Steps: host + 3 joiners, all set distinct secrets 123/456/789/... ; scan every frame of every player for the
        // three secrets that are not their own or their opponent's (after the end). Implemented with QaFlows when the
        // server lands.
        TODO("needs a running server")
    }

    @Ignore(WAITING)
    @Test fun acProto10_malformedFramesGetAnErrorAndTheConnectionStaysOpen() {
        val rows = QaServerCorpus.load("protocol_frames.tsv").filter { it["state"] == "ANY" && it["layer"] != "SIZE" }
        val p = QaPlayer.fresh()
        for (row in rows) {
            p.ws.sendRaw(QaServerCorpus.decode(row.getValue("frame")))
            val expect = row.getValue("expect").split('|')
            when (row.getValue("layer")) {
                "DECODE" -> p.ws.awaitError("malformed_message")
                "SERVER" -> {
                    val e = p.ws.awaitType("error")
                    assertTrue("${row["id"]}: got ${e["code"]}, expected $expect", e["code"]!!.jsonPrimitive.content in expect)
                }
                "TOLERATED" -> Unit // either an error or normal processing; liveness below is what matters
            }
            assertEquals("${row["id"]} closed the connection", null, p.ws.closeCode)
            p.ws.assertAlive()
        }
        p.close()
    }

    @Ignore(WAITING)
    @Test fun acProto11_sizeLimitsBinaryAndFragmentedFrames() {
        val p = QaPlayer.fresh()
        val prefix = "{\"type\":\"ping\",\"rid\":1,\"pad\":\""
        val exact = prefix + "a".repeat(2048 - prefix.length - 2) + "\"}"
        assertEquals(2048, exact.toByteArray().size)
        p.ws.sendRaw(exact) // exactly at the limit: processed
        p.ws.awaitType("pong")
        p.ws.sendRaw("{\"type\":\"ping\",\"pad\":\"" + "a".repeat(2025) + "\"}")
        p.ws.awaitError("message_too_large")
        p.ws.sendBinary(byteArrayOf(1, 2, 3)) // ignored
        p.ws.assertAlive()
        // A message split into small fragments whose total exceeds 16 KB must not be buffered without limit.
        p.ws.sendFragmented(List(40) { "a".repeat(1000) })
        val code = p.ws.awaitClose(5_000)
        assertTrue("fragmented 40 KB message: expected close 1009 or message_too_large, close=$code",
            code == 1009 || p.ws.all.any { it.contains("message_too_large") })
        val q = QaPlayer.fresh()
        q.ws.sendRaw("{\"type\":\"ping\",\"pad\":\"" + "a".repeat(17_000) + "\"}")
        assertEquals(1009, q.ws.awaitClose())
    }

    @Ignore(WAITING)
    @Test fun acSec03_websocketUpgradeNeedsAValidToken() {
        for (token in listOf(null, "garbage", "")) {
            val r = runCatching { QaWs.connect(token) }
            assertTrue("upgrade with token <$token> must be refused with 401", r.isFailure)
        }
    }

    @Ignore(WAITING)
    @Test fun acSec04_restRequestsCorpus() {
        val user = QaHttp.newAccount()
        val other = QaHttp.newAccount()
        val revoked = QaHttp.newAccount().also {
            val code = QaHttp.call("POST", "/api/v1/me/transfer-code", it.token).json!!["code"]!!.jsonPrimitive.content
            assertEquals(200, QaHttp.call("POST", "/api/v1/transfer", body = "{\"code\":\"$code\"}").status)
        }
        val deleted = QaHttp.newAccount().also { assertEquals(204, QaHttp.call("DELETE", "/api/v1/me", it.token).status) }
        for (row in QaServerCorpus.load("rest_requests.tsv")) {
            val path = row.getValue("path").replace("{self}", user.account.id).replace("{other}", other.account.id)
            val body = row.getValue("body").takeIf { it != "-" }?.let { QaServerCorpus.decode(it).replace("{other}", other.account.id) }
            val auth: String? = when (row.getValue("auth")) {
                "NONE" -> null
                "VALID" -> "Bearer ${user.token}"
                "GARBAGE" -> "Bearer x"
                "BASIC" -> "Basic dXNlcjpwYXNz"
                "EMPTY" -> "Bearer "
                "REVOKED" -> "Bearer ${revoked.token}"
                "DELETED" -> "Bearer ${deleted.token}"
                "ADMIN_WITH_USER_TOKEN" -> "Bearer ${user.token}"
                else -> error(row["auth"]!!)
            }
            val ct = if (path.startsWith("/delete-account")) "application/x-www-form-urlencoded" else "application/json"
            val r = QaHttp.call(row.getValue("method"), path, body = body, contentType = ct, authHeader = auth)
            val statuses = row.getValue("status").split('|').map { it.toInt() }
            assertTrue("${row["id"]}: status ${r.status} not in $statuses (${r.body.take(200)})", r.status in statuses)
            val codes = row.getValue("code").split('|')
            if ("-" !in codes) assertTrue("${row["id"]}: code ${r.code} not in $codes", r.code in codes)
            assertFalse("${row["id"]}: response leaks a stack trace", r.body.contains("Exception") || r.body.contains("\tat "))
        }
    }

    @Ignore(WAITING)
    @Test fun acSec09_adminApiIsInvisibleWithoutTheAdminToken() {
        val user = QaHttp.newAccount()
        for (path in listOf("/admin/v1/reports", "/admin/v1/reports?status=all")) {
            assertTrue(QaHttp.call("GET", path).status in setOf(401, 404))
            assertTrue(QaHttp.call("GET", path, user.token).status in setOf(401, 403, 404))
        }
        assertTrue(QaHttp.call("POST", "/admin/v1/accounts/${user.account.id}/ban", user.token, "{\"reason\":\"x\"}").status in setOf(401, 403, 404))
        // The ban must not have happened.
        assertEquals(200, QaHttp.call("GET", "/api/v1/me", user.token).status)
    }

    @Ignore(WAITING)
    @Test fun acAds26_appAdsTxtIsServedFromTheEnvironment() {
        val r = QaHttp.call("GET", "/app-ads.txt")
        val expected = System.getenv("APP_ADS_TXT")
        if (expected == null) {
            assertEquals(404, r.status)
        } else {
            assertEquals(200, r.status)
            assertTrue(r.headers.entries.first { it.key.equals("content-type", true) }.value.first().startsWith("text/plain"))
            assertEquals(expected, r.body)
        }
    }

    @Ignore(WAITING)
    @Test fun acSec07_rateLimitsHold() {
        // WS: about 10 msg/s with bursts of 30; 50 dropped in a row -> close 1008 (protocol 4).
        val p = QaPlayer.fresh()
        repeat(200) { p.ws.sendRaw("{\"type\":\"ping\"}") }
        p.ws.awaitError("rate_limited")
        assertEquals(1008, p.ws.awaitClose(10_000))
        // Account creation: 10 per IP per hour -> the 11th from this IP is 429 (run against a fresh server instance).
        val statuses = (0 until 12).map { QaHttp.call("POST", "/api/v1/accounts", body = "{\"nickname\":\"Rate$it\"}").status }
        assertTrue("no 429 after 12 creations: $statuses", 429 in statuses)
    }

    @Ignore(WAITING)
    @Test fun acSec10_transferCodeIsSingleUseAndRevokesTheOldDevice() {
        val a = QaHttp.newAccount()
        val old = QaWs.connect(a.token).also { it.awaitType("welcome") }
        val code = QaHttp.call("POST", "/api/v1/me/transfer-code", a.token).json!!["code"]!!.jsonPrimitive.content
        val redeemed = QaHttp.call("POST", "/api/v1/transfer", body = "{\"code\":\"${code.lowercase().replace("-", " ")}\"}")
        assertEquals("dashes, spaces and case are ignored", 200, redeemed.status)
        val newToken = redeemed.json!!["token"]!!.jsonPrimitive.content
        assertNotEquals(a.token, newToken)
        assertEquals(401, QaHttp.call("GET", "/api/v1/me", a.token).status)
        assertEquals(200, QaHttp.call("GET", "/api/v1/me", newToken).status)
        assertEquals("second use", 400, QaHttp.call("POST", "/api/v1/transfer", body = "{\"code\":\"$code\"}").status)
        // DEF-010: the protocol does not say what the old device's live socket receives; QA expects a close it will not retry.
        assertTrue("old socket still open after revocation", old.awaitClose(5_000) != null)
    }

    @Ignore(WAITING)
    @Test fun acSec06_messageReportCannotTargetARoomTheReporterWasNeverIn() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 0))
        host.ws.send(app.numbaseball.core.protocol.Chat("hello"))
        val msg = host.ws.await { it.type == "chat" }
        val id = msg["message"]!!.let { (it as kotlinx.serialization.json.JsonObject)["id"]!!.jsonPrimitive.content.toLong() }
        val stranger = QaHttp.newAccount()
        val body = "{\"targetId\":\"${host.id}\",\"reason\":\"spam\",\"roomId\":\"${host.room!!.id}\",\"messageId\":$id}"
        val r = QaHttp.call("POST", "/api/v1/reports", stranger.token, body)
        // Accepted as a player report at most, never with a snapshot of a room the reporter was not in (DEF-040).
        assertTrue(r.status in setOf(201, 400, 404))
    }

    @Ignore(WAITING)
    @Test fun acSec01_tokenFormat() {
        val tokens = (0 until 5).map { QaHttp.newAccount().token }
        for (t in tokens) {
            assertEquals("43 characters, 256 bits base64url (protocol 3.1)", 43, t.length)
            assertTrue(t.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        }
        assertEquals(tokens.size, tokens.toSet().size)
        val wsCount = tokens.count { t -> QaWs.connect(t).use { it.awaitType("welcome")["account"] != null } }
        assertEquals(5, wsCount)
    }
}
