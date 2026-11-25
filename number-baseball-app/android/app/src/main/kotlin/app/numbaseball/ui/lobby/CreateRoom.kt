package app.numbaseball.ui.lobby

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numbaseball.R
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.ui.computer.RELAXED_SECONDS
import app.numbaseball.ui.design.IconButton48
import app.numbaseball.ui.design.LocalReduceMotion
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbModalSheet
import app.numbaseball.ui.design.NumberUnitLabel
import app.numbaseball.ui.design.Segmented
import app.numbaseball.ui.design.SheetTitle
import app.numbaseball.ui.design.TextSegmentLabel
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbMotion
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbType

/**
 * Create room / room settings sheet (07 §1): digits, players (1:1 … 5:5) and the time limit as segmented controls;
 * "Relaxed" shows its caption line when selected. [minTeamSize] disables sizes that would remove seated players.
 */
@Composable
fun RoomSettingsSheet(
    title: String,
    actionLabel: String,
    settings: RoomSettings,
    onChange: (RoomSettings) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    minTeamSize: Int = 1,
) {
    NbModalSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SheetTitle(title) {
                IconButton48(R.drawable.ic_close, stringResource(R.string.action_close), onDismiss, tint = NbColors.text2)
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.label_digits), style = NbType.labelSmall, color = NbColors.text3)
                VSpacer(8.dp)
                val unit = pluralStringResource(R.plurals.digit_unit, 2)
                Segmented(Limits.DIGITS.toList(), settings.digits, { onChange(settings.copy(digits = it)) }) { d, selected ->
                    NumberUnitLabel(d.toString(), unit, selected)
                }
                VSpacer(20.dp)
                Text(stringResource(R.string.label_team_size), style = NbType.labelSmall, color = NbColors.text3)
                VSpacer(8.dp)
                Segmented(Limits.TEAM_SIZE.toList(), settings.teamSize, { if (it >= minTeamSize) onChange(settings.copy(teamSize = it)) }) { n, selected ->
                    Text(
                        "$n:$n",
                        style = NbType.numLabel.copy(fontSize = 16.sp),
                        color = when {
                            selected -> NbColors.onChalk
                            n < minTeamSize -> NbColors.textDisabled
                            else -> NbColors.text2
                        },
                    )
                }
                VSpacer(20.dp)
                Text(stringResource(R.string.label_timer), style = NbType.labelSmall, color = NbColors.text3)
                VSpacer(8.dp)
                val secondsUnit = stringResource(R.string.seconds_unit)
                Segmented(Limits.ROUND_SECONDS, settings.roundSeconds, { onChange(settings.copy(roundSeconds = it)) }) { s, selected ->
                    if (s >= RELAXED_SECONDS) {
                        TextSegmentLabel(stringResource(R.string.timer_relaxed_short), selected)
                    } else {
                        NumberUnitLabel(s.toString(), secondsUnit, selected)
                    }
                }
                if (settings.roundSeconds >= RELAXED_SECONDS) {
                    VSpacer(8.dp)
                    Text(stringResource(R.string.timer_relaxed_note), style = NbType.caption, color = NbColors.text3)
                }
                VSpacer(24.dp)
                NbButton(actionLabel, onConfirm, modifier = Modifier.fillMaxWidth().testTag("roomConfirm"))
                VSpacer(24.dp)
            }
        }
    }
}

/** Plate states of the code plates (07 §2). */
enum class CodePlateState { EMPTY, ACTIVE, FILLED, ERROR }

/**
 * Five code plates 48 × 60 dp, gap 8, `numCode`: empty `bg` + 1 dp `outline`, active 2 dp chalk, filled `surface2`, error
 * 2 dp `out`. [shakeKey] changes trigger the 280 ms shake. Read as single characters.
 */
@Composable
fun CodePlates(code: String, modifier: Modifier = Modifier, active: Boolean = false, error: Boolean = false, shakeKey: Int = 0, description: String? = null) {
    val density = LocalDensity.current
    val shake = remember { Animatable(0f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0 || reduce) return@LaunchedEffect
        val amp = with(density) { 6.dp.toPx() }
        repeat(3) {
            shake.animateTo(amp, tween(NbMotion.shake / 12))
            shake.animateTo(-amp, tween(NbMotion.shake / 6))
            shake.animateTo(0f, tween(NbMotion.shake / 12))
        }
    }
    val spoken = (description?.let { "$it " } ?: "") + code.toList().joinToString(" ")
    Row(
        modifier.graphicsLayer { translationX = shake.value }.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(Limits.ROOM_CODE_LENGTH) { i ->
            val ch = code.getOrNull(i)
            val state = when {
                error -> CodePlateState.ERROR
                ch != null -> CodePlateState.FILLED
                active && i == code.length -> CodePlateState.ACTIVE
                else -> CodePlateState.EMPTY
            }
            val shape = RoundedCornerShape(NbRadius.m)
            Box(
                Modifier
                    .size(48.dp, 60.dp)
                    .background(if (ch != null) NbColors.surface2 else NbColors.bg, shape)
                    .then(
                        when (state) {
                            CodePlateState.EMPTY -> Modifier.border(1.dp, NbColors.outline, shape)
                            CodePlateState.ACTIVE -> Modifier.border(2.dp, NbColors.chalk, shape)
                            CodePlateState.ERROR -> Modifier.border(2.dp, NbColors.out, shape)
                            CodePlateState.FILLED -> Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (ch != null) Text(ch.toString(), style = NbType.numCode, color = NbColors.chalk)
            }
        }
    }
}
