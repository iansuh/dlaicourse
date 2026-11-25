package app.numbaseball.server.qa

import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.SetSecret
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Reusable black-box setups for the server scenario tests (protocol sections 5, 7, 8). */
class QaPlayer(val auth: AuthResponse, val inst: QaInstance = QaServer.main) : AutoCloseable {
    val ws: QaWs = QaWs.connect(auth.token, inst)
    val id: String get() = auth.account.id
    var room: RoomView? = null
        private set

    init {
        ws.awaitType("welcome")
    }

    fun awaitRoom(timeoutMs: Long = 5_000, predicate: (RoomView) -> Boolean = { true }): RoomView {
        val j = ws.await(timeoutMs, "room_state") { it.type == "room_state" && predicate(roomOf(it)) }
        return roomOf(j).also { room = it }
    }

    /** Asks for a snapshot (resync) and returns it. */
    fun snapshot(): RoomView {
        val rid = ws.nextRid()
        ws.sendRaw("{\"type\":\"resync\",\"rid\":$rid}")
        var last: JsonObject? = null
        ws.await(5_000, "ack of resync $rid") {
            if (it.type == "room_state") last = it
            it.type == "ack" && it.str("rid") == rid.toString()
        }
        return roomOf(last ?: throw AssertionError("resync answered no room_state")).also { room = it }
    }

    /** The player's duel phase name in the latest snapshot ("CHOOSING", "GUESSING", "FINISHED") or null. */
    val duelPhase: String? get() = room?.match?.myDuel?.phase?.name

    override fun close() = ws.close()

    companion object {
        fun roomOf(j: JsonObject): RoomView = ProtocolJson.decodeFromJsonElement(RoomView.serializer(), j["room"]!!.jsonObject)
        fun fresh(prefix: String = "Qa", inst: QaInstance = QaServer.main, acceptTerms: Boolean = true) =
            QaPlayer(inst.newAccount(prefix, acceptTerms), inst)
    }
}

/** A started private match: [players] in join order (host first); teams are read from the room snapshot. */
class QaTeamRoom(val code: String, val players: List<QaPlayer>) : AutoCloseable {
    private fun team(p: QaPlayer) = p.room!!.players.first { it.id == p.id }.team.name
    val teamA: List<QaPlayer> get() = players.filter { team(it) == "A" }
    val teamB: List<QaPlayer> get() = players.filter { team(it) == "B" }
    fun player(id: String) = players.first { it.id == id }
    fun opponentOf(p: QaPlayer) = player(p.room!!.match!!.myDuel!!.opponentId!!)
    override fun close() = players.forEach { it.close() }
}

object QaFlows {
    /** Two fresh players paired by quick match; both have received the quick room in phase playing. */
    fun quickPair(digits: Int = 3, inst: QaInstance = QaServer.main): Pair<QaPlayer, QaPlayer> {
        val a = QaPlayer.fresh("QaA", inst)
        val b = QaPlayer.fresh("QaB", inst)
        a.ws.send(QueueJoin(digits))
        a.ws.awaitType("queued")
        b.ws.send(QueueJoin(digits))
        a.awaitRoom(10_000) { it.kind.name == "QUICK" }
        b.awaitRoom(10_000) { it.kind.name == "QUICK" }
        return a to b
    }

    /** Host alone in a private lobby with [settings]. */
    fun privateLobby(settings: RoomSettings = RoomSettings(3, 2, 60), inst: QaInstance = QaServer.main): QaPlayer {
        val host = QaPlayer.fresh("QaH", inst)
        host.ws.send(RoomCreate(settings))
        host.awaitRoom { it.phase.name == "LOBBY" }
        return host
    }

    fun join(player: QaPlayer, code: String): RoomView {
        player.ws.send(RoomJoin(code))
        return player.awaitRoom()
    }

    /** Quick pair in GUESSING round 1 with known secrets. */
    fun guessingPair(secretA: String = "012", secretB: String = "345", inst: QaInstance = QaServer.main): Pair<QaPlayer, QaPlayer> {
        val (a, b) = quickPair(secretA.length, inst)
        a.ws.send(SetSecret(secretA))
        b.ws.send(SetSecret(secretB))
        a.awaitRoom { it.match?.myDuel?.phase?.name == "GUESSING" }
        b.awaitRoom { it.match?.myDuel?.phase?.name == "GUESSING" }
        return a to b
    }

    /** A private room of [teamSize] per side, filled and started; everyone has seen phase PLAYING (all duels CHOOSING). */
    fun startedTeamRoom(teamSize: Int, digits: Int = 3, roundSeconds: Int = 60, inst: QaInstance = QaServer.main): QaTeamRoom {
        val host = privateLobby(RoomSettings(digits, teamSize, roundSeconds), inst)
        val code = host.room!!.code!!
        val all = mutableListOf(host)
        repeat(2 * teamSize - 1) { all += QaPlayer.fresh("QaJ", inst).also { join(it, code) } }
        all.forEach { if (it.room?.phase?.name != "PLAYING") it.awaitRoom(10_000) { r -> r.phase.name == "PLAYING" } }
        return QaTeamRoom(code, all)
    }

    /** Everybody sends [secret]; waits until every duel is GUESSING (round 1). */
    fun everybodySecret(room: QaTeamRoom, secret: String = "123") {
        room.players.forEach { it.ws.send(SetSecret(secret)) }
        room.players.forEach { p -> p.awaitRoom(10_000) { it.match?.myDuel?.phase?.name == "GUESSING" } }
    }

    /** Both players of a duel guess in [round]; the round resolves at once. */
    fun guessBoth(a: QaPlayer, b: QaPlayer, guessA: String, guessB: String, round: Int) {
        a.ws.send(Guess(guessA, round))
        b.ws.send(Guess(guessB, round))
    }

    // ---- secret leak scan ----

    /** True when any JSON *string value* of any frame equals [secret] exactly (numbers such as timestamps never count). */
    fun leaks(frames: List<String>, secret: String): Boolean = frames.any { f ->
        val j = runCatching { ProtocolJson.parseToJsonElement(f) }.getOrNull() ?: return@any f.contains("\"$secret\"")
        containsString(j, secret)
    }

    private fun containsString(e: JsonElement, s: String): Boolean = when (e) {
        is JsonPrimitive -> e !is JsonNull && e.isString && e.content == s
        is JsonObject -> e.values.any { containsString(it, s) }
        is JsonArray -> e.any { containsString(it, s) }
    }
}
