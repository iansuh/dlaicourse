package app.numbaseball.ui.rules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.numbaseball.R
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.match.HintLegend
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType

private val STEPS = listOf(R.string.how_1, R.string.how_2, R.string.how_3, R.string.how_4, R.string.how_5)

/**
 * How to play: a static page (the interactive tutorial of 01 §2 is not built yet): the goal, the hint legend of the
 * match screen (DESIGN_SYSTEM 12.7) and five numbered steps.
 */
@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    NbScreen(topBar = { TopBar(stringResource(R.string.how_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.how_goal),
                style = NbType.title,
                color = NbColors.chalk,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
            VSpacer(8.dp)
            HintLegend(compact = false)
            VSpacer(16.dp)
            STEPS.forEachIndexed { i, step ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text((i + 1).toString(), style = NbType.numLabel, color = NbColors.text3, modifier = Modifier.width(28.dp).padding(top = 3.dp))
                    Text(stringResource(step), style = NbType.reading, color = NbColors.chalk)
                }
            }
            VSpacer(24.dp)
        }
    }
}
