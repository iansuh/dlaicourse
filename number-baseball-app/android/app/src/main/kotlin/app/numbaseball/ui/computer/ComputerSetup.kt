package app.numbaseball.ui.computer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.numbaseball.R
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.Limits
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.ListRow
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbModalSheet
import app.numbaseball.ui.design.NumberUnitLabel
import app.numbaseball.ui.design.RadioRow
import app.numbaseball.ui.design.Segmented
import app.numbaseball.ui.design.SheetTitle
import app.numbaseball.ui.design.TextSegmentLabel
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.isCompactHeight
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType

/** Relaxed timer (10 min): the default for vs-computer, never the quick-match timer. */
const val RELAXED_SECONDS = 600

data class ComputerChoice(val digits: Int, val level: ComputerLevel, val roundSeconds: Int)

/**
 * vs-Computer setup (03): bottom sheet over Home with digits, level (+ one caption line) and the time-limit row, which
 * opens a 4-option radio sheet. Selections persist (last used).
 */
@Composable
fun ComputerSetupSheet(
    choice: ComputerChoice,
    onChange: (ComputerChoice) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    var timerSheet by rememberSaveable { mutableStateOf(false) }
    val compact = isCompactHeight()
    NbModalSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SheetTitle(stringResource(R.string.cpu_title)) {
                IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), onDismiss, tint = NbColors.text2)
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.label_digits), style = NbType.labelSmall, color = NbColors.text3)
                VSpacer(8.dp)
                val unit = pluralStringResource(R.plurals.digit_unit, 2)
                Segmented(Limits.DIGITS.toList(), choice.digits, { onChange(choice.copy(digits = it)) }) { d, selected ->
                    NumberUnitLabel(d.toString(), unit, selected)
                }
                VSpacer(20.dp)
                Text(stringResource(R.string.label_level), style = NbType.labelSmall, color = NbColors.text3)
                VSpacer(8.dp)
                Segmented(ComputerLevel.entries, choice.level, { onChange(choice.copy(level = it)) }) { level, selected ->
                    TextSegmentLabel(levelLabel(level), selected)
                }
                if (!compact) {
                    VSpacer(8.dp)
                    Text(stringResource(levelDescription(choice.level)), style = NbType.caption, color = NbColors.text3)
                }
                VSpacer(20.dp)
            }
            ListRow(
                title = stringResource(R.string.label_timer),
                // The `timer` icon (`text2`) justifies the 56 dp text start (DESIGN_SYSTEM 4, D2-29).
                icon = R.drawable.ic_timer,
                titleStyle = NbType.body,
                value = timerLabel(choice.roundSeconds),
                chevron = true,
                onClick = { timerSheet = true },
                modifier = Modifier.padding(start = 0.dp),
            )
            VSpacer(24.dp)
            NbButton(
                stringResource(R.string.action_start),
                onStart,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("cpuStart"),
            )
            VSpacer(24.dp)
        }
    }
    if (timerSheet) {
        TimerChoiceSheet(
            selected = choice.roundSeconds,
            onSelect = {
                onChange(choice.copy(roundSeconds = it))
                timerSheet = false
            },
            onDismiss = { timerSheet = false },
        )
    }
}

/** Radio sheet: 30 s / 60 s / 120 s / Relaxed (10 min); no untimed option. Shared with the room settings (07). */
@Composable
fun TimerChoiceSheet(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    NbModalSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.label_timer))
        Limits.ROUND_SECONDS.forEach { seconds ->
            RadioRow(timerLabel(seconds), selected == seconds, { onSelect(seconds) })
        }
        VSpacer(24.dp)
    }
}

@Composable
fun timerLabel(seconds: Int): String =
    if (seconds >= RELAXED_SECONDS) stringResource(R.string.timer_relaxed) else pluralStringResource(R.plurals.timer_seconds, seconds, seconds)

@Composable
fun levelLabel(level: ComputerLevel): String = stringResource(
    when (level) {
        ComputerLevel.EASY -> R.string.level_easy
        ComputerLevel.NORMAL -> R.string.level_normal
        ComputerLevel.HARD -> R.string.level_hard
    },
)

private fun levelDescription(level: ComputerLevel) = when (level) {
    ComputerLevel.EASY -> R.string.level_easy_desc
    ComputerLevel.NORMAL -> R.string.level_normal_desc
    ComputerLevel.HARD -> R.string.level_hard_desc
}
