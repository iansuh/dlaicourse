package app.numbaseball.server

import app.numbaseball.core.protocol.AccountDto
import app.numbaseball.core.protocol.AdminReportList
import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.Chat
import app.numbaseball.core.protocol.ChatEvent
import app.numbaseball.core.protocol.ChatSenderDeleted
import app.numbaseball.core.protocol.CodeResponse
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.ErrorResponse
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.ServerInfo
import app.numbaseball.core.protocol.Welcome
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

class AccountsTest {
    private val admin = "admin-token-for-tests-0123456789"
    private suspend fun HttpResponse.code() = ProtocolJson.decodeFromString(ErrorResponse.serializer(), bodyAsText()).code

    @Test fun createRenameAndTags() = serverTest {
        val a = account("Mina", acceptTerms = false)
        assertEquals("Mina", a.account.nickname)
        assertEquals(4, a.account.tag.length)
        assertEquals(43, a.token.length)
        val b = account("mina", acceptTerms = false)
        assertNotEquals("same nickname gets a different tag", a.account.tag, b.account.tag)

        val renamed = ProtocolJson.decodeFromString(AccountDto.serializer(),
            http(HttpMethod.Patch, "/api/v1/me", a.token, """{"nickname":"Minah"}""").bodyAsText())
        assertEquals("Minah", renamed.nickname)
        assertEquals("tag kept when free", a.account.tag, renamed.tag)

        assertEquals(ErrorCodes.INVALID_NICKNAME, http(HttpMethod.Patch, "/api/v1/me", a.token, """{"nickname":"a"}""").code())
        assertEquals(ErrorCodes.NICKNAME_PROFANITY, http(HttpMethod.Post, "/api/v1/accounts", body = """{"nickname":"fuck"}""").code())
        assertEquals(ErrorCodes.INVALID_NICKNAME, http(HttpMethod.Post, "/api/v1/accounts", body = """{"nickname":"admin"}""").code())
        assertEquals(ErrorCodes.BAD_REQUEST, http(HttpMethod.Post, "/api/v1/accounts", body = """{}""").code())
        assertEquals(ErrorCodes.BAD_REQUEST, http(HttpMethod.Post, "/api/v1/accounts", body = "nickname=Mina",
            type = ContentType.Application.FormUrlEncoded).code())
        val big = http(HttpMethod.Post, "/api/v1/accounts", body = """{"nickname":"${"a".repeat(5000)}"}""")
        assertEquals(HttpStatusCode.PayloadTooLarge, big.status)
        assertEquals(ErrorCodes.MESSAGE_TOO_LARGE, big.code())

        // Only the hash of the token is stored.
        DriverManager.getConnection("jdbc:sqlite:${services.config.dataDir}/numbaseball.db").use { c ->
            c.createStatement().executeQuery("SELECT hash FROM tokens").use { rs ->
                while (rs.next()) assertFalse(rs.getString(1) == a.token)
            }
        }
    }

    @Test fun accountCreationIsRateLimitedPerIp() = serverTest {
        val ip = mapOf("X-Forwarded-For" to "203.0.113.9")
        repeat(10) { assertEquals(HttpStatusCode.Created, http(HttpMethod.Post, "/api/v1/accounts", body = """{"nickname":"Bot$it"}""", headers = ip).status) }
        val r = http(HttpMethod.Post, "/api/v1/accounts", body = """{"nickname":"Bot99"}""", headers = ip)
        assertEquals(HttpStatusCode.TooManyRequests, r.status)
        assertEquals(ErrorCodes.RATE_LIMITED, r.code())
        assertNotNull(r.headers["Retry-After"])
    }

    @Test fun authFailures() = serverTest {
        assertEquals(HttpStatusCode.Unauthorized, http(HttpMethod.Get, "/api/v1/me").status)
        assertEquals(HttpStatusCode.Unauthorized, http(HttpMethod.Get, "/api/v1/me", "garbage").status)
        assertEquals(ErrorCodes.UNAUTHORIZED, http(HttpMethod.Get, "/api/v1/me", headers = mapOf("Authorization" to "Basic dXNlcjpwYXNz")).code())
        assertEquals(HttpStatusCode.Unauthorized, http(HttpMethod.Get, "/api/v1/me", headers = mapOf("Authorization" to "Bearer ")).status)
        val ws = runCatching { connect(TestAccount("not-a-token", AccountDto("x", "x", "0001", app.numbaseball.core.protocol.Stats(), 0))) }
        assertTrue(ws.isFailure)
    }

    @Test fun termsAndAgeMustBeAcceptedBeforeOnlinePlay() = serverTest {
        val a = account("Mina", acceptTerms = false)
        val info = ProtocolJson.decodeFromString(ServerInfo.serializer(), http(HttpMethod.Get, "/api/v1/config").bodyAsText())
        assertEquals(services.accounts.currentTermsVersion, info.termsVersion)
        assertEquals("2026-11-v1", info.termsVersion) // read from the bundled docs/play/TERMS_OF_USE.md
        assertFalse(info.quickMatchFreeChat)
        assertEquals(14, info.minimumAge)

        val p = connect(a); p.expect<Welcome>()
        p.send(QueueJoin(3, rid = 1))
        assertEquals(ErrorCodes.TERMS_REQUIRED, p.expect<ErrorMessage> { it.rid == 1L }.code)
        p.send(RoomCreate(RoomSettings(3, 1, 60), rid = 2))
        assertEquals(ErrorCodes.TERMS_REQUIRED, p.expect<ErrorMessage> { it.rid == 2L }.code)

        assertEquals(ErrorCodes.AGE_CONFIRMATION_REQUIRED,
            http(HttpMethod.Put, "/api/v1/me/terms", a.token, """{"version":"${info.termsVersion}"}""").code())
        assertEquals(ErrorCodes.TERMS_OUTDATED,
            http(HttpMethod.Put, "/api/v1/me/terms", a.token, """{"version":"2020-01","ageConfirmed":true}""").code())
        val ok = http(HttpMethod.Put, "/api/v1/me/terms", a.token, """{"version":"${info.termsVersion}","ageConfirmed":true}""")
        val dto = ProtocolJson.decodeFromString(AccountDto.serializer(), ok.bodyAsText())
        assertEquals(info.termsVersion, dto.termsVersion)
        assertNotNull(dto.termsAcceptedAt)
        assertNotNull(dto.ageConfirmedAt)
        p.send(QueueJoin(3, rid = 3))
        p.expect<app.numbaseball.core.protocol.Ack> { it.rid == 3L }
    }

    @Test fun transferMovesTheAccountAndRevokesTheOldToken() = serverTest {
        val a = account("Mina")
        val old = connect(a); old.expect<Welcome>()
        val code = ProtocolJson.decodeFromString(CodeResponse.serializer(), http(HttpMethod.Post, "/api/v1/me/transfer-code", a.token).bodyAsText())
        assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}").matches(code.code))
        assertEquals(clock.now() + 600_000, code.expiresAt)

        assertEquals(ErrorCodes.INVALID_CODE, http(HttpMethod.Post, "/api/v1/transfer", body = """{"code":"AAAA-AAAA-AAAA"}""").code())
        val r = http(HttpMethod.Post, "/api/v1/transfer", body = """{"code":"${code.code.lowercase().replace("-", " ")}"}""")
        assertEquals(r.bodyAsText(), HttpStatusCode.OK, r.status)
        val moved = ProtocolJson.decodeFromString(AuthResponse.serializer(), r.bodyAsText())
        assertEquals(a.id, moved.account.id)
        assertNotEquals(a.token, moved.token)
        assertEquals(HttpStatusCode.Unauthorized, http(HttpMethod.Get, "/api/v1/me", a.token).status)
        assertEquals(HttpStatusCode.OK, http(HttpMethod.Get, "/api/v1/me", moved.token).status)
        assertEquals(4000.toShort(), old.awaitClose()?.code)
        // Single use.
        assertEquals(ErrorCodes.INVALID_CODE, http(HttpMethod.Post, "/api/v1/transfer", body = """{"code":"${code.code}"}""").code())
    }

    @Test fun transferCodesExpireAndAreAttemptLimited() = serverTest {
        val a = account("Mina")
        val code = ProtocolJson.decodeFromString(CodeResponse.serializer(), http(HttpMethod.Post, "/api/v1/me/transfer-code", a.token).bodyAsText()).code
        clock.advance(600_001)
        assertEquals(ErrorCodes.INVALID_CODE, http(HttpMethod.Post, "/api/v1/transfer", body = """{"code":"$code"}""").code())

        // Five wrong secrets for the same code kill it, even from different addresses.
        val fresh = ProtocolJson.decodeFromString(CodeResponse.serializer(), http(HttpMethod.Post, "/api/v1/me/transfer-code", a.token).bodyAsText()).code
        val wrong = fresh.substring(0, 5) + (if (fresh[5] == 'A') 'B' else 'A') + fresh.substring(6)
        repeat(5) { i ->
            http(HttpMethod.Post, "/api/v1/transfer", body = """{"code":"$wrong"}""", headers = mapOf("X-Forwarded-For" to "198.51.100.$i"))
        }
        assertEquals(ErrorCodes.INVALID_CODE, http(HttpMethod.Post, "/api/v1/transfer", body = """{"code":"$fresh"}""",
            headers = mapOf("X-Forwarded-For" to "198.51.100.99")).code())
        // Ten failures from one address lock that address out.
        val ip = mapOf("X-Forwarded-For" to "192.0.2.1")
        repeat(10) { http(HttpMethod.Post, "/api/v1/transfer", body = """{"code":"ZZZZ-ZZZZ-ZZZZ"}""", headers = ip) }
        assertEquals(ErrorCodes.TOO_MANY_ATTEMPTS, http(HttpMethod.Post, "/api/v1/transfer", body = """{"code":"ZZZZ-ZZZZ-ZZZZ"}""", headers = ip).code())
    }

    @Test fun deleteRemovesEverythingAndMasksLiveChat() = serverTest {
        val a = account("Mina")
        val b = account("Leo")
        val pa = connect(a); pa.expect<Welcome>()
        val pb = connect(b); pb.expect<Welcome>()
        pa.send(RoomCreate(RoomSettings(3, 2, 60)))
        val code = pa.state().code!!
        pb.send(RoomJoin(code))
        pb.state { s -> s.players.size == 2 }
        pb.send(Chat("hello"))
        val m = pa.expect<ChatEvent>().message
        http(HttpMethod.Put, "/api/v1/me/blocks/${a.id}", b.token)
        http(HttpMethod.Post, "/api/v1/reports", a.token, """{"targetId":"${b.id}","reason":"spam","roomId":"${pa.lastRoom!!.id}","messageId":${m.id}}""")
        http(HttpMethod.Post, "/api/v1/reports", b.token, """{"targetId":"${a.id}","reason":"spam"}""")

        assertEquals(HttpStatusCode.NoContent, http(HttpMethod.Delete, "/api/v1/me", b.token).status)
        assertEquals(4004.toShort(), pb.awaitClose()?.code)
        assertEquals(b.id, pa.expect<ChatSenderDeleted>().senderId)
        pa.state { s -> s.players.none { it.id == b.id } }
        assertEquals(HttpStatusCode.Unauthorized, http(HttpMethod.Get, "/api/v1/me", b.token).status)
        // Reports about b are gone; b's report about a stays without a reporter id.
        val reports = ProtocolJson.decodeFromString(AdminReportList.serializer(), http(HttpMethod.Get, "/admin/v1/reports", admin).bodyAsText()).reports
        assertEquals(1, reports.size)
        assertNull(reports.single().reporterId)
        assertEquals(a.id, reports.single().targetId)
        assertTrue(services.accounts.listBlocks(a.id).isEmpty())
        assertNull(services.accounts.get(b.id))
    }

    @Test fun deletionAnonymisesSnapshotsOfOtherReports() = serverTest {
        val a = account("Mina")
        val b = account("Leo")
        val c = account("Kai")
        val ps = listOf(a, b, c).map { connect(it).also { p -> p.expect<Welcome>() } }
        ps[0].send(RoomCreate(RoomSettings(3, 2, 60)))
        val code = ps[0].state().code!!
        ps.drop(1).forEach { p -> p.send(RoomJoin(code)); p.state { s -> s.players.any { it.id == p.id } } }
        ps[1].send(Chat("i am leo"))
        ps[2].send(Chat("i am kai"))
        val kai = ps[0].expect<ChatEvent> { it.message.text == "i am kai" }.message
        http(HttpMethod.Post, "/api/v1/reports", a.token, """{"targetId":"${c.id}","reason":"spam","roomId":"${ps[0].lastRoom!!.id}","messageId":${kai.id}}""")
        services.deleteAccount(b.id)
        val snap = ProtocolJson.decodeFromString(AdminReportList.serializer(), http(HttpMethod.Get, "/admin/v1/reports", admin).bodyAsText())
            .reports.single().snapshot!!
        val leoLine = snap.recent.first { it.text == "i am leo" }
        assertNull(leoLine.senderId)
        assertNull(leoLine.senderNickname)
        assertEquals(c.id, snap.message!!.senderId)
    }

    @Test fun resolvedReportsArePurgedAfter180Days() = serverTest {
        val a = account("Mina")
        val b = account("Leo")
        http(HttpMethod.Post, "/api/v1/reports", a.token, """{"targetId":"${b.id}","reason":"spam"}""")
        http(HttpMethod.Post, "/api/v1/reports", b.token, """{"targetId":"${a.id}","reason":"spam"}""")
        val first = services.accounts.listReports("all").last().id
        http(HttpMethod.Post, "/admin/v1/reports/$first/resolve", admin, """{"resolution":"no action"}""")
        clock.advance(179L * 24 * 3_600_000)
        services.runMaintenance()
        assertEquals(2, services.accounts.listReports("all").size)
        clock.advance(2L * 24 * 3_600_000)
        services.runMaintenance()
        assertEquals(listOf("open"), services.accounts.listReports("all").map { it.status })
    }

    @Test fun deletionCodeWebForm() = serverTest {
        val a = account("Mina")
        val code = ProtocolJson.decodeFromString(CodeResponse.serializer(), http(HttpMethod.Post, "/api/v1/me/deletion-code", a.token).bodyAsText())
        assertEquals(clock.now() + 24 * 3_600_000L, code.expiresAt)
        val form = ContentType.Application.FormUrlEncoded
        assertEquals(HttpStatusCode.BadRequest, http(HttpMethod.Post, "/delete-account", body = "code=${code.code}", type = form).status)
        assertEquals(HttpStatusCode.OK, http(HttpMethod.Get, "/api/v1/me", a.token).status)
        assertEquals(HttpStatusCode.BadRequest, http(HttpMethod.Post, "/delete-account", body = "code=AAAA-AAAA-AAAA&confirm=yes", type = form).status)
        val ok = http(HttpMethod.Post, "/delete-account", body = "code=${code.code}&confirm=yes", type = form)
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue(ok.bodyAsText().contains("deleted"))
        assertEquals(HttpStatusCode.Unauthorized, http(HttpMethod.Get, "/api/v1/me", a.token).status)
    }

    @Test fun deletionRequestWithoutTheApp() = serverTest {
        val a = account("Mina")
        val handle = "${a.account.nickname}#${a.account.tag}"
        val form = ContentType.Application.FormUrlEncoded
        val existing = http(HttpMethod.Post, "/delete-account/request", body = "handle=${handle.replace("#", "%23")}&confirm=yes", type = form)
        val missing = http(HttpMethod.Post, "/delete-account/request", body = "handle=Nobody%230001&confirm=yes", type = form)
        assertEquals(HttpStatusCode.OK, existing.status)
        assertEquals("identical answer whether or not the account exists", existing.bodyAsText(), missing.bodyAsText())
        assertNotNull(services.accounts.get(a.id)!!.deletionRequestedAt)

        // Using the account cancels the request and leaves a notice for the app.
        val dto = me(a.token)
        assertNotNull(dto.deletionRequestedAt)
        assertNull(services.accounts.get(a.id)!!.deletionRequestedAt)
        assertEquals(HttpStatusCode.NoContent, http(HttpMethod.Delete, "/api/v1/me/deletion-request", a.token).status)
        assertNull(me(a.token).deletionRequestedAt)

        // A request that nobody cancels deletes the account after 30 days.
        http(HttpMethod.Post, "/delete-account/request", body = "handle=${handle.replace("#", "%23")}&confirm=yes", type = form)
        clock.advance(29L * 24 * 3_600_000)
        services.runMaintenance()
        assertNotNull(services.accounts.get(a.id))
        clock.advance(2L * 24 * 3_600_000)
        services.runMaintenance()
        assertNull(services.accounts.get(a.id))
        assertEquals(HttpStatusCode.Unauthorized, http(HttpMethod.Get, "/api/v1/me", a.token).status)
    }

    @Test fun deletionRequestsAreRateLimitedPerIp() = serverTest {
        val form = ContentType.Application.FormUrlEncoded
        val ip = mapOf("X-Forwarded-For" to "192.0.2.77")
        repeat(10) { http(HttpMethod.Post, "/delete-account/request", body = "handle=X%230001&confirm=yes", type = form, headers = ip) }
        assertEquals(HttpStatusCode.TooManyRequests,
            http(HttpMethod.Post, "/delete-account/request", body = "handle=X%230001&confirm=yes", type = form, headers = ip).status)
    }
}
