package app.numbaseball.server

import app.numbaseball.core.protocol.AcceptTermsRequest
import app.numbaseball.core.protocol.AccountDto
import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.ClientMessage
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.RoomState
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/** Finds the app root (the directory holding docs/) from the test's working directory. */
object CorpusDir {
    fun root(): java.io.File {
        var dir: java.io.File? = java.io.File("").absoluteFile
        while (dir != null && !java.io.File(dir, "docs/play").isDirectory) dir = dir.parentFile
        return dir ?: error("docs/play not found")
    }
}

class FakeClock(start: Long = 1_800_000_000_000L) : Clock {
    private val t = AtomicLong(start)
    override fun now(): Long = t.get()
    fun advance(ms: Long) { t.addAndGet(ms) }
}

fun testConfig(
    adminToken: String? = "admin-token-for-tests-0123456789",
    appAdsTxt: String? = null,
    reviewRoomCode: String? = null,
    quickMatchFreeChat: Boolean = false,
) = Config(
    port = 0,
    dataDir = Files.createTempDirectory("nb-test").toFile(),
    adminToken = adminToken,
    appAdsTxt = appAdsTxt,
    reviewRoomCode = reviewRoomCode,
    quickMatchFreeChat = quickMatchFreeChat,
    tickMillis = 20,
    maintenanceMillis = 3_600_000,
    randomSeed = 7,
)

/** A running test server with helpers for REST calls and WebSocket players. */
class TestServer(val builder: ApplicationTestBuilder, val client: HttpClient, val clock: FakeClock, val services: Services) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ipCounter = AtomicInteger(1)

    suspend fun http(
        method: HttpMethod, path: String, token: String? = null, body: String? = null,
        type: ContentType = ContentType.Application.Json, headers: Map<String, String> = emptyMap(),
    ): HttpResponse = client.request(path) {
        this.method = method
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        headers.forEach { (k, v) -> header(k, v) }
        if (body != null) { contentType(type); setBody(body) }
    }

    /** Creates an account (from its own fake IP) and accepts the terms unless told otherwise. */
    suspend fun account(nickname: String, acceptTerms: Boolean = true): TestAccount {
        val r = http(HttpMethod.Post, "/api/v1/accounts", body = """{"nickname":"$nickname"}""",
            headers = mapOf("X-Forwarded-For" to "10.0.0.${ipCounter.getAndIncrement()}"))
        assertEquals(r.bodyAsText(), HttpStatusCode.Created, r.status)
        val auth = ProtocolJson.decodeFromString(AuthResponse.serializer(), r.bodyAsText())
        if (acceptTerms) {
            val t = http(HttpMethod.Put, "/api/v1/me/terms", auth.token,
                ProtocolJson.encodeToString(AcceptTermsRequest.serializer(), AcceptTermsRequest(services.accounts.currentTermsVersion, ageConfirmed = true)))
            assertEquals(HttpStatusCode.OK, t.status)
        }
        return TestAccount(auth.token, auth.account)
    }

    suspend fun me(token: String): AccountDto =
        ProtocolJson.decodeFromString(AccountDto.serializer(), http(HttpMethod.Get, "/api/v1/me", token).bodyAsText())

    suspend fun connect(account: TestAccount): Player {
        val ws = client.webSocketSession("/ws/v1") { header(HttpHeaders.Authorization, "Bearer ${account.token}") }
        return Player(account, ws, scope)
    }

    /** Moves time forward and runs the timers once. */
    suspend fun advance(ms: Long) {
        clock.advance(ms)
        services.hub.tick()
    }
}

data class TestAccount(val token: String, val account: AccountDto) {
    val id: String get() = account.id
}

class Player(val account: TestAccount, val ws: DefaultClientWebSocketSession, scope: CoroutineScope) {
    val id: String get() = account.id
    val inbox = Channel<ServerMessage>(Channel.UNLIMITED)
    val raw = Channel<String>(Channel.UNLIMITED)
    @Volatile var closeReason: CloseReason? = null
    @Volatile var lastRoom: RoomView? = null

    init {
        scope.launch {
            try {
                for (f in ws.incoming) {
                    if (f !is Frame.Text) continue
                    val text = f.readText()
                    raw.send(text)
                    val msg = ProtocolJson.decodeFromString(ServerMessage.serializer(), text)
                    if (msg is RoomState) lastRoom = msg.room
                    inbox.send(msg)
                }
            } catch (_: Exception) {
            }
            closeReason = runCatching { ws.closeReason.await() }.getOrNull()
            inbox.close()
        }
    }

    suspend fun send(msg: ClientMessage) = ws.send(Frame.Text(ProtocolJson.encodeToString(ClientMessage.serializer(), msg)))

    suspend fun sendRaw(text: String) = ws.send(Frame.Text(text))

    /** Next message of type [T] matching [predicate]; other messages are skipped. */
    suspend inline fun <reified T : ServerMessage> expect(timeoutMs: Long = 5_000, crossinline predicate: (T) -> Boolean = { true }): T =
        withTimeout(timeoutMs) {
            while (true) {
                val m = inbox.receiveCatching().getOrNull() ?: fail("connection closed while waiting for ${T::class.simpleName}: $closeReason")
                if (m is T && predicate(m)) return@withTimeout m
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }

    /** Next room snapshot matching [predicate]. */
    suspend fun state(timeoutMs: Long = 5_000, predicate: (RoomView) -> Boolean = { true }): RoomView =
        expect<RoomState>(timeoutMs) { predicate(it.room) }.room

    /** Drops everything received so far. */
    fun drain() { while (inbox.tryReceive().isSuccess) Unit }

    suspend fun close() = runCatching { ws.close() }

    /** Waits until the server closes the connection and returns its close reason. */
    suspend fun awaitClose(timeoutMs: Long = 5_000): CloseReason? = withTimeout(timeoutMs) {
        while (inbox.receiveCatching().isSuccess) Unit
        closeReason
    }
}

fun serverTest(config: Config = testConfig(), block: suspend TestServer.() -> Unit) = testApplication {
    val clock = FakeClock()
    var services: Services? = null
    application {
        module(config, clock)
        services = attributes[ServicesKey]
    }
    startApplication()
    val client = createClient { install(WebSockets) }
    val server = TestServer(this, client, clock, services!!)
    try {
        server.block()
    } finally {
        server.scope.cancel()
    }
}
