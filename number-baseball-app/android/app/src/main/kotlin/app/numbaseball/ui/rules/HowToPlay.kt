package app.numbaseball.ui.rules

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.numbaseball.R
import app.numbaseball.ui.common.ScreenScaffold

private val RULES = listOf(R.string.rules_1, R.string.rules_2, R.string.rules_3, R.string.rules_4, R.string.rules_5, R.string.rules_6)

@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.rules_title), onBack = onBack) {
        RULES.forEach { Text(stringResource(it)) }
    }
}
