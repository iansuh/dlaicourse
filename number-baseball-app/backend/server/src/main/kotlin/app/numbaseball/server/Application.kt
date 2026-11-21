package app.numbaseball.server

import app.numbaseball.core.protocol.AcceptTermsRequest
import app.numbaseball.core.protocol.AdminReportList
import app.numbaseball.core.protocol.AuthResponse
import app.numbaseball.core.protocol.BanRequest
import app.numbaseball.core.protocol.BlockListResponse
import app.numbaseball.core.protocol.ChatRestrictRequest
import app.numbaseball.core.protocol.ClientMessage
import app.numbaseball.core.protocol.CodeResponse
import app.numbaseball.core.protocol.CreateAccountRequest
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.ErrorResponse
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.PROTOCOL_VERSION
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.RedeemTransferRequest
import app.numbaseball.core.protocol.RenameRequest
import app.numbaseball.core.protocol.ReportRequest
import app.numbaseball.core.protocol.ReportResponse
import app.numbaseball.core.protocol.ResolveReportRequest
import app.numbaseball.core.protocol.ServerInfo
import app.numbaseball.core.protocol.ServerMessage
import app.numbaseball.core.text.QuickPhrases
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseQueryString
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.Hook
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.application.isHandled
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.utils.io.readRemaining
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("app.numbaseball.server")

const val DEFAULT_TERMS_VERSION = "2026-11-v1"
const val MAX_BODY_BYTES = 4096

/** Everything the routes need; also reachable from tests through [ServicesKey]. */
class Services(
    val config: Config,
    val clock: Clock,
    val db: Database,
    val accounts: AccountService,
    val hub: Hub,
    val pages: WebPages,
    val limiter: RateLimiter,
) {
    suspend fun deleteAccount(id: String) {
        hub.accountRemoved(id, deleted = true)
        accounts.delete(id)
    }

    /** Pending web deletions older than 30 days, report retention, code expiry, limiter clean-up. */
    suspend fun runMaintenance() {
        for (id in accounts.dueDeletions()) deleteAccount(id)
        accounts.purge()
        limiter.sweep()
    }
}

val ServicesKey = AttributeKey<Services>("services")
private val AccountKey = AttributeKey<Account>("account")

/** Like Ktor's authentication hook: a plugin that answers early stops the pipeline. */
private object EarlyResponseHook : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Plugins) {
            handler(call)
            if (call.isHandled) finish()
        }
    }
}

fun Application.module(config: Config = Config.fromEnv(), clock: Clock = Clock.SYSTEM) {
    val db = Database(File(config.dataDir, "numbaseball.db"))
    val pages = WebPages(storeUrl = config.storeUrl)
    val termsVersion = config.termsVersion ?: pages.termsVersion ?: DEFAULT_TERMS_VERSION
    val accounts = AccountService(db, clock, termsVersion)
    val random: Random = config.randomSeed?.let { Random(it) } ?: SecureRandom().asKotlinRandom()
    val hub = Hub(accounts, clock, config, random)
    val services = Services(config, clock, db, accounts, hub, pages, RateLimiter(clock))
    attributes.put(ServicesKey, services)

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = Limits.MAX_FRAME_BYTES.toLong()
        masking = false
    }
    install(CallLogging) {
        // Method, path and status only: no IP addresses, query strings, headers or bodies (privacy policy).
        format { call -> "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()?.value}" }
        filter { call -> call.request.path() != "/health" }
    }
    install(StatusPages) {
        exception<ApiException> { call, e ->
            e.retryAfterSeconds?.let { call.response.header(HttpHeaders.RetryAfter, it.toString()) }
            call.respondJson(e.status, ErrorResponse(e.code, e.message, e.retryAfterSeconds), ErrorResponse.serializer())
        }
        exception<Throwable> { call, e ->
            log.error("unhandled error on ${call.request.path()}: ${e::class.simpleName}", e)
            call.respondJson(HttpStatusCode.InternalServerError, ErrorResponse(ErrorCodes.INTERNAL, "internal error"), ErrorResponse.serializer())
        }
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed) { call, status ->
            val path = call.request.path()
            if (path.startsWith("/api/") || path.startsWith("/admin/")) {
                call.respondJson(status, ErrorResponse(ErrorCodes.NOT_FOUND, "not found"), ErrorResponse.serializer())
            } else {
                call.respondText("Not found", ContentType.Text.Plain, status)
            }
        }
    }
    install(
        createApplicationPlugin("SecurityHeaders") {
            on(EarlyResponseHook) { call ->
                val proto = call.request.headers["X-Forwarded-Proto"]?.substringBefore(',')?.trim()
                if (config.forceHttps && proto == "http" && call.request.path() != "/health") {
                    val host = call.request.headers[HttpHeaders.Host] ?: return@on
                    call.respondRedirect("https://$host${call.request.uri}", permanent = true)
                    return@on
                }
                call.response.header("X-Content-Type-Options", "nosniff")
                call.response.header("Referrer-Policy", "no-referrer")
                if (proto == "https") call.response.header("Strict-Transport-Security", "max-age=31536000")
            }
        },
    )

    routing {
        get("/health") { call.respondText("""{"status":"ok"}""", ContentType.Application.Json) }
        webRoutes(services)
        apiRoutes(services)
        adminRoutes(services)
        route("/ws/v1") {
            install(
                createRouteScopedPlugin("WsAuth") {
                    on(EarlyResponseHook) { call ->
                        val token = bearer(call)
                        val account = accounts.authenticate(token)?.takeIf { !it.isBot }
                        when {
                            account == null -> call.respondJson(HttpStatusCode.Unauthorized, ErrorResponse(ErrorCodes.UNAUTHORIZED, "unauthorized"), ErrorResponse.serializer())
                            account.banned -> call.respondJson(HttpStatusCode.Forbidden, ErrorResponse(ErrorCodes.BANNED, "banned"), ErrorResponse.serializer())
                            else -> call.attributes.put(AccountKey, account)
                        }
                    }
                },
            )
            webSocket { gameSocket(services, call.attributes[AccountKey]) }
        }
    }

    launch {
        while (isActive) {
            delay(config.tickMillis)
            try { hub.tick() } catch (e: Exception) { log.error("tick failed: ${e::class.simpleName}", e) }
        }
    }
    launch {
        while (isActive) {
            try { services.runMaintenance() } catch (e: Exception) { log.error("maintenance failed: ${e::class.simpleName}", e) }
            delay(config.maintenanceMillis)
        }
    }
    monitor.subscribe(ApplicationStopped) { db.close() }
}

// ---------------------------------------------------------------------- WebSocket

private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.gameSocket(services: Services, account: Account) {
    val session = ClientSession(account.id)
    val writer = launch {
        for (out in session.outgoing) {
            when (out) {
                is Outgoing.Msg -> send(Frame.Text(ProtocolJson.encodeToString(ServerMessage.serializer(), out.message)))
                is Outgoing.Close -> { close(CloseReason(out.code, out.reason)); break }
            }
        }
    }
    services.hub.connect(account, session)
    // ~10 messages per second sustained, bursts of 30; 50 dropped messages in a row close the connection.
    val bucket = TokenBucket(30.0, 10.0 / 1000)
    var dropped = 0
    try {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val bytes = frame.readBytes()
            val now = System.currentTimeMillis()
            val wait = bucket.take(now)
            if (wait > 0) {
                session.send(ErrorMessage(ErrorCodes.RATE_LIMITED, "too many messages", retryAfterMs = wait))
                if (++dropped >= 50) { session.close(1008, "flooding"); break }
                continue
            }
            dropped = 0
            if (bytes.size > Limits.MAX_MESSAGE_BYTES) {
                session.send(ErrorMessage(ErrorCodes.MESSAGE_TOO_LARGE, "message larger than ${Limits.MAX_MESSAGE_BYTES} bytes"))
                continue
            }
            val msg = try {
                ProtocolJson.decodeFromString(ClientMessage.serializer(), bytes.decodeToString())
            } catch (e: Throwable) {
                if (e !is Exception && e !is StackOverflowError) throw e
                session.send(ErrorMessage(ErrorCodes.MALFORMED_MESSAGE, "not a valid protocol message"))
                continue
            }
            services.hub.handle(session, msg)
        }
    } finally {
        services.hub.disconnect(session)
        session.close(1000, "bye")
        writer.join()
    }
}

/** Per-connection token bucket (no shared state). */
private class TokenBucket(private val capacity: Double, private val perMs: Double) {
    private var tokens = capacity
    private var last = -1L

    /** 0 when allowed, otherwise ms until the next token. */
    fun take(now: Long): Long {
        if (last >= 0) tokens = minOf(capacity, tokens + (now - last) * perMs)
        last = now
        if (tokens >= 1) { tokens -= 1; return 0 }
        return ((1 - tokens) / perMs).toLong().coerceAtLeast(1)
    }
}

// ---------------------------------------------------------------------- helpers

private suspend fun <T> ApplicationCall.respondJson(status: HttpStatusCode, value: T, serializer: KSerializer<T>) {
    response.header(HttpHeaders.CacheControl, "no-store")
    respondText(ProtocolJson.encodeToString(serializer, value), ContentType.Application.Json, status)
}

private fun bearer(call: ApplicationCall): String? {
    val h = call.request.headers[HttpHeaders.Authorization]?.trim() ?: return null
    if (h.length < 8 || !h.substring(0, 7).equals("Bearer ", ignoreCase = true)) return null
    return h.substring(7).trim().takeIf { it.isNotEmpty() }
}

/** Client address for in-memory rate limits only (first X-Forwarded-For entry behind the host's proxy). */
private fun clientIp(call: ApplicationCall): String =
    call.request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() && it.length <= 64 }
        ?: call.request.local.remoteAddress

private suspend fun readBody(call: ApplicationCall): String {
    call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let {
        if (it > MAX_BODY_BYTES) throw ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.MESSAGE_TOO_LARGE, "body larger than 4 KB")
    }
    val bytes = call.receiveChannel().readRemaining(MAX_BODY_BYTES + 1L).readByteArray()
    if (bytes.size > MAX_BODY_BYTES) throw ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.MESSAGE_TOO_LARGE, "body larger than 4 KB")
    return bytes.decodeToString()
}

private suspend fun <T> ApplicationCall.body(serializer: KSerializer<T>): T {
    val text = readBody(this)
    return try {
        ProtocolJson.decodeFromString(serializer, text)
    } catch (e: Exception) {
        throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "invalid JSON body")
    }
}

private fun limited(services: Services, key: String, capacity: Int, windowMillis: Long) {
    val wait = services.limiter.acquire(key, capacity, windowMillis)
    if (wait > 0) throw ApiException(HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED, "too many requests", (wait + 999) / 1000)
}

private fun RoutingContext.me(services: Services, allowBanned: Boolean = false): Account {
    val a = services.accounts.authenticate(bearer(call))?.takeIf { !it.isBot }
        ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "missing or unknown token")
    if (a.banned && !allowBanned) throw ApiException(HttpStatusCode.Forbidden, ErrorCodes.BANNED, "account banned")
    return a
}

private fun preferEnglish(call: ApplicationCall): Boolean {
    call.request.queryParameters["lang"]?.let { return it != "ko" }
    return !(call.request.headers[HttpHeaders.AcceptLanguage]?.trim()?.lowercase()?.startsWith("ko") ?: false)
}

private suspend fun ApplicationCall.html(status: HttpStatusCode, body: String) {
    response.header(
        "Content-Security-Policy",
        "default-src 'none'; style-src 'unsafe-inline'; img-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
    )
    response.header(HttpHeaders.CacheControl, "no-store")
    respondText(body, ContentType.Text.Html.withParameter("charset", "utf-8"), status)
}

// ---------------------------------------------------------------------- web pages

private fun io.ktor.server.routing.Route.webRoutes(services: Services) {
    val pages = services.pages
    get("/") { call.html(HttpStatusCode.OK, pages.landing()) }
    get("/privacy") { call.html(HttpStatusCode.OK, pages.privacy(preferEnglish(call))) }
    get("/terms") { call.html(HttpStatusCode.OK, pages.terms(preferEnglish(call))) }
    get("/delete-account") { call.html(HttpStatusCode.OK, pages.deleteAccount(preferEnglish(call))) }
    get("/app-ads.txt") {
        val text = services.config.appAdsTxt
        if (text == null) call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
        else call.respondText(text, ContentType.Text.Plain.withParameter("charset", "utf-8"))
    }

    // Method B: deletion code from the app.
    post("/delete-account") {
        val en = preferEnglish(call)
        val form = parseQueryString(runCatching { readBody(call) }.getOrDefault(""))
        if (form["confirm"] != "yes") return@post call.html(HttpStatusCode.BadRequest, pages.deleteAccount(en, pages.confirmStatus))
        val ip = clientIp(call)
        if (services.limiter.isExhausted("code:$ip", 10, 600_000)) {
            return@post call.html(HttpStatusCode.TooManyRequests, pages.deleteAccount(en, pages.rateStatus))
        }
        val id = services.accounts.consumeCode(form["code"].orEmpty().take(64), CodeKind.DELETION)
        if (id == null) {
            services.limiter.acquire("code:$ip", 10, 600_000)
            return@post call.html(HttpStatusCode.BadRequest, pages.deleteAccount(en, pages.badCodeStatus))
        }
        services.deleteAccount(id)
        call.html(HttpStatusCode.OK, pages.deleteAccount(en, pages.deletedStatus))
    }

    // Method C: request without the app. The answer never reveals whether the account exists.
    post("/delete-account/request") {
        val en = preferEnglish(call)
        val form = parseQueryString(runCatching { readBody(call) }.getOrDefault(""))
        if (form["confirm"] != "yes") return@post call.html(HttpStatusCode.BadRequest, pages.deleteAccount(en, pages.confirmStatus))
        if (services.limiter.acquire("delreq:${clientIp(call)}", 10, 600_000) > 0) {
            return@post call.html(HttpStatusCode.TooManyRequests, pages.deleteAccount(en, pages.rateStatus))
        }
        services.accounts.requestDeletion(form["handle"].orEmpty().take(64))
        call.html(HttpStatusCode.OK, pages.deleteAccount(en, pages.requestStatus))
    }
}

// ---------------------------------------------------------------------- REST API

private fun io.ktor.server.routing.Route.apiRoutes(services: Services) {
    val accounts = services.accounts
    route("/api/v1") {
        get("/config") {
            call.respondJson(
                HttpStatusCode.OK,
                ServerInfo(
                    protocol = PROTOCOL_VERSION,
                    serverTime = services.clock.now(),
                    digits = Limits.DIGITS.toList(),
                    teamSizes = Limits.TEAM_SIZE.toList(),
                    roundSeconds = Limits.ROUND_SECONDS,
                    quickRoundSeconds = Limits.QUICK_ROUND_SECONDS,
                    chatMaxLength = Limits.CHAT_MAX_LENGTH,
                    maxMessageBytes = Limits.MAX_MESSAGE_BYTES,
                    quickPhrases = QuickPhrases.ALL,
                    termsVersion = accounts.currentTermsVersion,
                    quickMatchFreeChat = services.config.quickMatchFreeChat,
                ),
                ServerInfo.serializer(),
            )
        }
        post("/accounts") {
            val req = call.body(CreateAccountRequest.serializer())
            val ip = clientIp(call)
            limited(services, "create:$ip", 10, 3_600_000)
            limited(services, "create:*", 300, 3_600_000)
            val (token, account) = accounts.create(req.nickname)
            call.respondJson(HttpStatusCode.Created, AuthResponse(token, accounts.toDto(account)), AuthResponse.serializer())
        }
        get("/me") { call.respondJson(HttpStatusCode.OK, accounts.toDto(me(services, allowBanned = true)), app.numbaseball.core.protocol.AccountDto.serializer()) }
        patch("/me") {
            val a = me(services)
            val req = call.body(RenameRequest.serializer())
            limited(services, "rename:${a.id}", 5, 24 * 3_600_000L)
            val renamed = accounts.rename(a.id, req.nickname)
            services.hub.accountRenamed(a.id)
            call.respondJson(HttpStatusCode.OK, accounts.toDto(renamed), app.numbaseball.core.protocol.AccountDto.serializer())
        }
        delete("/me") {
            val a = me(services, allowBanned = true)
            services.deleteAccount(a.id)
            call.respond(HttpStatusCode.NoContent)
        }
        put("/me/terms") {
            val a = me(services)
            val req = call.body(AcceptTermsRequest.serializer())
            call.respondJson(HttpStatusCode.OK, accounts.toDto(accounts.acceptTerms(a.id, req.version)), app.numbaseball.core.protocol.AccountDto.serializer())
        }
        delete("/me/deletion-request") {
            val a = me(services, allowBanned = true)
            accounts.acknowledgeDeletionNotice(a.id)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/me/transfer-code") {
            val a = me(services)
            limited(services, "issue:${a.id}", 5, 3_600_000)
            val (code, expires) = accounts.issueCode(a.id, CodeKind.TRANSFER)
            call.respondJson(HttpStatusCode.OK, CodeResponse(code, expires), CodeResponse.serializer())
        }
        post("/me/deletion-code") {
            val a = me(services, allowBanned = true)
            limited(services, "issue:${a.id}", 5, 3_600_000)
            val (code, expires) = accounts.issueCode(a.id, CodeKind.DELETION)
            call.respondJson(HttpStatusCode.OK, CodeResponse(code, expires), CodeResponse.serializer())
        }
        post("/transfer") {
            val req = call.body(RedeemTransferRequest.serializer())
            val ip = clientIp(call)
            if (services.limiter.isExhausted("code:$ip", 10, 600_000) || services.limiter.isExhausted("code:*", 500, 600_000)) {
                throw ApiException(HttpStatusCode.TooManyRequests, ErrorCodes.TOO_MANY_ATTEMPTS, "too many wrong codes", 600)
            }
            val result = accounts.redeemTransfer(req.code.take(64))
            if (result == null) {
                services.limiter.acquire("code:$ip", 10, 600_000)
                services.limiter.acquire("code:*", 500, 600_000)
                throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.INVALID_CODE, "wrong, used or expired code")
            }
            // The old device's connection is closed: its token is no longer valid.
            services.hub.accountRemoved(result.second.id, deleted = false).let { }
            call.respondJson(HttpStatusCode.OK, AuthResponse(result.first, accounts.toDto(result.second)), AuthResponse.serializer())
        }
        get("/me/blocks") {
            val a = me(services, allowBanned = true)
            call.respondJson(HttpStatusCode.OK, BlockListResponse(accounts.listBlocks(a.id)), BlockListResponse.serializer())
        }
        put("/me/blocks/{id}") {
            val a = me(services, allowBanned = true)
            accounts.block(a.id, call.parameters["id"].orEmpty())
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/me/blocks/{id}") {
            val a = me(services, allowBanned = true)
            accounts.unblock(a.id, call.parameters["id"].orEmpty())
            call.respond(HttpStatusCode.NoContent)
        }
        post("/reports") {
            val a = me(services, allowBanned = true)
            val req = call.body(ReportRequest.serializer())
            if ((req.note?.length ?: 0) > 500) throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "note longer than 500 characters")
            if (req.messageId != null && req.roomId == null) throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "messageId needs roomId")
            if (req.targetId == a.id) throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "you cannot report yourself")
            val target = accounts.get(req.targetId) ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no such player")
            limited(services, "report:${a.id}", 10, 3_600_000)
            val snapshot = services.hub.reportSnapshot(a.id, req.roomId, req.messageId, target.id)
            val (id, _) = accounts.createReport(a.id, target, req.reason, req.note?.trim()?.takeIf { it.isNotEmpty() }, req.roomId, req.messageId, snapshot)
            call.respondJson(HttpStatusCode.Created, ReportResponse(id), ReportResponse.serializer())
        }
    }
}

// ---------------------------------------------------------------------- admin API

private fun io.ktor.server.routing.Route.adminRoutes(services: Services) {
    val accounts = services.accounts
    fun RoutingContext.requireAdmin() {
        val expected = services.config.adminToken ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "not found")
        val given = bearer(call) ?: ""
        if (!MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())) {
            throw ApiException(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "admin token required")
        }
    }
    fun RoutingContext.target(): Account =
        accounts.get(call.parameters["id"].orEmpty()) ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no such account")

    route("/admin/v1") {
        get("/reports") {
            requireAdmin()
            val status = call.request.queryParameters["status"] ?: "open"
            call.respondJson(HttpStatusCode.OK, AdminReportList(accounts.listReports(status)), AdminReportList.serializer())
        }
        post("/reports/{rid}/resolve") {
            requireAdmin()
            val req = call.body(ResolveReportRequest.serializer())
            val id = call.parameters["rid"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no such report")
            accounts.resolveReport(id, req.resolution.take(500))
            call.respond(HttpStatusCode.NoContent)
        }
        get("/accounts") {
            requireAdmin()
            val a = accounts.findByHandle(call.request.queryParameters["handle"].orEmpty())
                ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no such account")
            call.respondJson(HttpStatusCode.OK, accounts.adminView(a), app.numbaseball.core.protocol.AdminAccount.serializer())
        }
        get("/accounts/{id}") {
            requireAdmin()
            call.respondJson(HttpStatusCode.OK, accounts.adminView(target()), app.numbaseball.core.protocol.AdminAccount.serializer())
        }
        post("/accounts/{id}/ban") {
            requireAdmin()
            val a = target()
            val req = call.body(BanRequest.serializer())
            accounts.setBan(a.id, req.reason.take(500))
            services.hub.accountRemoved(a.id, deleted = false)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/accounts/{id}/unban") {
            requireAdmin()
            accounts.setBan(target().id, null)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/accounts/{id}/chat-restrict") {
            requireAdmin()
            val a = target()
            val req = call.body(ChatRestrictRequest.serializer())
            if (req.hours !in 0..24 * 365) throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "hours must be 0..8760")
            val until = if (req.hours == 0) null else services.clock.now() + req.hours * 3_600_000L
            accounts.restrictChat(a.id, until, if (req.hours == 0) null else req.reason.take(500))
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/accounts/{id}") {
            requireAdmin()
            services.deleteAccount(target().id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
