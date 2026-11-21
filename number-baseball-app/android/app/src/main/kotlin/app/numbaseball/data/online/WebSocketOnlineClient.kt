package app.numbaseball.data.online

import app.numbaseball.core.protocol.ClientMessage
import app.numbaseball.core.protocol.ProtocolJson
import app.numbaseball.core.protocol.ServerMessage
import app.numbaseball.core.protocol.Welcome
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * OkHttp WebSocket client for `GET /ws/v1` (docs/protocol.md §4, §10).
 *
 * - Auth: `Authorization: Bearer <token>`; the server answers with `welcome`, then `room_state` + `chat_history` if the
 *   account has a seat. Those snapshots replace local state, so a reconnect *is* the resync ([OnlineSession] handles it).
 * - Heartbeat: the server pings every 15 s; [httpClient] should also ping (`pingInterval`) so a dead link fails fast.
 * - Reconnect with [ReconnectPolicy], never after HTTP 401/403 or close 4000/4003/4004.
 * - Lifecycle: open while [acquire]d and the app is in the foreground, plus [backgroundGraceMillis] after it leaves,
 *   so a short app switch keeps the connection (the server holds the seat for another 60 s after that).
 *
 * All connection state is confined to one coroutine that processes [Event]s in order; socket callbacks carry their
 * socket so events from an old socket are ignored.
 */
class WebSocketOnlineClient(
    private val httpClient: OkHttpClient,
    private val url: String,
    private val clientVersion: String,
    private val tokenProvider: suspend () -> String?,
    private val scope: CoroutineScope,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    private val backgroundGraceMillis: Long = 60_000,
    private val json: Json = ProtocolJson,
) : OnlineClient {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 256)
    override val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    private val events = Channel<Event>(Channel.UNLIMITED)
    private val rids = AtomicLong()

    /** The authenticated socket (after `welcome`); read from any thread by [send]. */
    @Volatile private var liveSocket: WebSocket? = null

    // ---- confined to the event loop ----
    private var users = 0
    private var foreground = true
    private var socket: WebSocket? = null
    private var failures = 0
    private var stopReason: StopReason? = null
    private var reconnectJob: Job? = null
    private var graceJob: Job? = null

    init {
        scope.launch { for (event in events) handle(event) }
    }

    override fun acquire() { events.trySend(Event.Acquire) }
    override fun release() { events.trySend(Event.Release) }
    override fun retry() { events.trySend(Event.Retry) }
    override fun setAppInForeground(foreground: Boolean) { events.trySend(Event.Foreground(foreground)) }
    override fun nextRid(): Long = rids.incrementAndGet()

    override fun send(message: ClientMessage): Boolean {
        val ws = liveSocket ?: return false
        return ws.send(json.encodeToString(ClientMessage.serializer(), message))
    }

    private sealed interface Event {
        data object Acquire : Event
        data object Release : Event
        data object Retry : Event
        data class Foreground(val value: Boolean) : Event
        data object GraceExpired : Event
        data object ReconnectDue : Event
        class Text(val ws: WebSocket, val text: String) : Event
        class Closed(val ws: WebSocket, val code: Int) : Event
        class Failed(val ws: WebSocket, val httpStatus: Int?) : Event
    }

    /** May use the connection now: someone needs it, nothing forbids it, and the app is visible or within its grace. */
    private val allowed: Boolean
        get() = users > 0 && stopReason == null && (foreground || graceJob != null)

    private suspend fun handle(event: Event) {
        when (event) {
            Event.Acquire -> {
                users++
                // The account may have been registered since the last attempt.
                if (stopReason == StopReason.NO_ACCOUNT) stopReason = null
                connectIfAllowed()
            }
            Event.Release -> {
                users = (users - 1).coerceAtLeast(0)
                if (users == 0) disconnect(ConnectionState.Idle)
            }
            Event.Retry -> {
                stopReason = null
                failures = 0
                cancelReconnect()
                connectIfAllowed()
            }
            is Event.Foreground -> onForeground(event.value)
            Event.GraceExpired -> {
                graceJob = null
                if (!foreground && users > 0) disconnect(ConnectionState.Paused)
            }
            Event.ReconnectDue -> {
                reconnectJob = null
                connectIfAllowed()
            }
            is Event.Text -> if (event.ws === socket) onText(event.ws, event.text)
            is Event.Closed -> if (event.ws === socket) onSocketGone(closeCode = event.code, httpStatus = null)
            is Event.Failed -> if (event.ws === socket) onSocketGone(closeCode = null, httpStatus = event.httpStatus)
        }
    }

    private suspend fun onForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value) {
            graceJob?.cancel()
            graceJob = null
            if (_state.value == ConnectionState.Paused || _state.value is ConnectionState.Reconnecting) {
                // Coming back: try at once instead of waiting for the next backoff step.
                failures = 0
                cancelReconnect()
            }
            connectIfAllowed()
        } else if (users > 0) {
            graceJob = scope.launch {
                delay(backgroundGraceMillis)
                events.send(Event.GraceExpired)
            }
        }
    }

    private suspend fun connectIfAllowed() {
        if (!allowed || socket != null || reconnectJob != null) return
        val token = tokenProvider()
        if (token == null) {
            stop(StopReason.NO_ACCOUNT)
            return
        }
        _state.value = ConnectionState.Connecting(attempt = failures + 1)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("X-Client-Version", clientVersion)
            .build()
        socket = httpClient.newWebSocket(request, Listener())
    }

    private suspend fun onText(ws: WebSocket, text: String) {
        // Unknown message types and fields are ignored (docs/protocol.md §1).
        val message = runCatching { json.decodeFromString(ServerMessage.serializer(), text) }.getOrNull() ?: return
        if (message is Welcome) {
            failures = 0
            liveSocket = ws
            _state.value = ConnectionState.Connected(message)
        }
        _messages.emit(message)
    }

    private fun onSocketGone(closeCode: Int?, httpStatus: Int?) {
        socket = null
        liveSocket = null
        val reason = ReconnectPolicy.stopReasonFor(closeCode, httpStatus)
        when {
            reason != null -> stop(reason)
            !allowed -> _state.value = if (users == 0) ConnectionState.Idle else ConnectionState.Paused
            else -> {
                failures++
                val delayMillis = reconnectPolicy.delayMillis(failures, closeCode)
                _state.value = ConnectionState.Reconnecting(attempt = failures, delayMillis = delayMillis)
                reconnectJob = scope.launch {
                    delay(delayMillis)
                    events.send(Event.ReconnectDue)
                }
            }
        }
    }

    private fun disconnect(newState: ConnectionState) {
        cancelReconnect()
        if (newState == ConnectionState.Idle) {
            graceJob?.cancel()
            graceJob = null
        }
        closeSocket()
        failures = 0
        if (newState == ConnectionState.Idle) {
            // Nobody needs the connection any more: the next acquire starts fresh.
            stopReason = null
            _state.value = newState
        } else if (_state.value !is ConnectionState.Stopped) {
            _state.value = newState
        }
    }

    private fun stop(reason: StopReason) {
        stopReason = reason
        cancelReconnect()
        closeSocket()
        _state.value = ConnectionState.Stopped(reason)
    }

    private fun closeSocket() {
        val ws = socket ?: return
        socket = null
        liveSocket = null
        ws.close(NORMAL_CLOSURE, null)
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            events.trySend(Event.Text(webSocket, text))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
            events.trySend(Event.Closed(webSocket, code))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            events.trySend(Event.Closed(webSocket, code))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            events.trySend(Event.Failed(webSocket, response?.code))
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
    }
}
