package app.numbaseball.core.protocol

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolJsonTest {
    private val json = ProtocolJson

    @Test fun clientMessagesUseDocumentedTypeNames() {
        val samples: Map<ClientMessage, String> = mapOf(
            Ping(1) to "ping", Resync() to "resync", QueueJoin(3) to "queue_join", QueueLeave() to "queue_leave",
            RoomCreate(RoomSettings(4, 2, 60)) to "room_create", RoomJoin("ABCDE") to "room_join", RoomLeave() to "room_leave",
            RoomSettingsUpdate(RoomSettings(3, 1, 0)) to "room_settings", TeamSet(Team.B) to "team_set", RoomReset() to "room_reset",
            SetSecret("123") to "secret", Guess("456", 1) to "guess", Forfeit() to "forfeit", Chat("hi") to "chat",
            Phrase("hello") to "phrase",
        )
        for ((msg, type) in samples) {
            val text = json.encodeToString(ClientMessage.serializer(), msg)
            assertEquals(type, json.parseToJsonElement(text).jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals(msg, json.decodeFromString(ClientMessage.serializer(), text))
        }
    }

    @Test fun decodesHandWrittenClientJsonAndIgnoresUnknownFields() {
        val m = json.decodeFromString(ClientMessage.serializer(), """{"type":"guess","guess":"012","round":3,"rid":9,"extra":true}""")
        assertEquals(Guess("012", 3, 9), m)
        val t = json.decodeFromString(ClientMessage.serializer(), """{"type":"team_set","team":"B"}""")
        assertEquals(TeamSet(Team.B), t)
    }

    @Test fun serverMessagesRoundTrip() {
        val room = RoomView(
            id = "r1", kind = RoomKind.PRIVATE, code = "ABCDE", phase = RoomPhase.PLAYING,
            settings = RoomSettings(3, 1, 60), hostId = "p1", youId = "p1",
            players = listOf(PlayerView("p1", "Mina", "0420", Team.A, connected = true, left = false)),
            match = MatchView(
                number = 1,
                duels = listOf(DuelSummary(0, "p1", "p2", DuelPhase.FINISHED, 4, DuelResult(null, EndReason.BOTH_SOLVED))),
                score = Score(0, 0), result = MatchResult(null, false),
                myDuel = MyDuelView(
                    0, Team.A, "p2", DuelPhase.FINISHED, 4, null, "123", true, null, false,
                    listOf(RoundEntry(1, null, 0, 0)), emptyList(), 0, 0, DuelResult(null, EndReason.BOTH_SOLVED), "456",
                ),
            ),
            version = 7, serverTime = 1000,
        )
        val msgs = listOf(
            Welcome(PROTOCOL_VERSION, 1, AccountDto("p1", "Mina", "0420", Stats(1, 2, 3), 5), null),
            RoomState(room), Ack(1), ErrorMessage(ErrorCodes.ROOM_FULL, "full", 2),
            ChatEvent("r1", ChatMessage(1, 2, "p1", "Mina", "0420", Team.A, ChatKind.PHRASE, null, "hello")),
            RoomLeft("r1", LeaveReason.INACTIVE), ChatSenderDeleted("r1", "p2"), Queued(4, 9), Pong(null, 3),
            ChatHistory("r1", emptyList()),
        )
        for (m in msgs) {
            val text = json.encodeToString(ServerMessage.serializer(), m)
            assertEquals(m, json.decodeFromString(ServerMessage.serializer(), text))
        }
        val text = json.encodeToString(ServerMessage.serializer(), RoomState(room))
        assertTrue(text, text.contains("\"type\":\"room_state\"") && text.contains("\"phase\":\"playing\"") &&
            text.contains("\"reason\":\"both_solved\"") && text.contains("\"winner\":null") && text.contains("\"team\":\"A\""))
    }
}
