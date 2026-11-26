package app.numbaseball.ui.home

import app.numbaseball.ui.design.isLargeFont
import app.numbaseball.ui.design.scrollFade
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.stateDescription
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbLayout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
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
import androidx.compose.ui.unit.sp
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
import app.numbaseball.ui.design.Avatar
import app.numbaseball.ui.design.EqualTilesRow
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.QuestionPlate
import app.numbaseball.ui.design.NameWithTag
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.NumberUnitLabel
import app.numbaseball.ui.design.Segmented
import app.numbaseball.ui.design.VSpacer
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

/**
 * Home (02, v2): header · Me row and 4-number record · `? ? ? ?` hero with the tutorial link · three mode tiles · the
 * pinned quick-match block · 24 dp clear space · ad band. When the content does not fit, the hero hides first, then the
 * region above the quick-match block scrolls.
 */
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
    val large = isLargeFont()
    val margin = if (compact) 12.dp else 16.dp
    NbScreen(
        bottomBar = { if (state.online) AdBanner() },
    ) {
        HomeHeader(onHowToPlay, onSettings)
        if (!state.online) {
            InfoBand(R.drawable.ic_cloud_off, stringResource(R.string.offline_band))
        } else if (state.nicknameRejected) {
            InfoBand(R.drawable.ic_person, stringResource(R.string.choose_nickname_again), onClick = onChooseNickname)
        }
        val upper: @Composable () -> Unit = {
            MeAndRecord(state, compact, onProfile)
        }
        val modes: @Composable () -> Unit = {
            Column {
                if (!compact) {
                    Text(stringResource(R.string.home_other_modes), style = NbType.labelSmall, color = NbColors.text3, modifier = Modifier.padding(start = 4.dp))
                    VSpacer(12.dp)
                }
                // Three equal tiles, all as tall as the tallest (labels may wrap to 2 lines, 02 States). Large text uses
                // the small window's 56 dp tiles, so all three stay visible above the pinned quick-match block (review v2
                // polish 3).
                val tileCompact = compact || large
                EqualTilesRow(8.dp, if (tileCompact) 56.dp else NbSize.modeTileH) {
                    ModeTile(R.drawable.ic_memory, stringResource(R.string.tile_cpu), enabled = true, compact = tileCompact, onClick = onVsComputer)
                    ModeTile(R.drawable.ic_group, stringResource(R.string.tile_room), enabled = state.canPlayOnline, compact = tileCompact, onClick = onCreateRoom)
                    ModeTile(R.drawable.ic_login, stringResource(R.string.tile_join), enabled = state.canPlayOnline, compact = tileCompact, onClick = onJoinRoom)
                }
            }
        }
        val hero: @Composable () -> Unit = { Hero(onHowToPlay) }
        SubcomposeLayout(Modifier.weight(1f).fillMaxWidth()) { constraints ->
            val probe = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            // Measuring passes only: no semantics, so tests and TalkBack see each control once.
            val fixedHeight = subcompose("probe") { Column(Modifier.clearAndSetSemantics {}.padding(horizontal = margin)) { upper(); modes() } }.sumOf { it.measure(probe).height }
            val heroHeight = subcompose("hero") { Box(Modifier.clearAndSetSemantics {}) { hero() } }.sumOf { it.measure(probe).height }
            val min = 16.dp.roundToPx()
            // The fit rule (02 States): the hero hides first; small windows and large text never show it.
            val showHero = !compact && !large && fixedHeight + heroHeight + 2 * min <= constraints.maxHeight
            val content = subcompose("content") {
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .scrollFade(scroll)
                        .verticalScroll(scroll)
                        .padding(horizontal = margin),
                ) {
                    Column(Modifier.heightIn(min = with(this@SubcomposeLayout) { constraints.maxHeight.toDp() })) {
                        upper()
                        Spacer(Modifier.weight(1f).heightIn(min = if (compact) 8.dp else 16.dp))
                        if (showHero) {
                            hero()
                            Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
                        }
                        modes()
                    }
                }
            }.first().measure(constraints)
            layout(content.width, content.height) { content.place(0, 0) }
        }
        QuickBlock(state, compact, onQuickDigits, onQuickMatch)
    }
}

/** Home top bar (12.1 Home variant): brand mark 32 dp at x = 16, 10 dp, name stack, `help` and `settings` (`ink`). */
@Composable
private fun HomeHeader(onHowToPlay: () -> Unit, onSettings: () -> Unit) {
    val compact = isCompactHeight()
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 56.dp).padding(start = if (compact) 12.dp else 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        HSpacer(10.dp)
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(stringResource(R.string.app_name_main), style = NbType.subtitle.copy(fontSize = 17.sp), color = NbColors.ink, maxLines = if (isLargeFont() && !compact) 2 else 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.app_name_sub), style = NbType.caption, color = NbColors.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton48(R.drawable.ic_help, stringResource(R.string.home_help), onHowToPlay)
        HSpacer(8.dp)
        IconButton48(R.drawable.ic_settings, stringResource(R.string.home_settings), onSettings)
    }
}

/** 48 dp `surface2` band under the header (offline, nickname to choose again). */
@Composable
private fun InfoBand(@DrawableRes icon: Int, text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(NbColors.surface2)
            .then(if (onClick != null) Modifier.rowClick(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NbIcon(icon, null, NbColors.text2, 20.dp)
        HSpacer(12.dp)
        Text(text, style = NbType.caption, color = NbColors.ink, modifier = Modifier.weight(1f))
        if (onClick != null) NbIcon(R.drawable.ic_chevron_right, null, NbColors.text3, 20.dp)
    }
}

/**
 * Me row + record (02): avatar 48 · 12 · nickname (`subtitle` 17 sp) + tag over "내 기록"; then 승 · 패 · 무 · 승률 spread
 * across the width (`numStat` over `caption` `text3`), 20 dp bottom padding and a 1 dp `line`. One button → Profile.
 */
@Composable
private fun MeAndRecord(state: HomeUiState, compact: Boolean, onProfile: () -> Unit) {
    val stats = state.stats
    val loading = stats == null
    val rate = stats?.takeIf { state.gamesPlayed > 0 }?.let { (it.wins * 100f / state.gamesPlayed).toInt() }
    val spoken = buildString {
        append(stringResource(R.string.record_label)).append(", ").append(state.nickname)
        state.tag?.let { append(" ").append(it) }
        if (stats != null) append(", ").append(stringResource(R.string.record_value, stats.wins, stats.losses, stats.draws))
        if (rate != null) append(", ").append(stringResource(R.string.record_rate)).append(" ").append(rate).append("%")
    }
    val openProfile = stringResource(R.string.profile_title)
    val large = isLargeFont()
    Column(
        Modifier
            .fillMaxWidth()
            // Before clearAndSetSemantics, so tests can find the block by its tag (DEF-063).
            .testTag("profile")
            .rowClick(onClick = onProfile)
            .clearAndSetSemantics {
                contentDescription = spoken
                onClick(openProfile) { onProfile(); true }
            },
    ) {
        VSpacer(if (compact) 8.dp else 16.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(state.nickname, if (compact) NbSize.avatarM else NbSize.avatarL)
            HSpacer(12.dp)
            Column(Modifier.weight(1f)) {
                NameWithTag(state.nickname, state.tag, nameStyle = NbType.subtitle.copy(fontSize = 17.sp, lineHeight = 24.sp))
                if (!compact) Text(stringResource(R.string.record_label), style = NbType.caption, color = NbColors.text3)
            }
        }
        VSpacer(if (compact) 8.dp else 20.dp)
        val values = listOf(
            stats?.wins to stringResource(R.string.record_win),
            stats?.losses to stringResource(R.string.record_loss),
            stats?.draws to stringResource(R.string.record_draw),
        )
        val rateCell: @Composable () -> Unit = { RateCell(rate, loading, showLabel = !compact) }
        if (large && !compact) {
            // Large text (ADAPTIVE §2.5): W L D on one line, the win rate on the next.
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                values.forEach { (v, l) -> StatCell(v, l, loading, showLabel = true) }
            }
            VSpacer(8.dp)
            Box(Modifier.padding(horizontal = 4.dp)) { rateCell() }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                values.forEach { (v, l) -> StatCell(v, l, loading, showLabel = !compact) }
                rateCell()
            }
        }
        // V2-08: 23 dp (the prototype's caption line box under the values), so the divider sits at 237 dp as in the shot.
        VSpacer(if (compact) 8.dp else 23.dp)
        Hairline()
    }
}

@Composable
private fun StatCell(value: Int?, label: String, loading: Boolean, showLabel: Boolean) {
    val shown by animateIntAsState(value ?: 0, tween(NbMotion.short), label = "stat")
    Column {
        Text(if (loading) "–" else shown.toString(), style = NbType.numStat, color = if (loading) NbColors.textDisabled else NbColors.ink, maxLines = 1, softWrap = false, autoFit = false)
        if (showLabel) Text(label, style = NbType.caption, color = NbColors.text3, maxLines = 1)
    }
}

/** 승률: `52` + `%` at 0.7 em; no games = `–` in `text3`; loading = `–` in `textDisabled`. */
@Composable
private fun RateCell(rate: Int?, loading: Boolean, showLabel: Boolean) {
    Column(horizontalAlignment = Alignment.Start) {
        if (rate == null) {
            Text("–", style = NbType.numStat, color = if (loading) NbColors.textDisabled else NbColors.text3, maxLines = 1, softWrap = false, autoFit = false)
        } else {
            Text(
                buildAnnotatedString {
                    append(rate.toString())
                    withStyle(SpanStyle(fontSize = NbType.numStat.fontSize * 0.7f)) { append("%") }
                },
                style = NbType.numStat,
                color = NbColors.ink,
                maxLines = 1,
                softWrap = false,
                autoFit = false,
            )
        }
        if (showLabel) Text(stringResource(R.string.record_rate), style = NbType.caption, color = NbColors.text3, maxLines = 1)
    }
}

/** Hero (02): four decorative `?` plates 56 × 64 dp, 8 dp gap; 12 dp; link "처음이라면 게임 방법 보기" → tutorial. */
@Composable
private fun Hero(onHowToPlay: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { QuestionPlate(lit = false, width = 56.dp, height = 64.dp) }
        }
        VSpacer(8.dp)
        NbButton(stringResource(R.string.home_first_time), onHowToPlay, kind = ButtonKind.LINK)
    }
}

/** Mode tile (DESIGN_SYSTEM 12.19): 84 dp min, radius 12, `bg` + 1.5 dp `line`; icon 24 `ink` over `labelSmall` 14 sp. */
@Composable
private fun ModeTile(@DrawableRes icon: Int, label: String, enabled: Boolean, compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(NbRadius.m)
    val color = if (enabled) NbColors.ink else NbColors.textDisabled
    val needsInternet = stringResource(R.string.needs_internet)
    val content: @Composable () -> Unit = {
        NbIcon(icon, null, color)
    }
    val text: @Composable () -> Unit = {
        // V2-13: strict CJK line breaking by phrase, so "ー" never starts a line and no word is split.
        Text(label, style = NbType.labelSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, lineBreak = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Strict, LineBreak.WordBreak.Phrase)), color = color, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, fitMinScale = 0.5f)
    }
    Box(
        modifier
            .clip(shape)
            .border(NbSize.border, NbColors.line, shape)
            .rowClick(enabled = enabled, onClick = onClick)
            .then(if (!enabled) Modifier.semantics { stateDescription = needsInternet } else Modifier)
            .padding(horizontal = 4.dp, vertical = if (compact) 6.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (compact && isLargeFont()) {
            // Small window at large text: the label alone keeps the tile's width (the icon is decorative).
            text()
        } else if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                content()
                HSpacer(6.dp)
                text()
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                content()
                VSpacer(6.dp)
                text()
            }
        }
    }
}

/**
 * Quick-match block (02), pinned: head "빠른 대전" + "60초 · 비슷한 상대"; digit tiles 52 dp; 12 dp; primary "빠른 대전 시작";
 * 24 dp clear space before the band. Small windows: no head, 48 dp tiles and button side by side when they fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickBlock(state: HomeUiState, compact: Boolean, onQuickDigits: (Int) -> Unit, onQuickMatch: () -> Unit) {
    val margin = if (compact) 12.dp else 16.dp
    val unit = pluralStringResource(R.plurals.digit_unit, 2)
    val tiles: @Composable (Modifier) -> Unit = { m ->
        Segmented(
            items = Limits.DIGITS.toList(),
            selected = state.quickDigits,
            onSelect = onQuickDigits,
            modifier = m,
            tileHeight = if (compact) 48.dp else if (isLargeFont()) 64.dp else NbSize.digitTileH,
        ) { d, selected -> NumberUnitLabel(d.toString(), if (compact) null else unit, selected) }
    }
    val button: @Composable (Modifier) -> Unit = { m ->
        NbButton(
            text = stringResource(if (state.online) R.string.quick_start else R.string.waiting_connection),
            onClick = onQuickMatch,
            enabled = state.canPlayOnline,
            heightOverride = if (compact) 48.dp else null,
            modifier = m.testTag("quickMatch"),
        )
    }
    Column(Modifier.fillMaxWidth().padding(start = margin, end = margin, top = if (compact) 8.dp else 24.dp, bottom = if (compact) 12.dp else NbLayout.bannerClearSpace)) {
        if (compact) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Side by side only when the button keeps room for its label (not at large text).
                if (!isLargeFont() && maxWidth - (48.dp * 4 + 8.dp * 4) >= 112.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        tiles(Modifier.width(48.dp * 4 + 8.dp * 3))
                        HSpacer(8.dp)
                        button(Modifier.weight(1f))
                    }
                } else {
                    Column {
                        tiles(Modifier)
                        VSpacer(8.dp)
                        button(Modifier.fillMaxWidth())
                    }
                }
            }
            return@Column
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.quick_title), style = NbType.subtitle.copy(fontSize = 17.sp, lineHeight = 24.sp), color = NbColors.ink, modifier = Modifier.alignByBaseline().padding(end = 12.dp))
            Text(stringResource(R.string.quick_meta), style = NbType.caption, color = NbColors.text3, modifier = Modifier.alignByBaseline())
        }
        VSpacer(10.dp)
        tiles(Modifier)
        VSpacer(12.dp)
        button(Modifier.fillMaxWidth())
    }
}
