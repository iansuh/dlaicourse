package app.numbaseball.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.ads.AdBanner
import app.numbaseball.core.protocol.Stats
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.AccountState
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SectionLabel
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(val displayName: String = "", val registered: Boolean = false, val stats: Stats? = null)

class ProfileViewModel(private val accounts: AccountRepository) : ViewModel() {
    val state: StateFlow<ProfileUiState> = accounts.state.map { it.toProfileUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), accounts.state.value.toProfileUi())

    init {
        viewModelScope.launch { accounts.refresh() }
    }
}

private fun AccountState.toProfileUi(): ProfileUiState = when (this) {
    is AccountState.Registered -> ProfileUiState(profile.displayName, registered = true, stats = stats)
    is AccountState.Unregistered -> ProfileUiState(nickname)
    AccountState.Loading, AccountState.NeedsNickname -> ProfileUiState()
}

@Composable
fun ProfileScreen(state: ProfileUiState, onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.profile_title), onBack = onBack, bottomBar = { AdBanner() }) {
        Text(state.displayName, style = NbType.numTag.merge(MaterialTheme.typography.titleLarge))
        SectionLabel(stringResource(R.string.profile_record))
        val stats = state.stats
        when {
            !state.registered -> Text(stringResource(R.string.profile_unregistered), color = NbColors.text2)
            stats == null -> Text(stringResource(R.string.profile_stats_unknown), color = NbColors.text2)
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(stringResource(R.string.profile_wins), stats.wins)
                Stat(stringResource(R.string.profile_losses), stats.losses)
                Stat(stringResource(R.string.profile_draws), stats.draws)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = NbType.numStat)
        Text(label, color = NbColors.text2)
    }
}
