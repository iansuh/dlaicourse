package app.numbaseball.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.Team
import app.numbaseball.core.rules.NumberProblem
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.online.RequestResult
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.toUiError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Keypad state that only exists on this device; reset when the duel moves to another round. */
data class LocalInput(
    val text: String = "",
    val problem: NumberProblem? = null,
    val busy: Boolean = false,
    val error: UiError? = null,
    val round: Int = -1,
)

/** Local view state the server does not send: entry, memo, disconnect timers (server time ms). */
data class LocalMatchState(
    val input: LocalInput = LocalInput(),
    val memo: MemoState? = null,
    val opponentOfflineSince: Long? = null,
    val myOfflineSince: Long? = null,
)

/** Online duel: the server snapshot (`room_state`) is the truth; this only adds the keypad, memo and countdowns. */
class OnlineMatchViewModel(private val session: OnlineSession) : ViewModel() {
    private val local = MutableStateFlow(LocalMatchState())
    private val ticker = flow {
        while (true) {
            emit(session.serverNow())
            delay(TICK_MILLIS)
        }
    }
    private val startedAt = session.serverNow()

    /** null when there is no running duel any more (seat lost, left): the screen goes home. */
    val state: StateFlow<MatchUiState?> = combine(session.room, local, ticker, session.connection) { room, l, now, connection ->
        trackOffline(room, connection, now)
        room?.toMatchUiState(l, now, startedAt)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), session.room.value?.toMatchUiState(LocalMatchState(), session.serverNow(), startedAt))

    val connection get() = session.connection

    init {
        session.acquire()
    }

    override fun onCleared() {
        session.release()
    }

    fun onDigit(digit: Char) = edit { current, digits -> NumberInput.append(current, digit, digits) }
    fun onDelete() = edit { current, _ -> NumberInput.deleteLast(current) }
    fun onClear() = edit { _, _ -> "" }
    fun onTapSlot(index: Int) = edit { current, _ -> current.take(index) }

    fun onRandomSecret() {
        if (state.value?.phase == MatchPhase.CHOOSING_SECRET) edit { current, digits -> NumberInput.fillRandom(current, digits) }
    }

    fun onMemoDigit(digit: Int) = memo { Memo.cycleDigit(it, digit) }
    fun onMemoCell(position: Int, digit: Int) = memo { Memo.cycleCell(it, position, digit) }
    fun onMemoClear() = memo { Memo.clear(it) }
    fun onMemoCollapse(collapsed: Boolean) = memo { Memo.setCollapsed(it, collapsed) }

    fun onSubmit() {
        val ui = state.value ?: return
        if (!ui.acceptsInput) return
        val problem = NumberInput.problem(ui.input, ui.digits)
        if (problem != null) {
            local.update { it.copy(input = it.input.copy(problem = problem)) }
            return
        }
        local.update { it.copy(input = it.input.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val result = if (ui.phase == MatchPhase.CHOOSING_SECRET) session.setSecret(ui.input) else session.guess(ui.input, ui.round)
            local.update {
                it.copy(input = if (result == RequestResult.Ok) LocalInput(round = it.input.round) else it.input.copy(busy = false, error = result.toUiError()))
            }
        }
    }

    fun onForfeit() {
        viewModelScope.launch { session.forfeit() }
    }

    fun retryConnection() = session.retryConnection()

    private fun edit(change: (String, Int) -> String) {
        val ui = state.value ?: return
        if (!ui.acceptsInput) return
        local.update { it.copy(input = it.input.copy(text = change(ui.input, ui.digits), problem = null, error = null, round = ui.round)) }
    }

    private fun memo(change: (MemoState) -> MemoState) {
        val ui = state.value ?: return
        local.update { it.copy(memo = change(it.memo ?: ui.memo)) }
    }

    private fun trackOffline(room: RoomView?, connection: ConnectionState, now: Long) {
        val duel = room?.match?.myDuel
        val opponentOnline = duel == null || room.players.firstOrNull { it.id == duel.opponentId }?.connected != false
        val meOnline = connection is ConnectionState.Connected
        val l = local.value
        val opp = if (opponentOnline) null else l.opponentOfflineSince ?: now
        val me = if (meOnline) null else l.myOfflineSince ?: now
        if (opp != l.opponentOfflineSince || me != l.myOfflineSince) local.update { it.copy(opponentOfflineSince = opp, myOfflineSince = me) }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}

private const val GRACE_SECONDS = 60

/** Maps the personalised room snapshot to the shared match screen state (docs/protocol.md §7). */
internal fun RoomView.toMatchUiState(local: LocalMatchState, serverNow: Long, startedAt: Long = serverNow): MatchUiState? {
    val match = match ?: return null
    val duel = match.myDuel ?: return null
    val opponent = players.firstOrNull { it.id == duel.opponentId }
    val teamMatch = settings.teamSize > 1
    val mode = when {
        kind == RoomKind.QUICK -> MatchMode.QUICK
        teamMatch -> MatchMode.TEAM
        else -> MatchMode.ROOM
    }
    val phase = when (duel.phase) {
        DuelPhase.CHOOSING -> if (duel.mySecret == null) MatchPhase.CHOOSING_SECRET else MatchPhase.WAITING_FOR_OPPONENT_SECRET
        DuelPhase.GUESSING -> if (duel.myPendingGuess == null) MatchPhase.GUESSING else MatchPhase.WAITING_FOR_OPPONENT_GUESS
        DuelPhase.FINISHED -> MatchPhase.FINISHED
    }
    val input = local.input
    val sameRound = input.round == duel.round
    val duelResult = duel.result
    val matchResult = match.result
    val opponentName = opponent?.nickname.orEmpty()
    fun summary(outcomeWinner: Team?) = MatchSummary(
        mode = mode,
        outcome = outcomeFor(outcomeWinner, duel.myTeam),
        reason = duelResult!!.reason,
        rounds = duel.round,
        opponentSecret = duel.opponentSecret.orEmpty(),
        digits = settings.digits,
        teamMatch = teamMatch,
        scoreA = match.score.a,
        scoreB = match.score.b,
        mySecret = duel.mySecret.orEmpty(),
        opponentName = opponentName,
        myStrikes = duel.myRounds.map { r -> r.guess?.let { r.strikes } },
        opponentStrikes = duel.opponentRounds.map { r -> r.guess?.let { r.strikes } },
        durationSeconds = ((serverNow - startedAt) / 1000).toInt().takeIf { it > 0 },
        iEnded = duelResult.winner != null && duelResult.winner != duel.myTeam &&
            duelResult.reason in setOf(EndReason.FORFEIT, EndReason.TIMEOUT, EndReason.DISCONNECT),
    )
    val summary = when {
        duelResult == null -> null
        !teamMatch -> summary(duelResult.winner)
        matchResult != null -> summary(matchResult.winner)
        else -> null // my duel is over, the team match is not: the screen offers the match board
    }
    val opponentStatus = when {
        local.opponentOfflineSince != null ->
            OpponentStatus.Disconnected((GRACE_SECONDS - ((serverNow - local.opponentOfflineSince) / 1000).toInt()).coerceAtLeast(0))
        duel.phase == DuelPhase.CHOOSING -> if (duel.opponentHasSecret) OpponentStatus.Ready else OpponentStatus.Picking
        duel.opponentGuessed -> OpponentStatus.Pitched
        else -> OpponentStatus.Thinking
    }
    return MatchUiState(
        digits = settings.digits,
        opponent = Opponent.Player(opponentName, opponent?.tag.orEmpty()),
        mode = mode,
        phase = phase,
        input = if (sameRound) input.text else "",
        inputProblem = if (sameRound) input.problem else null,
        mySecret = duel.mySecret,
        round = duel.round,
        myRounds = duel.myRounds,
        opponentRounds = duel.opponentRounds,
        secondsLeft = duel.deadline?.let { deadline -> ((deadline - serverNow + 999) / 1000).coerceAtLeast(0).toInt() },
        roundSeconds = settings.roundSeconds.takeIf { it > 0 },
        busy = sameRound && input.busy,
        error = if (sameRound) input.error else null,
        summary = summary,
        opponentStatus = opponentStatus,
        memo = local.memo ?: MemoState(settings.digits),
        teamScore = if (teamMatch) match.score.a to match.score.b else null,
        reconnectSeconds = local.myOfflineSince?.let { (GRACE_SECONDS - ((serverNow - it) / 1000).toInt()).coerceAtLeast(0) },
    )
}

private fun outcomeFor(winner: Team?, myTeam: Team): Outcome = when (winner) {
    null -> Outcome.DRAW
    myTeam -> Outcome.WIN
    else -> Outcome.LOSE
}
