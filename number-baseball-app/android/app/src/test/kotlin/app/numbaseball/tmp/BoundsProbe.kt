package app.numbaseball.tmp

import androidx.compose.ui.test.junit4.v2.createComposeRule

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.numbaseball.core.protocol.Stats
import app.numbaseball.ui.home.HomeScreen
import app.numbaseball.ui.home.HomeUiState
import app.numbaseball.ui.theme.NumBaseballTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w360dp-h800dp-xhdpi")
class BoundsProbe {
    @get:Rule val compose = createComposeRule()

    private fun dump(n: SemanticsNode, depth: Int = 0) {
        val b = n.boundsInRoot
        val t = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() ?: ""
        println("PROBE " + "  ".repeat(depth) + "y=${b.top / 2} h=${b.height / 2} x=${b.left / 2} w=${b.width / 2} $t")
        n.children.forEach { dump(it, depth + 1) }
    }

    @Test fun texts() {
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text("Abc", style = app.numbaseball.ui.theme.NbType.caption)
                androidx.compose.material3.Text("가나", style = app.numbaseball.ui.theme.NbType.caption)
                androidx.compose.material3.Text("가나", style = app.numbaseball.ui.theme.NbType.subtitle)
                androidx.compose.material3.Text("Abc", style = app.numbaseball.ui.theme.NbType.subtitle)
                androidx.compose.material3.Text("12", style = app.numbaseball.ui.theme.NbType.numStat)
                androidx.compose.foundation.text.BasicText("가나", style = app.numbaseball.ui.theme.NbType.caption)
                for ((t, st) in listOf("0000" to app.numbaseball.ui.theme.NbType.numLog, "1111" to app.numbaseball.ui.theme.NbType.numLog, "1111" to app.numbaseball.ui.theme.NbType.numLog.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), "0000" to app.numbaseball.ui.theme.NbType.numLog.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), "1111" to app.numbaseball.ui.theme.NbType.numLog.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified, fontFeatureSettings = null), "Abc" to app.numbaseball.ui.theme.NbType.caption, "가나" to app.numbaseball.ui.theme.NbType.caption, "가나" to app.numbaseball.ui.theme.NbType.subtitle, "12" to app.numbaseball.ui.theme.NbType.numStat, "1234" to app.numbaseball.ui.theme.NbType.numLog, "가나\n다라" to app.numbaseball.ui.theme.NbType.body)) {
                    androidx.compose.material3.Text(t, style = st, onTextLayout = { r -> println("PROBE layout '$t' w=${r.size.width / 2f} size=${r.size.height / 2f} base=${r.firstBaseline / 2f} last=${r.lastBaseline / 2f} top0=${r.getLineTop(0) / 2f} bot0=${r.getLineBottom(0) / 2f} lines=${r.lineCount} botLast=${r.getLineBottom(r.lineCount - 1) / 2f}") })
                }
            }
        }
        dump(compose.onNode(isRoot()).fetchSemanticsNode(), 0)
    }

    @Test fun home() {
        compose.setContent {
            NumBaseballTheme {
                HomeScreen(HomeUiState(nickname = "새벽투수", tag = "4821", registered = true, stats = Stats(12, 8, 3), recentForm = "WWLWDLWWLW"), {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        dump(compose.onNode(isRoot()).fetchSemanticsNode(), 0)
    }
}
