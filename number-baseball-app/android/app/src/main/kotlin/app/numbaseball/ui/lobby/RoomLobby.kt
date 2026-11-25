package app.numbaseball.ui.lobby

import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.isCompactHeight
import androidx.compose.ui.platform.LocalDensity
import app.numbaseball.audio.ScreenMusic
import app.numbaseball.audio.MusicTrack
import app.numbaseball.audio.LocalGameAudio
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import app.numbaseball.ui.design.targetOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.ChatMessage
import app.numbaseball.core.protocol.PlayerView
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoomView
import app.numbaseball.core.protocol.Team
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.online.RequestResult
import app.numbaseball.data.settings.SettingsRepository
import app.numbaseball.ui.chat.phraseText
import app.numbaseball.ui.common.OnlineConnectionBanner
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.message
import app.numbaseball.ui.common.offline
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.computer.RELAXED_SECONDS
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.ChaseLamps
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.Lamp
import app.numbaseball.ui.design.LampColor
import app.numbaseball.ui.design.MenuItem
import app.numbaseball.ui.design.NameWithTag
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbDialog
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.NbTag
import app.numbaseball.ui.design.OverflowMenu
import app.numbaseball.ui.design.TeamBadge
import app.numbaseball.ui.design.ToastHost
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.fadingEdges
import app.numbaseball.ui.design.rememberToastState
import app.numbaseball.ui.design.rowClick
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LobbyPlayer(
    val id: String,
    val nickname: String,
    val tag: String,
    val team: Team,
    val host: Boolean,
    val you: Boolean,
    val connected: Boolean,
) {
    val displayName: String get() = "$nickname#$tag"
}

data class LobbyUiState(
    val room: RoomView? = null,
    val players: List<LobbyPlayer> = emptyList(),
    val connection: ConnectionState = ConnectionState.Idle,
    val chat: List<ChatMessage> = emptyList(),
    val chatReadUpTo: Long = -1L,
    val busy: Boolean = false,
    val error: UiError? = null,
) {
    val isHost: Boolean get() = room != null && room.hostId == room.youId
    val me: LobbyPlayer? get() = players.firstOrNull { it.you }
    val seats: Int get() = (room?.settings?.teamSize ?: 1) * 2
    val full: Boolean get() = players.size >= seats
    val unread: Int get() = chat.count { it.id > chatReadUpTo && it.senderId != room?.youId }
}

/** Private room lobby (07 §3–5). The match starts by itself when both teams are full. */
class RoomLobbyViewModel(
    private val create: RoomSettings?,
    private val joinCode: String,
    private val session: OnlineSession,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val local = MutableStateFlow(LobbyUiState())

    val state: StateFlow<LobbyUiState> = combine(local, session.room, session.connection, session.chat, session.chatReadUpTo) { ui, room, connection, chat, read ->
        ui.copy(room = room, players = room?.toPlayers().orEmpty(), connection = connection, chat = chat, chatReadUpTo = read)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    private val _matchStarted = Channel<Unit>(Channel.CONFLATED)

    /** Fires once per LOBBY -> PLAYING transition, so returning to the lobby during a match does not bounce back. */
    val matchStarted: Flow<Unit> = _matchStarted.receiveAsFlow()

    init {
        session.acquire()
        val current = session.room.value
        when {
            create != null && current == null -> request { session.createRoom(create) }
            joinCode.isNotEmpty() && current?.code != joinCode -> request { session.joinRoom(joinCode) }
        }
        viewModelScope.launch {
            var previous: RoomPhase? = session.room.value?.phase
            session.room.collect { room ->
                if (previous == RoomPhase.LOBBY && room?.phase == RoomPhase.PLAYING) _matchStarted.trySend(Unit)
                // Back to the room after a finished match: the host reopens it with the same code (07 §4).
                if (room?.phase == RoomPhase.FINISHED && room.hostId == room.youId && previous != RoomPhase.FINISHED) {
                    request { session.resetRoom() }
                }
                previous = room?.phase
            }
        }
    }

    fun onMove() {
        val me = state.value.me ?: return
        request { session.setTeam(me.team.other) }
    }

    fun onSettingsChange(next: RoomSettings) {
        request { session.updateSettings(next) }
        viewModelScope.launch { settings.update { it.copy(roomDigits = next.digits, roomTeamSize = next.teamSize, roomRoundSeconds = next.roundSeconds) } }
    }

    fun onLeave() = session.leaveRoom()

    fun onRetryConnection() = session.retryConnection()

    fun onErrorShown() = local.update { it.copy(error = null) }

    /** Requests wait for a live connection first (the server answers with a fresh `room_state`). */
    private fun request(call: suspend () -> RequestResult) {
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
    nickname = nickname,
    tag = tag,
    team = team,
    host = id == room.hostId,
    you = id == room.youId,
    connected = connected,
)

class LobbyActions(
    val onBack: () -> Unit,
    val onLeave: () -> Unit,
    val onCopy: (String) -> Unit,
    val onShare: (String) -> Unit,
    val onSettingsChange: (RoomSettings) -> Unit,
    val onMove: () -> Unit,
    val onPractice: () -> Unit,
    val onOpenMatch: () -> Unit,
    val onChat: () -> Unit,
    val onHowToPlay: () -> Unit,
    val onPlayerCard: (LobbyPlayer) -> Unit,
    val onRetryConnection: () -> Unit,
    val onErrorShown: () -> Unit,
)

@Composable
fun RoomLobbyScreen(state: LobbyUiState, actions: LobbyActions) {
    ScreenMusic(MusicTrack.MENU, LocalGameAudio.current)
    var leaveDialog by rememberSaveable { mutableStateOf(false) }
    var settingsSheet by rememberSaveable { mutableStateOf(false) }
    val toast = rememberToastState()
    val room = state.room
    val copied = stringResource(R.string.code_copied)
    state.error?.let { error ->
        val text = stringResource(error.message())
        LaunchedEffect(error) {
            toast.show(text, error = true)
            actions.onErrorShown()
        }
    }
    val compact = isCompactHeight()
    val menu = buildList {
        if (state.isHost && !state.full && room?.phase == RoomPhase.LOBBY) add(MenuItem(stringResource(R.string.room_settings)) { settingsSheet = true })
        add(MenuItem(stringResource(R.string.menu_how_to), onClick = actions.onHowToPlay))
        add(MenuItem(stringResource(R.string.leave_room), destructive = true) { leaveDialog = true })
    }
    NbScreen(
        topBar = {
            TopBar(
                title = stringResource(R.string.lobby_title),
                navIcon = R.drawable.ic_arrow_back,
                navDescription = stringResource(R.string.leave_room),
                onNav = { leaveDialog = true },
                actions = { OverflowMenu(menu, stringResource(R.string.action_more)) },
            )
        },
        bottomBar = {
            // Small window (D2-04): practice moves into the pinned chat bar so the scroll region shows a full seat.
            val compactPractice = compact && room != null && state.isHost && room.phase == RoomPhase.LOBBY
            ChatPeek(state, actions.onChat, onPractice = if (compactPractice) actions.onPractice else null)
        },
        overlay = { ToastHost(toast, bottomOffset = 72.dp) },
    ) {
        OnlineConnectionBanner(state.connection, actions.onRetryConnection)
        if (room == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ChaseLamps()
            }
            return@NbScreen
        }
        val greyed = state.connection.offline
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).fillMaxWidth().fadingEdges(bottom = 16.dp, enabled = scroll.canScrollForward).verticalScroll(scroll)) {
            Invite(room, state.players.size <= 1, onCopy = {
                actions.onCopy(it)
                toast.show(copied)
            }, onShare = actions.onShare)
            RulesBand(room.settings, editable = state.isHost && !state.full && room.phase == RoomPhase.LOBBY) { settingsSheet = true }
            if (!compact) FillStatus(state)
            Teams(state, greyed, actions)
            Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
        }
        when {
            room.phase == RoomPhase.PLAYING && room.match?.myDuel != null ->
                NbButton(
                    stringResource(R.string.open_match),
                    actions.onOpenMatch,
                    heightOverride = if (compact) 48.dp else null,
                    modifier = Modifier.fillMaxWidth().padding(if (compact) 8.dp else 16.dp),
                )
            state.isHost && room.phase == RoomPhase.LOBBY && !compact -> NbButton(
                stringResource(R.string.practice),
                actions.onPractice,
                kind = ButtonKind.TONAL,
                icon = R.drawable.ic_memory,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("practice"),
            )
        }
    }
    if (leaveDialog) {
        NbDialog(
            title = stringResource(R.string.leave_room_title),
            body = null,
            confirmLabel = stringResource(R.string.leave),
            onConfirm = {
                leaveDialog = false
                actions.onLeave()
            },
            onDismiss = { leaveDialog = false },
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
    if (settingsSheet && room != null) {
        var draft by rememberSaveable(stateSaver = RoomSettingsSaver) { mutableStateOf(room.settings) }
        val seatedMax = Team.entries.maxOf { team -> state.players.count { it.team == team } }
        RoomSettingsSheet(
            title = stringResource(R.string.room_settings),
            actionLabel = stringResource(R.string.action_ok),
            settings = draft,
            onChange = { draft = it },
            onConfirm = {
                settingsSheet = false
                if (draft != room.settings) actions.onSettingsChange(draft)
            },
            onDismiss = { settingsSheet = false },
            minTeamSize = seatedMax.coerceAtLeast(1),
        )
    }
}

internal val RoomSettingsSaver = androidx.compose.runtime.saveable.listSaver<RoomSettings, Int>(
    save = { listOf(it.digits, it.teamSize, it.roundSeconds) },
    restore = { RoomSettings(it[0], it[1], it[2]) },
)

@Composable
private fun Invite(room: RoomView, alone: Boolean, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    val code = room.code ?: return
    if (isCompactHeight()) {
        // Small window (ADAPTIVE.md §1): 40 × 48 plates with copy and share icon buttons on the same row.
        val label = stringResource(R.string.invite_code)
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CodePlates(code, description = label, plateWidth = 40.dp, plateHeight = 48.dp, modifier = Modifier.weight(1f))
            IconButton48(R.drawable.ic_content_copy, stringResource(R.string.action_copy), { onCopy(code) }, tint = NbColors.text2)
            IconButton48(R.drawable.ic_share, stringResource(R.string.action_share), { onShare(code) }, tint = NbColors.text2)
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val label = stringResource(R.string.invite_code)
        Text(label, style = NbType.caption, color = NbColors.text3)
        VSpacer(12.dp)
        CodePlates(code, description = label)
        VSpacer(16.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NbButton(stringResource(R.string.action_copy), { onCopy(code) }, kind = ButtonKind.TONAL, icon = R.drawable.ic_content_copy, modifier = Modifier.weight(1f))
            NbButton(stringResource(R.string.action_share), { onShare(code) }, kind = ButtonKind.TONAL, icon = R.drawable.ic_share, modifier = Modifier.weight(1f))
        }
        if (alone) {
            VSpacer(12.dp)
            Text(stringResource(R.string.invite_hint), style = NbType.caption, color = NbColors.text3)
        }
    }
}

@Composable
fun roomRulesText(settings: RoomSettings): String {
    val digits = pluralStringResource(R.plurals.digits_n, settings.digits, settings.digits)
    val timer = if (settings.roundSeconds >= RELAXED_SECONDS) {
        stringResource(R.string.timer_relaxed)
    } else {
        pluralStringResource(R.plurals.timer_seconds, settings.roundSeconds, settings.roundSeconds)
    }
    return stringResource(R.string.room_rules, digits, settings.teamSize, timer)
}

/** Rules band: 48 dp `surface1`, 1 dp lines: `tune` · "4자리 · 3 대 3 · 60초" · host: "변경". */
@Composable
private fun RulesBand(settings: RoomSettings, editable: Boolean, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(NbColors.surface1)) {
        Hairline()
        // 48 dp including both 1 dp lines (prototype `.rules`, border-box).
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            NbIcon(R.drawable.ic_tune, null, NbColors.text2, 20.dp)
            HSpacer(8.dp)
            Text(roomRulesText(settings), style = NbType.bodyStrong, color = NbColors.chalk, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            // The 48 dp target overlaps the band's 1 dp lines instead of making the band 50 dp (D2-19 a).
            if (editable) NbButton(stringResource(R.string.action_edit), onEdit, kind = ButtonKind.TEXT, modifier = Modifier.targetOverflow(46.dp))
        }
        Hairline()
    }
}

@Composable
private fun FillStatus(state: LobbyUiState) {
    val present = state.players.size
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(state.seats) { Lamp(LampColor.CHALK, lit = it < present, size = 8.dp, glow = false) }
            }
            HSpacer(12.dp)
            Text(stringResource(R.string.room_filled, present, state.seats), style = NbType.bodyStrong, color = NbColors.chalk)
        }
        VSpacer(2.dp)
        Text(stringResource(R.string.auto_start), style = NbType.caption, color = NbColors.text3)
    }
}

@Composable
private fun Teams(state: LobbyUiState, greyed: Boolean, actions: LobbyActions) {
    val teamSize = state.room?.settings?.teamSize ?: 1
    val myTeam = state.me?.team
    val team: @Composable (Team, Modifier) -> Unit = { team, modifier ->
        val members = state.players.filter { it.team == team }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TeamHeader(team, members.size, teamSize, myTeam)
            members.forEachIndexed { index, player -> Seat(player, team, index, greyed, actions.onPlayerCard) }
            repeat((teamSize - members.size).coerceAtLeast(0)) { i ->
                EmptySeat(team, members.size + i, canMove = myTeam != null && team != myTeam && !greyed, onMove = actions.onMove)
            }
        }
    }
    if (app.numbaseball.ui.design.isLargeFont()) {
        // Large text (ADAPTIVE.md §2): A above B instead of two columns.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Team.entries.forEach { team(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Team.entries.forEach { team(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TeamHeader(team: Team, count: Int, teamSize: Int, myTeam: Team?) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        if (teamSize > 1) {
            TeamBadge(team)
            HSpacer(8.dp)
            Text(stringResource(if (team == Team.A) R.string.team_a else R.string.team_b), style = NbType.subtitle, color = NbColors.chalk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$count/$teamSize", style = NbType.numLabel, color = NbColors.text3)
        } else {
            // 1:1 room: "나" / "상대" without team badges (07 §5).
            Text(
                stringResource(if (team == myTeam) R.string.tag_me else R.string.seat_opponent),
                style = NbType.subtitle,
                color = NbColors.chalk,
            )
        }
    }
}

private fun teamStripe(team: Team) = if (team == Team.A) NbColors.teamA else NbColors.teamB

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Seat(player: LobbyPlayer, team: Team, index: Int, greyed: Boolean, onCard: (LobbyPlayer) -> Unit) {
    val shape = RoundedCornerShape(NbRadius.m)
    val teamName = stringResource(if (team == Team.A) R.string.team_a else R.string.team_b)
    val tags = buildList {
        if (player.you) add(stringResource(R.string.tag_me))
        if (player.host) add(stringResource(R.string.tag_host))
        if (!player.connected) add(stringResource(R.string.tag_offline))
    }
    val spoken = "$teamName, ${index + 1}, ${player.nickname} ${player.tag}" + tags.joinToString("") { ", $it" }
    val cardLabel = stringResource(R.string.report_player)
    val stripe = teamStripe(team)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(NbColors.surface1)
            // The 3 dp stripe is drawn over my seat's outline, on the outer edge, full height (D2-19 b).
            .drawWithContent {
                drawContent()
                drawRect(stripe, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            }
            .then(if (player.you) Modifier.border(1.dp, NbColors.outline, shape) else Modifier)
            .then(if (!player.you) Modifier.combinedClickable(onClick = {}, onLongClick = { onCard(player) }) else Modifier)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (!player.you) onClick(cardLabel) { onCard(player); true }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(3.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            val dim = greyed || !player.connected
            NameWithTag(player.nickname, player.tag, nameStyle = NbType.bodyStrong, nameColor = if (dim) NbColors.text3 else NbColors.chalk)
            if (tags.isNotEmpty()) {
                VSpacer(2.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (player.you) NbTag(stringResource(R.string.tag_me), solid = true)
                    if (player.host) NbTag(stringResource(R.string.tag_host))
                    if (!player.connected) NbTag(stringResource(R.string.tag_offline))
                }
            }
        }
    }
}

@Composable
private fun EmptySeat(team: Team, index: Int, canMove: Boolean, onMove: () -> Unit) {
    val shape = RoundedCornerShape(NbRadius.m)
    val teamName = stringResource(if (team == Team.A) R.string.team_a else R.string.team_b)
    val label = stringResource(if (canMove) R.string.seat_move else R.string.seat_empty)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(1.dp, if (canMove) NbColors.outline else NbColors.line, shape)
            .then(if (canMove) Modifier.rowClick(onClick = onMove) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "$teamName ${index + 1}, $label" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canMove) {
            // Prototype `.seat.move`: `swap_horiz` 20 dp, 6 dp gap, `labelSmall` chalk.
            NbIcon(R.drawable.ic_swap_horiz, null, NbColors.chalk, 20.dp)
            HSpacer(6.dp)
            Text(label, style = NbType.labelSmall, color = NbColors.chalk, maxLines = 1)
        } else {
            // `.seat.empty`: unlit 6 dp lamp, 6 dp gap, `caption` `text3`.
            Lamp(LampColor.CHALK, lit = false, size = 6.dp)
            HSpacer(6.dp)
            Text(label, style = NbType.caption, color = NbColors.text3, maxLines = 1)
        }
    }
}

/** Chat bar: 56 dp `surface1`, 1 dp line top: `chat` · last message (sender + text, one line) · unread badge. */
@Composable
private fun ChatPeek(state: LobbyUiState, onChat: () -> Unit, onPractice: (() -> Unit)? = null) {
    val last = state.chat.lastOrNull()
    Column(Modifier.fillMaxWidth().background(NbColors.surface1)) {
        Hairline()
        Row(
            // 56 dp including the 1 dp top line (prototype `.chatbar`, border-box).
            Modifier.fillMaxWidth().height(55.dp).rowClick(onClick = onChat).padding(horizontal = 16.dp).testTag("chatPeek"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NbIcon(R.drawable.ic_chat, stringResource(R.string.chat_title), NbColors.text2)
            HSpacer(12.dp)
            val text = if (last == null) {
                buildAnnotatedString { withStyle(SpanStyle(color = NbColors.text3)) { append(stringResource(R.string.chat_title)) } }
            } else if (last.kind == ChatKind.SYSTEM) {
                buildAnnotatedString { withStyle(SpanStyle(color = NbColors.text3)) { append(app.numbaseball.ui.chat.systemText(last)) } }
            } else {
                val sender = last.senderNickname ?: stringResource(R.string.chat_deleted_player)
                val body = if (last.kind == ChatKind.PHRASE) last.phraseId?.let { phraseText(it) }.orEmpty() else last.text.orEmpty()
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = NbType.bodyStrong.fontWeight, color = NbColors.chalk)) { append(sender) }
                    append(" ")
                    append(body)
                }
            }
            Text(text, style = NbType.body, color = NbColors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (state.unread > 0) {
                HSpacer(12.dp)
                Box(
                    Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).background(NbColors.chalk, CircleShape).padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.unread.coerceAtMost(99).toString(),
                        style = NbType.numLabel.copy(fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
                        color = NbColors.onChalk,
                    )
                }
            }
            if (onPractice != null) {
                HSpacer(8.dp)
                NbButton(
                    stringResource(R.string.practice_short),
                    onPractice,
                    kind = ButtonKind.TONAL,
                    small = true,
                    heightOverride = 48.dp,
                    icon = R.drawable.ic_memory,
                    modifier = Modifier.testTag("practice"),
                )
            }
        }
    }
}
