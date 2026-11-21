package app.numbaseball.server

import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.ai.ComputerPlayer
import app.numbaseball.core.match.ActionResult
import app.numbaseball.core.match.DuelConfig
import app.numbaseball.core.match.TeamMatch
import app.numbaseball.core.protocol.Ack
import app.numbaseball.core.protocol.Chat
import app.numbaseball.core.protocol.ChatEvent
import app.numbaseball.core.protocol.ChatHistory
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.ChatMessage
import app.numbaseball.core.protocol.ChatSenderDeleted
import app.numbaseball.core.protocol.ClientMessage
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.ErrorMessage
import app.numbaseball.core.protocol.Forfeit
import app.numbaseball.core.protocol.Guess
import app.numbaseball.core.protocol.LeaveReason
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.PROTOCOL_VERSION
import app.numbaseball.core.protocol.Phrase
import app.numbaseball.core.protocol.Ping
import app.numbaseball.core.protocol.Pong
import app.numbaseball.core.protocol.QueueJoin
import app.numbaseball.core.protocol.QueueLeave
import app.numbaseball.core.protocol.Queued
import app.numbaseball.core.protocol.ReportSnapshot
import app.numbaseball.core.protocol.Resync
import app.numbaseball.core.protocol.RoomCreate
import app.numbaseball.core.protocol.RoomJoin
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.RoomLeave
import app.numbaseball.core.protocol.RoomLeft
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomReset
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoomSettingsUpdate
import app.numbaseball.core.protocol.RoomState
import app.numbaseball.core.protocol.SetSecret
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.protocol.TeamSet
import app.numbaseball.core.protocol.Welcome
import app.numbaseball.core.text.ChatFilter
import app.numbaseball.core.text.QuickPhrases
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * The real-time part of the server: connections, the quick-match queue, rooms, matches and chat.
 *
 * Every state change runs under one mutex, so the logic below is single-threaded. Game rules, timers and team results
 * come from the pure engines in `:core` (TeamMatch / Duel); the hub feeds them the current time and turns their state
 * into personalised `room_state` snapshots. Secrets and guesses only ever live in memory.
 */
class Hub(
    private val accounts: AccountService,
    private val clock: Clock,
    private val config: Config,
    private val random: Random,
) {
    private val mutex = Mutex()
    private val limiter = RateLimiter(clock)
    private val sessions = HashMap<String, ClientSession>()
    private val queue = LinkedHashMap<String, QueueEntry>()
    private val rooms = LinkedHashMap<String, Room>()
    private val roomsByCode = HashMap<String, Room>()
    private val seatOf = HashMap<String, Room>()
    private var seatOrder = 0L

    private class QueueEntry(val accountId: String, val digits: Int, val since: Long)

    companion object {
        const val GRACE_MS = Limits.DISCONNECT_GRACE_MS
        const val IDLE_ROOM_MS = 10 * 60 * 1000L
        const val CHAT_BURST = 5
        const val CHAT_WINDOW_MS = 10_000L
        const val RECENT_FOR_REPORT = 20
        const val BOT_ID = "reviewbot0001"
        const val BOT_NICKNAME = "ReviewBot"
        const val BOT_SECRET_DELAY_MS = 3_000L
        const val BOT_GUESS_DELAY_MS = 4_000L
        const val BOT_TEXT = "Hi! I am a test opponent for app review. Good luck!"
    }

    /** Thrown inside handlers to answer with an error. */
    private class Reject(val code: String, val text: String = code, val retryAfterMs: Long? = null) : Exception(text, null, false, false)

    private fun reject(code: String, text: String = code, retryAfterMs: Long? = null): Nothing = throw Reject(code, text, retryAfterMs)

    // ------------------------------------------------------------------ connections

    suspend fun connect(account: Account, session: ClientSession) = mutex.withLock {
        val now = clock.now()
        val id = account.id
        sessions.put(id, session)?.let { old ->
            old.send(ErrorMessage(ErrorCodes.SESSION_REPLACED, "a newer connection took over"))
            old.close(4000, "session_replaced")
        }
        queue.remove(id)
        val room = seatOf[id]
        session.send(Welcome(PROTOCOL_VERSION, now, accounts.toDto(account), room?.id))
        if (room != null) {
            val seat = room.seats.getValue(id)
            seat.session = session
            seat.disconnectedAt = null
            room.lastActiveAt = now
            room.match?.takeIf { room.phase == RoomPhase.PLAYING }?.reconnected(id, now)
            afterMatchChange(room, now)
            broadcast(room, now)
            sendHistory(room, seat)
        }
    }

    suspend fun disconnect(session: ClientSession) = mutex.withLock {
        val id = session.accountId
        if (sessions[id] !== session) return@withLock
        sessions.remove(id)
        queue.remove(id)
        val room = seatOf[id] ?: return@withLock
        val seat = room.seats[id] ?: return@withLock
        val now = clock.now()
        seat.session = null
        seat.disconnectedAt = now
        if (room.phase == RoomPhase.PLAYING) room.match?.disconnected(id, now)
        broadcast(room, now)
    }

    suspend fun handle(session: ClientSession, msg: ClientMessage) = mutex.withLock {
        if (sessions[session.accountId] !== session) return@withLock
        val now = clock.now()
        try {
            dispatch(session, msg, now)
            msg.rid?.takeIf { msg !is Ping }?.let { session.send(Ack(it)) }
        } catch (r: Reject) {
            session.send(ErrorMessage(r.code, r.text, msg.rid, r.retryAfterMs))
        }
    }

    private fun dispatch(session: ClientSession, msg: ClientMessage, now: Long) {
        val me = session.accountId
        when (msg) {
            is Ping -> session.send(Pong(msg.rid, now))
            is Resync -> {
                val room = seatOf[me]
                if (room != null) {
                    session.send(RoomState(room.view(me, now)))
                    sendHistory(room, room.seats.getValue(me))
                } else queue[me]?.let { session.send(Queued(it.digits, it.since)) }
            }
            is QueueJoin -> queueJoin(session, msg.digits, now)
            is QueueLeave -> if (queue.remove(me) == null) reject(ErrorCodes.NOT_QUEUED)
            is RoomCreate -> roomCreate(session, msg.settings, now)
            is RoomJoin -> roomJoin(session, msg.code, now)
            is RoomLeave -> {
                val room = seatOf[me] ?: reject(ErrorCodes.NOT_IN_ROOM)
                leave(room, room.seats.getValue(me), LeaveReason.LEFT, now)
            }
            is RoomSettingsUpdate -> roomSettings(me, msg.settings, now)
            is TeamSet -> teamSet(me, msg.team, msg.playerId, now)
            is RoomReset -> roomReset(me, now)
            is SetSecret -> duelAction(me, now) { m -> m.setSecret(me, msg.secret, now) }
            is Guess -> duelAction(me, now) { m -> m.guess(me, msg.guess, msg.round, now) }
            is Forfeit -> duelAction(me, now) { m -> m.forfeit(me, now) }
            is Chat -> chat(me, msg.text, now)
            is Phrase -> phrase(me, msg.phraseId, now)
        }
    }

    // ------------------------------------------------------------------ queue

    private fun requireTerms(me: String) {
        if (!accounts.hasAcceptedTerms(me)) reject(ErrorCodes.TERMS_REQUIRED, "accept the current Terms of Use first")
    }

    private fun queueJoin(session: ClientSession, digits: Int, now: Long) {
        val me = session.accountId
        if (digits !in Limits.DIGITS) reject(ErrorCodes.INVALID_SETTINGS, "digits must be 3 to 6")
        if (seatOf.containsKey(me)) reject(ErrorCodes.ALREADY_IN_ROOM)
        if (queue.containsKey(me)) reject(ErrorCodes.ALREADY_QUEUED)
        requireTerms(me)
        val entry = QueueEntry(me, digits, now)
        queue[me] = entry
        session.send(Queued(digits, now))
        pairQueue(now)
    }

    /** First come, first served among the same digit count, never pairing players who blocked each other. */
    private fun pairQueue(now: Long) {
        var paired = true
        while (paired) {
            paired = false
            val entries = queue.values.toList()
            outer@ for (i in entries.indices) for (j in i + 1 until entries.size) {
                val a = entries[i]
                val b = entries[j]
                if (a.digits != b.digits || accounts.isBlockedEitherWay(a.accountId, b.accountId)) continue
                queue.remove(a.accountId)
                queue.remove(b.accountId)
                startQuickRoom(a, b, now)
                paired = true
                break@outer
            }
        }
    }

    private fun startQuickRoom(a: QueueEntry, b: QueueEntry, now: Long) {
        val room = Room(newRoomId(), RoomKind.QUICK, null, RoomSettings(a.digits, 1, Limits.QUICK_ROUND_SECONDS), now)
        rooms[room.id] = room
        addSeat(room, a.accountId, Team.A)
        addSeat(room, b.accountId, Team.B)
        startMatch(room, now)
        broadcast(room, now)
        room.seats.values.forEach { sendHistory(room, it) }
    }

    // ------------------------------------------------------------------ rooms

    private fun newRoomId(): String {
        var id: String
        do { id = "r_" + (1..10).map { "abcdefghijkmnpqrstuvwxyz23456789"[random.nextInt(32)] }.joinToString("") } while (rooms.containsKey(id))
        return id
    }

    private fun newRoomCode(): String {
        var code: String
        do {
            code = (1..Limits.ROOM_CODE_LENGTH).map { RoomCodes.ALPHABET[random.nextInt(RoomCodes.ALPHABET.length)] }.joinToString("")
        } while (roomsByCode.containsKey(code) || code == config.reviewRoomCode)
        return code
    }

    private fun validSettings(s: RoomSettings): Boolean =
        s.digits in Limits.DIGITS && s.teamSize in Limits.TEAM_SIZE && s.roundSeconds in Limits.ROUND_SECONDS

    private fun addSeat(room: Room, id: String, team: Team, bot: Boolean = false): Seat {
        val a = accounts.get(id)
        val seat = Seat(id, a?.nickname ?: id, a?.tagText ?: "0000", team, ++seatOrder, bot)
        if (!bot) {
            seat.session = sessions[id]
            if (seat.session == null) seat.disconnectedAt = clock.now()
            seatOf[id] = room
        }
        room.seats[id] = seat
        room.members += id
        return seat
    }

    private fun roomCreate(session: ClientSession, settings: RoomSettings, now: Long) {
        val me = session.accountId
        if (!validSettings(settings)) reject(ErrorCodes.INVALID_SETTINGS)
        if (seatOf.containsKey(me) || queue.containsKey(me)) reject(ErrorCodes.ALREADY_IN_ROOM)
        requireTerms(me)
        val room = Room(newRoomId(), RoomKind.PRIVATE, newRoomCode(), settings, now)
        rooms[room.id] = room
        roomsByCode[room.code!!] = room
        room.hostId = me
        val seat = addSeat(room, me, Team.A)
        maybeStart(room, now)
        broadcast(room, now)
        sendHistory(room, seat)
    }

    private fun roomJoin(session: ClientSession, rawCode: String, now: Long) {
        val me = session.accountId
        val code = RoomCodes.normalize(rawCode.take(64))
        if (queue.containsKey(me)) reject(ErrorCodes.ALREADY_IN_ROOM, "leave the queue first")
        seatOf[me]?.let { current ->
            if (current.code != null && current.code == code) {
                session.send(RoomState(current.view(me, now)))
                sendHistory(current, current.seats.getValue(me))
                return
            }
            reject(ErrorCodes.ALREADY_IN_ROOM)
        }
        if (config.reviewRoomCode != null && code == config.reviewRoomCode) {
            requireTerms(me)
            startReviewRoom(me, now)
            return
        }
        val room = roomsByCode[code] ?: reject(ErrorCodes.ROOM_NOT_FOUND)
        if (room.phase != RoomPhase.LOBBY) reject(ErrorCodes.MATCH_IN_PROGRESS)
        if (room.isFull) reject(ErrorCodes.ROOM_FULL)
        requireTerms(me)
        val a = room.active(Team.A).size
        val b = room.active(Team.B).size
        val team = if (a <= b && a < room.settings.teamSize) Team.A else Team.B
        val seat = addSeat(room, me, team)
        maybeStart(room, now)
        broadcast(room, now)
        sendHistory(room, seat)
    }

    private fun hostRoom(me: String): Room {
        val room = seatOf[me] ?: reject(ErrorCodes.NOT_IN_ROOM)
        if (room.kind != RoomKind.PRIVATE || room.hostId != me) reject(ErrorCodes.NOT_HOST)
        return room
    }

    private fun roomSettings(me: String, settings: RoomSettings, now: Long) {
        val room = hostRoom(me)
        if (room.phase != RoomPhase.LOBBY) reject(ErrorCodes.WRONG_PHASE)
        if (!validSettings(settings)) reject(ErrorCodes.INVALID_SETTINGS)
        if (room.active(Team.A).size > settings.teamSize || room.active(Team.B).size > settings.teamSize) {
            reject(ErrorCodes.INVALID_SETTINGS, "a team already has more players than the new team size")
        }
        room.settings = settings
        maybeStart(room, now)
        broadcast(room, now)
    }

    private fun teamSet(me: String, team: Team, playerId: String?, now: Long) {
        val room = seatOf[me] ?: reject(ErrorCodes.NOT_IN_ROOM)
        if (room.kind != RoomKind.PRIVATE) reject(ErrorCodes.NOT_HOST, "quick rooms have no teams to choose")
        if (room.phase != RoomPhase.LOBBY) reject(ErrorCodes.WRONG_PHASE)
        val targetId = playerId ?: me
        if (targetId != me && room.hostId != me) reject(ErrorCodes.NOT_HOST)
        val seat = room.seats[targetId]?.takeIf { !it.left } ?: reject(ErrorCodes.NOT_FOUND, "no such player in this room")
        if (seat.team == team) return
        if (room.active(team).size >= room.settings.teamSize) reject(ErrorCodes.TEAM_FULL)
        seat.team = team
        broadcast(room, now)
    }

    private fun roomReset(me: String, now: Long) {
        val room = hostRoom(me)
        if (room.phase != RoomPhase.FINISHED) reject(ErrorCodes.WRONG_PHASE)
        room.seats.values.removeIf { it.left }
        room.phase = RoomPhase.LOBBY
        room.match = null
        maybeStart(room, now)
        broadcast(room, now)
    }

    /** Private rooms start by themselves as soon as both teams are full. */
    private fun maybeStart(room: Room, now: Long) {
        if (room.phase == RoomPhase.LOBBY && room.isFull) startMatch(room, now)
    }

    private fun startMatch(room: Room, now: Long) {
        val a = room.active(Team.A).shuffled(random)
        val b = room.active(Team.B).shuffled(random)
        val pairs = a.zip(b).map { it.first.id to it.second.id }
        val m = TeamMatch(DuelConfig.of(room.settings.digits, room.settings.roundSeconds), pairs, random)
        m.start(now)
        room.match = m
        room.phase = RoomPhase.PLAYING
        room.matchNumber++
        room.resultRecorded = false
        for (s in room.seats.values) if (!s.bot && s.session == null) m.disconnected(s.id, now)
        room.bot?.let { it.ai = ComputerPlayer(ComputerLevel.EASY, room.settings.digits, random); it.observed = 0 }
    }

    /** Records results once the match has ended. */
    private fun afterMatchChange(room: Room, now: Long) {
        val m = room.match ?: return
        if (room.phase != RoomPhase.PLAYING || !m.isFinished) return
        room.phase = RoomPhase.FINISHED
        if (!room.resultRecorded) {
            room.resultRecorded = true
            val winner = m.result?.winner
            val results = HashMap<String, MatchOutcome>()
            m.pairs.forEach { (pa, pb) ->
                for ((id, team) in listOf(pa to Team.A, pb to Team.B)) {
                    val seat = room.seats[id]
                    if (seat == null || seat.bot || seat.deleted) continue
                    results[id] = when (winner) { null -> MatchOutcome.DRAW; team -> MatchOutcome.WIN; else -> MatchOutcome.LOSS }
                }
            }
            accounts.recordResults(results)
        }
        room.lastActiveAt = maxOf(room.lastActiveAt, now)
    }

    private fun leave(room: Room, seat: Seat, reason: LeaveReason, now: Long) {
        val session = seat.session
        when (room.phase) {
            RoomPhase.LOBBY -> room.seats.remove(seat.id)
            RoomPhase.PLAYING -> {
                val m = room.match!!
                m.locate(seat.id)?.let { (i, _) -> if (!m.duels[i].isFinished) m.forfeit(seat.id, now) }
                seat.left = true
            }
            RoomPhase.FINISHED -> seat.left = true
        }
        seat.session = null
        seat.disconnectedAt = null
        seatOf.remove(seat.id)
        session?.send(RoomLeft(room.id, reason))
        afterMatchChange(room, now)
        if (room.humans().isEmpty()) {
            closeRoom(room, LeaveReason.CLOSED)
            return
        }
        if (room.hostId == seat.id) room.hostId = room.humans().minByOrNull { it.order }?.id
        broadcast(room, now)
    }

    private fun closeRoom(room: Room, reason: LeaveReason) {
        for (s in room.seats.values) {
            if (seatOf[s.id] === room) {
                seatOf.remove(s.id)
                s.session?.send(RoomLeft(room.id, reason))
            }
        }
        rooms.remove(room.id)
        room.code?.let { if (roomsByCode[it] === room) roomsByCode.remove(it) }
    }

    // ------------------------------------------------------------------ duels

    private inline fun duelAction(me: String, now: Long, action: (TeamMatch) -> ActionResult) {
        val room = seatOf[me] ?: reject(ErrorCodes.NOT_IN_ROOM)
        val m = room.match
        if (m == null || room.phase == RoomPhase.LOBBY) reject(ErrorCodes.WRONG_PHASE, "there is no match yet")
        val result = action(m)
        afterMatchChange(room, now)
        if (result is ActionResult.Rejected) {
            broadcast(room, now) // timers may have fired on the way
            reject(result.code)
        }
        broadcast(room, now)
    }

    // ------------------------------------------------------------------ chat

    private fun chatRoom(me: String): Room = seatOf[me] ?: reject(ErrorCodes.NOT_IN_ROOM)

    private fun chatChecks(me: String) {
        requireTerms(me)
        accounts.chatRestrictedUntil(me)?.let { reject(ErrorCodes.CHAT_RESTRICTED, "chat is restricted", it - clock.now()) }
        val wait = limiter.acquire("chat:$me", CHAT_BURST, CHAT_WINDOW_MS)
        if (wait > 0) reject(ErrorCodes.CHAT_RATE_LIMITED, "slow down", wait)
    }

    private fun chat(me: String, text: String, now: Long) {
        val room = chatRoom(me)
        if (room.kind == RoomKind.QUICK && !config.quickMatchFreeChat) {
            reject(ErrorCodes.CHAT_NOT_ALLOWED, "quick match allows quick phrases only")
        }
        val clean = when (val r = ChatFilter.clean(text)) {
            is ChatFilter.Result.Ok -> r.text
            is ChatFilter.Result.Rejected -> reject(r.code)
        }
        chatChecks(me)
        post(room, room.seats.getValue(me), ChatKind.TEXT, clean, null, now)
    }

    private fun phrase(me: String, phraseId: String, now: Long) {
        val room = chatRoom(me)
        if (!QuickPhrases.isValid(phraseId)) reject(ErrorCodes.UNKNOWN_PHRASE)
        chatChecks(me)
        post(room, room.seats.getValue(me), ChatKind.PHRASE, null, phraseId, now)
    }

    private fun post(room: Room, from: Seat, kind: ChatKind, text: String?, phraseId: String?, now: Long) {
        val msg = ChatMessage(++room.chatSeq, now, from.id, from.nickname, from.tag, from.team, kind, text, phraseId)
        room.chat.addLast(msg)
        while (room.chat.size > Limits.CHAT_HISTORY) room.chat.removeFirst()
        for (s in room.seats.values) {
            val session = s.session ?: continue
            if (s.left || (s.id != from.id && accounts.isBlockedEitherWay(s.id, from.id))) continue
            session.send(ChatEvent(room.id, msg))
        }
    }

    private fun sendHistory(room: Room, seat: Seat) {
        val session = seat.session ?: return
        val visible = room.chat.filter { m -> m.senderId == null || m.senderId == seat.id || !accounts.isBlockedEitherWay(seat.id, m.senderId!!) }
        session.send(ChatHistory(room.id, visible))
    }

    private fun broadcast(room: Room, now: Long) {
        if (!rooms.containsKey(room.id)) return
        room.version++
        for (s in room.seats.values) {
            if (s.left) continue
            s.session?.send(RoomState(room.view(s.id, now)))
        }
    }

    // ------------------------------------------------------------------ review room (Play review)

    private fun startReviewRoom(me: String, now: Long) {
        val bot = accounts.ensureBot(BOT_ID, BOT_NICKNAME)
        val room = Room(newRoomId(), RoomKind.PRIVATE, config.reviewRoomCode, RoomSettings(4, 1, 120), now)
        rooms[room.id] = room
        room.hostId = me
        val seat = addSeat(room, me, Team.A)
        addSeat(room, bot.id, Team.B, bot = true)
        room.bot = ReviewBotState(bot.id, ComputerPlayer(ComputerLevel.EASY, 4, random))
        startMatch(room, now)
        broadcast(room, now)
        sendHistory(room, seat)
    }

    /** The bot chooses and guesses a few seconds after each phase starts, and says hello once (phrase and text). */
    private fun botStep(room: Room, now: Long): Boolean {
        val bot = room.bot ?: return false
        val seat = room.seats[bot.seatId] ?: return false
        var changed = false
        if (!bot.saidHello && now >= room.createdAt + 1_000) {
            bot.saidHello = true
            post(room, seat, ChatKind.PHRASE, null, "hello", now)
        }
        if (!bot.saidText && now >= room.createdAt + 2_500) {
            bot.saidText = true
            post(room, seat, ChatKind.TEXT, BOT_TEXT, null, now)
        }
        val m = room.match ?: return false
        if (room.phase != RoomPhase.PLAYING) return false
        val (i, team) = m.locate(bot.seatId) ?: return false
        val d = m.duels[i]
        if (d.phase == DuelPhase.CHOOSING && d.secretOf(team) == null && now >= d.phaseStartedAt + BOT_SECRET_DELAY_MS) {
            m.setSecret(bot.seatId, bot.ai.chooseSecret(), now)
            changed = true
        }
        if (d.phase == DuelPhase.GUESSING && d.pendingGuessOf(team) == null && now >= d.phaseStartedAt + BOT_GUESS_DELAY_MS) {
            val rounds = d.roundsOf(team)
            while (bot.observed < rounds.size) {
                val r = rounds[bot.observed++]
                if (r.guess != null) bot.ai.onResult(r.guess!!, r.strikes, r.balls)
            }
            m.guess(bot.seatId, bot.ai.nextGuess(), d.round, now)
            changed = true
        }
        return changed
    }

    // ------------------------------------------------------------------ timers

    /** Fires due timers: round deadlines, disconnect forfeits, lobby clean-up, idle rooms, bot moves. */
    suspend fun tick() = mutex.withLock {
        val now = clock.now()
        for (room in rooms.values.toList()) {
            var changed = false
            if (room.phase == RoomPhase.PLAYING && room.match!!.advance(now)) changed = true
            if (botStep(room, now)) changed = true
            if (changed) afterMatchChange(room, now)
            if (room.seats.values.any { it.session != null }) room.lastActiveAt = now
            if (room.phase != RoomPhase.PLAYING) {
                val gone = room.seats.values.filter { !it.bot && !it.left && it.session == null && (it.disconnectedAt ?: now) + GRACE_MS <= now }
                for (s in gone) {
                    if (!rooms.containsKey(room.id)) break
                    leave(room, s, LeaveReason.INACTIVE, now)
                    changed = false // leave() already broadcast
                }
            }
            if (!rooms.containsKey(room.id)) continue
            if (now - room.lastActiveAt >= IDLE_ROOM_MS) {
                closeRoom(room, LeaveReason.CLOSED)
                continue
            }
            if (changed) broadcast(room, now)
        }
    }

    // ------------------------------------------------------------------ account events from REST / admin

    suspend fun accountRenamed(id: String) = mutex.withLock {
        val a = accounts.get(id) ?: return@withLock
        val now = clock.now()
        for (room in rooms.values) {
            val seat = room.seats[id] ?: continue
            seat.nickname = a.nickname
            seat.tag = a.tagText
            broadcast(room, now)
        }
    }

    /** The account was deleted or banned: leave queue and room, close its connection. */
    suspend fun accountRemoved(id: String, deleted: Boolean) = mutex.withLock {
        val now = clock.now()
        queue.remove(id)
        val reason = if (deleted) LeaveReason.ACCOUNT_DELETED else LeaveReason.BANNED
        seatOf[id]?.let { room -> leave(room, room.seats.getValue(id), reason, now) }
        if (deleted) {
            for (room in rooms.values.toList()) {
                var touched = false
                if (room.chat.any { it.senderId == id }) {
                    val rewritten = room.chat.map { if (it.senderId == id) it.copy(senderId = null, senderNickname = null, senderTag = null) else it }
                    room.chat.clear()
                    room.chat.addAll(rewritten)
                    for (s in room.seats.values) if (!s.left) s.session?.send(ChatSenderDeleted(room.id, id))
                }
                room.seats[id]?.let { it.deleted = true; it.nickname = ""; it.tag = ""; touched = true }
                if (touched) broadcast(room, now)
            }
        }
        sessions.remove(id)?.let { s ->
            s.send(ErrorMessage(if (deleted) ErrorCodes.UNAUTHORIZED else ErrorCodes.BANNED, if (deleted) "account deleted" else "account banned"))
            s.close(if (deleted) 4004 else 4003, if (deleted) "account_deleted" else "banned")
        }
    }

    /** Closes the account's live connection as replaced (used after a transfer revoked its token). */
    suspend fun closeSession(id: String) = mutex.withLock {
        sessions[id]?.let { s ->
            s.send(ErrorMessage(ErrorCodes.SESSION_REPLACED, "the account moved to another device"))
            s.close(4000, "session_replaced")
        }
    }

    /**
     * Context for a report: the reported message and the last messages of the room, as delivered (masked), if the room
     * is alive and the reporter is or was in it.
     */
    suspend fun reportSnapshot(reporter: String, roomId: String?, messageId: Long?, targetId: String): ReportSnapshot? = mutex.withLock {
        val room = roomId?.let { rooms[it] } ?: return@withLock null
        if (reporter !in room.members) return@withLock null
        val message = messageId?.let { mid -> room.chat.firstOrNull { it.id == mid } }
        if (message != null && message.senderId != targetId) {
            throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "that message was not sent by targetId")
        }
        ReportSnapshot(message, room.chat.toList().takeLast(RECENT_FOR_REPORT))
    }

    /** Live counts for /health and tests. */
    suspend fun counts(): Map<String, Int> = mutex.withLock {
        mapOf("connections" to sessions.size, "queued" to queue.size, "rooms" to rooms.size)
    }
}
