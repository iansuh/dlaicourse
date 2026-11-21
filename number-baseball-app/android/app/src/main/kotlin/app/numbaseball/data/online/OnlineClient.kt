package app.numbaseball.data.online

import app.numbaseball.core.protocol.Ack
import app.numbaseball.core.protocol.ClientMessage
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.ServerMessage
import app.numbaseball.core.protocol.Welcome
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ConnectionState {
    /** No online screen needs the connection. */
    data object Idle : ConnectionState
    data class Connecting(val attempt: Int) : ConnectionState
    data class Connected(val welcome: Welcome) : ConnectionState
    data class Reconnecting(val attempt: Int, val delayMillis: Long) : ConnectionState

    /** The app stayed in the background past the grace period; reconnects when it returns. */
    data object Paused : ConnectionState

    /** No automatic reconnect (docs/protocol.md §4, §10); needs a user action. */
    data class Stopped(val reason: StopReason) : ConnectionState
}

enum class StopReason {
    /** No account token yet (registration pending). */
    NO_ACCOUNT,

    /** HTTP 401: unknown token, show the "account not found" flow. */
    UNAUTHORIZED,

    /** HTTP 403 or close 4003. */
    BANNED,

    /** Close 4000: a newer connection of this account took over. */
    SESSION_REPLACED,

    /** Close 4004. */
    ACCOUNT_DELETED,
}

/** The real-time channel `/ws/v1`. Lifecycle-aware, reconnects by itself; see [WebSocketOnlineClient]. */
interface OnlineClient {
    val state: StateFlow<ConnectionState>

    /** Every server message, in order. */
    val messages: SharedFlow<ServerMessage>

    /** Reference-counted: the connection is open while at least one user holds it (and the app is in use). */
    fun acquire()
    fun release()

    /** Clears a [ConnectionState.Stopped] state and connects again (for example "Play here" after `session_replaced`). */
    fun retry()

    fun setAppInForeground(foreground: Boolean)

    /** Sends [message] if connected; false otherwise (nothing is queued, the server resends state on reconnect). */
    fun send(message: ClientMessage): Boolean

    /** A fresh request id for the `rid` field. */
    fun nextRid(): Long
}

sealed interface RequestResult {
    data object Ok : RequestResult
    data class Failed(val code: String, val retryAfterMs: Long?) : RequestResult
    data object NotConnected : RequestResult
    data object TimedOut : RequestResult
}

/** Sends a message with a fresh `rid` and waits for the matching `ack` or `error` (docs/protocol.md §5). */
suspend fun OnlineClient.request(timeoutMillis: Long = 10_000, build: (rid: Long) -> ClientMessage): RequestResult =
    coroutineScope {
        val rid = nextRid()
        val reply = async(start = CoroutineStart.UNDISPATCHED) {
            messages.first { (it is Ack && it.rid == rid) || (it is ErrorMessage && it.rid == rid) }
        }
        if (!send(build(rid))) {
            reply.cancel()
            return@coroutineScope RequestResult.NotConnected
        }
        when (val message = withTimeoutOrNull(timeoutMillis) { reply.await() }) {
            null -> {
                reply.cancel()
                RequestResult.TimedOut
            }
            is ErrorMessage -> RequestResult.Failed(message.code, message.retryAfterMs)
            else -> RequestResult.Ok
        }
    }
