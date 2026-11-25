package app.numbaseball.ui.age

import app.numbaseball.ui.design.underlined
import app.numbaseball.ui.theme.NbRadius
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.audio.HapticCue
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.LocalGameHaptics
import app.numbaseball.audio.SoundCue
import app.numbaseball.data.age.AgeRepository
import app.numbaseball.ui.design.ActionState
import app.numbaseball.ui.design.exactLineHeight
import app.numbaseball.ui.design.BrandMark
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.DigitKeypad
import app.numbaseball.ui.design.DigitSlots
import app.numbaseball.ui.design.HSpacer
import app.numbaseball.ui.design.KeypadMode
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.isCompactHeight
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The answer being typed: four year digits, then two month digits (DEF-051). */
data class AgeUiState(val year: String = "", val month: String = "", val invalid: Boolean = false, val submitting: Boolean = false, val shake: Int = 0) {
    val complete: Boolean get() = year.length == 4 && month.length == 2
}

class AgeViewModel(private val ages: AgeRepository) : ViewModel() {
    private val _state = MutableStateFlow(AgeUiState())
    val state: StateFlow<AgeUiState> = _state.asStateFlow()

    /** Repeated digits are allowed here; the year fills first, then the month; after an invalid answer the slots clear on the next key (01 §0). */
    fun onDigit(digit: Char) = _state.update {
        if (it.submitting) return@update it
        val year = if (it.invalid) "" else it.year
        val month = if (it.invalid) "" else it.month
        when {
            year.length < 4 -> it.copy(year = year + digit, month = "", invalid = false)
            month.length < 2 -> it.copy(year = year, month = month + digit, invalid = false)
            else -> it
        }
    }

    fun onDelete() = _state.update {
        when {
            it.submitting -> it
            it.invalid -> it.copy(year = "", month = "", invalid = false)
            it.month.isNotEmpty() -> it.copy(month = it.month.dropLast(1))
            else -> it.copy(year = it.year.dropLast(1))
        }
    }

    fun onClear() = _state.update { if (it.submitting) it else it.copy(year = "", month = "", invalid = false) }

    /** One answer only: once submitted, the keypad and Confirm are locked until NbApp leaves the screen (DEF-052). */
    fun onConfirm() {
        val current = _state.value
        if (current.submitting) return
        val year = current.year.toIntOrNull() ?: return
        val month = current.month.toIntOrNull()
        if (!current.complete || month == null || !ages.isValid(year, month)) {
            _state.update { it.copy(invalid = true, shake = it.shake + 1) }
            return
        }
        _state.update { it.copy(submitting = true) }
        // The stored band drives NbApp (continue or stop); nothing else to do here.
        viewModelScope.launch { ages.submitBirth(year, month) }
    }
}

/** Eyebrow: brand mark 22 × 32 dp + 10 dp + a text stack (01). */
@Composable
fun Eyebrow(content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        HSpacer(10.dp)
        content()
    }
}

/**
 * Neutral first-launch age screen (01 §0, DEF-051): birth year and month only, no default, nothing that hints at the required age. Shown
 * before any ad SDK call and before account creation; the digit keypad is pinned at the bottom.
 */
@Composable
fun AgeScreen(state: AgeUiState, onDigit: (Char) -> Unit, onDelete: () -> Unit, onClear: () -> Unit, onConfirm: () -> Unit) {
    val audio = LocalGameAudio.current
    val haptics = LocalGameHaptics.current
    val compact = isCompactHeight()
    val margin = if (compact) 12.dp else 16.dp
    NbScreen {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = margin)) {
            // Small window: title and the year only, so the slots stay above the pinned keypad (ADAPTIVE.md §1).
            VSpacer(if (compact) 12.dp else 32.dp)
            if (!compact) {
                Eyebrow {
                    Column {
                        Text(stringResource(R.string.app_name_main), style = NbType.labelSmall, color = NbColors.text2)
                        Text(stringResource(R.string.app_name_sub), style = NbType.caption, color = NbColors.text3)
                    }
                }
                VSpacer(24.dp)
            }
            Text(stringResource(R.string.age_title), style = if (compact) NbType.title else NbType.headline, color = NbColors.chalk)
            if (!compact) {
                VSpacer(8.dp)
                Text(stringResource(R.string.age_lead), style = NbType.body, color = NbColors.text2)
            }
            VSpacer(if (compact) 12.dp else 40.dp)
            BirthFields(state, compact)
            VSpacer(12.dp)
            Box(Modifier.fillMaxWidth().heightIn(min = 18.dp).semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
                if (state.invalid) Text(stringResource(R.string.age_invalid), style = NbType.caption, color = NbColors.out)
            }
        }
        DigitKeypad(
            mode = KeypadMode.AGE,
            used = emptySet(),
            locked = state.submitting,
            actionLabel = stringResource(R.string.age_confirm),
            actionState = if (state.complete && !state.invalid && !state.submitting) ActionState.ENABLED else ActionState.DISABLED,
            onDigit = {
                audio.play(SoundCue.KEY_DOWN)
                haptics.perform(HapticCue.KEY_TAP)
                onDigit(it)
            },
            onBackspace = {
                audio.play(SoundCue.BACKSPACE)
                haptics.perform(HapticCue.KEY_TAP)
                onDelete()
            },
            onAction = {
                haptics.perform(HapticCue.CONFIRM)
                onConfirm()
            },
            onClear = onClear,
            compact = compact,
            modifier = Modifier.padding(horizontal = margin),
        )
        VSpacer(12.dp)
    }
}

/**
 * Year (four plates) and month (two plates) side by side, each with its label above (DEF-051). The keypad fills the
 * year, then the month; the plates shrink to fit narrow windows.
 */
@Composable
private fun BirthFields(state: AgeUiState, compact: Boolean) {
    val yearLabel = stringResource(R.string.age_year)
    val monthLabel = stringResource(R.string.age_month)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val gap = app.numbaseball.ui.theme.NbSize.slotGap
        val between = 16.dp
        val fit = (maxWidth - between - gap * 4) / 6
        val w = minOf(if (compact) 40.dp else 44.dp, fit)
        val h = if (compact) 48.dp else 56.dp
        Row(horizontalArrangement = Arrangement.spacedBy(between), verticalAlignment = Alignment.Bottom) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(yearLabel, style = NbType.labelSmall, color = NbColors.text3, textAlign = TextAlign.Center, maxLines = 1)
                VSpacer(12.dp)
                DigitSlots(
                    digits = 4,
                    value = state.year,
                    modifier = Modifier.width(w * 4 + gap * 3),
                    plateWidth = w,
                    plateHeight = h,
                    error = state.invalid,
                    showActive = state.year.length < 4,
                    shakeKey = state.shake,
                    contentDescription = yearLabel + ", " + state.year.toList().joinToString(" "),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(monthLabel, style = NbType.labelSmall, color = NbColors.text3, textAlign = TextAlign.Center, maxLines = 1)
                VSpacer(12.dp)
                DigitSlots(
                    digits = 2,
                    value = state.month,
                    modifier = Modifier.width(w * 2 + gap),
                    plateWidth = w,
                    plateHeight = h,
                    error = state.invalid,
                    showActive = state.year.length == 4,
                    shakeKey = state.shake,
                    contentDescription = monthLabel + ", " + state.month.toList().joinToString(" "),
                )
            }
        }
    }
}

/**
 * Below the minimum age (01 §0.1): calm, final, no blame, no number. Vertically centred column, 32 dp side margins.
 * No buttons, no ads, no sound; the answer is stored, so re-opening the app shows this again.
 */
@Composable
fun NotAvailableScreen(onPrivacyPolicy: () -> Unit) {
    NbScreen {
        // Centred, and scrolls when large text makes it taller than a small window.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(width = 33.dp, height = 48.dp, alpha = 0.55f)
            VSpacer(24.dp)
            Text(stringResource(R.string.na_title), style = NbType.title, color = NbColors.chalk, textAlign = TextAlign.Center)
            VSpacer(16.dp)
            Text(stringResource(R.string.na_body), style = NbType.body, color = NbColors.text2, textAlign = TextAlign.Center)
            VSpacer(40.dp)
            // Text link (prototype `.na .link`): `labelSmall` `text2`, underlined in `outline`, 48 dp target.
            val label = stringResource(R.string.privacy_policy)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(NbRadius.s))
                    .clickable(role = Role.Button, onClick = onPrivacyPolicy),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = NbType.labelSmall,
                    color = NbColors.text2,
                    modifier = Modifier.underlined(),
                )
            }
        }
        }
    }
}
