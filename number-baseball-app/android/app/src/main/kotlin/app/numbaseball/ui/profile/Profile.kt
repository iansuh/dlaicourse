package app.numbaseball.ui.profile

import app.numbaseball.audio.ScreenMusic
import app.numbaseball.audio.MusicTrack
import app.numbaseball.audio.LocalGameAudio
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.InsetDivider
import app.numbaseball.ui.design.SectionHeader
import app.numbaseball.ui.design.isLargeFont
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.ads.AdBanner
import app.numbaseball.core.protocol.Stats
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.AccountState
import app.numbaseball.data.online.NetworkMonitor
import app.numbaseball.data.settings.SettingsRepository
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.exactLineHeight
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.Avatar
import app.numbaseball.ui.theme.NbSize
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.numbaseball.ui.design.NameWithTag
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.settings.NicknameChangeSheet
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val nickname: String = "",
    val tag: String? = null,
    val registered: Boolean = false,
    val stats: Stats? = null,
    val recentForm: String = "",
    val online: Boolean = true,
    val renaming: Boolean = false,
    val renameError: UiError? = null,
    val renamed: Boolean = false,
    /** Per digit count (3..6). Null: not available (the account endpoint sends only totals); the table shows `–`. */
    val byDigits: Map<Int, ModeRecord>? = null,
    /** Per mode. Null: not available; the rows show `–`. */
    val byMode: Map<RecordMode, ModeRecord>? = null,
)

/** One row of the profile tables (10 Layout). [fewestRounds] is the fewest rounds to solve, null when never solved. */
data class ModeRecord(val wins: Int, val losses: Int, val draws: Int, val fewestRounds: Int? = null)

enum class RecordMode { QUICK, ROOM, TEAM, COMPUTER }

class ProfileViewModel(private val accounts: AccountRepository, settings: SettingsRepository, network: NetworkMonitor) : ViewModel() {
    private val local = MutableStateFlow(ProfileUiState())

    val state: StateFlow<ProfileUiState> = combine(local, accounts.state, settings.settings, network.online) { ui, account, prefs, online ->
        val base = ui.copy(recentForm = prefs.recentForm, online = online)
        when (account) {
            is AccountState.Registered -> base.copy(nickname = account.profile.nickname, tag = account.profile.tag, registered = true, stats = account.stats)
            is AccountState.Unregistered -> base.copy(nickname = account.nickname, stats = Stats())
            else -> base
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch { accounts.refresh() }
    }

    fun onRename(nickname: String) {
        local.update { it.copy(renaming = true, renameError = null, renamed = false) }
        viewModelScope.launch {
            val error = accounts.rename(nickname).toUiError()
            local.update { it.copy(renaming = false, renameError = error, renamed = error == null) }
        }
    }

    fun onRenameShown() = local.update { it.copy(renamed = false, renameError = null) }
}

/**
 * Profile (10): identity with "change nickname", the record row at the larger size, the form row, the per-digit and
 * per-mode tables and the ad band. `/api/v1/me` returns only the totals, so the two tables are hidden until the server
 * sends them (design decision on D2-15, README deviations).
 */
@Composable
fun ProfileScreen(state: ProfileUiState, onRename: (String) -> Unit, onRenameShown: () -> Unit, onBack: () -> Unit) {
    ScreenMusic(MusicTrack.MENU, LocalGameAudio.current)
    var sheet by rememberSaveable { mutableStateOf(false) }
    NbScreen(
        topBar = { TopBar(stringResource(R.string.profile_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
        bottomBar = { if (state.online) AdBanner() },
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            // Profile (10 v2): avatar 48 + name over status / "닉네임 바꾸기", then the Home-style record row (no form row).
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(state.nickname, NbSize.avatarL)
                HSpacer(12.dp)
                Column(Modifier.weight(1f)) {
                    NameWithTag(state.nickname, state.tag, nameStyle = NbType.title, tagStyle = NbType.numTag.copy(fontSize = 18.sp, lineHeight = 28.sp))
                    if (!state.online && state.registered) {
                        Text(stringResource(R.string.profile_offline), style = NbType.caption, color = NbColors.text3)
                    } else if (!state.registered) {
                        Text(stringResource(R.string.profile_unregistered), style = NbType.caption, color = NbColors.text3)
                    }
                }
            }
            Box(Modifier.padding(start = 8.dp)) {
                NbButton(stringResource(R.string.change_nickname), { sheet = true }, kind = ButtonKind.TEXT, textColorOverride = NbColors.accent, enabled = state.registered && state.online)
            }
            VSpacer(8.dp)
            RecordRow(state.stats)
            VSpacer(16.dp)
            if ((state.stats?.let { it.wins + it.losses + it.draws } ?: 0) == 0) {
                Text(stringResource(R.string.first_game_waiting), style = NbType.caption, color = NbColors.text3, modifier = Modifier.padding(horizontal = 16.dp))
                VSpacer(8.dp)
            }
            // Design decision (round 2, D2-15): until `/api/v1/me` sends per-digit / per-mode records, both sections are
            // hidden, header included; a table of dashes would read as "never played".
            state.byDigits?.let { DigitTable(it) }
            state.byMode?.let { ModeRows(it) }
            VSpacer(24.dp)
        }
    }
    if (sheet) {
        NicknameChangeSheet(state.nickname, state.renaming, state.renameError, onRename, onDismiss = {
            sheet = false
            onRenameShown()
        })
        LaunchedEffect(state.renamed) {
            if (state.renamed) {
                sheet = false
                onRenameShown()
            }
        }
    }
}

/** Record row as on Home (02): 승 · 패 · 무 · 승률 spread across the width, `numStat` `ink` over `caption` `text3`. */
@Composable
private fun RecordRow(stats: Stats?) {
    val total = stats?.let { it.wins + it.losses + it.draws } ?: 0
    val rate = stats?.takeIf { total > 0 }?.let { (it.wins * 100f / total).toInt() }
    val spoken = stats?.let { stringResource(R.string.record_value, it.wins, it.losses, it.draws) }.orEmpty()
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics(mergeDescendants = true) { contentDescription = spoken },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat(stats?.wins, stringResource(R.string.record_win))
            Stat(stats?.losses, stringResource(R.string.record_loss))
            Stat(stats?.draws, stringResource(R.string.record_draw))
            Column {
                Text(
                    if (rate == null) AnnotatedString("–") else buildAnnotatedString {
                        append(rate.toString())
                        withStyle(SpanStyle(fontSize = NbType.numStat.fontSize * 0.7f)) { append("%") }
                    },
                    style = NbType.numStat,
                    color = if (rate == null) NbColors.text3 else NbColors.ink,
                    maxLines = 1,
                    softWrap = false,
                    autoFit = false,
                )
                Text(stringResource(R.string.record_rate), style = NbType.caption, color = NbColors.text3, maxLines = 1)
            }
        }
        VSpacer(20.dp)
        Hairline()
    }
}

@Composable
private fun Stat(value: Int?, label: String) {
    Column {
        // Zero counts in `text3` (faint-zero fix, DESIGN_SYSTEM 2.2).
        Text(value?.toString() ?: "–", style = NbType.numStat, color = when (value) { null -> NbColors.textDisabled; 0 -> NbColors.text3; else -> NbColors.ink }, maxLines = 1, softWrap = false, autoFit = false)
        Text(label, style = NbType.caption, color = NbColors.text3, maxLines = 1)
    }
}

/** "자리수별 기록" (10 Layout): 자리수 · 승 · 패 · 무 · 최소 회; rows 3–6자리, 40 dp, 1 dp `line` between. */
@Composable
private fun DigitTable(records: Map<Int, ModeRecord>?) {
    SectionHeader(stringResource(R.string.profile_by_digits), first = true)
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            TableHeader(stringResource(R.string.profile_col_digits), Modifier.weight(1.4f), TextAlign.Start)
            TableHeader(stringResource(R.string.record_win), Modifier.weight(1f))
            TableHeader(stringResource(R.string.record_loss), Modifier.weight(1f))
            TableHeader(stringResource(R.string.record_draw), Modifier.weight(1f))
            // Large text keeps every column; the headers wrap instead (D2-49).
            TableHeader(stringResource(R.string.profile_col_fewest), Modifier.weight(1.2f), TextAlign.End)
        }
        (3..6).forEach { digits ->
            val record = records?.get(digits)
            val label = pluralStringResource(R.plurals.digits_n, digits, digits)
            val spoken = if (record == null) "$label, ${stringResource(R.string.profile_no_record)}" else "$label, " + stringResource(R.string.record_value, record.wins, record.losses, record.draws)
            Hairline()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 39.dp).clearAndSetSemantics { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = NbType.labelSmall, color = NbColors.text3, modifier = Modifier.weight(1.4f))
                TableValue(record?.wins, Modifier.weight(1f))
                TableValue(record?.losses, Modifier.weight(1f))
                TableValue(record?.draws, Modifier.weight(1f))
                TableValue(record?.fewestRounds, Modifier.weight(1.2f), TextAlign.End, zeroDisabled = false)
            }
        }
        Hairline()
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier, align: TextAlign = TextAlign.Center) {
    Text(text, style = NbType.labelSmall, color = NbColors.text3, textAlign = align, maxLines = if (isLargeFont()) 2 else 1, softWrap = isLargeFont(), modifier = modifier)
}

/** `numLog` `ink`; zero in `text3` (AA); `–` when there is no record (`textDisabled`). */
@Composable
private fun TableValue(value: Int?, modifier: Modifier, align: TextAlign = TextAlign.Center, zeroDisabled: Boolean = true) {
    Text(
        value?.toString() ?: "–",
        style = NbType.numLog.copy(letterSpacing = NbType.numHint.letterSpacing),
        color = if (value == null) NbColors.textDisabled else if (zeroDisabled && value == 0) NbColors.text3 else NbColors.ink,
        textAlign = align,
        modifier = modifier,
    )
}

/** "모드별" (10 Layout): 56 dp rows, "12승 8패 3무" right-aligned; computer games listed apart, not in the record. */
@Composable
private fun ModeRows(records: Map<RecordMode, ModeRecord>?) {
    SectionHeader(stringResource(R.string.profile_by_mode))
    listOf(
        RecordMode.QUICK to R.string.mode_quick,
        RecordMode.ROOM to R.string.mode_room,
        RecordMode.TEAM to R.string.mode_team,
        RecordMode.COMPUTER to R.string.profile_cpu_mode,
    ).forEachIndexed { i, (mode, label) ->
        if (i > 0) InsetDivider(16.dp)
        val record = records?.get(mode)
        val title = stringResource(label)
        val value = record?.let { stringResource(R.string.record_value, it.wins, it.losses, it.draws) }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp)
                .clearAndSetSemantics { contentDescription = "$title, ${value ?: "–"}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = NbType.subtitle, color = NbColors.ink, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            HSpacer(12.dp)
            if (record == null) {
                Text("–", style = NbType.numTag, color = NbColors.textDisabled)
            } else {
                RecordValue(record)
            }
        }
    }
}

/** "12승 8패 3무": numbers `numTag`, units `caption`. */
@Composable
private fun RecordValue(record: ModeRecord) {
    val units = listOf(record.wins to R.string.record_win, record.losses to R.string.record_loss, record.draws to R.string.record_draw)
    Row(verticalAlignment = Alignment.CenterVertically) {
        units.forEachIndexed { i, (n, unit) ->
            if (i > 0) HSpacer(6.dp)
            Text(n.toString(), style = NbType.numTag, color = NbColors.text2, modifier = Modifier.alignByBaseline())
            Text(stringResource(unit), style = NbType.caption, color = NbColors.text3, modifier = Modifier.alignByBaseline())
        }
    }
}
