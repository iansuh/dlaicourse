package app.numbaseball.ui.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import app.numbaseball.ui.design.Lamp
import app.numbaseball.ui.design.LampColor
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
)

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
 * Profile (10): identity with "change nickname", the record row at the larger size, the form row and the ad band. The
 * per-digit and per-mode tables need server data the protocol does not provide yet, so they are not shown.
 */
@Composable
fun ProfileScreen(state: ProfileUiState, onRename: (String) -> Unit, onRenameShown: () -> Unit, onBack: () -> Unit) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    NbScreen(
        topBar = { TopBar(stringResource(R.string.profile_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
        bottomBar = { if (state.online) AdBanner() },
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)) {
                NameWithTag(state.nickname, state.tag, modifier = Modifier.exactLineHeight(NbType.headline), nameStyle = NbType.headline, tagStyle = NbType.numTag.copy(fontSize = 22.sp, lineHeight = 28.sp))
                if (!state.online && state.registered) {
                    Text(stringResource(R.string.profile_offline), style = NbType.caption, color = NbColors.text3)
                } else if (!state.registered) {
                    Text(stringResource(R.string.profile_unregistered), style = NbType.caption, color = NbColors.text3)
                }
                VSpacer(4.dp)
                Box(Modifier.padding(start = 0.dp)) {
                    NbButton(stringResource(R.string.change_nickname), { sheet = true }, kind = ButtonKind.TEXT, enabled = state.registered && state.online)
                }
            }
            RecordRow(state.stats)
            VSpacer(16.dp)
            FormRow(state)
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

@Composable
private fun RecordRow(stats: Stats?) {
    val total = stats?.let { it.wins + it.losses + it.draws } ?: 0
    val rate = stats?.takeIf { total > 0 }?.let { (it.wins * 100f / total).toInt() }
    val spoken = stats?.let { stringResource(R.string.record_value, it.wins, it.losses, it.draws) }.orEmpty()
    Row(Modifier.padding(horizontal = 16.dp).height(IntrinsicSize.Min).semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Stat(stats?.wins, stringResource(R.string.record_win), first = true)
        Divider()
        Stat(stats?.losses, stringResource(R.string.record_loss))
        Divider()
        Stat(stats?.draws, stringResource(R.string.record_draw))
        Divider()
        Column(Modifier.padding(start = 20.dp).align(Alignment.Bottom)) {
            Text(if (rate == null) "–" else "$rate%", style = NbType.numStat, color = NbColors.text2)
            Text(stringResource(R.string.record_rate), style = NbType.caption, color = NbColors.text3)
        }
    }
}

@Composable
private fun Stat(value: Int?, label: String, first: Boolean = false) {
    Column(Modifier.padding(start = if (first) 0.dp else 20.dp, end = 20.dp)) {
        Text(value?.toString() ?: "–", style = NbType.numScore, color = if (value == null) NbColors.textDisabled else NbColors.chalk)
        Text(label, style = NbType.caption, color = NbColors.text3)
    }
}

@Composable
private fun Divider() = Box(Modifier.width(1.dp).fillMaxHeight().background(NbColors.line))

@Composable
private fun FormRow(state: ProfileUiState) {
    val total = state.stats?.let { it.wins + it.losses + it.draws } ?: 0
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (total == 0 || state.recentForm.isEmpty()) {
            if (total == 0) Text(stringResource(R.string.first_game_waiting), style = NbType.caption, color = NbColors.text3)
            return@Row
        }
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
