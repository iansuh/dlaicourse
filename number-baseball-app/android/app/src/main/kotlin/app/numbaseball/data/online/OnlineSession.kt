package app.numbaseball.data.online

import app.numbaseball.core.protocol.Chat
import app.numbaseball.core.protocol.ChatEvent
import app.numbaseball.core.protocol.ChatHistory
import app.numbaseball.core.protocol.ChatMessage
import app.numbaseball.core.protocol.ChatSenderDeleted
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.Forfeit
import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.LeaveReason
import app.numbaseball.core.protocol.Phrase
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.QueueLeave
import app.numbaseball.core.protocol.Queued
import app.numbaseball.core.protocol.Resync
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.RoomLeave
import app.numbaseball.core.protocol.RoomLeft
import app.numbaseball.core.protocol.RoomReset
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoomSettingsUpdate
import app.numbaseball.core.protocol.RoomState
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.ServerMessage
import app.numbaseball.core.protocol.SetSecret
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.protocol.TeamSet
import app.numbaseball.core.protocol.Welcome
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface QueueState {
    data object Idle : QueueState

    /** The user wants a quick match; `queue_join` is (re)sent whenever a connection is established. */
    data class Searching(val digits: Int, val since: Long? = null) : QueueState
}

sealed interface SessionEvent {
    /** `welcome.roomId` was null although we had a room: the seat is gone (docs/protocol.md §10.3). */
    data object SeatLost : SessionEvent
    data class LeftRoom(val reason: LeaveReason) : SessionEvent

    /** An error without a request id (requests with `rid` get theirs from [request]). */
    data class Error(val code: String) : SessionEvent
}

/**
 * The online state the screens observe, rebuilt from server snapshots. Every connect brings `welcome` + `room_state` +
 * `chat_history`, which replace local state, so nothing is replayed after a reconnect. The quick-match queue does not
 * survive a reconnect, so it is re-joined here while the user is still searching.
 */
class OnlineSession(
    private val client: OnlineClient,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val connection: StateFlow<ConnectionState> get() = client.state

    private val _room = MutableStateFlow<RoomView?>(null)
    val room: StateFlow<RoomView?> = _room.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())

    /** Chat lines of the current room, oldest first. Lines of blocked players are hidden by [hideSender]. */
    val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()

    private val _chatReadUpTo = MutableStateFlow(-1L)

    /** Highest chat id the player has seen with the chat sheet open (lobby chat peek badge, 07 §3). */
    val chatReadUpTo: StateFlow<Long> = _chatReadUpTo.asStateFlow()

    fun markChatRead() {
        _chat.value.lastOrNull()?.let { last -> _chatReadUpTo.update { maxOf(it, last.id) } }
    }

    private val _offlineSince = MutableStateFlow<Map<String, Long>>(emptyMap())

    /**
     * Server time at which each player of the current room was first seen offline in a snapshot (D2-14). Kept for the
     * whole session, so a screen opened later (the team board) counts the 60 s grace from the same moment as the duel
     * strip instead of from when it was opened.
     */
    val offlineSince: StateFlow<Map<String, Long>> = _offlineSince.asStateFlow()

    private val _queue = MutableStateFlow<QueueState>(QueueState.Idle)
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** Fresh `welcome` account data (stats, renamed nickname) for the account repository. */
    private val _welcomes = MutableSharedFlow<Welcome>(extraBufferCapacity = 4)
    val welcomes: SharedFlow<Welcome> = _welcomes.asSharedFlow()

    /** `serverTime - localTime`, updated from every snapshot; countdowns use `deadline - (now + offset)`. */
    @Volatile var serverTimeOffset: Long = 0
        private set

    // Written by the message collector and by UI commands.
    @Volatile private var chatRoomId: String? = null
    private val hiddenSenders: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        scope.launch { client.messages.collect { onMessage(it) } }
    }

    fun acquire() = client.acquire()
    fun release() = client.release()
    fun retryConnection() = client.retry()
    fun serverNow(): Long = clock() + serverTimeOffset

    // ---- commands ----

    fun startQuickMatch(digits: Int) {
        _queue.value = QueueState.Searching(digits)
        client.send(QueueJoin(digits))
    }

    fun cancelQuickMatch() {
        if (_queue.value is QueueState.Searching) client.send(QueueLeave())
        _queue.value = QueueState.Idle
    }

    suspend fun createRoom(settings: RoomSettings): RequestResult = client.request { RoomCreate(settings, it) }
    suspend fun joinRoom(code: String): RequestResult = client.request { RoomJoin(code, it) }
    suspend fun updateSettings(settings: RoomSettings): RequestResult = client.request { RoomSettingsUpdate(settings, it) }
    suspend fun setTeam(team: Team, playerId: String? = null): RequestResult = client.request { TeamSet(team, playerId, it) }
    suspend fun resetRoom(): RequestResult = client.request { RoomReset(it) }
    suspend fun setSecret(secret: String): RequestResult = client.request { SetSecret(secret, it) }
    suspend fun guess(guess: String, round: Int): RequestResult = client.request { Guess(guess, round, it) }
    suspend fun forfeit(): RequestResult = client.request { Forfeit(it) }
    suspend fun sendChat(text: String): RequestResult = client.request { Chat(text, it) }
    suspend fun sendPhrase(phraseId: String): RequestResult = client.request { Phrase(phraseId, it) }

    fun leaveRoom() {
        client.send(RoomLeave())
        clearRoom()
    }

    /** Asks the server for fresh snapshots (docs/protocol.md §10.4). */
    fun resync() {
        client.send(Resync())
    }

    /** Hides a blocked player's lines at once (the server stops delivering new ones). */
    fun hideSender(playerId: String) {
        hiddenSenders += playerId
        _chat.update { lines -> lines.filter { it.senderId != playerId } }
    }

    // ---- server messages ----

    private suspend fun onMessage(message: ServerMessage) {
        when (message) {
            is Welcome -> onWelcome(message)
            is RoomState -> onRoomState(message.room)
            is RoomLeft -> if (_room.value?.id == message.roomId) {
                clearRoom()
                _events.emit(SessionEvent.LeftRoom(message.reason))
            }
            is Queued -> _queue.update { if (it is QueueState.Searching) it.copy(since = message.since) else it }
            is ChatHistory -> if (message.roomId == _room.value?.id) {
                chatRoomId = message.roomId
                _chat.value = message.messages.filterNot { it.senderId in hiddenSenders }
            }
            is ChatEvent -> if (message.roomId == _room.value?.id && message.message.senderId !in hiddenSenders) {
                if (chatRoomId != message.roomId) {
                    chatRoomId = message.roomId
                    _chat.value = emptyList()
                }
                _chat.update { (it + message.message).takeLast(CHAT_KEEP) }
            }
            is ChatSenderDeleted -> if (message.roomId == chatRoomId) {
                _chat.update { lines ->
                    lines.map {
                        if (it.senderId == message.senderId) it.copy(senderId = null, senderNickname = null, senderTag = null) else it
                    }
                }
            }
            is ErrorMessage -> if (message.rid == null) _events.emit(SessionEvent.Error(message.code))
            else -> Unit
        }
    }

    private suspend fun onWelcome(welcome: Welcome) {
        serverTimeOffset = welcome.serverTime - clock()
        _welcomes.emit(welcome)
        if (welcome.roomId == null && _room.value != null) {
            clearRoom()
            _events.emit(SessionEvent.SeatLost)
        }
        val queue = _queue.value
        if (queue is QueueState.Searching && welcome.roomId == null) client.send(QueueJoin(queue.digits))
    }

    private fun onRoomState(room: RoomView) {
        val current = _room.value
        if (current != null && current.id == room.id && room.version < current.version) return
        serverTimeOffset = room.serverTime - clock()
        if (current?.id != room.id) {
            chatRoomId = room.id
            _chat.value = emptyList()
            _chatReadUpTo.value = -1L
        }
        val known = if (current?.id == room.id) _offlineSince.value else emptyMap()
        _offlineSince.value = room.players.filter { !it.connected && !it.left }.associate { it.id to (known[it.id] ?: room.serverTime) }
        _room.value = room
        if (room.kind == RoomKind.QUICK) _queue.value = QueueState.Idle
    }

    private fun clearRoom() {
        _room.value = null
        _offlineSince.value = emptyMap()
        _chat.value = emptyList()
        _chatReadUpTo.value = -1L
        chatRoomId = null
    }

    private companion object {
        const val CHAT_KEEP = 100
    }
}
