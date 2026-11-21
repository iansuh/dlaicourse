package app.numbaseball.server.qa

import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.ClientMessage
import app.numbaseball.core.protocol.CreateAccountRequest
import app.numbaseball.core.protocol.ProtocolJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Black-box clients for the QA server tests, written against docs/protocol.md with JDK classes only
 * (java.net.http HttpClient and WebSocket), so they need no server internals and no extra dependencies.
 *
 * Target: `NB_QA_BASE_URL` (env) or `-Dnb.qa.baseUrl=...`, e.g. http://localhost:8080. When the server module gets an
 * in-process test host, [QaServer.baseUrl] will start it instead.
 */
object QaServer {
    val baseUrl: String? = System.getenv("NB_QA_BASE_URL") ?: System.getProperty("nb.qa.baseUrl")

    fun require(): String = baseUrl ?: throw AssertionError("set NB_QA_BASE_URL to a running server (see TEST_PLAN.md 3.4)")

    val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun wsUri(): URI = URI.create(require().replaceFirst("http", "ws") + "/ws/v1")
}

data class QaResponse(val status: Int, val body: String, val headers: Map<String, List<String>>) {
    val json: JsonObject? get() = runCatching { ProtocolJson.parseToJsonElement(body).jsonObject }.getOrNull()
    val code: String? get() = json?.get("code")?.jsonPrimitive?.contentOrNull
}

object QaHttp {
    fun call(method: String, path: String, token: String? = null, body: String? = null, contentType: String = "application/json",
             authHeader: String? = null): QaResponse {
        val b = HttpRequest.newBuilder(URI.create(QaServer.require() + path)).timeout(Duration.ofSeconds(10))
            .header("X-Client-Version", "qa")
        (authHeader ?: token?.let { "Bearer $it" })?.let { b.header("Authorization", it) }
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)
        if (body != null) b.header("Content-Type", contentType)
        b.method(method, publisher)
        val r = QaServer.http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        return QaResponse(r.statusCode(), r.body(), r.headers().map())
    }

    private val counter = AtomicLong(System.nanoTime() % 1000)

    /** Creates a fresh device account with a unique plain nickname. */
    fun newAccount(prefix: String = "Qa"): AuthResponse {
        val nick = prefix + (counter.incrementAndGet() % 100_000)
        val r = call("POST", "/api/v1/accounts", body = ProtocolJson.encodeToString(CreateAccountRequest.serializer(), CreateAccountRequest(nick)))
        if (r.status != 201) throw AssertionError("account creation failed: ${r.status} ${r.body}")
        return ProtocolJson.decodeFromString(AuthResponse.serializer(), r.body)
    }
}

/** One WebSocket connection. Every text frame is kept in [all] (for leak scans) and queued for [await]. */
class QaWs private constructor(val token: String?) : AutoCloseable {
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
            all += "<binary frame from server: protocol says text only>"
            webSocket.request(1)
            return null
        }
        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            closeCode = statusCode
            return CompletableFuture.completedFuture(null)
        }
    }

    companion object {
        /** Connects with `Authorization: Bearer token`. Throws with the HTTP status if the upgrade is refused. */
        fun connect(token: String?, headers: Map<String, String> = emptyMap()): QaWs {
            val c = QaWs(token)
            val b = QaServer.http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).header("X-Client-Version", "qa")
            token?.let { b.header("Authorization", "Bearer $it") }
            headers.forEach { (k, v) -> b.header(k, v) }
            c.ws = b.buildAsync(QaServer.wsUri(), c.listener).get(10, TimeUnit.SECONDS)
            return c
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
            if (left <= 0) throw AssertionError("timed out waiting for $what; last frames: ${all.takeLast(5)}; close=$closeCode")
            val s = queue.poll(left, TimeUnit.MILLISECONDS) ?: continue
            val j = runCatching { ProtocolJson.parseToJsonElement(s).jsonObject }.getOrNull()
                ?: throw AssertionError("server sent a frame that is not a JSON object: $s")
            if (predicate(j)) return j
        }
    }

    fun awaitType(type: String, timeoutMs: Long = 5_000): JsonObject = await(timeoutMs, type) { it.type == type }

    fun awaitError(code: String? = null, timeoutMs: Long = 5_000): JsonObject =
        await(timeoutMs, "error ${code ?: ""}") { it.type == "error" && (code == null || it["code"]?.jsonPrimitive?.contentOrNull == code) }

    fun awaitClose(timeoutMs: Long = 5_000): Int? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (closeCode == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        return closeCode
    }

    /** ping with rid -> pong; proves the connection is alive and processing. */
    fun assertAlive() {
        val r = nextRid()
        sendRaw("{\"type\":\"ping\",\"rid\":$r}")
        await(5_000, "pong $r") { it.type == "pong" && it["rid"]?.jsonPrimitive?.contentOrNull == r.toString() }
    }

    override fun close() {
        runCatching { ws.sendClose(WebSocket.NORMAL_CLOSURE, "qa").get(2, TimeUnit.SECONDS) }
        runCatching { ws.abort() }
    }
}

val JsonObject.type: String? get() = this["type"]?.jsonPrimitive?.contentOrNull

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
