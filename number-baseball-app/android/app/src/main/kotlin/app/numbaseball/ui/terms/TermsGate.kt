package app.numbaseball.ui.terms

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.numbaseball.R
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SecondaryButton
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbLayout

/**
 * Shown in place of quick match / room / chat until the current terms are accepted with the minimum-age confirmation
 * (docs/protocol.md §3.1, policy review R6). There is no way past it except Agree; Back leaves the online feature.
 */
@Composable
fun TermsGateScreen(
    updated: Boolean,
    minimumAge: Int,
    ageConfirmed: Boolean,
    submitting: Boolean,
    error: UiError?,
    onAgeConfirmedChange: (Boolean) -> Unit,
    onReadTerms: () -> Unit,
    onAgree: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.terms_title), onBack = onBack) {
        if (updated) Text(stringResource(R.string.terms_updated), color = NbColors.strike)
        Text(stringResource(R.string.terms_summary), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onReadTerms) { Text(stringResource(R.string.terms_read)) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NbLayout.touchMin)
                .toggleable(value = ageConfirmed, role = Role.Checkbox, onValueChange = onAgeConfirmedChange)
                .testTag("ageConfirmed"),
        ) {
            Checkbox(checked = ageConfirmed, onCheckedChange = null)
            Text(pluralStringResource(R.plurals.terms_age_confirm, minimumAge, minimumAge))
        }
        ErrorText(error)
        PrimaryButton(stringResource(R.string.terms_agree), onAgree, enabled = ageConfirmed && !submitting)
    }
}

/** Server config not loaded yet (or offline). */
@Composable
fun TermsLoadingScreen(onRetry: () -> Unit, onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.terms_title), onBack = onBack) {
        CircularProgressIndicator()
        Text(stringResource(R.string.error_network), color = NbColors.text3, style = MaterialTheme.typography.bodySmall)
        SecondaryButton(stringResource(R.string.action_retry), onRetry)
    }
}
