package app.numbaseball.data.online

import app.numbaseball.R
import app.numbaseball.core.protocol.BestHint
import app.numbaseball.core.protocol.ChatEvent
import app.numbaseball.core.protocol.ChatHistory
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.PlayerCard
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.RoomState
import app.numbaseball.core.protocol.ServerMessage
import app.numbaseball.core.protocol.Stats
import app.numbaseball.core.protocol.SystemChatCodes
import app.numbaseball.data.account.ApiResult
import app.numbaseball.testing.FakeOnlineClient
import app.numbaseball.testing.SampleData
import app.numbaseball.ui.common.errorMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protocol 1.1 additions (docs/protocol.md §3.4, §5, §7, §9.3, §11) as the app decodes them. */
@OptIn(ExperimentalCoroutinesApi::class)
class Protocol11Test {
    private fun decode(text: String): ServerMessage = ProtocolJson.decodeFromString(ServerMessage.serializer(), text)

    @Test fun systemChatLinesDecodeWithTheirSubject() {
        val message = decode(
            """{"type":"chat","roomId":"r_1","message":{"id":9,"at":1795000000000,"senderId":null,"senderNickname":null,""" +
                """"senderTag":null,"senderTeam":null,"kind":"system","text":null,"phraseId":null,"systemCode":"player_joined",""" +
                """"subjectId":"mint00000000","subjectNickname":"민트초코","subjectTag":"0912"}}""",
        ) as ChatEvent
        val line = message.message
        assertEquals(ChatKind.SYSTEM, line.kind)
        assertEquals(SystemChatCodes.PLAYER_JOINED, line.systemCode)
        assertEquals("민트초코", line.subjectNickname)
        assertEquals("0912", line.subjectTag)
        assertNull(line.senderId)
    }

    @Test fun systemLinesForDeletedSubjectsAndUnknownCodesStillDecode() {
        val history = decode(
            """{"type":"chat_history","roomId":"r_1","messages":[""" +
                """{"id":1,"at":1,"senderId":null,"senderNickname":null,"senderTag":null,"senderTeam":null,"kind":"system",""" +
                """"text":null,"phraseId":null,"systemCode":"player_left","subjectId":null,"subjectNickname":null,"subjectTag":null},""" +
                """{"id":2,"at":2,"senderId":null,"senderNickname":null,"senderTag":null,"senderTeam":null,"kind":"system",""" +
                """"text":null,"phraseId":null,"systemCode":"host_changed_in_a_future_version"},""" +
                """{"id":3,"at":3,"senderId":null,"senderNickname":null,"senderTag":null,"senderTeam":null,"kind":"text","text":"hi","phraseId":null}]}""",
        ) as ChatHistory
        assertEquals(listOf(ChatKind.SYSTEM, ChatKind.SYSTEM, ChatKind.TEXT), history.messages.map { it.kind })
        assertNull(history.messages[0].subjectNickname)
        assertEquals("host_changed_in_a_future_version", history.messages[1].systemCode)
        // A text line without a sender is still a deleted player's line, not a system line.
        assertNull(history.messages[2].systemCode)
    }

    @Test fun systemLinesReachTheSessionChat() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeOnlineClient()
        val session = OnlineSession(client, backgroundScope, clock = { 0 })
        client.receive(RoomState(SampleData.quickRoom()))
        client.receive(
            decode(
                """{"type":"chat","roomId":"${SampleData.quickRoom().id}","message":{"id":4,"at":1,"senderId":null,"senderNickname":null,""" +
                    """"senderTag":null,"senderTeam":null,"kind":"system","text":null,"phraseId":null,"systemCode":"player_left",""" +
                    """"subjectNickname":"Kestrel","subjectTag":"2048"}}""",
            ),
        )
        assertEquals(ChatKind.SYSTEM, session.chat.value.single().kind)
    }

    @Test fun duelSummariesCarryBestHintsAndOldSnapshotsStillDecode() {
        val room = ProtocolJson.encodeToString(ServerMessage.serializer(), RoomState(SampleData.quickRoom()))
        val nulls = "\"bestHintA\":null,\"bestHintB\":null"
        assertTrue(room, room.contains(nulls))
        // A 1.0 server sends no hint fields at all.
        val old = decode(room.replace(",$nulls", "")) as RoomState
        assertNull(old.room.match!!.duels.single().bestHintA)
        val withHints = decode(room.replace(nulls, "\"bestHintA\":{\"strikes\":2,\"balls\":1},\"bestHintB\":{\"strikes\":0,\"balls\":0}")) as RoomState
        val duel = withHints.room.match!!.duels.single()
        assertEquals(BestHint(2, 1), duel.bestHintA)
        assertEquals(BestHint(0, 0), duel.bestHintB)
    }

    @Test fun tooManyAttemptsOnRoomJoinHasARetryTimeAndAFriendlyMessage() {
        val error = decode("""{"type":"error","code":"too_many_attempts","message":"slow down","rid":3,"retryAfterMs":30000}""") as ErrorMessage
        assertEquals(ErrorCodes.TOO_MANY_ATTEMPTS, error.code)
        assertEquals(30_000L, error.retryAfterMs)
        assertEquals(R.string.error_too_many_attempts, errorMessage(error.code))
    }

    @Test fun playerCardSendsTheHandleEncodedAndParsesTheRecord() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = RestApi(OkHttpClient(), ServerEndpoints(server.url("/").toString()), clientVersion = "1.0")
            server.enqueue(MockResponse.Builder().code(200).body("""{"id":"kestrel00000","nickname":"Kestrel","tag":"2048","stats":{"wins":31,"losses":27,"draws":4}}""").build())
            val result = api.playerCard("t0k3n", "Kestrel#2048")
            assertEquals(PlayerCard("kestrel00000", "Kestrel", "2048", Stats(31, 27, 4)), (result as ApiResult.Success).value)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/players/Kestrel%232048", request.url.encodedPath)
            assertEquals("Bearer t0k3n", request.headers["Authorization"])

            server.enqueue(MockResponse.Builder().code(404).body("""{"code":"not_found","message":"no"}""").build())
            assertEquals(ApiResult.Failure(404, ErrorCodes.NOT_FOUND, null), api.playerCard("t0k3n", "blocked00000"))
            server.enqueue(MockResponse.Builder().code(429).body("""{"code":"rate_limited","message":"wait","retryAfterSeconds":12}""").build())
            assertEquals(ApiResult.Failure(429, ErrorCodes.RATE_LIMITED, 12), api.playerCard("t0k3n", "x"))
        } finally {
            server.close()
        }
    }

    @Test fun offlinePlayersKeepTheirFirstSightingAcrossSnapshots() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeOnlineClient()
        val session = OnlineSession(client, backgroundScope, clock = { 0 })
        val room = SampleData.quickRoom()
        val opponent = room.players.first { it.id != room.youId }
        val offline = room.copy(players = room.players.map { if (it.id == opponent.id) it.copy(connected = false) else it })
        client.receive(RoomState(offline.copy(version = 4, serverTime = 1_000)))
        client.receive(RoomState(offline.copy(version = 5, serverTime = 20_000)))
        assertEquals(mapOf(opponent.id to 1_000L), session.offlineSince.value)
        client.receive(RoomState(room.copy(version = 6, serverTime = 30_000)))
        assertTrue(session.offlineSince.value.isEmpty())
    }
}
