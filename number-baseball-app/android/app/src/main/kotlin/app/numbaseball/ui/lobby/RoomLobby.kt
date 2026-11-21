package app.numbaseball.ui.lobby

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.PlayerView
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.Team
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.ui.common.ChoiceRow
import app.numbaseball.ui.common.ConnectionStatus
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SecondaryButton
import app.numbaseball.ui.common.SectionLabel
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbLayout
import app.numbaseball.ui.theme.NbSpace
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LobbyPlayer(val id: String, val displayName: String, val team: Team, val host: Boolean, val you: Boolean, val connected: Boolean)

data class LobbyUiState(
    /** Settings form for a new room (before the server created it). */
    val draft: RoomSettings = RoomSettings(digits = 3, teamSize = 1, roundSeconds = 60),
    val room: RoomView? = null,
    val players: List<LobbyPlayer> = emptyList(),
    val connection: ConnectionState = ConnectionState.Idle,
    val busy: Boolean = false,
    val error: UiError? = null,
) {
    val creating: Boolean get() = room == null
    val waitingFor: Int get() = room?.let { it.settings.teamSize * 2 - it.players.count { p -> !p.left } }?.coerceAtLeast(0) ?: 0
    val started: Boolean get() = room?.phase == RoomPhase.PLAYING
}

/** Private room: create or join, teams, waiting practice, chat. The match starts by itself when both teams are full. */
class RoomLobbyViewModel(private val joinCode: String, private val session: OnlineSession) : ViewModel() {
    private val local = MutableStateFlow(LobbyUiState())

    val state: StateFlow<LobbyUiState> = combine(local, session.room, session.connection) { ui, room, connection ->
        ui.copy(room = room, players = room?.toPlayers().orEmpty(), connection = connection)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        session.acquire()
        if (joinCode.isNotEmpty() && session.room.value?.code != joinCode) {
            request { session.joinRoom(joinCode) }
        }
    }

    fun onDraftChange(settings: RoomSettings) = local.update { it.copy(draft = settings) }

    fun onCreate() = request { session.createRoom(local.value.draft) }

    fun onSwitchTeam() {
        val me = state.value.players.firstOrNull { it.you } ?: return
        request { session.setTeam(me.team.other) }
    }

    fun onLeave() = session.leaveRoom()

    fun onRetryConnection() = session.retryConnection()

    /** Requests wait for a live connection first (the server answers with a fresh `room_state`). */
    private fun request(call: suspend () -> app.numbaseball.data.online.RequestResult) {
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            session.connection.first { it is ConnectionState.Connected }
            val error = call().toUiError()
            local.update { it.copy(busy = false, error = error) }
        }
    }

    override fun onCleared() = session.release()
}

private fun RoomView.toPlayers(): List<LobbyPlayer> = players.filterNot { it.left }.map { it.toLobbyPlayer(this) }

private fun PlayerView.toLobbyPlayer(room: RoomView) = LobbyPlayer(
    id = id,
    displayName = "$nickname#$tag",
    team = team,
    host = id == room.hostId,
    you = id == room.youId,
    connected = connected,
)

@Composable
fun RoomLobbyScreen(
    state: LobbyUiState,
    onDraftChange: (RoomSettings) -> Unit,
    onCreate: () -> Unit,
    onSwitchTeam: () -> Unit,
    onPractice: () -> Unit,
    onChat: () -> Unit,
    onBoard: () -> Unit,
    onLeave: () -> Unit,
    onPlayerLongPress: (LobbyPlayer) -> Unit,
    onRetryConnection: () -> Unit,
    onBack: () -> Unit,
) {
    val room = state.room
    ScreenScaffold(
        title = if (room?.code != null) stringResource(R.string.lobby_title, room.code!!) else stringResource(R.string.lobby_title_new),
        onBack = onBack,
    ) {
        ConnectionStatus(state.connection, onRetryConnection)
        if (room == null) {
            SettingsForm(state.draft, onDraftChange)
            ErrorText(state.error)
            PrimaryButton(stringResource(R.string.lobby_create), onCreate, enabled = !state.busy)
            return@ScreenScaffold
        }
        room.code?.let {
            SectionLabel(stringResource(R.string.lobby_code))
            Text(it, style = NbType.numCode)
            Text(stringResource(R.string.lobby_share), color = NbColors.text2)
        }
        TeamList(stringResource(R.string.lobby_team_a), state.players.filter { it.team == Team.A }, onPlayerLongPress)
        TeamList(stringResource(R.string.lobby_team_b), state.players.filter { it.team == Team.B }, onPlayerLongPress)
        ErrorText(state.error)
        if (room.phase == RoomPhase.LOBBY) {
            Text(pluralStringResource(R.plurals.lobby_waiting, state.waitingFor, state.waitingFor))
            Text(stringResource(R.string.lobby_auto_start), color = NbColors.text3, style = MaterialTheme.typography.bodySmall)
            PrimaryButton(stringResource(R.string.lobby_practice), onPractice)
            SecondaryButton(stringResource(R.string.lobby_switch_team), onSwitchTeam, enabled = !state.busy)
        } else {
            SecondaryButton(stringResource(R.string.lobby_board), onBoard)
        }
        SecondaryButton(stringResource(R.string.lobby_chat), onChat)
        SecondaryButton(stringResource(R.string.lobby_leave), onLeave)
    }
}

@Composable
private fun SettingsForm(draft: RoomSettings, onChange: (RoomSettings) -> Unit) {
    SectionLabel(stringResource(R.string.setup_digits))
    ChoiceRow(Limits.DIGITS.toList(), draft.digits, { it.toString() }) { onChange(draft.copy(digits = it)) }
    SectionLabel(stringResource(R.string.lobby_team_size))
    ChoiceRow(Limits.TEAM_SIZE.toList(), draft.teamSize, { it.toString() }) { onChange(draft.copy(teamSize = it)) }
    SectionLabel(stringResource(R.string.lobby_round_timer))
    ChoiceRow(Limits.ROUND_SECONDS, draft.roundSeconds, { timerLabel(it) }) { onChange(draft.copy(roundSeconds = it)) }
}

@Composable
private fun timerLabel(seconds: Int): String =
    if (seconds == 0) stringResource(R.string.lobby_timer_none) else pluralStringResource(R.plurals.lobby_timer_seconds, seconds, seconds)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TeamList(title: String, players: List<LobbyPlayer>, onLongPress: (LobbyPlayer) -> Unit) {
    SectionLabel(title)
    players.forEach { player ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s8),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NbLayout.touchMin)
                .combinedClickable(onClick = {}, onLongClick = { if (!player.you) onLongPress(player) }),
        ) {
            Text(player.displayName, style = NbType.numTag, modifier = Modifier.weight(1f))
            if (player.host) Tag(stringResource(R.string.lobby_host))
            if (player.you) Tag(stringResource(R.string.lobby_you))
            if (!player.connected) Tag(stringResource(R.string.lobby_offline))
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = NbColors.text2)
}
