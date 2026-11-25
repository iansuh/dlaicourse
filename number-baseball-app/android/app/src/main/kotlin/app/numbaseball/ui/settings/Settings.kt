package app.numbaseball.ui.settings

import app.numbaseball.audio.ScreenMusic
import app.numbaseball.audio.MusicTrack
import android.media.AudioManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.audio.HapticCue
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.LocalGameHaptics
import app.numbaseball.audio.SoundCue
import app.numbaseball.core.protocol.BlockedPlayer
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.AccountState
import app.numbaseball.data.account.ApiResult
import app.numbaseball.data.account.NicknameRules
import app.numbaseball.data.online.SafetyRepository
import app.numbaseball.data.settings.AppSettings
import app.numbaseball.data.settings.LanguageSettings
import app.numbaseball.data.settings.SettingsRepository
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.common.message
import app.numbaseball.ui.common.toUiError
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.ChaseLamps
import app.numbaseball.ui.design.DialogSurface
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.InsetDivider
import app.numbaseball.ui.design.ListRow
import app.numbaseball.ui.design.NameWithTag
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbModalSheet
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.NbSwitchVisual
import app.numbaseball.ui.design.NbTextField
import app.numbaseball.ui.design.RadioRow
import app.numbaseball.ui.design.SectionHeader
import app.numbaseball.ui.design.SheetTitle
import app.numbaseball.ui.design.ToastHost
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.VolumeSliderRow
import app.numbaseball.ui.design.formatClock
import app.numbaseball.ui.design.rememberToastState
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransferCode(val code: String, val expiresAt: Long)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** null = follow the system language. */
    val language: String? = null,
    val registered: Boolean = false,
    val nickname: String = "",
    val tag: String? = null,
    val blockedCount: Int = 0,
    val transferCode: TransferCode? = null,
    val transferLoading: Boolean = false,
    val renaming: Boolean = false,
    val renameError: UiError? = null,
    val renamed: Boolean = false,
    val deleting: Boolean = false,
    val error: UiError? = null,
    /** The account is gone: return to first run. */
    val deleted: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val languages: LanguageSettings,
    private val safety: SafetyRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUiState(language = languages.current()))

    val state: StateFlow<SettingsUiState> = combine(local, settings.settings, accounts.state, safety.blocked) { ui, prefs, account, blocked ->
        when (account) {
            is AccountState.Registered -> ui.copy(settings = prefs, registered = true, nickname = account.profile.nickname, tag = account.profile.tag, blockedCount = blocked.size)
            is AccountState.Unregistered -> ui.copy(settings = prefs, registered = false, nickname = account.nickname, tag = null, blockedCount = blocked.size)
            else -> ui.copy(settings = prefs, blockedCount = blocked.size)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch { safety.loadBlocked() }
    }

    fun onSettingsChange(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(change) }
    }

    fun onLanguageChange(tag: String?) {
        local.update { it.copy(language = tag) }
        languages.set(tag)
    }

    fun onTransferCode() {
        local.update { it.copy(transferLoading = true, error = null) }
        viewModelScope.launch {
            val result = accounts.createTransferCode()
            local.update {
                when (result) {
                    is ApiResult.Success -> it.copy(
                        transferLoading = false,
                        transferCode = TransferCode(result.value.code, result.value.expiresAt.takeIf { at -> at > 0 } ?: (clock() + TRANSFER_MS)),
                    )
                    else -> it.copy(transferLoading = false, error = result.toUiError())
                }
            }
        }
    }

    fun onDismissCode() = local.update { it.copy(transferCode = null) }

    fun onRename(nickname: String) {
        if (NicknameRules.problem(nickname) != null) {
            local.update { it.copy(renameError = UiError.Code(ErrorCodes.INVALID_NICKNAME)) }
            return
        }
        local.update { it.copy(renaming = true, renameError = null, renamed = false) }
        viewModelScope.launch {
            val error = accounts.rename(nickname).toUiError()
            local.update { it.copy(renaming = false, renameError = error, renamed = error == null) }
        }
    }

    fun onRenameShown() = local.update { it.copy(renamed = false, renameError = null) }

    fun onDeleteConfirm() {
        local.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            val error = accounts.deleteAccount().toUiError()
            local.update { it.copy(deleting = false, error = error, deleted = error == null) }
        }
    }

    fun onErrorShown() = local.update { it.copy(error = null) }

    companion object {
        const val TRANSFER_MS = 10 * 60_000L

        /** 12-character codes are shown as XXXX-XXXX-XXXX (docs/protocol.md §3.1); the server ignores the dashes. */
        fun formatCode(code: String): String = code.chunked(4).joinToString("-")
    }
}

class SettingsActions(
    val onBack: () -> Unit,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    val onLanguageChange: (String?) -> Unit,
    val onBlocked: () -> Unit,
    val onRename: (String) -> Unit,
    val onRenameShown: () -> Unit,
    val onTransferCode: () -> Unit,
    val onDismissCode: () -> Unit,
    val onCopy: (label: String, text: String) -> Unit,
    val onDeleteConfirm: () -> Unit,
    val onPrivacyOptions: () -> Unit,
    val onPrivacyPolicy: () -> Unit,
    val onTerms: () -> Unit,
    val onLicences: () -> Unit,
    val onErrorShown: () -> Unit,
)

/** Settings (11): sound and vibration, chat safety, account and legal items. Every switch applies at once. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    showPrivacyOptions: Boolean,
    versionName: String,
    contactEmail: String,
    languageTags: List<String>,
    actions: SettingsActions,
) {
    val audio = LocalGameAudio.current
    val haptics = LocalGameHaptics.current
    ScreenMusic(MusicTrack.MENU, audio)
    val toast = rememberToastState()
    val scroll = rememberScrollState()
    var languageSheet by rememberSaveable { mutableStateOf(false) }
    var nicknameSheet by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val silentNow = remember(state.settings.followSilentMode) {
        context.getSystemService(AudioManager::class.java)?.ringerMode?.let { it != AudioManager.RINGER_MODE_NORMAL } == true
    }
    val emailCopied = stringResource(R.string.contact_copied)
    state.error?.let { error ->
        val text = stringResource(error.message())
        LaunchedEffect(error) {
            toast.show(text, error = true)
            actions.onErrorShown()
        }
    }
    val toggle: (Boolean, (AppSettings) -> AppSettings) -> Unit = { on, change ->
        actions.onSettingsChange(change)
        haptics.perform(if (on) HapticCue.TOGGLE_ON else HapticCue.TOGGLE_OFF)
        audio.play(if (on) SoundCue.TOGGLE_ON else SoundCue.TOGGLE_OFF)
    }
    val s = state.settings
    NbScreen(
        topBar = {
            TopBar(stringResource(R.string.settings_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), actions.onBack, scrolled = scroll.value > 0)
        },
        overlay = { ToastHost(toast) },
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            SectionHeader(stringResource(R.string.sec_game), first = true)
            ListRow(
                stringResource(R.string.language),
                icon = R.drawable.ic_language,
                value = state.language?.let { languageName(it) } ?: stringResource(R.string.language_system),
                chevron = true,
                onClick = { languageSheet = true },
            )
            SectionHeader(stringResource(R.string.sec_sound))
            SwitchRow(
                R.drawable.ic_volume_up,
                stringResource(R.string.sfx),
                if (s.effectsEnabled && s.followSilentMode && silentNow) stringResource(R.string.sfx_silent_now) else stringResource(R.string.sfx_sub),
                s.effectsEnabled,
                subtitleColor = if (s.effectsEnabled && s.followSilentMode && silentNow) NbColors.strike else NbColors.text3,
            ) { toggle(it) { a -> a.copy(effectsEnabled = it) } }
            VolumeSliderRow(stringResource(R.string.sfx_volume), s.effectsVolume, s.effectsEnabled, { v -> actions.onSettingsChange { it.copy(effectsVolume = v) } }) {
                audio.play(SoundCue.KEY_DOWN)
            }
            InsetDivider()
            SwitchRow(R.drawable.ic_do_not_disturb_on, stringResource(R.string.follow_silent), stringResource(R.string.follow_silent_sub), s.followSilentMode) {
                toggle(it) { a -> a.copy(followSilentMode = it) }
            }
            InsetDivider()
            SwitchRow(R.drawable.ic_music_note, stringResource(R.string.music), stringResource(R.string.music_sub), s.musicEnabled) {
                toggle(it) { a -> a.copy(musicEnabled = it) }
            }
            VolumeSliderRow(stringResource(R.string.music_volume), s.musicVolume, s.musicEnabled, { v -> actions.onSettingsChange { it.copy(musicVolume = v) } })
            InsetDivider()
            SwitchRow(R.drawable.ic_mobile_vibrate, stringResource(R.string.haptics), stringResource(R.string.haptics_sub), s.hapticsEnabled) {
                toggle(it) { a -> a.copy(hapticsEnabled = it) }
            }
            SectionHeader(stringResource(R.string.sec_chat))
            SwitchRow(R.drawable.ic_notifications_off, stringResource(R.string.chat_mute), stringResource(R.string.mute_chat_sub), s.chatMuted) {
                toggle(it) { a -> a.copy(chatMuted = it) }
            }
            InsetDivider()
            ListRow(
                stringResource(R.string.blocked_players),
                icon = R.drawable.ic_block,
                value = pluralStringResource(R.plurals.people_count, state.blockedCount, state.blockedCount),
                chevron = true,
                onClick = actions.onBlocked,
            )
            SectionHeader(stringResource(R.string.sec_account))
            ListRow(
                stringResource(R.string.nickname),
                icon = R.drawable.ic_person,
                value = state.nickname + (state.tag?.let { "#$it" } ?: ""),
                chevron = state.registered,
                enabled = true,
                onClick = if (state.registered) ({ nicknameSheet = true }) else null,
            )
            InsetDivider()
            ListRow(
                stringResource(R.string.transfer),
                icon = R.drawable.ic_key,
                subtitle = stringResource(R.string.transfer_sub),
                chevron = true,
                enabled = state.registered,
                onClick = actions.onTransferCode,
            )
            InsetDivider()
            ListRow(
                stringResource(R.string.delete_account),
                icon = R.drawable.ic_delete,
                subtitle = stringResource(R.string.delete_sub),
                destructive = true,
                destructiveIcon = false,
                chevron = true,
                enabled = state.registered,
                onClick = { deleteDialog = true },
                modifier = Modifier.testTag("deleteAccount"),
            )
            SectionHeader(stringResource(R.string.sec_about))
            if (showPrivacyOptions) {
                ListRow(stringResource(R.string.privacy_options), icon = R.drawable.ic_shield, subtitle = stringResource(R.string.privacy_options_sub), chevron = true, onClick = actions.onPrivacyOptions)
                InsetDivider()
            }
            ListRow(stringResource(R.string.privacy_policy), icon = R.drawable.ic_policy, chevron = true, onClick = actions.onPrivacyPolicy)
            InsetDivider()
            ListRow(stringResource(R.string.terms_of_use), icon = R.drawable.ic_description, chevron = true, onClick = actions.onTerms)
            InsetDivider()
            ContactRow(contactEmail) {
                actions.onCopy("email", contactEmail)
                // Android 13+ shows its own clipboard confirmation.
                if (android.os.Build.VERSION.SDK_INT < 33) toast.show(emailCopied)
            }
            InsetDivider()
            ListRow(stringResource(R.string.licences), icon = R.drawable.ic_info, chevron = true, onClick = actions.onLicences)
            InsetDivider()
            VersionRow(versionName) { actions.onCopy("version", versionName) }
            VSpacer(24.dp)
        }
    }
    if (languageSheet) {
        NbModalSheet(onDismiss = { languageSheet = false }) {
            SheetTitle(stringResource(R.string.language))
            Column(Modifier.selectableGroup()) {
                RadioRow(stringResource(R.string.language_system), state.language == null, {
                    languageSheet = false
                    actions.onLanguageChange(null)
                })
                languageTags.forEach { tag ->
                    RadioRow(languageName(tag), state.language == tag, {
                        languageSheet = false
                        actions.onLanguageChange(tag)
                    })
                }
            }
            VSpacer(24.dp)
        }
    }
    if (nicknameSheet) {
        NicknameChangeSheet(state.nickname, state.renaming, state.renameError, actions.onRename, onDismiss = {
            nicknameSheet = false
            actions.onRenameShown()
        })
        LaunchedEffect(state.renamed) {
            if (state.renamed) {
                nicknameSheet = false
                actions.onRenameShown()
            }
        }
    }
    state.transferCode?.let { TransferSheet(it, state.transferLoading, actions) }
    if (deleteDialog || state.deleting) {
        Dialog(onDismissRequest = { if (!state.deleting) deleteDialog = false }) {
            app.numbaseball.ui.design.ScrimDim()
            DeleteDialog(state.deleting, onCancel = { deleteDialog = false }, onConfirm = actions.onDeleteConfirm)
        }
    }
}

private fun languageName(tag: String): String = when (tag) {
    "ko" -> "한국어"
    "en" -> "English"
    else -> tag
}

/** Switch row (DESIGN_SYSTEM 12.14): one element with role switch; the whole row toggles. */
@Composable
private fun SwitchRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    subtitleColor: androidx.compose.ui.graphics.Color = NbColors.text3,
    onChange: (Boolean) -> Unit,
) {
    val on = stringResource(R.string.state_on)
    val off = stringResource(R.string.state_off)
    ListRow(
        title,
        icon = icon,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        role = Role.Switch,
        onClick = { onChange(!checked) },
        trailing = { NbSwitchVisual(checked) },
        modifier = Modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            stateDescription = if (checked) on else off
        },
    )
}

/** 문의하기: the address on the second line (selectable), a trailing copy button; long-press copies too. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(email: String, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).combinedClickable(onClick = {}, onLongClick = onCopy).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NbIcon(R.drawable.ic_send, null, NbColors.text2)
        HSpacer(16.dp)
        // Prototype `.row`: 10 dp vertical padding, 2 dp between the lines, 16 dp before the copy button.
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(stringResource(R.string.contact), style = NbType.subtitle, color = NbColors.chalk)
            VSpacer(2.dp)
            SelectionContainer { Text(email, style = NbType.body, color = NbColors.text2) }
        }
        HSpacer(16.dp)
        IconButton48(R.drawable.ic_content_copy, stringResource(R.string.action_copy), onCopy, tint = NbColors.text2)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VersionRow(version: String, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).combinedClickable(onClick = {}, onLongClick = onCopy).padding(start = 56.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.version), style = NbType.body, color = NbColors.text3, modifier = Modifier.weight(1f))
        Text(version, style = NbType.numTag, color = NbColors.text3)
    }
}

/** Nickname sheet (11, 10): same rules as first run (01); the tag stays when free. */
@Composable
fun NicknameChangeSheet(nickname: String, renaming: Boolean, error: UiError?, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(nickname) }
    NbModalSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.change_nickname)) {
            IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), onDismiss, tint = NbColors.chalk)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            NbTextField(
                value = value,
                onValueChange = { raw -> value = raw.filter { Character.isLetterOrDigit(it.code) || Character.isSurrogate(it) }.take(24) },
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            VSpacer(8.dp)
            Row {
                Text(
                    error?.let { stringResource(it.message()) } ?: stringResource(R.string.nick_helper),
                    style = NbType.caption,
                    color = if (error != null) NbColors.out else NbColors.text3,
                    modifier = Modifier.weight(1f),
                )
                Text("${value.codePointCount(0, value.length)} / 12", style = NbType.numTag, color = NbColors.text3)
            }
            VSpacer(24.dp)
            NbButton(
                stringResource(R.string.action_ok),
                { onRename(value) },
                enabled = value.isNotEmpty() && value != nickname && !renaming,
                modifier = Modifier.fillMaxWidth(),
                content = if (renaming) ({ ChaseLamps() }) else null,
            )
            VSpacer(24.dp)
        }
    }
}

/** Transfer-code sheet (11 §2): code, "m:ss 남음" countdown, copy; expired → plates disabled and "new code". */
@Composable
private fun TransferSheet(code: TransferCode, loading: Boolean, actions: SettingsActions) {
    var left by remember(code) { mutableIntStateOf(((code.expiresAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)) }
    LaunchedEffect(code) {
        while (left > 0) {
            delay(1000)
            left = ((code.expiresAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
        }
    }
    val expired = left == 0
    NbModalSheet(onDismiss = actions.onDismissCode) {
        SheetTitle(stringResource(R.string.transfer_title)) {
            IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), actions.onDismissCode, tint = NbColors.chalk)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.transfer_body), style = NbType.body, color = NbColors.text2)
            VSpacer(24.dp)
            val formatted = SettingsViewModel.formatCode(code.code)
            SelectionContainer {
                Text(
                    formatted,
                    style = NbType.numCode.copy(fontSize = NbType.numSlotSm.fontSize),
                    color = if (expired) NbColors.textDisabled else NbColors.chalk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { contentDescription = code.code.toList().joinToString(" ") },
                )
            }
            VSpacer(8.dp)
            Text(stringResource(R.string.transfer_left, formatClock(left)), style = NbType.numTimer, color = NbColors.text2)
            VSpacer(24.dp)
            if (expired) {
                NbButton(stringResource(R.string.transfer_new), actions.onTransferCode, enabled = !loading, modifier = Modifier.fillMaxWidth())
            } else {
                NbButton(stringResource(R.string.action_copy), { actions.onCopy("code", code.code) }, kind = ButtonKind.TONAL, icon = R.drawable.ic_content_copy, modifier = Modifier.fillMaxWidth())
            }
            VSpacer(24.dp)
        }
    }
}

@Composable
private fun DeleteDialog(deleting: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    if (deleting) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { ChaseLamps() }
        return
    }
    DialogSurface(
        title = stringResource(R.string.delete_title),
        body = stringResource(R.string.delete_body),
        confirmLabel = stringResource(R.string.delete_confirm),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = onCancel,
        destructive = true,
    )
}

/** Settings > Blocked players (11 §2): rows with "차단 해제"; unblocking shows a toast with undo. */
@Composable
fun BlockedPlayersScreen(blocked: List<BlockedPlayer>, onUnblock: (BlockedPlayer) -> Unit, onUndo: (BlockedPlayer) -> Unit, onBack: () -> Unit) {
    val toast = rememberToastState()
    val unblocked = stringResource(R.string.unblocked_toast)
    val undo = stringResource(R.string.action_undo)
    NbScreen(
        topBar = { TopBar(stringResource(R.string.blocked_players), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
        overlay = { ToastHost(toast) },
    ) {
        if (blocked.isEmpty()) {
            Text(
                stringResource(R.string.blocked_empty),
                style = NbType.caption,
                color = NbColors.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            )
            return@NbScreen
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            blocked.forEach { player ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    NameWithTag(player.nickname, player.tag, modifier = Modifier.weight(1f))
                    NbButton(stringResource(R.string.unblock_action), {
                        onUnblock(player)
                        toast.show(unblocked, undo) { onUndo(player) }
                    }, kind = ButtonKind.TEXT)
                }
                InsetDivider(16.dp)
            }
        }
    }
}

/** Bundled components and their licences (11 §2); each opens its full text from assets. */
data class Licence(val component: String, val licence: String, val asset: String)

val LICENCES = listOf(
    Licence("Barlow Condensed", "SIL Open Font License 1.1", "licenses/OFL-BarlowCondensed.txt"),
    Licence("Material Symbols", "Apache License 2.0", "licenses/Apache-2.0.txt"),
    Licence("AndroidX, Jetpack Compose, Material 3", "Apache License 2.0", "licenses/Apache-2.0.txt"),
    Licence("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0", "licenses/Apache-2.0.txt"),
    Licence("OkHttp, Okio", "Apache License 2.0", "licenses/Apache-2.0.txt"),
    Licence("Google Mobile Ads SDK, User Messaging Platform, Play Age Signals", "Android Software Development Kit License / Google APIs Terms", "licenses/Apache-2.0.txt"),
)

@Composable
fun LicencesScreen(onOpen: (Licence) -> Unit, onBack: () -> Unit) {
    NbScreen(topBar = { TopBar(stringResource(R.string.licences), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LICENCES.forEach { licence ->
                ListRow(licence.component, subtitle = licence.licence, chevron = true, onClick = { onOpen(licence) }, icon = null)
                InsetDivider(16.dp)
            }
        }
    }
}

@Composable
fun LicenceTextScreen(licence: Licence, onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember(licence) { runCatching { context.assets.open(licence.asset).bufferedReader().use { it.readText() } }.getOrDefault("") }
    NbScreen(topBar = { TopBar(licence.component, R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) }) {
        SelectionContainer(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(text, style = NbType.caption, color = NbColors.text2)
            }
        }
    }
}
