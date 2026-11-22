package app.numbaseball.server.qa

import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.ClientMessage
import app.numbaseball.core.protocol.CreateAccountRequest
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.server.Config
import app.numbaseball.server.module
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.FileAppender
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Black-box clients for the QA server tests, written against docs/protocol.md with JDK classes only (HttpClient + WebSocket).
 *
 * Target: `NB_QA_BASE_URL` (+ `NB_QA_ADMIN_TOKEN`) for an external test server started with RELAXED_RATE_LIMITS=true and
 * TEST_HOOKS=true; otherwise the tests start the real server module in-process on a free port ([QaServer.start]) and talk to it
 * over real sockets. Tests that need several instances (strict limits, no admin token, restarts) assume the in-process mode.
 */
class QaInstance(
    val baseUrl: String,
    val adminToken: String?,
    val dataDir: File?,
    private val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>?,
) {
    val wsUri: URI get() = URI.create(baseUrl.replaceFirst("http", "ws") + "/ws/v1")
    val inProcess: Boolean get() = server != null
    val dbFile: File? get() = dataDir?.let { File(it, "numbaseball.db") }

    /** Graceful stop (players get room_left{closed} and close 1001). */
    fun stop() {
        server?.stop(500, 3_000)
    }

    private val counter = AtomicLong(System.nanoTime() % 1000)
    private var termsVersion: String? = null

    fun call(method: String, path: String, token: String? = null, body: String? = null, contentType: String = "application/json",
             authHeader: String? = null, headers: Map<String, String> = emptyMap()): QaResponse {
        val b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15)).header("X-Client-Version", "qa")
        (authHeader ?: token?.let { "Bearer $it" })?.let { b.header("Authorization", it) }
        headers.forEach { (k, v) -> b.header(k, v) }
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)
        if (body != null) b.header("Content-Type", contentType)
        b.method(method, publisher)
        val r = QaServer.http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        return QaResponse(r.statusCode(), r.body(), r.headers().map())
    }

    fun admin(method: String, path: String, body: String? = null): QaResponse {
        val t = adminToken ?: throw AssertionError("this instance has no admin token")
        return call(method, path, token = t, body = body)
    }

    /** Moves the server clock forward (TEST_HOOKS) and gives the tick loop time to run. */
    fun advance(ms: Long) {
        val r = admin("POST", "/admin/v1/test/clock?advanceMs=$ms")
        if (r.status != 200) throw AssertionError("test clock not available: ${r.status} ${r.body}")
        Thread.sleep(300)
    }

    fun currentTermsVersion(): String = termsVersion ?: call("GET", "/api/v1/config").json!!["termsVersion"]!!.jsonPrimitive.content
        .also { termsVersion = it }

    fun acceptTerms(token: String, version: String = currentTermsVersion(), ageConfirmed: Boolean = true): QaResponse =
        call("PUT", "/api/v1/me/terms", token, "{\"version\":${JsonPrimitive(version)},\"ageConfirmed\":$ageConfirmed}")

    /** A fresh device account with a unique plain nickname; terms and age accepted unless [acceptTerms] is false. */
    fun newAccount(prefix: String = "Qa", acceptTerms: Boolean = true): AuthResponse {
        val nick = prefix.take(6) + (counter.incrementAndGet() % 100_000)
        val r = call("POST", "/api/v1/accounts", body = ProtocolJson.encodeToString(CreateAccountRequest.serializer(), CreateAccountRequest(nick)))
        if (r.status != 201) throw AssertionError("account creation failed: ${r.status} ${r.body}")
        val auth = ProtocolJson.decodeFromString(AuthResponse.serializer(), r.body)
        if (acceptTerms) {
            val t = acceptTerms(auth.token)
            if (t.status != 200) throw AssertionError("terms acceptance failed: ${t.status} ${t.body}")
        }
        return auth
    }

    fun handleQuery(nickname: String, tag: String): String = URLEncoder.encode("$nickname#$tag", "UTF-8")
}

object QaServer {
    const val QA_ADMIN_TOKEN = "qa-admin-token-0123456789"
    const val REVIEW_CODE = "PLAYR"
    const val APP_ADS = "google.com, pub-0000000000000000, DIRECT, f08c47fec0942fa0"

    val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    private val external: String? = System.getenv("NB_QA_BASE_URL") ?: System.getProperty("nb.qa.baseUrl")

    /** Server log lines of every in-process instance (privacy scan). */
    val logFile: File by lazy {
        val f = Files.createTempFile("nb-qa-server", ".log").toFile()
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        val enc = PatternLayoutEncoder().apply { context = ctx; pattern = "%d %-5level %logger{20} - %msg%n%ex"; start() }
        val app = FileAppender<ILoggingEvent>().apply { context = ctx; file = f.absolutePath; encoder = enc; start() }
        (LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger).addAppender(app)
        f
    }

    /** The main target: external server, or an in-process relaxed test server. */
    val main: QaInstance by lazy {
        if (external != null) QaInstance(external.trimEnd('/'), System.getenv("NB_QA_ADMIN_TOKEN"), System.getenv("NB_QA_DATA_DIR")?.let(::File), null)
        else start()
    }

    fun requireInProcess() = Assume.assumeTrue("needs in-process server instances (NB_QA_BASE_URL is set)", external == null)

    fun start(
        adminToken: String? = QA_ADMIN_TOKEN,
        relaxed: Boolean = true,
        dataDir: File = Files.createTempDirectory("nb-qa").toFile(),
        termsVersion: String? = null,
        appAdsTxt: String? = "$APP_ADS\n",
        reviewRoomCode: String? = REVIEW_CODE,
        quickMatchFreeChat: Boolean = false,
        trustedProxyHops: Int = 0,
    ): QaInstance {
        logFile // attach the log capture before the server logs anything
        val config = Config(
            port = 0, dataDir = dataDir, adminToken = adminToken, appAdsTxt = appAdsTxt, reviewRoomCode = reviewRoomCode,
            quickMatchFreeChat = quickMatchFreeChat, termsVersion = termsVersion, forceHttps = false, relaxedRateLimits = relaxed,
            testHooks = true, tickMillis = 50, trustedProxyHops = trustedProxyHops,
        )
        val server = embeddedServer(Netty, port = 0, host = "127.0.0.1") { module(config) }
        server.start(wait = false)
        val port = runBlocking { server.engine.resolvedConnectors().first().port }
        return QaInstance("http://127.0.0.1:$port", adminToken, dataDir, server)
    }
}

data class QaResponse(val status: Int, val body: String, val headers: Map<String, List<String>>) {
    val json: JsonObject? get() = runCatching { ProtocolJson.parseToJsonElement(body).jsonObject }.getOrNull()
    val code: String? get() = json?.get("code")?.jsonPrimitive?.contentOrNull
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
}

/** One WebSocket connection. Every text frame is kept in [all] (for leak scans) and queued for [await]. */
class QaWs private constructor() : AutoCloseable {
    val all = CopyOnWriteArrayList<String>()
    private val queue = LinkedBlockingQueue<String>()
    private lateinit var ws: WebSocket
    @Volatile var closeCode: Int? = null
        private set
    private val rid = AtomicLong(0)

    private val listener = object : WebSocket.Listener {
        private val partial = StringBuilder()
        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            partial.append(data)
            if (last) {
                val s = partial.toString()
                partial.setLength(0)
                all += s
                queue += s
            }
            webSocket.request(1)
            return null
        }
        override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*>? {
            all += "<binary frame from server>"
            webSocket.request(1)
            return null
        }
        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            closeCode = statusCode
            return CompletableFuture.completedFuture(null)
        }
        override fun onError(webSocket: WebSocket, error: Throwable) {
            if (closeCode == null) closeCode = -1
        }
    }

    companion object {
        /** Connects with `Authorization: <scheme> token`. Throws if the upgrade is refused. */
        fun connect(token: String?, target: QaInstance = QaServer.main, scheme: String = "Bearer", uriSuffix: String = ""): QaWs {
            val c = QaWs()
            val b = QaServer.http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).header("X-Client-Version", "qa")
            token?.let { b.header("Authorization", "$scheme $it") }
            c.ws = b.buildAsync(URI.create(target.wsUri.toString() + uriSuffix), c.listener).get(10, TimeUnit.SECONDS)
            return c
        }

        /** HTTP status of a refused upgrade (or 101 when it succeeded). */
        fun upgradeStatus(token: String?, target: QaInstance = QaServer.main, uriSuffix: String = ""): Int = try {
            connect(token, target, uriSuffix = uriSuffix).close()
            101
        } catch (e: Exception) {
            var t: Throwable? = e
            var status = -1
            while (t != null) {
                if (t is java.net.http.WebSocketHandshakeException) status = t.response.statusCode()
                t = t.cause
            }
            status
        }
    }

    fun sendRaw(text: String) {
        ws.sendText(text, true).get(5, TimeUnit.SECONDS)
    }

    fun sendFragmented(parts: List<String>) {
        parts.forEachIndexed { i, p -> ws.sendText(p, i == parts.lastIndex).get(5, TimeUnit.SECONDS) }
    }

    fun sendBinary(bytes: ByteArray) {
        ws.sendBinary(ByteBuffer.wrap(bytes), true).get(5, TimeUnit.SECONDS)
    }

    fun nextRid(): Long = rid.incrementAndGet()

    fun send(m: ClientMessage) = sendRaw(ProtocolJson.encodeToString(ClientMessage.serializer(), m))

    /** Waits for the first queued frame matching [predicate]; earlier non-matching frames are dropped from the queue. */
    fun await(timeoutMs: Long = 5_000, what: String = "frame", predicate: (JsonObject) -> Boolean): JsonObject {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) throw AssertionError("timed out waiting for $what; last frames: ${all.takeLast(4).map { it.take(300) }}; close=$closeCode")
            val s = queue.poll(left, TimeUnit.MILLISECONDS) ?: continue
            val j = runCatching { ProtocolJson.parseToJsonElement(s).jsonObject }.getOrNull()
                ?: throw AssertionError("server sent a frame that is not a JSON object: ${s.take(300)}")
            if (predicate(j)) return j
        }
    }

    /** Frames that arrive within [ms] (queue drained). */
    fun drain(ms: Long = 300): List<JsonObject> {
        Thread.sleep(ms)
        val out = ArrayList<JsonObject>()
        while (true) {
            val s = queue.poll() ?: break
            runCatching { ProtocolJson.parseToJsonElement(s).jsonObject }.getOrNull()?.let { out += it }
        }
        return out
    }

    fun awaitType(type: String, timeoutMs: Long = 5_000): JsonObject = await(timeoutMs, type) { it.type == type }

    fun awaitError(code: String? = null, timeoutMs: Long = 5_000): JsonObject =
        await(timeoutMs, "error ${code ?: ""}") { it.type == "error" && (code == null || it.str("code") == code) }

    fun awaitClose(timeoutMs: Long = 5_000): Int? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (closeCode == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        return closeCode
    }

    /** ping with rid -> pong; proves the connection is alive and processing. */
    fun assertAlive() {
        val r = nextRid()
        sendRaw("{\"type\":\"ping\",\"rid\":$r}")
        await(5_000, "pong $r") { it.type == "pong" && it.str("rid") == r.toString() }
    }

    /** Sends [m] with a rid and returns the ack or error for it. */
    fun request(json: String): JsonObject {
        val r = nextRid()
        val withRid = json.trimEnd().removeSuffix("}") + (if (json.contains(":")) "," else "") + "\"rid\":$r}"
        sendRaw(withRid)
        return await(5_000, "reply to rid $r") { (it.type == "ack" || it.type == "error") && it.str("rid") == r.toString() }
    }

    override fun close() {
        runCatching { ws.sendClose(WebSocket.NORMAL_CLOSURE, "qa").get(2, TimeUnit.SECONDS) }
        runCatching { ws.abort() }
    }
}

val JsonObject.type: String? get() = str("type")
fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** Minimal copy of the QA corpus loader (core test classes are not visible to the server module). */
object QaServerCorpus {
    val dir: File by lazy {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            val c = File(d, "docs/qa/corpora")
            if (c.isDirectory) return@lazy c
            d = d.parentFile
        }
        error("docs/qa/corpora not found")
    }

    fun load(name: String): List<Map<String, String>> {
        val lines = File(dir, name).readLines(Charsets.UTF_8).filter { it.isNotEmpty() && !it.startsWith("#") }
        val header = lines.first().split('\t')
        return lines.drop(1).map { line -> header.zip(line.split('\t')).toMap() }
    }

    fun decode(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') { out.append(c); i++; continue }
            when (s[i + 1]) {
                'n' -> { out.append('\n'); i += 2 }
                'r' -> { out.append('\r'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                '0' -> { out.append(0.toChar()); i += 2 }
                's' -> { out.append(' '); i += 2 }
                '\\' -> { out.append('\\'); i += 2 }
                ']' -> { out.append(']'); i += 2 }
                'u' -> { out.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6 }
                'U' -> {
                    val close = s.indexOf('}', i + 3)
                    out.appendCodePoint(s.substring(i + 3, close).toInt(16))
                    i = close + 1
                }
                'R' -> {
                    val colon = s.indexOf(':', i + 3)
                    val n = s.substring(i + 3, colon).toInt()
                    var j = colon + 1
                    val body = StringBuilder()
                    while (s[j] != ']') {
                        if (s[j] == '\\') { body.append(s[j]).append(s[j + 1]); j += 2 } else { body.append(s[j]); j++ }
                    }
                    val unit = decode(body.toString())
                    repeat(n) { out.append(unit) }
                    i = j + 1
                }
                else -> throw IllegalArgumentException("bad escape in <$s>")
            }
        }
        return out.toString()
    }
}
