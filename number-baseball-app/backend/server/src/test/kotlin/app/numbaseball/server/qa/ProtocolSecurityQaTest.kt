package app.numbaseball.server.qa

import app.numbaseball.core.protocol.Chat
import app.numbaseball.core.protocol.Forfeit
import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomSettings
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AC-SEC-* and AC-PROTO-* against the real server module (protocol sections 3, 4, 5, 11, 12, 14). */
class ProtocolSecurityQaTest {

    // ------------------------------------------------------------------ secrets

    @Test fun acSec05_opponentSecretNeverReachesThePlayerBeforeTheDuelEnds() {
        val (a, b) = QaFlows.guessingPair("987", "654")
        var aws = a.ws
        fun awaitRound(n: Int) = aws.await(what = "round $n") { it.type == "room_state" && (QaPlayer.roomOf(it).match?.myDuel?.round ?: 0) >= n }
        for (round in 1..6) {
            aws.send(Guess("123", round)); b.ws.send(Guess("123", round))
            awaitRound(round + 1)
            if (round == 3) { // reconnect A in the middle: the new snapshot must not leak either
                aws = QaWs.connect(a.auth.token)
                aws.awaitType("welcome"); aws.awaitType("room_state")
            }
        }
        for (ws in listOf(a.ws, aws)) assertFalse("B's secret leaked to A before the duel ended", QaFlows.leaks(ws.all.toList(), "654"))
        assertFalse("A's secret leaked to B", QaFlows.leaks(b.ws.all.toList(), "987"))
        aws.send(Guess("654", 7)); b.ws.send(Guess("123", 7))
        val end = QaPlayer.roomOf(aws.await(what = "finish") { it.type == "room_state" && QaPlayer.roomOf(it).match?.myDuel?.phase?.name == "FINISHED" })
        assertEquals("654", end.match!!.myDuel!!.opponentSecret)
        aws.close(); a.close(); b.close()
    }

    @Test fun acSec05_teamMatesNeverSeeOtherDuelsGuessesOrSecrets() {
        val room = QaFlows.startedTeamRoom(2)
        val secrets = listOf("123", "456", "789", "097")
        room.players.forEachIndexed { i, p -> p.ws.send(app.numbaseball.core.protocol.SetSecret(secrets[i])) }
        room.players.forEach { p -> p.awaitRoom(10_000) { it.match?.myDuel?.phase?.name == "GUESSING" } }
        val secretOf = room.players.mapIndexed { i, p -> p.id to secrets[i] }.toMap()
        fun forbidden(p: QaPlayer): List<String> {
            val allowed = setOf(secretOf.getValue(p.id), secretOf.getValue(room.opponentOf(p).id))
            return secrets.filter { it !in allowed }
        }
        fun scan(finalCheck: Boolean) {
            for (p in room.players) {
                val frames = p.ws.all.toList()
                for (s in forbidden(p)) assertFalse("a secret of another duel leaked to ${p.id}", QaFlows.leaks(frames, s))
                if (!finalCheck && p.duelPhase != "FINISHED") {
                    assertFalse("opponent secret leaked before the end to ${p.id} (phase ${p.duelPhase}): " + frames.filter { it.contains("\"" + secretOf.getValue(room.opponentOf(p).id) + "\"") }.map { it.take(1500) }, QaFlows.leaks(frames, secretOf.getValue(room.opponentOf(p).id)))
                }
            }
        }
        // First duel ends: A1 solves.
        val a1 = room.teamA[0]; val b1 = room.opponentOf(a1)
        a1.ws.send(Guess(secretOf.getValue(b1.id), 1)); b1.ws.send(Guess("580", 1))
        a1.awaitRoom { it.match?.myDuel?.phase?.name == "FINISHED" }
        room.players.forEach { it.snapshot() }
        scan(false)
        // Second duel ends.
        val a2 = room.teamA[1]; val b2 = room.opponentOf(a2)
        a2.ws.send(Guess(secretOf.getValue(b2.id), 1)); b2.ws.send(Guess("580", 1))
        room.players.forEach { p -> p.awaitRoom(10_000) { it.match?.result != null } }
        scan(true)
        room.close()
    }

    // ------------------------------------------------------------------ malformed frames (corpus)

    private fun runRows(rows: List<Map<String, String>>, state: String) {
        rows.chunked(5).forEach { chunk -> runChunk(chunk, state) }
    }

    private fun runChunk(rows: List<Map<String, String>>, state: String) {
        val players = ArrayList<QaPlayer>()
        val p: QaPlayer = when (state) {
            "ANY" -> QaPlayer.fresh().also { players += it }
            "QUEUED" -> QaPlayer.fresh().also { players += it; it.ws.send(QueueJoin(3)); it.ws.awaitType("queued") }
            "LOBBY_HOST" -> QaFlows.privateLobby(RoomSettings(3, 2, 60)).also { players += it }
            "CHOOSING" -> QaFlows.quickPair(3).let { players += listOf(it.first, it.second); it.first }
            "GUESSING" -> QaFlows.guessingPair("012", "345").let { players += listOf(it.first, it.second); it.first }
            else -> error(state)
        }
        try {
            for (row in rows) {
                val id = row.getValue("id")
                val frame = QaServerCorpus.decode(row.getValue("frame"))
                val expect = row.getValue("expect").split('|')
                val before = if (p.room != null && state == "LOBBY_HOST") p.snapshot().version else null
                p.ws.drain(0)
                p.ws.sendRaw(frame)
                when (row.getValue("layer")) {
                    "DECODE" -> p.ws.awaitError("malformed_message")
                    "SERVER" -> {
                        val e = p.ws.awaitType("error")
                        assertTrue("$id: got ${e["code"]}, expected $expect", e["code"]!!.jsonPrimitive.content in expect)
                    }
                    "SIZE" -> when {
                        expect[0] == "pong" -> p.ws.awaitType("pong")
                        expect[0].startsWith("close:") -> {
                            assertEquals(id, expect[0].removePrefix("close:").toInt(), p.ws.awaitClose())
                            continue
                        }
                        else -> p.ws.awaitError(expect[0])
                    }
                    "TOLERATED" -> p.ws.drain(150)
                }
                assertNull("$id closed the connection", p.ws.closeCode)
                p.ws.assertAlive()
                if (before != null && row.getValue("layer") == "SERVER") {
                    assertEquals("$id changed room state", before, p.snapshot().version)
                }
            }
        } finally {
            players.forEach { it.close() }
        }
    }

    /** Protocol 5: "An error never changes state." Rejected duel actions must not bump the room version or send a room_state. */
    @Test fun acProto15_aRejectedDuelActionChangesNothing() = KnownServerDefects.scenario("acProto15_aRejectedDuelActionChangesNothing") {
        val changed = ArrayList<String>()
        for (state in listOf("CHOOSING", "GUESSING")) {
            val (a, b) = if (state == "CHOOSING") QaFlows.quickPair(3) else QaFlows.guessingPair("012", "345")
            for (row in rowsFor(state).chunked(1)) {
                val r = row.single()
                if (r.getValue("layer") != "SERVER") continue
                val before = a.snapshot().version
                a.ws.sendRaw(QaServerCorpus.decode(r.getValue("frame")))
                a.ws.awaitType("error")
                if (a.snapshot().version != before) changed += r.getValue("id")
                Thread.sleep(150)
            }
            a.close(); b.close()
        }
        assertTrue("rows whose rejection changed the room version: $changed", changed.isEmpty())
    }

    private fun rowsFor(state: String) = QaServerCorpus.load("protocol_frames.tsv").filter { it["state"] == state && it["id"] != "Z-006" }

    @Test fun acProto10_malformedAndInvalidFramesAnyState() = runRows(rowsFor("ANY"), "ANY")
    @Test fun acProto10_invalidFramesWhileQueued() = runRows(rowsFor("QUEUED"), "QUEUED")
    @Test fun acProto10_invalidFramesInALobbyAsHost() = runRows(rowsFor("LOBBY_HOST"), "LOBBY_HOST")
    @Test fun acProto10_invalidFramesWhileChoosing() = runRows(rowsFor("CHOOSING"), "CHOOSING")
    @Test fun acProto10_invalidFramesWhileGuessing() = runRows(rowsFor("GUESSING"), "GUESSING")

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
        val q = QaPlayer.fresh()
        q.ws.sendRaw("{\"type\":\"ping\",\"pad\":\"" + "a".repeat(17_000) + "\"}")
        assertEquals(1009, q.ws.awaitClose())
        // A message split into small fragments whose total exceeds 16 KB must not be buffered without limit.
        val f = QaPlayer.fresh()
        runCatching { f.ws.sendFragmented(List(40) { "a".repeat(1000) }) }
        val code = f.ws.awaitClose(5_000)
        assertTrue("fragmented 40 KB message: expected close 1009, close=$code", code == 1009)
    }

    @Test fun acProto12_everyRequestWithARidGetsExactlyOneReplyAndVersionsIncrease() {
        val (a, b) = QaFlows.guessingPair()
        val r1 = a.ws.request("{\"type\":\"guess\",\"guess\":\"678\",\"round\":1}")
        assertEquals("ack", r1.type)
        val bad = a.ws.request("{\"type\":\"guess\",\"guess\":\"678\",\"round\":1}")
        assertEquals("already_guessed", bad.str("code"))
        val frames = a.ws.drain(300)
        assertTrue("no second reply for the same rid", frames.none { (it.type == "ack" || it.type == "error") && it.str("rid") == bad.str("rid") })
        b.ws.send(Guess("678", 1))
        a.awaitRoom { (it.match?.myDuel?.round ?: 0) == 2 }
        val versions = a.ws.all.mapNotNull { runCatching { QaPlayer.roomOf(app.numbaseball.core.protocol.ProtocolJson.parseToJsonElement(it).jsonObject).version }.getOrNull() }
        assertTrue("versions strictly increase: $versions", versions.zipWithNext().all { it.first < it.second })
        // ping answers with pong only
        val rid = a.ws.nextRid()
        a.ws.sendRaw("{\"type\":\"ping\",\"rid\":$rid}")
        a.ws.await { it.type == "pong" && it.str("rid") == rid.toString() }
        assertTrue(a.ws.drain(200).none { it.type == "ack" })
        a.close(); b.close()
    }

    @Test fun acProto13_seatBoundMessagesWithoutASeatAnswerNotInRoom() {
        val p = QaPlayer.fresh()
        val frames = listOf(
            "{\"type\":\"room_leave\"}", "{\"type\":\"room_settings\",\"settings\":{\"digits\":3,\"teamSize\":1,\"roundSeconds\":60}}",
            "{\"type\":\"team_set\",\"team\":\"B\"}", "{\"type\":\"room_reset\"}", "{\"type\":\"secret\",\"secret\":\"123\"}",
            "{\"type\":\"guess\",\"guess\":\"123\",\"round\":1}", "{\"type\":\"forfeit\"}", "{\"type\":\"chat\",\"text\":\"hi\"}",
            "{\"type\":\"phrase\",\"phraseId\":\"hello\"}",
        )
        for (f in frames) assertEquals(f, "not_in_room", p.ws.request(f).str("code"))
        p.close()
    }

    @Test fun acProto14_restConventionsVersionBodyLimitFormBodyAndBearerCase() {
        val inst = QaServer.main
        assertEquals(404, inst.call("GET", "/api/v2/config").status)
        assertEquals(1, inst.call("GET", "/api/v1/config").json!!["protocol"]!!.jsonPrimitive.content.toInt())
        val big = inst.call("POST", "/api/v1/accounts", body = "{\"nickname\":\"${"a".repeat(5000)}\"}")
        assertEquals(413, big.status); assertEquals("message_too_large", big.code)
        val form = inst.call("POST", "/api/v1/accounts", body = "nickname=Mina", contentType = "application/x-www-form-urlencoded")
        assertEquals(400, form.status); assertEquals("bad_request", form.code)
        val a = inst.newAccount()
        assertEquals("scheme is case-insensitive", 200, inst.call("GET", "/api/v1/me", authHeader = "bEaReR ${a.token}").status)
        assertEquals(401, inst.call("GET", "/api/v1/me", authHeader = "Token ${a.token}").status)
        assertEquals("no-store on API answers", "no-store", inst.call("GET", "/api/v1/me", a.token).header("Cache-Control"))
        assertEquals("nosniff", inst.call("GET", "/api/v1/config").header("X-Content-Type-Options"))
        val chosen = inst.call("POST", "/api/v1/accounts", body = "{\"nickname\":\"Chooser\",\"token\":\"attacker-chosen-token-000000000000000000000\"}")
        assertEquals(201, chosen.status)
        assertNotEquals("attacker-chosen-token-000000000000000000000", chosen.json!!["token"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------------------ REST corpus, upgrade, admin

    @Test fun acSec03_websocketUpgradeNeedsAValidToken() {
        assertEquals(401, QaWs.upgradeStatus(null))
        assertEquals(401, QaWs.upgradeStatus("garbage"))
        assertTrue(runCatching { QaWs.connect("") }.isFailure)
        val a = QaServer.main.newAccount()
        assertTrue("lower-case scheme accepted", runCatching { QaWs.connect(a.token, scheme = "bearer").use { it.awaitType("welcome") } }.isSuccess)
        assertTrue("other schemes refused", runCatching { QaWs.connect(a.token, scheme = "Basic") }.isFailure)
    }

    @Test fun acSec04_restRequestsCorpus() {
        val inst = QaServer.main
        val user = inst.newAccount()
        val other = inst.newAccount()
        val revoked = inst.newAccount().also {
            val code = inst.call("POST", "/api/v1/me/transfer-code", it.token).json!!["code"]!!.jsonPrimitive.content
            assertEquals(200, inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"$code\"}").status)
        }
        val deleted = inst.newAccount().also { assertEquals(204, inst.call("DELETE", "/api/v1/me", it.token).status) }
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
            val r = inst.call(row.getValue("method"), path, body = body, contentType = ct, authHeader = auth)
            val statuses = row.getValue("status").split('|').map { it.toInt() }
            assertTrue("${row["id"]}: status ${r.status} not in $statuses (${r.body.take(200)})", r.status in statuses)
            val codes = row.getValue("code").split('|')
            if ("-" !in codes) assertTrue("${row["id"]}: code ${r.code} not in $codes", r.code in codes)
            assertFalse("${row["id"]}: response leaks a stack trace", r.body.contains("Exception") || r.body.contains("\tat "))
        }
    }

    @Test fun acSec09_adminApiIsInvisibleWithoutTheAdminTokenAndNeedsExactlyThatToken() {
        QaServer.start(adminToken = null).use { noAdmin ->
            val user = noAdmin.newAccount()
            for (path in listOf("/admin/v1/reports", "/admin/v1/reports?status=all")) {
                assertEquals(404, noAdmin.call("GET", path).status)
                assertEquals(404, noAdmin.call("GET", path, user.token).status)
            }
            assertEquals(404, noAdmin.call("POST", "/admin/v1/test/clock?advanceMs=1", user.token).status)
            assertEquals(404, noAdmin.call("POST", "/admin/v1/accounts/${user.account.id}/ban", user.token, "{\"reason\":\"x\"}").status)
            assertEquals(200, noAdmin.call("GET", "/api/v1/me", user.token).status)
        }
        val inst = QaServer.main
        val user = inst.newAccount()
        assertEquals(401, inst.call("GET", "/admin/v1/reports", user.token).status)
        assertEquals(401, inst.call("GET", "/admin/v1/reports", authHeader = "Bearer ${QaServer.QA_ADMIN_TOKEN}x").status)
        assertEquals(401, inst.call("GET", "/admin/v1/reports").status)
        assertEquals(401, inst.call("POST", "/admin/v1/accounts/${user.account.id}/ban", user.token, "{\"reason\":\"x\"}").status)
        assertEquals("the ban did not happen", 200, inst.call("GET", "/api/v1/me", user.token).status)
        assertEquals(200, inst.admin("GET", "/admin/v1/reports").status)
        QaWs.connect(user.token).use { assertNotNull(it.awaitType("welcome")) }
    }

    @Test fun acSec11_theAdminTokenNeverAppearsInLogs() {
        QaServer.start().use { inst ->
            inst.call("GET", "/admin/v1/reports", authHeader = "Bearer wrong-admin-token-for-log-scan")
            inst.admin("GET", "/admin/v1/reports?status=all")
            Thread.sleep(300)
            val log = QaServer.logFile.readText()
            assertFalse(log.contains(QaServer.QA_ADMIN_TOKEN))
            assertFalse(log.contains("wrong-admin-token-for-log-scan"))
        }
    }

    @Test fun acAds26_appAdsTxtIsServedFromTheConfiguration() {
        val r = QaServer.main.call("GET", "/app-ads.txt")
        assertEquals(200, r.status)
        assertTrue(r.header("Content-Type")!!.startsWith("text/plain"))
        assertEquals(QaServer.APP_ADS + "\n", r.body)
        QaServer.start(appAdsTxt = null).use { assertEquals(404, it.call("GET", "/app-ads.txt").status) }
    }

    @Test fun acPages01_publicPagesAreStaticBilingualAndHttpsIsForced() {
        val inst = QaServer.main
        for (path in listOf("/privacy", "/terms", "/delete-account")) {
            val en = inst.call("GET", "$path?lang=en"); val ko = inst.call("GET", "$path?lang=ko")
            assertEquals(path, 200, en.status); assertEquals(200, ko.status)
            assertTrue(en.header("Content-Type")!!.startsWith("text/html"))
            assertFalse("no scripts or forms on $path", en.body.contains("<script", true) || en.body.contains("<form", true))
            assertNotEquals("language switch", en.body, ko.body)
        }
        assertTrue("child-safety anchor", inst.call("GET", "/terms?lang=en").body.contains("child-safety"))
        assertTrue(inst.call("GET", "/terms?lang=ko").body.contains("child-safety-ko"))
        assertEquals(404, inst.call("GET", "/").status)
        assertEquals(200, inst.call("GET", "/health").status)
        QaServer.start(forceHttps = true).use { https ->
            val r = https.call("GET", "/terms", headers = mapOf("X-Forwarded-Proto" to "http"))
            assertEquals(301, r.status)
            assertEquals(200, https.call("GET", "/health", headers = mapOf("X-Forwarded-Proto" to "http")).status)
        }
    }

    // ------------------------------------------------------------------ limits

    @Test fun acSec07_websocketFloodIsDroppedThenClosedWith1008() {
        val p = QaPlayer.fresh()
        repeat(200) { runCatching { p.ws.sendRaw("{\"type\":\"ping\"}") } }
        p.ws.awaitError("rate_limited")
        assertEquals(1008, p.ws.awaitClose(10_000))
    }

    @Test fun acSec08_accountCreationAndTransferLimitsHold() {
        QaServer.start(relaxed = false).use { inst ->
            val statuses = (0 until 12).map { inst.call("POST", "/api/v1/accounts", body = "{\"nickname\":\"Rate$it\"}") }
            assertEquals("10 per IP per hour", 10, statuses.count { it.status == 201 })
            val limited = statuses.first { it.status == 429 }
            assertEquals("rate_limited", limited.code)
            assertNotNull(limited.header("Retry-After"))
            inst.advance(3_600_000L + 1000)
            assertEquals(201, inst.call("POST", "/api/v1/accounts", body = "{\"nickname\":\"RateAgain\"}").status)
            // Transfer codes: 5 per account per hour.
            val a = inst.newAccount("Xf")
            val codes = (1..6).map { inst.call("POST", "/api/v1/me/transfer-code", a.token) }
            assertEquals(listOf(200, 200, 200, 200, 200, 429), codes.map { it.status })
        }
    }

    @Test fun acSec12_aTransferCodeDiesAfterFiveWrongSecretsAndAnIpIsLimitedAfterTenFailures() {
        QaServer.start(relaxed = false).use { inst ->
            val a = inst.newAccount("Xf")
            val code = inst.call("POST", "/api/v1/me/transfer-code", a.token).json!!["code"]!!.jsonPrimitive.content
            assertTrue(code.matches(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}")))
            val wrong = code.dropLast(1) + (if (code.last() == 'A') 'B' else 'A')
            repeat(5) { assertEquals("invalid_code", inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"$wrong\"}").code) }
            assertEquals("5 wrong secrets kill the code, even the right one", "invalid_code",
                inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"$code\"}").code)
            var last = 0
            repeat(4) { last = inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"AAAA-AAAA-AAAA\"}").status }
            val blocked = inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"AAAA-AAAA-AAAA\"}")
            assertEquals(429, blocked.status)
            assertEquals("too_many_attempts", blocked.code)
            assertEquals(400, last)
        }
    }

    // ------------------------------------------------------------------ transfer and tokens

    @Test fun acSec10_transferCodeIsSingleUseExpiresAndRevokesTheOldDevice() {
        QaServer.start().use { inst ->
            val a = inst.newAccount()
            val old = QaWs.connect(a.token, inst).also { it.awaitType("welcome") }
            val code = inst.call("POST", "/api/v1/me/transfer-code", a.token).json!!["code"]!!.jsonPrimitive.content
            val second = inst.call("POST", "/api/v1/me/transfer-code", a.token).json!!["code"]!!.jsonPrimitive.content
            assertEquals("a new code replaces the previous one", 400, inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"$code\"}").status)
            val redeemed = inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"${second.lowercase().replace("-", " ")}\"}")
            assertEquals("dashes, spaces and case are ignored", 200, redeemed.status)
            val newToken = redeemed.json!!["token"]!!.jsonPrimitive.content
            assertNotEquals(a.token, newToken)
            old.awaitError("session_replaced")
            assertEquals("old device gets close 4000", 4000, old.awaitClose())
            assertEquals(401, inst.call("GET", "/api/v1/me", a.token).status)
            assertEquals(200, inst.call("GET", "/api/v1/me", newToken).status)
            assertEquals("second use", 400, inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"$second\"}").status)
            val third = inst.call("POST", "/api/v1/me/transfer-code", newToken).json!!
            inst.advance(10 * 60_000L + 1_000)
            assertEquals("expired after 10 minutes", 400, inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"${third["code"]!!.jsonPrimitive.content}\"}").status)
        }
    }

    @Test fun acSec13_transferKeepsTheSeatForTheNewDevice() {
        val inst = QaServer.main
        val (a, b) = QaFlows.guessingPair()
        val code = inst.call("POST", "/api/v1/me/transfer-code", a.auth.token).json!!["code"]!!.jsonPrimitive.content
        val token = inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"$code\"}").json!!["token"]!!.jsonPrimitive.content
        assertEquals(4000, a.ws.awaitClose())
        QaWs.connect(token).use { ws ->
            assertNotNull(ws.awaitType("welcome")["roomId"]?.takeIf { it !is JsonNull })
            assertEquals("012", QaPlayer.roomOf(ws.awaitType("room_state")).match!!.myDuel!!.mySecret)
        }
        b.close()
    }

    @Test fun acSec06_messageReportCannotTargetARoomTheReporterWasNeverIn() {
        val inst = QaServer.main
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 600))
        host.ws.send(Chat("hello"))
        val id = host.ws.awaitType("chat")["message"]!!.jsonObject["id"]!!.jsonPrimitive.content.toLong()
        val stranger = inst.newAccount()
        val real = inst.call("POST", "/api/v1/reports", stranger.token,
            "{\"targetId\":\"${host.id}\",\"reason\":\"spam\",\"roomId\":\"${host.room!!.id}\",\"messageId\":$id}")
        val fake = inst.call("POST", "/api/v1/reports", stranger.token,
            "{\"targetId\":\"${host.id}\",\"reason\":\"spam\",\"roomId\":\"${host.room!!.id}\",\"messageId\":${id + 999}}")
        assertEquals("no existence oracle: same status for real and unknown message ids", real.status, fake.status)
        assertTrue(real.status == 201)
        val mine = inst.admin("GET", "/admin/v1/reports?status=all").json!!["reports"]!!.let {
            (it as kotlinx.serialization.json.JsonArray).map { r -> r.jsonObject }.filter { r -> r["reporterId"]?.jsonPrimitive?.content == stranger.account.id }
        }
        assertTrue(mine.isNotEmpty() && mine.all { it["snapshot"] == null || it["snapshot"] is JsonNull })
        host.close()
    }

    @Test fun acSec01_tokenFormat() {
        val tokens = (0 until 5).map { QaServer.main.newAccount().token }
        for (t in tokens) {
            assertEquals("43 characters, 256 bits base64url (protocol 3.1)", 43, t.length)
            assertTrue(t.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        }
        assertEquals(tokens.size, tokens.toSet().size)
        assertEquals(5, tokens.count { t -> QaWs.connect(t).use { it.awaitType("welcome")["account"] != null } })
    }

    @Test fun acSec02_oneConnectionPerAccountAndBotsCannotLogIn() {
        val a = QaServer.main.newAccount()
        val one = QaWs.connect(a.token); one.awaitType("welcome")
        val two = QaWs.connect(a.token); two.awaitType("welcome")
        assertEquals(4000, one.awaitClose())
        two.assertAlive()
        two.close()
        // Rooms of two players: leaving then the other still plays on (sanity of forfeits through the socket close path).
        val (x, y) = QaFlows.guessingPair()
        x.ws.send(Forfeit())
        y.awaitRoom { it.match?.result != null }
        x.close(); y.close()
    }

    @Test fun acSec14_roomCreateNeedsValidSettingsAndOneSeat() {
        val p = QaPlayer.fresh()
        p.ws.send(RoomCreate(RoomSettings(3, 6, 60))); p.ws.awaitError("invalid_settings")
        p.ws.send(RoomCreate(RoomSettings(3, 1, 60))); p.awaitRoom { it.phase.name == "LOBBY" }
        p.ws.send(RoomCreate(RoomSettings(3, 1, 60))); p.ws.awaitError("already_in_room")
        p.close()
    }
}
