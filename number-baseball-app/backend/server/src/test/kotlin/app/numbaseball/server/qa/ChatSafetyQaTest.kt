package app.numbaseball.server.qa

import app.numbaseball.core.protocol.Chat
import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.Phrase
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.Resync
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.text.ChatFilter
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

/** AC-CHAT-* (server side), AC-SAFE-*, AC-TERMS-* against the real server module (protocol sections 3, 5, 9). */
class ChatSafetyQaTest {

    private fun text(chat: JsonObject): String = chat["message"]!!.jsonObject["text"]!!.jsonPrimitive.content

    @Test fun acChat10_everybodyIncludingTheSenderGetsTheSameMaskedText() {
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 600))
        val guest = QaPlayer.fresh().also { QaFlows.join(it, host.room!!.code!!) }
        host.ws.send(Chat("f u c k  you"))
        val mine = text(host.ws.awaitType("chat"))
        val theirs = text(guest.ws.awaitType("chat"))
        assertEquals(mine, theirs)
        assertFalse(mine.contains("f u c k"))
        assertEquals("same cleaning as the core filter", (ChatFilter.clean("f u c k  you") as ChatFilter.Result.Ok).text, mine)
        host.close(); guest.close()
    }

    @Test fun acChat11_serverAppliesTheWholeCorpusLikeTheCoreFilter() {
        // Every chat_profanity row through a real private room: the delivered text must equal core ChatFilter.clean(input)
        // (protocol 9.1: same filter on both sides); rejected inputs get the same error code. The chat rate limit
        // (5 lines per 10 s, server clock) is refilled with the test clock, so the run needs no real waiting.
        QaServer.start().use { inst ->
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 600), inst)
            val rows = QaServerCorpus.load("chat_profanity.tsv") + QaServerCorpus.load("chat_sanitize.tsv").take(80)
            var sent = 0
            for (row in rows) {
                val input = QaServerCorpus.decode(row.getValue("input"))
                if (input.toByteArray().size > 1500) continue // stays below the 2048-byte frame cap with JSON escaping
                if (sent > 0 && sent % 5 == 0) inst.advance(10_000)
                sent++
                host.ws.send(Chat(input))
                when (val expected = ChatFilter.clean(input)) {
                    is ChatFilter.Result.Ok -> assertEquals(row["id"], expected.text, text(host.ws.awaitType("chat")))
                    is ChatFilter.Result.Rejected -> {
                        host.ws.awaitError(expected.code)
                        sent-- // a refused line costs no chat budget
                    }
                }
            }
            assertTrue("corpus was exercised", sent > 100)
            host.close()
        }
    }

    @Test fun acChat12_rateLimitFiveLinesPerTenSecondsTextAndPhrasesTogether() {
        QaServer.start().use { inst ->
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 600), inst)
            repeat(5) { host.ws.send(Chat("line $it")) }
            repeat(5) { host.ws.awaitType("chat") }
            host.ws.send(Phrase("hello")) // phrases share the budget
            val e = host.ws.awaitError("chat_rate_limited")
            assertTrue(e["retryAfterMs"]!!.jsonPrimitive.content.toLong() in 1..10_000)
            host.ws.send(Chat("again"))
            host.ws.awaitError("chat_rate_limited")
            inst.advance(10_000)
            host.ws.send(Chat("after the wait"))
            assertEquals("after the wait", text(host.ws.awaitType("chat")))
            host.close()
        }
    }

    @Test fun acChat13_quickMatchAcceptsQuickPhrasesButNoFreeText() {
        val (a, b) = QaFlows.quickPair()
        a.ws.send(Chat("hello there"))
        a.ws.awaitError("chat_not_allowed")
        a.ws.send(Chat(""))
        a.ws.awaitError("chat_not_allowed") // the room rule is checked before the text rules (protocol 5)
        assertFalse(b.ws.all.any { it.contains("hello there") })
        a.ws.send(Phrase("good_luck"))
        val got = b.ws.awaitType("chat")
        assertEquals("good_luck", got["message"]!!.jsonObject["phraseId"]!!.jsonPrimitive.content)
        assertEquals("the sender gets the line too", "good_luck", a.ws.awaitType("chat")["message"]!!.jsonObject["phraseId"]!!.jsonPrimitive.content)
        a.ws.send(Phrase("not_a_phrase"))
        a.ws.awaitError("unknown_phrase")
        // Every advertised phrase works.
        val ids = QaServer.main.call("GET", "/api/v1/config").json!!["quickPhrases"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(ids.containsAll(listOf("hello", "good_game", "rematch", "bye")))
        a.close(); b.close()
    }

    @Test fun acChat14_quickMatchFreeChatFlagTurnsFreeTextOn() {
        QaServer.start(quickMatchFreeChat = true).use { inst ->
            assertEquals(true, inst.call("GET", "/api/v1/config").json!!["quickMatchFreeChat"]!!.jsonPrimitive.content.toBoolean())
            val (a, b) = QaFlows.quickPair(inst = inst)
            a.ws.send(Chat("hello there"))
            assertEquals("hello there", text(b.ws.awaitType("chat")))
            a.close(); b.close()
        }
        assertEquals("flag is off by default", false,
            QaServer.main.call("GET", "/api/v1/config").json!!["quickMatchFreeChat"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun acChat15_chatLengthEmojiAndEmptyLinesInPrivateRooms() {
        QaServer.start().use { inst ->
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 600), inst)
            host.ws.send(Chat("a".repeat(101)))
            host.ws.awaitError("chat_too_long")
            host.ws.send(Chat("a".repeat(100)))
            assertEquals(100, text(host.ws.awaitType("chat")).length)
            host.ws.send(Chat("😀😀"))
            host.ws.awaitError("chat_empty")
            host.ws.send(Chat("   "))
            host.ws.awaitError("chat_empty")
            host.ws.send(Chat("go 😀 ^^ ♥"))
            assertEquals("go ^^ ♥", text(host.ws.awaitType("chat")))
            host.close()
        }
    }

    @Test fun acSafe01_blockHidesChatBothWaysLiveAndInHistory() {
        val inst = QaServer.main
        val host = QaFlows.privateLobby(RoomSettings(3, 2, 600))
        val guest = QaPlayer.fresh().also { QaFlows.join(it, host.room!!.code!!) }
        host.ws.send(Chat("before block"))
        guest.ws.awaitType("chat")
        assertEquals(204, inst.call("PUT", "/api/v1/me/blocks/${guest.id}", host.auth.token).status)
        assertEquals("idempotent", 204, inst.call("PUT", "/api/v1/me/blocks/${guest.id}", host.auth.token).status)
        val list = inst.call("GET", "/api/v1/me/blocks", host.auth.token).json!!["blocked"]!!.jsonArray
        assertEquals(guest.id, list.single().jsonObject["id"]!!.jsonPrimitive.content)
        guest.ws.send(Chat("from the blocked one"))
        host.ws.send(Chat("from the blocker"))
        guest.ws.awaitType("chat") // the sender still gets their own line
        host.ws.awaitType("chat")
        Thread.sleep(500)
        assertFalse(host.ws.all.any { it.contains("from the blocked one") })
        assertFalse("blocking is two-way (protocol 3.2)", guest.ws.all.any { it.contains("from the blocker") })
        guest.ws.send(Resync())
        assertFalse("history must be block-filtered", guest.ws.awaitType("chat_history").toString().contains("before block"))
        // Unblock: new lines flow again.
        assertEquals(204, inst.call("DELETE", "/api/v1/me/blocks/${guest.id}", host.auth.token).status)
        host.ws.send(Chat("friends again"))
        assertEquals("friends again", text(guest.ws.await(what = "chat after unblock") { it.type == "chat" && it.toString().contains("friends again") }))
        host.close(); guest.close()
    }

    @Test fun acSafe02_reportStoresAMaskedSnapshotAndIsRateLimited() {
        QaServer.start(relaxed = false).use { inst ->
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 600), inst)
            val guest = QaPlayer.fresh(inst = inst).also { QaFlows.join(it, host.room!!.code!!) }
            host.ws.send(Chat("you are a f u c k"))
            val id = guest.ws.awaitType("chat")["message"]!!.jsonObject["id"]!!.jsonPrimitive.content
            val body = "{\"targetId\":\"${host.id}\",\"reason\":\"harassment\",\"roomId\":\"${host.room!!.id}\",\"messageId\":$id}"
            val ok = inst.call("POST", "/api/v1/reports", guest.auth.token, body)
            assertEquals(201, ok.status)
            assertNotNull(ok.json!!["reportId"])
            val stored = inst.admin("GET", "/admin/v1/reports").body
            assertFalse("snapshot is masked", stored.contains("f u c k"))
            assertTrue("snapshot has the line", stored.contains("you are a"))
            val wrongTarget = body.replace("\"targetId\":\"${host.id}\"", "\"targetId\":\"${guest.id}\"")
            assertEquals("message report must name the sender", 400, inst.call("POST", "/api/v1/reports", guest.auth.token, wrongTarget).status)
            assertEquals("note over 500 characters", 400, inst.call("POST", "/api/v1/reports", guest.auth.token,
                "{\"targetId\":\"${host.id}\",\"reason\":\"spam\",\"note\":\"${"a".repeat(501)}\"}").status)
            assertEquals("yourself", 400, inst.call("POST", "/api/v1/reports", guest.auth.token,
                "{\"targetId\":\"${guest.id}\",\"reason\":\"spam\"}").status)
            val statuses = (0 until 10).map { inst.call("POST", "/api/v1/reports", guest.auth.token, "{\"targetId\":\"${host.id}\",\"reason\":\"spam\"}") }
            assertTrue("10 reports per hour: the 11th valid one is refused", statuses.any { it.status == 429 })
            val limited = statuses.first { it.status == 429 }
            assertEquals("rate_limited", limited.code)
            assertNotNull(limited.header("Retry-After"))
            assertEquals("still limited", 429,
                inst.call("POST", "/api/v1/reports", guest.auth.token, "{\"targetId\":\"${host.id}\",\"reason\":\"spam\"}").status)
            inst.advance(3_600_000L + 1000)
            assertEquals(201, inst.call("POST", "/api/v1/reports", guest.auth.token, "{\"targetId\":\"${host.id}\",\"reason\":\"spam\"}").status)
            host.close(); guest.close()
        }
    }

    @Test fun acSafe03_aBannedAccountKeepsReportBlockAndDeleteButNothingElse() {
        QaServer.start().use { inst ->
            val a = inst.newAccount("Ban")
            val other = inst.newAccount("Oth")
            val live = QaWs.connect(a.token, inst).also { it.awaitType("welcome") }
            assertEquals(204, inst.admin("POST", "/admin/v1/accounts/${a.account.id}/ban", "{\"reason\":\"qa\"}").status)
            assertEquals(4003, live.awaitClose())
            assertEquals("the WebSocket upgrade is refused with 403", 403, QaWs.upgradeStatus(a.token, inst))
            assertEquals("GET /me still works", 200, inst.call("GET", "/api/v1/me", a.token).status)
            assertEquals(403, inst.call("PATCH", "/api/v1/me", a.token, "{\"nickname\":\"Banned2\"}").status)
            assertEquals("banned", inst.call("PATCH", "/api/v1/me", a.token, "{\"nickname\":\"Banned2\"}").code)
            assertEquals(403, inst.call("PUT", "/api/v1/me/terms", a.token, "{\"version\":\"x\",\"ageConfirmed\":true}").status)
            assertEquals(403, inst.call("POST", "/api/v1/me/transfer-code", a.token).status)
            assertEquals(204, inst.call("PUT", "/api/v1/me/blocks/${other.account.id}", a.token).status)
            assertEquals(201, inst.call("POST", "/api/v1/reports", a.token, "{\"targetId\":\"${other.account.id}\",\"reason\":\"spam\"}").status)
            assertEquals(204, inst.admin("POST", "/admin/v1/accounts/${a.account.id}/unban").status)
            QaWs.connect(a.token, inst).use { assertNotNull(it.awaitType("welcome")) }
            assertEquals(204, inst.call("DELETE", "/api/v1/me", a.token).status)
        }
    }

    @Test fun acSafe05_threeDistinctReportersIn24hRestrictChatFor24h() {
        QaServer.start().use { inst ->
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 600), inst)
            val guest = QaPlayer.fresh("Tgt", inst).also { QaFlows.join(it, host.room!!.code!!) }
            fun report(from: app.numbaseball.core.protocol.AuthResponse, repeatTimes: Int = 1) = repeat(repeatTimes) {
                assertEquals(201, inst.call("POST", "/api/v1/reports", from.token, "{\"targetId\":\"${guest.id}\",\"reason\":\"harassment\"}").status)
            }
            report(host.auth, 3) // three reports from ONE account do not trigger
            guest.ws.send(Chat("still allowed 1"))
            assertEquals("still allowed 1", text(guest.ws.awaitType("chat")))
            report(inst.newAccount("R2")) // second distinct reporter
            guest.ws.send(Chat("still allowed 2"))
            assertEquals("still allowed 2", text(guest.ws.awaitType("chat")))
            assertNull(inst.call("GET", "/api/v1/me", guest.auth.token).json!!["chatRestrictedUntil"]?.takeIf { it !is JsonNull })
            report(inst.newAccount("R3")) // third distinct reporter within 24 h
            val me = inst.call("GET", "/api/v1/me", guest.auth.token).json!!
            val until = me["chatRestrictedUntil"]!!.jsonPrimitive.content.toLong()
            val now = inst.call("GET", "/api/v1/config").json!!["serverTime"]!!.jsonPrimitive.content.toLong()
            assertTrue("restricted for about 24 h", until - now in 23 * 3_600_000L..24 * 3_600_000L + 5_000)
            guest.ws.send(Chat("now refused"))
            val e = guest.ws.awaitError("chat_restricted")
            assertTrue("retryAfterMs = time left", e["retryAfterMs"]!!.jsonPrimitive.content.toLong() > 23 * 3_600_000L)
            guest.ws.send(Phrase("hello")) // phrases are chat too (protocol 9.1, DEF-028)
            guest.ws.awaitError("chat_restricted")
            assertFalse("host never saw the refused line", host.ws.all.any { it.contains("now refused") })
            // Play still works for a restricted player: the connection is alive and the lobby accepts a team switch.
            assertEquals("ack", guest.ws.request("{\"type\":\"team_set\",\"team\":\"B\"}").type)
            assertTrue("pending review: the moderator sees the automatic reason",
                inst.admin("GET", "/admin/v1/accounts/${guest.id}").json!!["chatRestrictReason"]!!.jsonPrimitive.content.contains("automatic"))
            inst.advance(24 * 3_600_000L + 1_000)
            guest.ws.send(Chat("free again"))
            assertEquals("free again", text(guest.ws.awaitType("chat")))
            host.close(); guest.close()
        }
    }

    @Test fun acSafe06_moderatorCanRestrictAndLiftChat() {
        QaServer.start().use { inst ->
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 600), inst)
            host.ws.send(Chat("fine"))
            host.ws.awaitType("chat")
            assertEquals(204, inst.admin("POST", "/admin/v1/accounts/${host.id}/chat-restrict", "{\"hours\":2,\"reason\":\"qa\"}").status)
            host.ws.send(Chat("blocked"))
            host.ws.awaitError("chat_restricted")
            assertEquals(400, inst.admin("POST", "/admin/v1/accounts/${host.id}/chat-restrict", "{\"hours\":-1,\"reason\":\"qa\"}").status)
            assertEquals(204, inst.admin("POST", "/admin/v1/accounts/${host.id}/chat-restrict", "{\"hours\":0,\"reason\":\"lift\"}").status)
            host.ws.send(Chat("fine again"))
            assertEquals("fine again", text(host.ws.awaitType("chat")))
            host.close()
        }
    }

    @Test fun acSafe07_threeThrowawayAccountsThatNeverSharedARoomCanStillRestrictAPlayer() {
        // Characterisation of DEF-043 (design risk, protocol 3.3 accepts it): the rule counts any 3 distinct reporters.
        QaServer.start().use { inst ->
            val victim = inst.newAccount("Vic")
            repeat(3) {
                val thrower = inst.newAccount("Thr")
                assertEquals(201, inst.call("POST", "/api/v1/reports", thrower.token, "{\"targetId\":\"${victim.account.id}\",\"reason\":\"spam\"}").status)
            }
            assertNotNull("restricted by three accounts with no shared room and no finished match (DEF-043 still open)",
                inst.call("GET", "/api/v1/me", victim.token).json!!["chatRestrictedUntil"]?.takeIf { it !is JsonNull })
        }
    }

    // ------------------------------------------------------------------ terms and age

    @Test fun acTerms01_serverRefusesQueueRoomCreateAndJoinUntilTermsAreAccepted() {
        QaServer.start().use { inst ->
            val host = QaFlows.privateLobby(RoomSettings(3, 2, 60), inst)
            val fresh = inst.newAccount(acceptTerms = false)
            assertNull(fresh.account.termsVersion)
            val p = QaPlayer(fresh, inst)
            p.ws.send(QueueJoin(3)); p.ws.awaitError("terms_required")
            p.ws.send(RoomCreate(RoomSettings(3, 1, 60))); p.ws.awaitError("terms_required")
            // protocol 5 order: room_not_found comes before terms_required; a real code gives terms_required.
            p.ws.send(RoomJoin("ABCDE")); p.ws.awaitError("room_not_found")
            p.ws.send(RoomJoin(host.room!!.code!!)); p.ws.awaitError("terms_required")
            p.ws.send(QueueJoin(2)); p.ws.awaitError("invalid_settings") // invalid_settings is checked first
            // Seat-bound messages answer not_in_room for an unseated account (protocol 5), terms or not.
            p.ws.send(Chat("hi")); p.ws.awaitError("not_in_room")
            p.ws.send(Phrase("hello")); p.ws.awaitError("not_in_room")
            // Age confirmation is part of the acceptance.
            val version = inst.currentTermsVersion()
            val noAge = inst.acceptTerms(fresh.token, version, ageConfirmed = false)
            assertEquals(400, noAge.status); assertEquals("age_confirmation_required", noAge.code)
            assertNull("nothing stored", inst.call("GET", "/api/v1/me", fresh.token).json!!["termsVersion"]?.takeIf { it !is JsonNull })
            p.ws.send(QueueJoin(3)); p.ws.awaitError("terms_required")
            val stale = inst.acceptTerms(fresh.token, "1999-01-old", true)
            assertEquals(409, stale.status); assertEquals("terms_outdated", stale.code)
            p.ws.send(QueueJoin(3)); p.ws.awaitError("terms_required")
            val ok = inst.acceptTerms(fresh.token, version, true)
            assertEquals(200, ok.status)
            assertEquals(version, ok.json!!["termsVersion"]!!.jsonPrimitive.content)
            assertNotNull(ok.json!!["termsAcceptedAt"]); assertNotNull(ok.json!!["ageConfirmedAt"])
            p.ws.send(QueueJoin(3))
            p.ws.awaitType("queued")
            p.ws.send(app.numbaseball.core.protocol.QueueLeave())
            p.ws.awaitError("not_queued")
            host.close(); p.close()
        }
    }

    @Test fun acTerms02_aNewTermsVersionAsksAgainAndMinimumAgeIsServed() {
        val dir = java.nio.file.Files.createTempDirectory("nb-qa-terms").toFile()
        val token: String
        QaServer.start(dataDir = dir, termsVersion = "2026-11-v1").use { v1 ->
            assertEquals("2026-11-v1", v1.call("GET", "/api/v1/config").json!!["termsVersion"]!!.jsonPrimitive.content)
            assertEquals(14, v1.call("GET", "/api/v1/config").json!!["minimumAge"]!!.jsonPrimitive.content.toInt())
            val a = v1.newAccount()
            token = a.token
            QaWs.connect(a.token, v1).use { ws ->
                ws.awaitType("welcome")
                ws.send(QueueJoin(3)); ws.awaitType("queued")
            }
        }
        QaServer.start(dataDir = dir, termsVersion = "2026-11-v2").use { v2 ->
            assertEquals(200, v2.call("GET", "/api/v1/me", token).status)
            assertEquals("v1 acceptance is stored", "2026-11-v1", v2.call("GET", "/api/v1/me", token).json!!["termsVersion"]!!.jsonPrimitive.content)
            QaWs.connect(token, v2).use { ws ->
                ws.awaitType("welcome")
                ws.send(QueueJoin(3)); ws.awaitError("terms_required")
            }
            assertEquals("old version cannot be accepted any more", "terms_outdated", v2.acceptTerms(token, "2026-11-v1").code)
            assertEquals(200, v2.acceptTerms(token, "2026-11-v2").status)
            QaWs.connect(token, v2).use { ws ->
                ws.awaitType("welcome")
                ws.send(QueueJoin(3)); ws.awaitType("queued")
            }
        }
        QaServer.start(dataDir = java.nio.file.Files.createTempDirectory("nb-qa-terms2").toFile()).use { d ->
            // Default version comes from the bundled terms page (non-empty, stable between calls).
            val v = d.call("GET", "/api/v1/config").json!!["termsVersion"]!!.jsonPrimitive.content
            assertTrue(v.isNotBlank())
            assertEquals(v, d.call("GET", "/api/v1/config").json!!["termsVersion"]!!.jsonPrimitive.content)
        }
    }

    @Test fun acChat16_guessAfterTheChatDoesNotDisturbTheRoomVersionOrder() {
        // Chat lines do not change room_state; guesses do. (Guards against double broadcasts that break "version strictly increases".)
        val (a, b) = QaFlows.guessingPair()
        val v0 = a.room!!.version
        a.ws.send(Phrase("hello"))
        a.ws.awaitType("chat")
        a.ws.send(Guess("678", 1))
        val next = a.awaitRoom { it.match?.myDuel?.myPendingGuess == "678" }
        assertTrue(next.version > v0)
        a.close(); b.close()
    }
}
