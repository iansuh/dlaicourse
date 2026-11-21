package app.numbaseball.server.qa

import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.SetSecret
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Reusable black-box setups for the server scenario tests (protocol sections 5, 7, 8). */
class QaPlayer(val auth: AuthResponse) : AutoCloseable {
    val ws: QaWs = QaWs.connect(auth.token)
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

    override fun close() = ws.close()

    companion object {
        fun roomOf(j: JsonObject): RoomView = ProtocolJson.decodeFromJsonElement(RoomView.serializer(), j["room"]!!.jsonObject)
        fun fresh(prefix: String = "Qa") = QaPlayer(QaHttp.newAccount(prefix))
    }
}

object QaFlows {
    /** Two fresh players paired by quick match; both have received the quick room in phase playing. */
    fun quickPair(digits: Int = 3): Pair<QaPlayer, QaPlayer> {
        val a = QaPlayer.fresh("QaA")
        val b = QaPlayer.fresh("QaB")
        a.ws.send(QueueJoin(digits))
        a.ws.awaitType("queued")
        b.ws.send(QueueJoin(digits))
        a.awaitRoom(10_000) { it.kind.name == "QUICK" }
        b.awaitRoom(10_000) { it.kind.name == "QUICK" }
        return a to b
    }

    /** Host alone in a private lobby with [settings]. */
    fun privateLobby(settings: RoomSettings = RoomSettings(3, 2, 60)): QaPlayer {
        val host = QaPlayer.fresh("QaH")
        host.ws.send(RoomCreate(settings))
        host.awaitRoom { it.phase.name == "LOBBY" }
        return host
    }

    fun join(player: QaPlayer, code: String): RoomView {
        player.ws.send(RoomJoin(code))
        return player.awaitRoom()
    }

    /** Quick pair in GUESSING round 1 with known secrets. */
    fun guessingPair(secretA: String = "012", secretB: String = "345"): Pair<QaPlayer, QaPlayer> {
        val (a, b) = quickPair(secretA.length)
        a.ws.send(SetSecret(secretA))
        b.ws.send(SetSecret(secretB))
        a.awaitRoom { it.match?.myDuel?.phase?.name == "GUESSING" }
        b.awaitRoom { it.match?.myDuel?.phase?.name == "GUESSING" }
        return a to b
    }
}
