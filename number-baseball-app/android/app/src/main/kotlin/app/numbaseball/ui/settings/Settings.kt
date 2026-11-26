package app.numbaseball.ui.settings

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import app.numbaseball.ui.theme.NbRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import app.numbaseball.audio.ScreenMusic
import app.numbaseball.audio.MusicTrack
import android.media.AudioManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.audio.HapticCue
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.LocalGameHaptics
import app.numbaseball.audio.SoundCue
import app.numbaseball.billing.PurchaseUiState
import app.numbaseball.billing.RemoveAdsState
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
import app.numbaseball.ui.common.isServerError
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
import app.numbaseball.ui.design.isLargeFont
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
        fun formatCode(code: String): String = codeGroups(code).joinToString("-")

        /** The code's characters in groups of four, whatever separators it came with (D2-42). */
        fun codeGroups(code: String): List<String> = code.filter { it.isLetterOrDigit() }.uppercase().chunked(4)
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
    /** 구매 section (11 §1.1): start Play's purchase sheet, reconnect after an error, re-query Play. */
    val onRemoveAds: () -> Unit = {},
    val onRetryBilling: () -> Unit = {},
    val onRestorePurchase: () -> Unit = {},
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
    purchase: PurchaseUiState = PurchaseUiState(),
    /** Wall clock for the transfer-code countdown (injected so renders and tests are deterministic, D2-42). */
    now: () -> Long = System::currentTimeMillis,
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
    // Server errors (5xx, unknown codes) get the 12.17 dialog; everything else a toast (D2-43).
    var serverError by rememberSaveable { mutableStateOf(false) }
    if (serverError) {
        app.numbaseball.ui.design.ServerErrorDialog(onRetry = { serverError = false }, onHome = { serverError = false; actions.onBack() }, onDismiss = { serverError = false })
    }
    state.error?.let { error ->
        if (error.isServerError()) {
            LaunchedEffect(error) {
                serverError = true
                actions.onErrorShown()
            }
            return@let
        }
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
            // Between 계정 and 정보 및 지원, always present (also for reviewers), 11 §1.1.
            SectionHeader(stringResource(R.string.sec_purchase))
            PurchaseRows(purchase, actions)
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
            // Ten languages plus "follow system": the list scrolls in short windows (D2-42).
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup()) {
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
    state.transferCode?.let { TransferSheet(it, state.transferLoading, actions, now) }
    if (deleteDialog || state.deleting) {
        app.numbaseball.ui.design.DialogFrame(onDismiss = { if (!state.deleting) deleteDialog = false }) {
            DeleteDialog(state.deleting, onCancel = { deleteDialog = false }, onConfirm = actions.onDeleteConfirm)
        }
    }
}

private fun languageName(tag: String): String = when (tag) {
    "ko" -> "한국어"
    "en" -> "English"
    "ja" -> "日本語"
    "zh-Hans" -> "简体中文"
    "es" -> "Español"
    "fr" -> "Français"
    "de" -> "Deutsch"
    "pt" -> "Português (Brasil)"
    "ru" -> "Русский"
    "vi" -> "Tiếng Việt"
    else -> tag
}

/**
 * "광고 제거" and "구매 복원" (11 §1.1): loading, not purchased with Play's price, purchased, pending, error. No lamp
 * colours, badges or sale wording; the price is the only number and always comes from Play.
 */
@Composable
private fun PurchaseRows(purchase: PurchaseUiState, actions: SettingsActions) {
    val title = stringResource(R.string.remove_ads)
    val sub = stringResource(R.string.remove_ads_sub)
    when (val ads = purchase.removeAds) {
        RemoveAdsState.Loading -> PurchaseRow(title, sub)
        // The whole row starts the purchase; no chevron (it does not navigate).
        is RemoveAdsState.Available -> PurchaseRow(
            title, sub,
            spoken = stringResource(R.string.remove_ads_a11y_price, ads.price),
            onClick = actions.onRemoveAds,
            modifier = Modifier.testTag("removeAdsRow"),
        ) { Text(ads.price, style = NbType.body, color = NbColors.text2, maxLines = 1) }
        RemoveAdsState.Owned -> PurchaseRow(title, stringResource(R.string.remove_ads_done), spoken = stringResource(R.string.remove_ads_a11y_done)) {
            NbIcon(R.drawable.ic_check_circle, null, NbColors.text2)
        }
        RemoveAdsState.Pending -> PurchaseRow(title, stringResource(R.string.remove_ads_pending), spoken = stringResource(R.string.remove_ads_a11y_pending)) {
            NbIcon(R.drawable.ic_hourglass_empty, null, NbColors.text2)
        }
        // Same warning colour as the silent-mode subtitle; "다시 시도" is the icon button and the whole row.
        RemoveAdsState.Unavailable -> PurchaseRow(
            title, stringResource(R.string.remove_ads_error),
            subtitleColor = NbColors.strike,
            onClick = actions.onRetryBilling,
            iconButton = true,
        ) { IconButton48(R.drawable.ic_refresh, stringResource(R.string.action_retry), actions.onRetryBilling, tint = NbColors.text2) }
    }
    if (purchase.removeAds == RemoveAdsState.Owned) return
    InsetDivider()
    RestoreRow(purchase.restoring, actions.onRestorePurchase)
}

/**
 * A 구매 row (12.14: 64 dp, grows when the subtitle wraps). At large text (ADAPTIVE §2.5) the price or icon moves below
 * the subtitle so the title and subtitle keep the full width.
 */
@Composable
private fun PurchaseRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    subtitleColor: androidx.compose.ui.graphics.Color = NbColors.text3,
    spoken: String? = null,
    onClick: (() -> Unit)? = null,
    /** The trailing slot is a 48 dp icon button: the row ends 4 dp from the edge so its glyph aligns with the margin. */
    iconButton: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val large = isLargeFont()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (spoken != null) Modifier.semantics(mergeDescendants = true) { contentDescription = spoken } else Modifier)
            .padding(start = 16.dp, end = if (iconButton && !large) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NbIcon(R.drawable.ic_ad_off, null, NbColors.text2)
        HSpacer(16.dp)
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(title, style = NbType.subtitle, color = NbColors.ink)
            VSpacer(2.dp)
            Text(subtitle, style = NbType.caption, color = subtitleColor)
            if (large && trailing != null) {
                VSpacer(4.dp)
                trailing()
            }
        }
        if (!large && trailing != null) {
            HSpacer(16.dp)
            trailing()
        }
    }
}

/** "구매 복원": while Play is queried the subtitle becomes a 3-lamp chase (chalk). */
@Composable
private fun RestoreRow(restoring: Boolean, onRestore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = !restoring, role = Role.Button, onClick = onRestore)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .testTag("restorePurchase"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NbIcon(R.drawable.ic_history, null, NbColors.text2)
        HSpacer(16.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.restore_purchase), style = NbType.subtitle, color = NbColors.ink)
            VSpacer(2.dp)
            if (restoring) {
                Box(Modifier.heightIn(min = 18.dp), contentAlignment = Alignment.CenterStart) { ChaseLamps() }
            } else {
                Text(stringResource(R.string.restore_purchase_sub), style = NbType.caption, color = NbColors.text3)
            }
        }
    }
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
            Text(stringResource(R.string.contact), style = NbType.subtitle, color = NbColors.ink)
            VSpacer(2.dp)
            SelectionContainer { Text(email, style = NbType.body, color = NbColors.text2, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fitMinScale = 0.5f) }
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
            IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), onDismiss, tint = NbColors.ink)
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
                    color = if (error != null) NbColors.danger else NbColors.text3,
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

/**
 * Transfer-code sheet (11 §2): the code as plates (`numCode`, 3 groups of 4, D2-51), "m:ss 남음" countdown, copy. Expired:
 * plates `textDisabled`, no countdown but "코드가 만료됐어요", and "새 코드 만들기".
 */
@Composable
private fun TransferSheet(code: TransferCode, loading: Boolean, actions: SettingsActions, now: () -> Long) {
    var left by remember(code) { mutableIntStateOf(((code.expiresAt - now()) / 1000).toInt().coerceAtLeast(0)) }
    LaunchedEffect(code) {
        while (left > 0) {
            delay(1000)
            left = ((code.expiresAt - now()) / 1000).toInt().coerceAtLeast(0)
        }
    }
    val expired = left == 0
    NbModalSheet(onDismiss = actions.onDismissCode) {
        SheetTitle(stringResource(R.string.transfer_title)) {
            IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), actions.onDismissCode, tint = NbColors.ink)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.transfer_body), style = NbType.body, color = NbColors.text2)
            VSpacer(24.dp)
            val groups = SettingsViewModel.codeGroups(code.code)
            SelectionContainer {
                CodePlates(groups, expired, Modifier.semantics(mergeDescendants = true) { contentDescription = groups.joinToString("").toList().joinToString(" ") })
            }
            VSpacer(8.dp)
            if (expired) {
                Text(stringResource(R.string.transfer_expired), style = NbType.caption, color = NbColors.text3)
            } else {
                Text(stringResource(R.string.transfer_left, formatClock(left)), style = NbType.numTimer, color = NbColors.text2)
            }
            VSpacer(24.dp)
            if (expired) {
                NbButton(stringResource(R.string.transfer_new), actions.onTransferCode, enabled = !loading, modifier = Modifier.fillMaxWidth())
            } else {
                NbButton(stringResource(R.string.action_copy), { actions.onCopy("code", groups.joinToString("")) }, kind = ButtonKind.TONAL, icon = R.drawable.ic_content_copy, modifier = Modifier.fillMaxWidth())
            }
            VSpacer(24.dp)
        }
    }
}

/** Code plates: 28 × 40 dp (narrower when the window needs it), 2 dp apart, 8 dp between groups of four. */
@Composable
private fun CodePlates(groups: List<String>, disabled: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val count = groups.sumOf { it.length }.coerceAtLeast(1)
        val inner = 2.dp
        val between = 8.dp
        val gaps = inner * (count - groups.size) + between * (groups.size - 1).coerceAtLeast(0)
        val w = minOf(28.dp, (maxWidth - gaps) / count)
        Row(horizontalArrangement = Arrangement.spacedBy(between)) {
            groups.forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(inner)) {
                    group.forEach { ch ->
                        Box(
                            Modifier.width(w).heightIn(min = 40.dp).background(if (disabled) NbColors.surface1 else NbColors.surface2, RoundedCornerShape(NbRadius.s)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ch.toString(), style = NbType.numCode, color = if (disabled) NbColors.textDisabled else NbColors.ink, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
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
        // POLICY_REVIEW Android item 13: the remove-ads purchase stays with the Google account and can be restored.
        body = stringResource(R.string.delete_body) + "\n\n" + stringResource(R.string.delete_body_purchase),
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
    Licence("Inter", "SIL Open Font License 1.1", "licenses/OFL-Inter.txt"),
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
