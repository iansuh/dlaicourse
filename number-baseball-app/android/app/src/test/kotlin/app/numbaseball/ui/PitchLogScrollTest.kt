package app.numbaseball.ui

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.ui.match.PitchLog
import app.numbaseball.ui.match.RevealProgress
import app.numbaseball.ui.theme.NumBaseballTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Owner feedback 2026-11-26: after many rounds the newest result must come into view without scrolling by hand. */
@RunWith(AndroidJUnit4::class)
class PitchLogScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun newestRoundIsVisibleAfterManyRounds() {
        fun entry(r: Int) = RoundEntry(r, "0123", 0, 1)
        var rounds by mutableStateOf((1..12).map(::entry))
        compose.setContent {
            NumBaseballTheme {
                PitchLog(4, rounds, emptyList(), RevealProgress.Done, compact = false, modifier = Modifier.height(180.dp))
            }
        }
        compose.waitForIdle()
        rounds = rounds + entry(13)
        compose.waitForIdle()
        compose.onNodeWithText("13", substring = false, useUnmergedTree = true).assertIsDisplayed()
    }
}
