package app.numbaseball.ui.age

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.data.age.AgeRepository
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbLayout
import app.numbaseball.ui.theme.NbSpace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgeUiState(val years: List<Int>, val selectedYear: Int? = null, val submitting: Boolean = false)

class AgeViewModel(private val ages: AgeRepository) : ViewModel() {
    private val _state = MutableStateFlow(AgeUiState(years = ages.selectableYears()))
    val state: StateFlow<AgeUiState> = _state.asStateFlow()

    fun onYearSelected(year: Int) = _state.update { it.copy(selectedYear = year) }

    fun onContinue() {
        val year = _state.value.selectedYear ?: return
        _state.update { it.copy(submitting = true) }
        // The stored band drives NbApp (continue or stop); nothing else to do here.
        viewModelScope.launch { ages.submitBirthYear(year) }
    }
}

/**
 * Neutral first-launch age question (policy review R19): birth year only, no default, nothing that hints at the
 * required age. Shown before any ad SDK call and before account creation.
 */
@Composable
fun AgeScreen(state: AgeUiState, onYearSelected: (Int) -> Unit, onContinue: () -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.age_title), onBack = null) {
        Text(stringResource(R.string.age_question), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = NbLayout.touchMin).testTag("birthYear"),
        ) {
            Text(state.selectedYear?.toString() ?: stringResource(R.string.age_select_year))
        }
        Text(stringResource(R.string.age_privacy_note), color = NbColors.text3, style = MaterialTheme.typography.bodySmall)
        PrimaryButton(stringResource(R.string.age_continue), onContinue, enabled = state.selectedYear != null && !state.submitting)
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.age_question)) },
            text = {
                LazyColumn {
                    items(state.years) { year ->
                        Text(
                            text = year.toString(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = NbLayout.touchMin)
                                .clickable {
                                    onYearSelected(year)
                                    picking = false
                                }
                                .padding(vertical = NbSpace.s12),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Below the minimum age: a polite stop. The answer is stored, so re-opening the app shows this again. */
@Composable
fun NotAvailableScreen(minimumAge: Int) {
    ScreenScaffold(title = stringResource(R.string.app_name), onBack = null) {
        Text(stringResource(R.string.not_available_title), style = MaterialTheme.typography.headlineMedium)
        Text(pluralStringResource(R.plurals.not_available_body, minimumAge, minimumAge), color = NbColors.text2)
    }
}
