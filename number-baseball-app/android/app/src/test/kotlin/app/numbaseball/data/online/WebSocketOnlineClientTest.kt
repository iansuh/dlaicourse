package app.numbaseball.data.online

import app.numbaseball.core.protocol.Ack
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.Queued
import app.numbaseball.core.protocol.ServerMessage
import app.numbaseball.core.protocol.Welcome
import app.numbaseball.testing.account
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real OkHttp against MockWebServer; timing uses tiny reconnect delays. */
class WebSocketOnlineClientTest {
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    private var token: String? = "token-1"

    @Before fun setUp() = server.start()

    @After fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun client(graceMillis: Long = 60_000) = WebSocketOnlineClient(
        httpClient = http,
        url = server.url("/ws/v1").toString(),
        clientVersion = "1.2.3",
        tokenProvider = { token },
        scope = scope,
        reconnectPolicy = ReconnectPolicy(steps = listOf(10, 20, 40, 50), jitter = 0.0),
        backgroundGraceMillis = graceMillis,
    )

    /** Server side of one connection: sends `welcome` on open and records what the client sends. */
    private class ServerPeer(private val welcome: Boolean = true) : WebSocketListener() {
        val socket = CompletableDeferred<WebSocket>()
        val received = Channel<String>(Channel.UNLIMITED)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (welcome) webSocket.send(encode(Welcome(1, 1_795_000_000_000, account(), roomId = null)))
            socket.complete(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            received.trySend(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }
    }

    private fun upgrade(peer: ServerPeer) = server.enqueue(MockResponse.Builder().webSocketUpgrade(peer).build())

    private suspend fun OnlineClient.awaitState(predicate: (ConnectionState) -> Boolean): ConnectionState =
        withTimeout(5_000) { state.first(predicate) }

    @Test fun connectsWithBearerTokenAndBecomesConnectedOnWelcome() = runBlocking {
        val peer = ServerPeer().also(::upgrade)
        val client = client()
        client.acquire()
        val connected = client.awaitState { it is ConnectionState.Connected } as ConnectionState.Connected
        assertEquals("Mina", connected.welcome.account.nickname)
        val request = server.takeRequest()
        assertEquals("Bearer token-1", request.headers["Authorization"])
        assertEquals("1.2.3", request.headers["X-Client-Version"])
        peer.socket.await()
        Unit
    }

    @Test fun forwardsServerMessagesIgnoresUnknownOnesAndSendsClientMessages() = runBlocking {
        val peer = ServerPeer().also(::upgrade)
        val client = client()
        client.acquire()
        client.awaitState { it is ConnectionState.Connected }
        val ws = peer.socket.await()
        ws.send("""{"type":"something_new","x":1}""")
        ws.send(encode(Queued(digits = 4, since = 5)))
        val queued = withTimeout(5_000) { client.messages.first { it is Queued } } as Queued
        assertEquals(4, queued.digits)

        assertTrue(client.send(QueueJoin(digits = 4)))
        val text = withTimeout(5_000) { peer.received.receive() }
        assertTrue(text, text.contains("\"type\":\"queue_join\"") && text.contains("\"digits\":4"))
    }

    @Test fun requestWaitsForTheMatchingAck() = runBlocking {
        val peer = ServerPeer().also(::upgrade)
        val client = client()
        client.acquire()
        client.awaitState { it is ConnectionState.Connected }
        val ws = peer.socket.await()
        val reply = async(Dispatchers.Default) {
            val text = peer.received.receive()
            val rid = Regex("\"rid\":(\\d+)").find(text)!!.groupValues[1].toLong()
            ws.send(encode(Ack(rid)))
        }
        assertEquals(RequestResult.Ok, client.request { QueueJoin(3, it) })
        reply.await()
        Unit
    }

    @Test fun reconnectsAfterTheServerClosesTheSocket() = runBlocking {
        val first = ServerPeer().also(::upgrade)
        ServerPeer().also(::upgrade)
        val client = client()
        client.acquire()
        client.awaitState { it is ConnectionState.Connected }
        first.socket.await().close(1001, "restart")
        client.awaitState { it is ConnectionState.Reconnecting }
        client.awaitState { it is ConnectionState.Connected }
        assertEquals(2, server.requestCount)
    }

    @Test fun doesNotReconnectAfterSessionReplaced() = runBlocking {
        val peer = ServerPeer().also(::upgrade)
        val client = client()
        client.acquire()
        client.awaitState { it is ConnectionState.Connected }
        peer.socket.await().close(4000, "session_replaced")
        assertEquals(ConnectionState.Stopped(StopReason.SESSION_REPLACED), client.awaitState { it is ConnectionState.Stopped })
        Thread.sleep(200)
        assertEquals(1, server.requestCount)
    }

    @Test fun http401StopsWithUnauthorized() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).body("""{"code":"unauthorized","message":"no"}""").build())
        val client = client()
        client.acquire()
        assertEquals(ConnectionState.Stopped(StopReason.UNAUTHORIZED), client.awaitState { it is ConnectionState.Stopped })
    }

    @Test fun withoutTokenItStopsWithoutConnecting() = runBlocking {
        token = null
        val client = client()
        client.acquire()
        assertEquals(ConnectionState.Stopped(StopReason.NO_ACCOUNT), client.awaitState { it is ConnectionState.Stopped })
        assertEquals(0, server.requestCount)
    }

    @Test fun pausesAfterTheBackgroundGraceAndReconnectsInTheForeground() = runBlocking {
        ServerPeer().also(::upgrade)
        ServerPeer().also(::upgrade)
        val client = client(graceMillis = 50)
        client.acquire()
        client.awaitState { it is ConnectionState.Connected }
        client.setAppInForeground(false)
        client.awaitState { it == ConnectionState.Paused }
        client.setAppInForeground(true)
        client.awaitState { it is ConnectionState.Connected }
        assertEquals(2, server.requestCount)
    }

    @Test fun releasingTheLastUserDisconnects() = runBlocking {
        ServerPeer().also(::upgrade)
        val client = client()
        client.acquire()
        client.awaitState { it is ConnectionState.Connected }
        client.release()
        client.awaitState { it == ConnectionState.Idle }
        assertTrue(!client.send(QueueJoin(3)))
    }

    private companion object {
        fun encode(message: ServerMessage) = ProtocolJson.encodeToString(ServerMessage.serializer(), message)
    }
}
