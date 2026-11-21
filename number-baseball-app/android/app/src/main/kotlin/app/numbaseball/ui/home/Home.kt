package app.numbaseball.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SecondaryButton
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbSpace
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val displayName: String = "",
    val registered: Boolean = false,
    val stats: Stats? = null,
    val roomCode: String = "",
    /** The server refused the nickname chosen offline: offer to pick another one. */
    val nicknameRejected: Boolean = false,
    /** Play Age Signals can refuse online features (policy review R21). */
    val onlineAllowed: Boolean = true,
    val error: UiError? = null,
) {
    val roomCodeComplete: Boolean get() = roomCode.length == Limits.ROOM_CODE_LENGTH
}

object RoomCodes {
    /** docs/protocol.md §1: 5 characters from this alphabet; the server ignores case, spaces and dashes. */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun normalize(raw: String): String =
        raw.uppercase().filter { it in ALPHABET }.take(Limits.ROOM_CODE_LENGTH)
}

class HomeViewModel(private val accounts: AccountRepository, ages: AgeRepository) : ViewModel() {
    private val local = MutableStateFlow(HomeUiState())

    val state: StateFlow<HomeUiState> = combine(accounts.state, ages.state, local) { account, age, base ->
        val ui = base.copy(onlineAllowed = (age as? AgeState.Known)?.onlineAllowed ?: false)
        when (account) {
            is AccountState.Registered -> ui.copy(
                displayName = account.profile.displayName,
                registered = true,
                stats = account.stats,
            )
            is AccountState.Unregistered -> ui.copy(displayName = account.nickname, registered = false, stats = null)
            AccountState.Loading, AccountState.NeedsNickname -> ui
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch {
            when (accounts.state.value) {
                is AccountState.Unregistered -> {
                    val result = accounts.ensureRegistered()
                    if (result is RegistrationResult.Rejected) local.update { it.copy(nicknameRejected = true) }
                }
                is AccountState.Registered -> accounts.refresh()
                else -> Unit
            }
        }
    }

    fun onRoomCodeChange(value: String) = local.update { it.copy(roomCode = RoomCodes.normalize(value)) }

}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRoomCodeChange: (String) -> Unit,
    onVsComputer: () -> Unit,
    onQuickMatch: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onHowToPlay: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onChooseNickname: () -> Unit,
) {
    ScreenMusic(MusicTrack.MENU, LocalGameAudio.current)
    ScreenScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        actions = { TextButton(onClick = onSettings) { Text(stringResource(R.string.home_settings)) } },
        bottomBar = { AdBanner() },
    ) {
        Text(stringResource(R.string.home_tagline), color = NbColors.text2)
        TextButton(onClick = onProfile, modifier = Modifier.testTag("profile")) {
            Text(state.displayName, style = NbType.numTag.merge(MaterialTheme.typography.titleMedium))
        }
        state.stats?.let { Text(stringResource(R.string.home_record, it.wins, it.losses, it.draws), color = NbColors.text2) }
        ErrorText(state.error)
        PrimaryButton(stringResource(R.string.home_vs_computer), onVsComputer)
        if (!state.registered) {
            Text(stringResource(R.string.home_not_registered), color = NbColors.text3, style = MaterialTheme.typography.bodySmall)
            if (state.nicknameRejected) SecondaryButton(stringResource(R.string.home_choose_nickname_again), onChooseNickname)
        }
        val online = state.registered && state.onlineAllowed
        SecondaryButton(stringResource(R.string.home_quick_match), onQuickMatch, enabled = online)
        SecondaryButton(stringResource(R.string.home_create_room), onCreateRoom, enabled = online)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s8)) {
            OutlinedTextField(
                value = state.roomCode,
                onValueChange = onRoomCodeChange,
                label = { Text(stringResource(R.string.home_room_code)) },
                singleLine = true,
                textStyle = NbType.numTag,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                modifier = Modifier.weight(1f).testTag("roomCode"),
            )
            TextButton(onClick = { onJoinRoom(state.roomCode) }, enabled = online && state.roomCodeComplete) {
                Text(stringResource(R.string.home_join_room))
            }
        }
        TextButton(onClick = onHowToPlay) { Text(stringResource(R.string.home_how_to_play)) }
    }
}
