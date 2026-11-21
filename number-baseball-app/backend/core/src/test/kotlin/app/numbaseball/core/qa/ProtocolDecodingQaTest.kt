package app.numbaseball.core.qa

import app.numbaseball.core.protocol.AccountDto
import app.numbaseball.core.protocol.ChatEvent
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.ChatMessage
import app.numbaseball.core.protocol.ClientMessage
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.DuelResult
import app.numbaseball.core.protocol.DuelSummary
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.MatchResult
import app.numbaseball.core.protocol.MatchView
import app.numbaseball.core.protocol.MyDuelView
import app.numbaseball.core.protocol.Ping
import app.numbaseball.core.protocol.PlayerView
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoomState
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.protocol.Score
import app.numbaseball.core.protocol.ServerMessage
import app.numbaseball.core.protocol.Stats
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.protocol.Welcome
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AC-PROTO-*: the shared protocol classes and ProtocolJson against protocol_frames.tsv and docs/protocol.md sections 5-7.
 * The server decodes with the same ProtocolJson, so a frame that decodes here reaches the game logic there.
 */
class ProtocolDecodingQaTest {

    private fun decode(frame: String): ClientMessage = ProtocolJson.decodeFromString(ClientMessage.serializer(), frame)

    @Test fun acProto01_malformedFramesNeverDecode() {
        val failures = LinkedHashMap<String, String>()
        val rows = QaCorpus.load("protocol_frames.tsv").filter { it["layer"] == "DECODE" }
        for (row in rows) {
            val frame = row.text("frame")
            try {
                val m = decode(frame)
                failures[row.id] = "decoded to $m"
            } catch (e: SerializationException) {
                // expected: server answers malformed_message
            } catch (e: IllegalArgumentException) {
                // expected as well (kotlinx reports some problems this way)
            } catch (e: Throwable) {
                failures[row.id] = "unexpected ${e::class.simpleName}: ${e.message}"
            }
        }
        KnownDefects.check("ProtocolJson on DECODE rows", rows.map { it.id }, failures)
    }

    @Test fun acProto02_serverRowsDecodeSoTheServerCanGiveTheSpecificError() {
        val failures = LinkedHashMap<String, String>()
        val rows = QaCorpus.load("protocol_frames.tsv").filter { it["layer"] == "SERVER" || it.id == "Z-001" }
        for (row in rows) {
            try {
                decode(row.text("frame"))
            } catch (e: Throwable) {
                failures[row.id] = "${e::class.simpleName}: ${e.message?.take(200)}"
            }
        }
        KnownDefects.check("ProtocolJson on SERVER rows", rows.map { it.id }, failures)
    }

    @Test fun acProto03_toleratedFramesNeverCrashTheDecoder() {
        for (row in QaCorpus.load("protocol_frames.tsv").filter { it["layer"] == "TOLERATED" }) {
            val outcome = try {
                "decoded: " + decode(row.text("frame"))
            } catch (e: SerializationException) {
                "malformed_message"
            } catch (e: IllegalArgumentException) {
                "malformed_message"
            }
            println("${row.id}: $outcome")
        }
        // The discriminator does not have to come first (T-003).
        assertEquals(Ping(5), decode("{\"rid\":5,\"type\":\"ping\"}"))
    }

    @Test fun acProto04_messageTypeNamesMatchTheProtocolTables() {
        val settings = RoomSettings(3, 1, 60)
        val client: Map<String, ClientMessage> = mapOf(
            "ping" to Ping(), "resync" to app.numbaseball.core.protocol.Resync(), "queue_join" to QueueJoin(3),
            "queue_leave" to app.numbaseball.core.protocol.QueueLeave(), "room_create" to RoomCreate(settings),
            "room_join" to app.numbaseball.core.protocol.RoomJoin("ABCDE"), "room_leave" to app.numbaseball.core.protocol.RoomLeave(),
            "room_settings" to app.numbaseball.core.protocol.RoomSettingsUpdate(settings), "team_set" to app.numbaseball.core.protocol.TeamSet(Team.B),
            "room_reset" to app.numbaseball.core.protocol.RoomReset(), "secret" to app.numbaseball.core.protocol.SetSecret("012"),
            "guess" to Guess("012", 1), "forfeit" to app.numbaseball.core.protocol.Forfeit(), "chat" to app.numbaseball.core.protocol.Chat("hi"),
            "phrase" to app.numbaseball.core.protocol.Phrase("hello"),
        )
        for ((name, m) in client) {
            val json = ProtocolJson.encodeToJsonElement(ClientMessage.serializer(), m).jsonObject
            assertEquals("type of ${m::class.simpleName}", "\"$name\"", json["type"].toString())
            assertTrue("$name must carry rid (explicit null)", "rid" in json)
        }
        val account = AccountDto("me0000000001", "Mina", "0420", Stats(), 0)
        val msg = ChatMessage(1, 2, "a", "b", "0001", Team.A, ChatKind.PHRASE, null, "hello")
        val server: Map<String, ServerMessage> = mapOf(
            "welcome" to Welcome(1, 0, account, null), "pong" to app.numbaseball.core.protocol.Pong(null, 0),
            "ack" to app.numbaseball.core.protocol.Ack(1), "error" to ErrorMessage("x", "y"),
            "queued" to app.numbaseball.core.protocol.Queued(3, 0), "room_state" to RoomState(sampleRoom(false)),
            "room_left" to app.numbaseball.core.protocol.RoomLeft("r", app.numbaseball.core.protocol.LeaveReason.LEFT),
            "chat" to ChatEvent("r", msg), "chat_history" to app.numbaseball.core.protocol.ChatHistory("r", listOf(msg)),
            "chat_sender_deleted" to app.numbaseball.core.protocol.ChatSenderDeleted("r", "a"),
        )
        for ((name, m) in server) {
            val json = ProtocolJson.encodeToJsonElement(ServerMessage.serializer(), m).jsonObject
            assertEquals("type of ${m::class.simpleName}", "\"$name\"", json["type"].toString())
        }
    }

    private fun sampleRoom(revealed: Boolean): RoomView {
        val duel = MyDuelView(
            index = 0, myTeam = Team.A, opponentId = "opp000000001", phase = if (revealed) DuelPhase.FINISHED else DuelPhase.GUESSING,
            round = 2, deadline = if (revealed) null else 1_795_000_065_000, mySecret = "042", opponentHasSecret = true,
            myPendingGuess = null, opponentGuessed = false,
            myRounds = listOf(RoundEntry(1, "123", 0, 1)), opponentRounds = listOf(RoundEntry(1, null, 0, 0)),
            myMisses = 0, opponentMisses = 1, result = if (revealed) DuelResult(Team.A, EndReason.SOLVED) else null,
            opponentSecret = if (revealed) "719" else null,
        )
        return RoomView(
            id = "r_1", kind = RoomKind.QUICK, code = null, phase = RoomPhase.PLAYING, settings = RoomSettings(3, 1, 60), hostId = null,
            youId = "me0000000001", players = listOf(PlayerView("me0000000001", "Mina", "0420", Team.A, true, false)),
            match = MatchView(1, listOf(DuelSummary(0, "me0000000001", "opp000000001", duel.phase, 2, duel.result)), Score(0, 0),
                if (revealed) MatchResult(Team.A, false) else null, duel),
            version = 3, serverTime = 1_795_000_000_200,
        )
    }

    @Test fun acProto05_roomSnapshotUsesTheDocumentedFieldNames() {
        val json = ProtocolJson.encodeToJsonElement(ServerMessage.serializer(), RoomState(sampleRoom(false))).jsonObject
        assertEquals("room_state", json["type"].toString().trim('"'))
        val room = json["room"]!!.jsonObject
        assertEquals(setOf("id", "kind", "code", "phase", "settings", "hostId", "youId", "players", "match", "version", "serverTime"), room.keys)
        val match = room["match"]!!.jsonObject
        assertEquals(setOf("number", "duels", "score", "result", "myDuel"), match.keys)
        val my = match["myDuel"]!!.jsonObject
        assertEquals(setOf("index", "myTeam", "opponentId", "phase", "round", "deadline", "mySecret", "opponentHasSecret", "myPendingGuess",
            "opponentGuessed", "myRounds", "opponentRounds", "myMisses", "opponentMisses", "result", "opponentSecret"), my.keys)
        assertEquals(setOf("index", "playerA", "playerB", "phase", "round", "result"), (match["duels"]!! as kotlinx.serialization.json.JsonArray)[0].jsonObject.keys)
        // explicitNulls: an unrevealed secret is sent as null, never omitted and never filled.
        assertEquals("null", my["opponentSecret"].toString())
    }

    @Test fun acProto06_roundTripOfRepresentativeMessages() {
        val client: List<ClientMessage> = listOf(Ping(1), QueueJoin(3, 2), RoomCreate(RoomSettings(4, 2, 30), 3), Guess("0123", 1, 4))
        for (m in client) assertEquals(m, decode(ProtocolJson.encodeToString(ClientMessage.serializer(), m)))
        val account = AccountDto("me0000000001", "Mina", "0420", Stats(1, 2, 3), 1_795_000_000_000)
        val server: List<ServerMessage> = listOf(
            Welcome(1, 1L, account, null), ErrorMessage("invalid_guess", "x", 5, null), RoomState(sampleRoom(true)),
            ChatEvent("r_1", ChatMessage(1, 2, null, null, null, null, ChatKind.TEXT, "hi", null)),
        )
        for (m in server) {
            val text = ProtocolJson.encodeToString(ServerMessage.serializer(), m)
            assertEquals(m, ProtocolJson.decodeFromString(ServerMessage.serializer(), text))
        }
    }

    @Test fun acProto07_unknownFieldsAndTypesAreToleratedByClients() {
        // protocol 1: clients ignore unknown fields; a future server field must not break today's app.
        val future = "{\"type\":\"pong\",\"rid\":1,\"serverTime\":5,\"newField\":{\"a\":[1]}}"
        val decoded = ProtocolJson.decodeFromString(ServerMessage.serializer(), future)
        assertEquals(app.numbaseball.core.protocol.Pong(1, 5), decoded)
        // An unknown server type throws; the app must catch it and ignore the frame (AC-PROTO-07 on Android).
        assertTrue(runCatching { ProtocolJson.decodeFromString(ServerMessage.serializer(), "{\"type\":\"future_thing\"}") }.isFailure)
        val parsed: JsonObject = ProtocolJson.parseToJsonElement("{\"type\":\"future_thing\"}").jsonObject
        assertNull(parsed["rid"])
    }
}
