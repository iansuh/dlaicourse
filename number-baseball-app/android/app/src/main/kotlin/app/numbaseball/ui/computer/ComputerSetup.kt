package app.numbaseball.ui.computer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.numbaseball.R
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.Limits
import app.numbaseball.ui.common.ChoiceRow
import app.numbaseball.ui.common.PrimaryButton
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.common.SectionLabel

/** Two choices only, so the state lives in the composable (saved across configuration changes and process death). */
@Composable
fun ComputerSetupRoute(onStart: (digits: Int, level: ComputerLevel) -> Unit, onBack: () -> Unit) {
    var digits by rememberSaveable { mutableIntStateOf(3) }
    var level by rememberSaveable { mutableStateOf(ComputerLevel.NORMAL) }
    ComputerSetupScreen(digits, level, { digits = it }, { level = it }, { onStart(digits, level) }, onBack)
}

@Composable
fun ComputerSetupScreen(
    digits: Int,
    level: ComputerLevel,
    onDigitsChange: (Int) -> Unit,
    onLevelChange: (ComputerLevel) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.setup_title), onBack = onBack) {
        SectionLabel(stringResource(R.string.setup_digits))
        ChoiceRow(Limits.DIGITS.toList(), digits, { it.toString() }, onDigitsChange)
        SectionLabel(stringResource(R.string.setup_level))
        ChoiceRow(ComputerLevel.entries, level, { levelLabel(it) }, onLevelChange)
        PrimaryButton(stringResource(R.string.setup_start), onStart)
    }
}

@Composable
fun levelLabel(level: ComputerLevel): String = stringResource(
    when (level) {
        ComputerLevel.EASY -> R.string.level_easy
        ComputerLevel.NORMAL -> R.string.level_normal
        ComputerLevel.HARD -> R.string.level_hard
    },
)
