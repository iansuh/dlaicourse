package app.numbaseball.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.core.protocol.DuelPhase
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.Team
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
    val problem: app.numbaseball.core.rules.NumberProblem? = null,
    val busy: Boolean = false,
    val error: UiError? = null,
    val round: Int = -1,
)

/** Online duel: the server snapshot (`room_state`) is the truth; this only adds the keypad and a countdown. */
class OnlineMatchViewModel(private val session: OnlineSession) : ViewModel() {
    private val input = MutableStateFlow(LocalInput())
    private val ticker = flow {
        while (true) {
            emit(session.serverNow())
            delay(TICK_MILLIS)
        }
    }

    /** null when there is no running duel any more (seat lost, left): the screen goes home. */
    val state: StateFlow<MatchUiState?> = combine(session.room, input, ticker) { room, local, serverNow ->
        room?.toMatchUiState(local, serverNow)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), session.room.value?.toMatchUiState(LocalInput(), session.serverNow()))

    val connection get() = session.connection

    init {
        session.acquire()
    }

    override fun onCleared() {
        session.release()
    }

    fun onDigit(digit: Char) = edit { current, digits -> NumberInput.append(current, digit, digits) }
    fun onDelete() = edit { current, _ -> NumberInput.deleteLast(current) }

    fun onSubmit() {
        val ui = state.value ?: return
        if (!ui.acceptsInput) return
        val problem = NumberInput.problem(ui.input, ui.digits)
        if (problem != null) {
            input.update { it.copy(problem = problem) }
            return
        }
        input.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = if (ui.phase == MatchPhase.CHOOSING_SECRET) session.setSecret(ui.input) else session.guess(ui.input, ui.round)
            input.update {
                if (result == RequestResult.Ok) LocalInput(round = it.round) else it.copy(busy = false, error = result.toUiError())
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
        input.update { it.copy(text = change(ui.input, ui.digits), problem = null, error = null, round = ui.round) }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}

/** Maps the personalised room snapshot to the shared match screen state (docs/protocol.md §7). */
internal fun RoomView.toMatchUiState(local: LocalInput, serverNow: Long): MatchUiState? {
    val match = match ?: return null
    val duel = match.myDuel ?: return null
    val opponent = players.firstOrNull { it.id == duel.opponentId }
    val teamMatch = settings.teamSize > 1
    val phase = when (duel.phase) {
        DuelPhase.CHOOSING -> if (duel.mySecret == null) MatchPhase.CHOOSING_SECRET else MatchPhase.WAITING_FOR_OPPONENT_SECRET
        DuelPhase.GUESSING -> if (duel.myPendingGuess == null) MatchPhase.GUESSING else MatchPhase.WAITING_FOR_OPPONENT_GUESS
        DuelPhase.FINISHED -> MatchPhase.FINISHED
    }
    val sameRound = local.round == duel.round
    val duelResult = duel.result
    val matchResult = match.result
    val summary = when {
        duelResult == null -> null
        !teamMatch -> MatchSummary(
            mode = MatchMode.ONLINE,
            outcome = outcomeFor(duelResult.winner, duel.myTeam),
            reason = duelResult.reason,
            rounds = duel.round,
            opponentSecret = duel.opponentSecret.orEmpty(),
            digits = settings.digits,
        )
        matchResult != null -> MatchSummary(
            mode = MatchMode.ONLINE,
            outcome = outcomeFor(matchResult.winner, duel.myTeam),
            reason = duelResult.reason,
            rounds = duel.round,
            opponentSecret = duel.opponentSecret.orEmpty(),
            digits = settings.digits,
            teamMatch = true,
            scoreA = match.score.a,
            scoreB = match.score.b,
        )
        else -> null // my duel is over, the team match is not: the screen offers the match board
    }
    return MatchUiState(
        digits = settings.digits,
        opponent = Opponent.Player(opponent?.let { "${it.nickname}#${it.tag}" }.orEmpty()),
        mode = MatchMode.ONLINE,
        phase = phase,
        input = if (sameRound) local.text else "",
        inputProblem = if (sameRound) local.problem else null,
        mySecret = duel.mySecret,
        round = duel.round,
        myRounds = duel.myRounds,
        opponentRounds = duel.opponentRounds,
        secondsLeft = duel.deadline?.let { deadline -> ((deadline - serverNow + 999) / 1000).coerceAtLeast(0).toInt() },
        busy = sameRound && local.busy,
        error = if (sameRound) local.error else null,
        summary = summary,
    )
}

private fun outcomeFor(winner: Team?, myTeam: Team): Outcome = when (winner) {
    null -> Outcome.DRAW
    myTeam -> Outcome.WIN
    else -> Outcome.LOSE
}
