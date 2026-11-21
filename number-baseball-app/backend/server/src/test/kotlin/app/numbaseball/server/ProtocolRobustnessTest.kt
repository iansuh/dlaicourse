package app.numbaseball.server

import app.numbaseball.core.protocol.Ack
import app.numbaseball.core.protocol.ChatEvent
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.Ping
import app.numbaseball.core.protocol.Pong
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.SetSecret
import app.numbaseball.core.protocol.Welcome
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolRobustnessTest {
    @Test fun malformedMessagesAreAnsweredAndTheConnectionStaysOpen() = serverTest {
        val p = connect(account("Mina"))
        p.expect<Welcome>()
        for (bad in listOf("", "null", "[]", "{", "{}", """{"type":"nope"}""", """{"type":ping}""", """{"type":"queue_join"}""",
            """{"type":"guess","guess":"123"}""", "﻿{\"type\":\"ping\"}", """{"type":"team_set","team":"C"}""")) {
            p.sendRaw(bad)
            assertEquals(bad, ErrorCodes.MALFORMED_MESSAGE, p.expect<ErrorMessage>().code)
        }
        p.ws.send(Frame.Binary(true, byteArrayOf(1, 2, 3))) // ignored
        p.send(Ping(rid = 1))
        assertEquals(1L, p.expect<Pong>().rid)
        p.sendRaw("""{"type":"ping","rid":2,"unknown":{"x":[1,2]}}""")
        assertEquals(2L, p.expect<Pong>().rid)
    }

    @Test fun oversizedMessages() = serverTest {
        val p = connect(account("Mina"))
        p.expect<Welcome>()
        val exact = """{"type":"ping","pad":"${"a".repeat(2048 - 24)}"}"""
        assertEquals(2048, exact.toByteArray().size)
        p.sendRaw(exact)
        p.expect<Pong>()
        p.sendRaw("""{"type":"ping","pad":"${"가".repeat(675)}"}""") // 2049 bytes
        assertEquals(ErrorCodes.MESSAGE_TOO_LARGE, p.expect<ErrorMessage>().code)
        p.send(Ping(rid = 3))
        assertEquals(3L, p.expect<Pong>().rid)
        p.sendRaw("""{"type":"ping","pad":"${"a".repeat(17_000)}"}""")
        assertEquals(1009.toShort(), p.awaitClose()?.code)
    }

    @Test fun floodingIsRateLimited() = serverTest {
        val p = connect(account("Mina"))
        p.expect<Welcome>()
        repeat(40) { p.send(Ping()) }
        p.expect<ErrorMessage> { it.code == ErrorCodes.RATE_LIMITED }
    }

    @Test fun errorsKeepStateAndOpponentSecretNeverLeaks() = serverTest {
        val a = connect(account("Mina")); a.expect<Welcome>()
        val b = connect(account("Leo")); b.expect<Welcome>()
        a.send(RoomCreate(RoomSettings(4, 1, 0)))
        val code = a.state().code!!
        b.send(RoomJoin(code))
        a.state { it.phase == RoomPhase.PLAYING }
        a.send(SetSecret("0123"))
        b.send(SetSecret("4567"))
        a.state { it.match?.myDuel?.phase == DuelPhase.GUESSING }
        assertNull(a.lastRoom!!.match!!.myDuel!!.deadline) // no timer
        for ((msg, err) in listOf(
            SetSecret("9876", rid = 1) to ErrorCodes.SECRET_ALREADY_SET,
            Guess("112", 1, rid = 2) to ErrorCodes.INVALID_GUESS,
            Guess("１２３４", 1, rid = 3) to ErrorCodes.INVALID_GUESS,
            Guess("1234", 5, rid = 4) to ErrorCodes.STALE_ROUND,
        )) {
            a.send(msg)
            assertEquals(err, a.expect<ErrorMessage> { it.rid == msg.rid }.code)
        }
        a.send(Guess("1234", 1, rid = 5))
        a.expect<Ack> { it.rid == 5L }
        a.send(Guess("5678", 1, rid = 6))
        assertEquals(ErrorCodes.ALREADY_GUESSED, a.expect<ErrorMessage> { it.rid == 6L }.code)
        // No frame sent to A before the end of the duel contains B's secret.
        a.drain()
        b.send(Guess("0123", 1))
        val end = a.state { it.phase == RoomPhase.FINISHED }
        assertEquals("4567", end.match!!.myDuel!!.opponentSecret)
    }

    @Test fun rawFramesNeverContainTheOpponentSecretBeforeTheEnd() = serverTest {
        val a = connect(account("Mina")); a.expect<Welcome>()
        val b = connect(account("Leo")); b.expect<Welcome>()
        a.send(RoomCreate(RoomSettings(5, 1, 60)))
        b.send(RoomJoin(a.state().code!!))
        a.state { it.phase == RoomPhase.PLAYING }
        b.send(SetSecret("97531"))
        a.send(SetSecret("02468"))
        a.state { it.match?.myDuel?.phase == DuelPhase.GUESSING }
        a.send(Guess("13579", 1))
        b.send(Guess("86420", 1))
        a.state { it.match?.myDuel?.round == 2 }
        val frames = generateSequence { a.raw.tryReceive().getOrNull() }.toList()
        assertTrue(frames.isNotEmpty())
        assertFalse(frames.any { it.contains("97531") })
    }

    @Test fun webPagesAndOtherEndpoints() = serverTest(testConfig(appAdsTxt = "google.com, pub-0000000000000000, DIRECT, f08c47fec0942fa0\n")) {
        assertEquals("""{"status":"ok"}""", http(HttpMethod.Get, "/health").bodyAsText())
        val terms = http(HttpMethod.Get, "/terms")
        assertEquals(HttpStatusCode.OK, terms.status)
        val termsHtml = terms.bodyAsText()
        assertTrue(termsHtml.contains("id=\"child-safety\""))
        assertTrue(termsHtml.contains("lang=\"ko\"") && termsHtml.contains("lang=\"en\""))
        assertFalse("team memo must not be published", termsHtml.contains("공개하지 마세요"))
        assertNull(terms.headers["Set-Cookie"])
        assertNotNull(terms.headers["Content-Security-Policy"])
        val privacy = http(HttpMethod.Get, "/privacy").bodyAsText()
        assertTrue(privacy.contains("<table>"))
        assertFalse(privacy.contains("공개하지 마세요"))
        val del = http(HttpMethod.Get, "/delete-account?lang=en").bodyAsText()
        assertTrue(del.contains("action=\"/delete-account\"") && del.contains("action=\"/delete-account/request\""))
        assertTrue(del.indexOf("lang=\"en\"") < del.indexOf("lang=\"ko\""))
        assertTrue(http(HttpMethod.Get, "/").bodyAsText().contains("/terms#child-safety"))
        val ads = http(HttpMethod.Get, "/app-ads.txt")
        assertEquals(HttpStatusCode.OK, ads.status)
        assertTrue(ads.headers["Content-Type"]!!.startsWith("text/plain"))
        assertTrue(ads.bodyAsText().startsWith("google.com, pub-"))
        assertEquals(HttpStatusCode.NotFound, http(HttpMethod.Get, "/api/v2/config").status)
        val redirect = http(HttpMethod.Get, "/privacy", headers = mapOf("X-Forwarded-Proto" to "http", "Host" to "nb.example.com"))
        assertEquals(HttpStatusCode.MovedPermanently, redirect.status)
        assertEquals("https://nb.example.com/privacy", redirect.headers["Location"])
    }

    @Test fun appAdsTxtIsMissingWhenUnset() = serverTest {
        assertEquals(HttpStatusCode.NotFound, http(HttpMethod.Get, "/app-ads.txt").status)
    }

    @Test fun reviewRoomPlaysAgainstABot() = serverTest(testConfig(reviewRoomCode = "REVUE")) {
        val p = connect(account("Reviewer")); p.expect<Welcome>()
        p.send(RoomJoin("revue"))
        val room = p.state { it.phase == RoomPhase.PLAYING }
        assertEquals(4, room.settings.digits)
        assertTrue(room.players.any { it.nickname == Hub.BOT_NICKNAME && it.connected })
        advance(3_000)
        assertEquals(ChatKind.PHRASE, p.expect<ChatEvent>().message.kind)
        assertEquals(Hub.BOT_TEXT, p.expect<ChatEvent>().message.text)
        p.state { it.match?.myDuel?.opponentHasSecret == true }
        p.send(SetSecret("0123"))
        p.state { it.match?.myDuel?.phase == DuelPhase.GUESSING }
        advance(4_000)
        p.state { it.match?.myDuel?.opponentGuessed == true }
        p.send(Guess("4567", 1))
        p.state { it.match?.myDuel?.round == 2 }
        // The reviewer can report and block the bot.
        val bot = room.players.first { it.nickname == Hub.BOT_NICKNAME }.id
        assertEquals(HttpStatusCode.Created, http(HttpMethod.Post, "/api/v1/reports", p.account.token, """{"targetId":"$bot","reason":"spam"}""").status)
        assertEquals(HttpStatusCode.NoContent, http(HttpMethod.Put, "/api/v1/me/blocks/$bot", p.account.token).status)
    }
}
