package app.numbaseball.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.AccountState
import app.numbaseball.data.account.ApiResult
import app.numbaseball.data.settings.AppSettings
import app.numbaseball.data.settings.LanguageSettings
import app.numbaseball.data.settings.SettingsRepository
import app.numbaseball.data.settings.SupportedLanguages
import app.numbaseball.ui.common.ChoiceRow
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SectionLabel
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbLayout
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** null = follow the system language. */
    val language: String? = null,
    val registered: Boolean = false,
    /** Transfer code shown in a dialog, formatted XXXX-XXXX-XXXX. */
    val transferCode: String? = null,
    val confirmDelete: Boolean = false,
    val busy: Boolean = false,
    val error: UiError? = null,
    /** The account is gone: return to first run. */
    val deleted: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val languages: LanguageSettings,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUiState(language = languages.current()))

    val state: StateFlow<SettingsUiState> = combine(local, settings.settings, accounts.state) { ui, prefs, account ->
        ui.copy(settings = prefs, registered = account is AccountState.Registered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun onSettingsChange(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(change) }
    }

    fun onLanguageChange(tag: String?) {
        local.update { it.copy(language = tag) }
        languages.set(tag)
    }

    fun onTransferCode() {
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = accounts.createTransferCode()
            local.update {
                when (result) {
                    is ApiResult.Success -> it.copy(busy = false, transferCode = formatCode(result.value.code))
                    else -> it.copy(busy = false, error = result.toUiError())
                }
            }
        }
    }

    fun onDismissCode() = local.update { it.copy(transferCode = null) }

    fun onDeleteRequest() = local.update { it.copy(confirmDelete = true) }
    fun onDeleteCancel() = local.update { it.copy(confirmDelete = false) }

    fun onDeleteConfirm() {
        local.update { it.copy(confirmDelete = false, busy = true, error = null) }
        viewModelScope.launch {
            val error = accounts.deleteAccount().toUiError()
            local.update { it.copy(busy = false, error = error, deleted = error == null) }
        }
    }

    companion object {
        /** 12-character codes are shown as XXXX-XXXX-XXXX (docs/protocol.md §3.1); the server ignores the dashes. */
        fun formatCode(code: String): String = code.chunked(4).joinToString("-")
    }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    showPrivacyOptions: Boolean,
    versionName: String,
    contactEmail: String,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onLanguageChange: (String?) -> Unit,
    onPrivacyOptions: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTerms: () -> Unit,
    onCopyContact: () -> Unit,
    onTransferCode: () -> Unit,
    onDismissCode: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        val prefs = state.settings
        SwitchRow(stringResource(R.string.settings_effects), null, prefs.effectsEnabled) { v -> onSettingsChange { it.copy(effectsEnabled = v) } }
        VolumeRow(stringResource(R.string.settings_effects_volume), prefs.effectsVolume, prefs.effectsEnabled) { v ->
            onSettingsChange { it.copy(effectsVolume = v) }
        }
        SwitchRow(stringResource(R.string.settings_follow_silent), stringResource(R.string.settings_follow_silent_summary), prefs.followSilentMode) { v ->
            onSettingsChange { it.copy(followSilentMode = v) }
        }
        SwitchRow(stringResource(R.string.settings_music), null, prefs.musicEnabled) { v -> onSettingsChange { it.copy(musicEnabled = v) } }
        VolumeRow(stringResource(R.string.settings_music_volume), prefs.musicVolume, prefs.musicEnabled) { v ->
            onSettingsChange { it.copy(musicVolume = v) }
        }
        SwitchRow(stringResource(R.string.settings_haptics), null, prefs.hapticsEnabled) { v -> onSettingsChange { it.copy(hapticsEnabled = v) } }
        SwitchRow(stringResource(R.string.settings_mute_chat), null, prefs.chatMuted) { v -> onSettingsChange { it.copy(chatMuted = v) } }
        SectionLabel(stringResource(R.string.settings_language))
        ChoiceRow(listOf<String?>(null) + SupportedLanguages.TAGS, state.language, { languageLabel(it) }, onLanguageChange)
        HorizontalDivider(color = NbColors.line)
        if (showPrivacyOptions) LinkRow(stringResource(R.string.settings_privacy_options), onPrivacyOptions)
        LinkRow(stringResource(R.string.settings_privacy_policy), onPrivacyPolicy)
        LinkRow(stringResource(R.string.settings_terms), onTerms)
        ContactRow(contactEmail, onCopyContact)
        HorizontalDivider(color = NbColors.line)
        if (state.registered) {
            LinkRow(stringResource(R.string.settings_transfer), onTransferCode, stringResource(R.string.settings_transfer_summary), enabled = !state.busy)
        }
        LinkRow(stringResource(R.string.settings_delete), onDeleteRequest, enabled = !state.busy, destructive = true)
        ErrorText(state.error)
        Text(stringResource(R.string.settings_version, versionName), color = NbColors.text3, style = MaterialTheme.typography.bodySmall)
    }
    state.transferCode?.let { code ->
        AlertDialog(
            onDismissRequest = onDismissCode,
            title = { Text(stringResource(R.string.settings_transfer_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    SelectionContainer { Text(code, style = NbType.numCode) }
                    Text(stringResource(R.string.settings_transfer_body))
                }
            },
            confirmButton = { TextButton(onClick = onDismissCode) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = onDeleteCancel,
            title = { Text(stringResource(R.string.settings_delete_title)) },
            text = { Text(stringResource(R.string.settings_delete_body)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) { Text(stringResource(R.string.settings_delete_confirm), color = NbColors.out) }
            },
            dismissButton = { TextButton(onClick = onDeleteCancel) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun languageLabel(tag: String?): String = when (tag) {
    null -> stringResource(R.string.settings_language_system)
    "ko" -> stringResource(R.string.language_name_ko)
    "en" -> stringResource(R.string.language_name_en)
    else -> tag
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = NbLayout.touchMin)) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title)
            summary?.let { Text(it, color = NbColors.text3, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** App only (README): no web form, so the contact address is shown as selectable text with a copy button. */
@Composable
private fun ContactRow(email: String, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = NbLayout.touchMin)) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_contact))
            SelectionContainer { Text(email, color = NbColors.text2, style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(onClick = onCopy) { Text(stringResource(R.string.action_copy)) }
    }
}

@Composable
private fun VolumeRow(title: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
        Text(title, color = if (enabled) NbColors.chalk else NbColors.textDisabled, style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, enabled = enabled, valueRange = 0f..1f)
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit, summary: String? = null, enabled: Boolean = true, destructive: Boolean = false) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = NbLayout.touchMin)) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
            Text(title, color = if (destructive) NbColors.out else NbColors.chalk)
            summary?.let { Text(it, color = NbColors.text3, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
