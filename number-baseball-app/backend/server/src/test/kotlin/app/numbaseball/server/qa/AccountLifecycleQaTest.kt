package app.numbaseball.server.qa

import app.numbaseball.core.protocol.Chat
import app.numbaseball.core.protocol.Forfeit
import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.RoomReset
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.SetSecret
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** AC-ACC-*, AC-DEL-*, AC-PRIV-*, AC-REV-* against the real server module (protocol sections 3.1, 8.5, 14, 15). */
class AccountLifecycleQaTest {

    private fun stats(inst: QaInstance, token: String): String {
        val s = inst.call("GET", "/api/v1/me", token).json!!["stats"]!!.jsonObject
        return "${s["wins"]}/${s["losses"]}/${s["draws"]}"
    }

    @Test fun acDel01_inAppDeletionIsImmediateAndEverywhere() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 600))
        val guest = QaPlayer.fresh().also { QaFlows.join(it, host.room!!.code!!) }
        host.ws.send(Chat("hello from soon-deleted"))
        guest.ws.awaitType("chat")
        val inst = QaServer.main
        assertEquals(204, inst.call("DELETE", "/api/v1/me", host.auth.token).status)
        assertEquals(4004, host.ws.awaitClose())
        val gone = guest.ws.awaitType("chat_sender_deleted")
        assertEquals(host.id, gone.str("senderId"))
        assertEquals("the deleted player left the lobby", 1, guest.snapshot().players.size)
        assertEquals(401, inst.call("GET", "/api/v1/me", host.auth.token).status)
        assertTrue("token must be dead for the WebSocket too", runCatching { QaWs.connect(host.auth.token) }.isFailure)
        guest.ws.send(app.numbaseball.core.protocol.Resync())
        val history = guest.ws.awaitType("chat_history")["messages"]!!.jsonArray
        assertEquals("deleted player's line stays, anonymised", JsonNull, history.filter { it.jsonObject.str("kind") != "system" }.single().jsonObject["senderId"])
        guest.close()
    }

    @Test fun acDel02_operatorDeletesByHandleAfterAnEmailRequest() {
        val inst = QaServer.main
        val a = inst.newAccount("Del")
        val handle = "${a.account.nickname}#${a.account.tag}"
        val q = inst.handleQuery(a.account.nickname, a.account.tag)
        val info = inst.admin("GET", "/admin/v1/accounts?handle=$q")
        assertEquals(200, info.status)
        assertNotNull("lastActiveAt is shown so the operator can check before deleting (protocol 14.2)", info.json!!["lastActiveAt"]?.takeIf { it !is JsonNull })
        assertEquals(a.account.id, info.json!!["account"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("unknown handle", 404, inst.admin("GET", "/admin/v1/accounts?handle=${inst.handleQuery("Nobody", "0001")}").status)
        assertEquals("handle $handle", 204, inst.admin("DELETE", "/admin/v1/accounts?handle=$q").status)
        assertEquals(401, inst.call("GET", "/api/v1/me", a.token).status)
        assertEquals(404, inst.admin("GET", "/admin/v1/accounts/${a.account.id}").status)
    }

    @Test fun acDel03_thereIsNoWebFormNoDeletionCodeAndNoCodelessWebRequest() {
        val inst = QaServer.main
        val a = inst.newAccount("NoWeb")
        for (path in listOf("/delete-account", "/delete-account/request")) {
            for (body in listOf("code=AAAA-AAAA-AAAA&confirm=yes", "handle=${a.account.nickname}%23${a.account.tag}&confirm=yes")) {
                val r = inst.call("POST", path, body = body, contentType = "application/x-www-form-urlencoded")
                assertTrue("POST $path must not exist (got ${r.status})", r.status in setOf(404, 405))
            }
        }
        assertEquals(404, inst.call("POST", "/api/v1/me/deletion-code", a.token).status)
        assertEquals("nothing was deleted", 200, inst.call("GET", "/api/v1/me", a.token).status)
        assertFalse("the account never carries a pending-deletion field", inst.call("GET", "/api/v1/me", a.token).body.contains("deletionRequestedAt"))
        val page = inst.call("GET", "/delete-account?lang=en")
        assertEquals(200, page.status)
        assertFalse("no form on the public page (protocol 14.1)", page.body.contains("<form", ignoreCase = true))
        assertTrue("page explains the e-mail route", page.body.contains("#"))
    }

    @Test fun acDel04_deletionAnonymisesReportsAndDropsReportsAboutThePlayer() {
        val inst = QaServer.start()
        inst.use {
            val host = QaFlows.privateLobby(RoomSettings(3, 3, 60), inst)
            val code = host.room!!.code!!
            val g = QaPlayer.fresh("Gone", inst).also { QaFlows.join(it, code) }
            val r = QaPlayer.fresh("Rep", inst).also { QaFlows.join(it, code) }
            g.ws.send(Chat("a line by the player who will delete"))
            host.ws.send(Chat("host line"))
            r.ws.awaitType("chat"); r.ws.awaitType("chat")
            val roomId = host.room!!.id
            // r reports the host (snapshot holds g's line), and the host reports g.
            assertEquals(201, inst.call("POST", "/api/v1/reports", r.auth.token,
                "{\"targetId\":\"${host.id}\",\"reason\":\"spam\",\"roomId\":\"$roomId\"}").status)
            assertEquals(201, inst.call("POST", "/api/v1/reports", host.auth.token,
                "{\"targetId\":\"${g.id}\",\"reason\":\"spam\",\"roomId\":\"$roomId\"}").status)
            assertEquals(204, inst.call("DELETE", "/api/v1/me", g.auth.token).status)
            val reports = inst.admin("GET", "/admin/v1/reports?status=all").body
            assertFalse("no trace of the deleted id", reports.contains(g.id))
            assertFalse("no trace of the deleted nickname", reports.contains(g.auth.account.nickname))
            val list = inst.admin("GET", "/admin/v1/reports?status=all").json!!["reports"]!!.jsonArray
            assertEquals("the report about the deleted player is gone, the other stays", 1, list.size)
            // Reporter deleted: report stays without a reporter id.
            assertEquals(204, inst.call("DELETE", "/api/v1/me", r.auth.token).status)
            val after = inst.admin("GET", "/admin/v1/reports?status=all").json!!["reports"]!!.jsonArray.single().jsonObject
            assertTrue("reporter id removed", after["reporterId"] == null || after["reporterId"] is JsonNull)
            host.close(); g.close(); r.close()
        }
    }

    @Test fun acAcc01_accountCreationNicknameRulesOverRest() {
        val inst = QaServer.main
        for (row in QaServerCorpus.load("nicknames.tsv")) {
            val nick = QaServerCorpus.decode(row.getValue("input"))
            val body = "{\"nickname\":" + JsonPrimitive(nick) + "}"
            val r = inst.call("POST", "/api/v1/accounts", body = body)
            when (val expect = row.getValue("expect")) {
                "VALID" -> assertEquals(row["id"], 201, r.status)
                "INVALID" -> assertEquals(row["id"], "invalid_nickname", r.code)
                "PROFANITY" -> assertEquals(row["id"], "nickname_profanity", r.code)
                else -> assertTrue("${row["id"]} ($expect)", r.status in setOf(201, 400))
            }
            assertTrue("never a 500", r.status < 500)
        }
    }

    @Test fun acAcc02_renameKeepsTagWhenFreeAndIsLimitedToFivePerDay() {
        QaServer.start(relaxed = false).use { inst ->
            val a = inst.newAccount("Ren")
            val first = inst.call("PATCH", "/api/v1/me", a.token, "{\"nickname\":\"Renamed0\"}")
            assertEquals(200, first.status)
            assertEquals("tag is kept when nickname#tag is free", a.account.tag, first.json!!["tag"]!!.jsonPrimitive.content)
            val statuses = (1..5).map { inst.call("PATCH", "/api/v1/me", a.token, "{\"nickname\":\"Renamed$it\"}").status }
            assertEquals("5 renames per account per day (the first one above counts)", listOf(200, 200, 200, 200, 429), statuses)
            val limited = inst.call("PATCH", "/api/v1/me", a.token, "{\"nickname\":\"Renamed9\"}")
            assertEquals(429, limited.status)
            assertEquals("rate_limited", limited.code)
            assertNotNull("Retry-After header on 429", limited.header("Retry-After"))
            inst.advance(24 * 3_600_000L + 1_000)
            assertEquals("a day later renaming works again", 200, inst.call("PATCH", "/api/v1/me", a.token, "{\"nickname\":\"Renamed9\"}").status)
        }
    }

    @Test fun acAcc03_renameIsShownInTheRoomsYouAreIn() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 60))
        host.ws.awaitType("chat_history")
        val r = QaServer.main.call("PATCH", "/api/v1/me", host.auth.token, "{\"nickname\":\"Newname\"}")
        assertEquals(200, r.status)
        val seen = host.awaitRoom { s -> s.players.any { it.nickname == "Newname" } }
        assertEquals("Newname", seen.players.single().nickname)
        host.close()
    }

    @Test fun acPriv01_noChatTextTokensCodesOrIpsInLogsOrTheDatabase() {
        val inst = QaServer.start()
        inst.use {
            val a = inst.newAccount()
            val code = inst.call("POST", "/api/v1/me/transfer-code", a.token).json!!["code"]!!.jsonPrimitive.content
            val marker = "QAMARKER" + System.nanoTime()
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 600), inst)
            host.ws.send(Chat(marker))
            host.ws.awaitType("chat")
            // Some more traffic that must not leave traces: a failed login, a rejected request, a report.
            inst.call("GET", "/api/v1/me", "wrong-token-" + marker)
            inst.call("POST", "/api/v1/transfer", body = "{\"code\":\"AAAA-BBBB-CCCC\"}")
            host.close()
            Thread.sleep(500)
            val log = QaServer.logFile.readText(Charsets.ISO_8859_1)
                .lineSequence().filterNot { it.contains("Responding at") || it.contains("Application started") }.joinToString("\n")
            val db = inst.dbFile!!
            val dbBytes = db.readBytes() + (File(db.path + "-wal").takeIf { it.exists() }?.readBytes() ?: ByteArray(0))
            for ((where, s) in mapOf("log" to log, "db" to String(dbBytes, Charsets.ISO_8859_1))) {
                assertFalse("chat text in $where", s.contains(marker))
                assertFalse("token in $where", s.contains(a.token))
                assertFalse("transfer code in $where", s.contains(code) || s.contains(code.replace("-", "")))
                assertFalse("IPv4 address in $where", Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""").containsMatchIn(s.replace("0.0.0.0", "")))
            }
            assertTrue("log capture works", log.contains("/api/v1/accounts"))
        }
    }

    // ------------------------------------------------------------------ review room (protocol 8.5)

    @Test fun acRev01_reviewRoomGivesABotOpponentWhoSpeaks() {
        val p = QaPlayer.fresh("Rev")
        val r = QaFlows.join(p, QaServer.REVIEW_CODE.lowercase())
        assertEquals(4, r.settings.digits)
        assertEquals(1, r.settings.teamSize)
        assertEquals(120, r.settings.roundSeconds)
        assertEquals("PLAYING", r.phase.name)
        assertEquals("you are the host on team A", p.id, r.hostId)
        assertEquals(2, r.players.size)
        assertTrue(r.players.any { it.nickname == "ReviewBot" })
        val phrase = p.ws.await(10_000, "bot quick phrase") { it.type == "chat" && it["message"]!!.jsonObject.str("kind") == "phrase" }
        assertNotNull(phrase)
        val text = p.ws.await(10_000, "bot text line") { it.type == "chat" && it["message"]!!.jsonObject.str("kind") == "text" }
        assertNotNull(text["message"]!!.jsonObject.str("text"))
        // The bot is a real player on the roster: Report and Block can be tried.
        val botId = r.players.first { it.nickname == "ReviewBot" }.id
        assertEquals(204, QaServer.main.call("PUT", "/api/v1/me/blocks/$botId", p.auth.token).status)
        assertEquals(201, QaServer.main.call("POST", "/api/v1/reports", p.auth.token,
            "{\"targetId\":\"$botId\",\"reason\":\"other\",\"roomId\":\"${r.id}\"}").status)
        p.close()
    }

    @Test fun acRev02_reviewRoomNeedsTermsAndIsOffWhenTheCodeIsUnset() {
        val noTerms = QaPlayer.fresh("Rev", acceptTerms = false)
        noTerms.ws.send(app.numbaseball.core.protocol.RoomJoin(QaServer.REVIEW_CODE))
        noTerms.ws.awaitError("terms_required")
        noTerms.close()
        QaServer.start(reviewRoomCode = null).use { off ->
            val p = QaPlayer.fresh("Rev", off)
            p.ws.send(app.numbaseball.core.protocol.RoomJoin(QaServer.REVIEW_CODE))
            p.ws.awaitError("room_not_found")
            p.close()
        }
        // The review code is never handed out as a normal room code.
        val codes = (1..30).map { QaFlows.privateLobby(RoomSettings(3, 1, 30)).also { h -> h.close() }.room!!.code }
        assertFalse(QaServer.REVIEW_CODE in codes)
    }

    @Test fun acRev03_botGamesRecordNoStatsForAnyone() = KnownServerDefects.scenario("acRev03_botGamesRecordNoStatsForAnyone") {
        QaServer.start().use { inst ->
            val p = QaPlayer.fresh("Rev", inst)
            QaFlows.join(p, QaServer.REVIEW_CODE)
            // Choose, then give up: the duel is lost against the bot.
            p.ws.send(SetSecret("0123"))
            p.awaitRoom { it.match?.myDuel?.phase?.name == "GUESSING" }
            p.ws.send(Forfeit())
            p.awaitRoom { it.match?.result != null }
            assertEquals("a forfeit against the review bot must not count (protocol 3.1 / 8.5)", "0/0/0", stats(inst, p.auth.token))
            // room_reset starts another duel against the bot.
            p.ws.send(RoomReset())
            val again = p.awaitRoom { it.phase.name == "PLAYING" && it.match?.number == 2 }
            assertEquals(2, again.match!!.number)
            // A second bot game after room_reset must not count either.
            p.ws.send(SetSecret("0123"))
            p.awaitRoom { it.match?.myDuel?.phase?.name == "GUESSING" }
            p.ws.send(Forfeit())
            p.awaitRoom { it.match?.result != null }
            assertEquals("0/0/0", stats(inst, p.auth.token))
            p.close()
        }
    }

    @Test fun acRev04_aRealMatchStillCountsSoTheBotExceptionIsNarrow() {
        val (a, b) = QaFlows.guessingPair("012", "345")
        a.ws.send(Guess("345", 1)); b.ws.send(Guess("678", 1))
        a.awaitRoom { it.match?.result != null }
        assertEquals("1/0/0", stats(QaServer.main, a.auth.token))
        a.close(); b.close()
    }
}
