package app.numbaseball.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.ads.AdBanner
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.Stats
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.AccountState
import app.numbaseball.data.account.RegistrationResult
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SecondaryButton
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.toUiError
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
    /** A deletion request was made on the website and is pending (README "Terms, deletion and review"). */
    val deletionPending: Boolean = false,
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

class HomeViewModel(private val accounts: AccountRepository) : ViewModel() {
    private val local = MutableStateFlow(HomeUiState())

    val state: StateFlow<HomeUiState> = combine(accounts.state, accounts.deletionRequestedAt, local) { account, deletion, ui ->
        when (account) {
            is AccountState.Registered -> ui.copy(
                displayName = account.profile.displayName,
                registered = true,
                stats = account.stats,
                deletionPending = deletion != null,
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

    fun onCancelDeletion() {
        viewModelScope.launch { local.update { it.copy(error = accounts.cancelDeletionRequest().toUiError()) } }
    }

    fun onDeleteNow(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val error = accounts.deleteAccount().toUiError()
            local.update { it.copy(error = error) }
            if (error == null) onDeleted()
        }
    }
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
    onCancelDeletion: () -> Unit,
    onDeleteNow: () -> Unit,
) {
    ScreenScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        actions = { TextButton(onClick = onSettings) { Text(stringResource(R.string.home_settings)) } },
        bottomBar = { AdBanner() },
    ) {
        if (state.deletionPending) DeletionPendingCard(onCancelDeletion, onDeleteNow)
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
        SecondaryButton(stringResource(R.string.home_quick_match), onQuickMatch, enabled = state.registered)
        SecondaryButton(stringResource(R.string.home_create_room), onCreateRoom, enabled = state.registered)
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
            TextButton(onClick = { onJoinRoom(state.roomCode) }, enabled = state.registered && state.roomCodeComplete) {
                Text(stringResource(R.string.home_join_room))
            }
        }
        TextButton(onClick = onHowToPlay) { Text(stringResource(R.string.home_how_to_play)) }
    }
}

@Composable
private fun DeletionPendingCard(onCancel: () -> Unit, onDeleteNow: () -> Unit) {
    Card {
        Column(Modifier.padding(NbSpace.s16), verticalArrangement = Arrangement.spacedBy(NbSpace.s8)) {
            Text(stringResource(R.string.deletion_pending_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.deletion_pending_body), color = NbColors.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s8)) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.deletion_pending_cancel)) }
                TextButton(onClick = onDeleteNow) { Text(stringResource(R.string.deletion_pending_delete_now), color = NbColors.out) }
            }
        }
    }
}
