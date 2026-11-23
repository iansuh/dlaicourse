package app.numbaseball.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.ads.AdBanner
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.MusicTrack
import app.numbaseball.audio.ScreenMusic
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.Stats
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.AccountState
import app.numbaseball.data.account.RegistrationResult
import app.numbaseball.data.age.AgeRepository
import app.numbaseball.data.age.AgeState
import app.numbaseball.data.online.NetworkMonitor
import app.numbaseball.data.settings.SettingsRepository
import app.numbaseball.ui.design.BrandMark
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.InsetDivider
import app.numbaseball.ui.design.Lamp
import app.numbaseball.ui.design.LampColor
import app.numbaseball.ui.design.NameWithTag
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.NumberUnitLabel
import app.numbaseball.ui.design.SectionHeader
import app.numbaseball.ui.design.Segmented
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.fadingEdges
import app.numbaseball.ui.design.isCompactHeight
import app.numbaseball.ui.design.rowClick
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbMotion
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val nickname: String = "",
    val tag: String? = null,
    val registered: Boolean = false,
    /** Null while loading (numerals show `–`). */
    val stats: Stats? = null,
    /** Oldest first, W / L / D. */
    val recentForm: String = "",
    val quickDigits: Int = 3,
    val online: Boolean = true,
    /** The server refused the nickname chosen offline: offer to pick another one. */
    val nicknameRejected: Boolean = false,
    /** Play Age Signals can refuse online features (policy review R21). */
    val onlineAllowed: Boolean = true,
) {
    val canPlayOnline: Boolean get() = online && onlineAllowed && !nicknameRejected
    val gamesPlayed: Int get() = stats?.let { it.wins + it.losses + it.draws } ?: 0
}

object RoomCodes {
    /** docs/protocol.md §1: 5 characters from this alphabet; the server ignores case, spaces and dashes. */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun normalize(raw: String): String =
        raw.uppercase().filter { it in ALPHABET }.take(Limits.ROOM_CODE_LENGTH)
}

class HomeViewModel(
    private val accounts: AccountRepository,
    ages: AgeRepository,
    private val settings: SettingsRepository,
    network: NetworkMonitor,
) : ViewModel() {
    private val rejected = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(accounts.state, ages.state, settings.settings, network.online, rejected) { account, age, prefs, online, nickRejected ->
        val base = HomeUiState(
            recentForm = prefs.recentForm,
            quickDigits = prefs.quickDigits,
            online = online,
            nicknameRejected = nickRejected,
            onlineAllowed = (age as? AgeState.Known)?.onlineAllowed ?: false,
        )
        when (account) {
            is AccountState.Registered -> base.copy(nickname = account.profile.nickname, tag = account.profile.tag, registered = true, stats = account.stats)
            is AccountState.Unregistered -> base.copy(nickname = account.nickname, stats = Stats())
            AccountState.Loading, AccountState.NeedsNickname -> base
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Register (or refresh the record) now and whenever the connection comes back.
        viewModelScope.launch {
            network.online.filter { it }.collect {
                when (accounts.state.value) {
                    is AccountState.Unregistered -> {
                        val result = accounts.ensureRegistered()
                        if (result is RegistrationResult.Rejected) rejected.value = true
                    }
                    is AccountState.Registered -> accounts.refresh()
                    else -> Unit
                }
            }
        }
    }

    fun onQuickDigits(digits: Int) {
        viewModelScope.launch { settings.update { it.copy(quickDigits = digits) } }
    }
}

/** Home (02): header, record hero, other modes, the quick-match dock and the ad band. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onQuickDigits: (Int) -> Unit,
    onQuickMatch: () -> Unit,
    onVsComputer: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onHowToPlay: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onChooseNickname: () -> Unit,
) {
    ScreenMusic(MusicTrack.MENU, LocalGameAudio.current)
    val compact = isCompactHeight()
    NbScreen(
        bottomBar = { if (state.online) AdBanner() },
    ) {
        HomeHeader(onHowToPlay, onSettings)
        if (!state.online) {
            InfoBand(R.drawable.ic_cloud_off, stringResource(R.string.offline_band))
        } else if (state.nicknameRejected) {
            InfoBand(R.drawable.ic_person, stringResource(R.string.choose_nickname_again), onClick = onChooseNickname)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val showForm = maxHeight >= 420.dp
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .fadingEdges(bottom = 16.dp, enabled = scroll.canScrollForward)
                    .verticalScroll(scroll),
            ) {
                Hero(state, showForm = showForm && !compact, onProfile = onProfile)
                Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
                SectionHeader(stringResource(R.string.other_modes), first = true)
                ModeRow(R.drawable.ic_memory, stringResource(R.string.cpu_title), stringResource(R.string.cpu_sub), enabled = true, onClick = onVsComputer)
                InsetDivider(72.dp)
                ModeRow(
                    R.drawable.ic_group,
                    stringResource(R.string.room_title),
                    if (state.online) stringResource(R.string.room_sub) else stringResource(R.string.needs_internet),
                    enabled = state.canPlayOnline,
                    onClick = onCreateRoom,
                )
                InsetDivider(72.dp)
                ModeRow(
                    R.drawable.ic_login,
                    stringResource(R.string.join_title),
                    if (state.online) stringResource(R.string.join_sub) else stringResource(R.string.needs_internet),
                    enabled = state.canPlayOnline,
                    onClick = onJoinRoom,
                )
                VSpacer(8.dp)
            }
        }
        QuickDock(state, onQuickDigits, onQuickMatch)
    }
}

@Composable
private fun HomeHeader(onHowToPlay: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        HSpacer(12.dp)
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(stringResource(R.string.app_name_main), style = NbType.subtitle, color = NbColors.chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.app_name_sub), style = NbType.caption, color = NbColors.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton48(R.drawable.ic_help, stringResource(R.string.home_help), onHowToPlay, tint = NbColors.text2)
        HSpacer(12.dp)
        IconButton48(R.drawable.ic_settings, stringResource(R.string.home_settings), onSettings, tint = NbColors.text2)
    }
}

/** 48 dp `surface3` band under the header (offline, nickname to choose again). */
@Composable
private fun InfoBand(@DrawableRes icon: Int, text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(NbColors.surface3)
            .then(if (onClick != null) Modifier.rowClick(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NbIcon(icon, null, NbColors.text2, 20.dp)
        HSpacer(12.dp)
        Text(text, style = NbType.caption, color = NbColors.chalk, modifier = Modifier.weight(1f))
        if (onClick != null) NbIcon(R.drawable.ic_chevron_right, null, NbColors.text3, 20.dp)
    }
}

@Composable
private fun Hero(state: HomeUiState, showForm: Boolean, onProfile: () -> Unit) {
    val stats = state.stats
    val loading = stats == null
    val newPlayer = !loading && state.gamesPlayed == 0
    val rate = stats?.takeIf { state.gamesPlayed > 0 }?.let { (it.wins * 100f / state.gamesPlayed).toInt() }
    val spoken = buildString {
        append(stringResource(R.string.record_label)).append(", ").append(state.nickname)
        state.tag?.let { append(" ").append(it) }
        if (stats != null) append(", ").append(stringResource(R.string.record_value, stats.wins, stats.losses, stats.draws))
        if (rate != null) append(", ").append(stringResource(R.string.record_rate)).append(" ").append(rate).append("%")
    }
    val openProfile = stringResource(R.string.profile_title)
    Column(
        Modifier
            .fillMaxWidth()
            .rowClick(onClick = onProfile)
            .clearAndSetSemantics {
                contentDescription = spoken
                onClick(openProfile) { onProfile(); true }
            }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            .testTag("profile"),
    ) {
        Text(stringResource(R.string.record_label), style = NbType.caption, color = NbColors.text3)
        VSpacer(4.dp)
        NameWithTag(state.nickname, state.tag, nameStyle = NbType.title, tagStyle = NbType.numTag.copy(fontSize = NbType.numMemo.fontSize))
        VSpacer(16.dp)
        Row(Modifier.height(IntrinsicSize.Min)) {
            StatColumn(stats?.wins, stringResource(R.string.record_win), loading, first = true)
            StatDivider()
            StatColumn(stats?.losses, stringResource(R.string.record_loss), loading)
            StatDivider()
            StatColumn(stats?.draws, stringResource(R.string.record_draw), loading)
            StatDivider()
            Column(Modifier.padding(start = 20.dp)) {
                Text(
                    if (rate == null) "–" else "$rate%",
                    style = NbType.numStat,
                    color = if (loading) NbColors.textDisabled else NbColors.text2,
                )
                Text(stringResource(R.string.record_rate), style = NbType.caption, color = NbColors.text3, maxLines = 1)
            }
        }
        if (showForm) {
            VSpacer(16.dp)
            if (newPlayer || state.recentForm.isEmpty()) {
                if (newPlayer) Text(stringResource(R.string.first_game_waiting), style = NbType.caption, color = NbColors.text3)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recent_form), style = NbType.caption, color = NbColors.text3)
                    HSpacer(12.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        state.recentForm.takeLast(10).forEach { result ->
                            when (result) {
                                'W' -> Lamp(LampColor.STRIKE, lit = true, size = 8.dp)
                                'D' -> Box(Modifier.size(8.dp).border(1.5.dp, NbColors.chalk, CircleShape))
                                else -> Lamp(LampColor.STRIKE, lit = false, size = 8.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(value: Int?, label: String, loading: Boolean, first: Boolean = false) {
    val shown by animateIntAsState(value ?: 0, tween(NbMotion.short), label = "stat")
    Column(Modifier.padding(start = if (first) 0.dp else 20.dp, end = 20.dp)) {
        Text(
            if (loading) "–" else shown.toString(),
            style = NbType.numStat,
            color = if (loading) NbColors.textDisabled else NbColors.chalk,
        )
        Text(label, style = NbType.caption, color = NbColors.text3, maxLines = 1)
    }
}

@Composable
private fun StatDivider() = Box(Modifier.width(1.dp).fillMaxHeight().background(NbColors.line))

/** Mode row: 64 dp, 40 × 40 `surface2` plate (radius 8, icon 24 chalk), text at x = 72, chevron (02). */
@Composable
private fun ModeRow(@DrawableRes icon: Int, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .rowClick(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(NbColors.surface2, RoundedCornerShape(NbRadius.m)), contentAlignment = Alignment.Center) {
            NbIcon(icon, null, if (enabled) NbColors.chalk else NbColors.textDisabled)
        }
        HSpacer(16.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = NbType.subtitle, color = if (enabled) NbColors.chalk else NbColors.textDisabled)
            Text(subtitle, style = NbType.caption, color = if (enabled) NbColors.text3 else NbColors.textDisabled, maxLines = 2)
        }
        HSpacer(8.dp)
        NbIcon(R.drawable.ic_chevron_right, null, if (enabled) NbColors.text3 else NbColors.textDisabled, 20.dp)
    }
}

/** Quick-match dock: `surface1`, 1 dp line top, padding 16 / 16 / 24; title over caption, digits, start button. */
@Composable
private fun QuickDock(state: HomeUiState, onQuickDigits: (Int) -> Unit, onQuickMatch: () -> Unit) {
    val compact = isCompactHeight()
    Column(Modifier.fillMaxWidth().background(NbColors.surface1)) {
        Hairline()
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = if (compact) 12.dp else 16.dp, bottom = if (compact) 12.dp else 24.dp)) {
            if (!compact) {
                Text(stringResource(R.string.quick_title), style = NbType.subtitle, color = NbColors.chalk)
                Text(stringResource(R.string.quick_sub), style = NbType.caption, color = NbColors.text3)
                VSpacer(12.dp)
            }
            val unit = pluralStringResource(R.plurals.digit_unit, 2)
            Segmented(
                items = Limits.DIGITS.toList(),
                selected = state.quickDigits,
                onSelect = onQuickDigits,
                enabled = state.canPlayOnline,
            ) { d, selected -> NumberUnitLabel(d.toString(), unit, selected) }
            VSpacer(12.dp)
            NbButton(
                text = stringResource(if (state.online) R.string.quick_start else R.string.waiting_connection),
                onClick = onQuickMatch,
                icon = R.drawable.ic_bolt,
                enabled = state.canPlayOnline,
                modifier = Modifier.fillMaxWidth().testTag("quickMatch"),
            )
        }
    }
}
