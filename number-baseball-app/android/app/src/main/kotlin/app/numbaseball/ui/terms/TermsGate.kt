package app.numbaseball.ui.terms

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.numbaseball.R
import app.numbaseball.ui.common.ErrorText
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.UiError
import app.numbaseball.ui.theme.NbColors

/**
 * Shown in place of quick match / room / chat until the current terms are accepted (policy review R6). There is no way
 * past it except Agree; Back leaves the online feature.
 */
@Composable
fun TermsGateScreen(
    updated: Boolean,
    submitting: Boolean,
    error: UiError?,
    onReadTerms: () -> Unit,
    onAgree: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.terms_title), onBack = onBack) {
        if (updated) Text(stringResource(R.string.terms_updated), color = NbColors.strike)
        Text(stringResource(R.string.terms_summary), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onReadTerms) { Text(stringResource(R.string.terms_read)) }
        ErrorText(error)
        PrimaryButton(stringResource(R.string.terms_agree), onAgree, enabled = !submitting)
    }
}
