package app.numbaseball

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.ads.AD_BANNER_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real activity and navigation graph on the test application (fakes, no ads, no audio). */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun firstLaunchAsksTheAgeThenTheNickname() {
        compose.onNodeWithText("What year were you born?").assertExists()
        compose.onNodeWithTag("birthYear").performClick()
        compose.onNodeWithText("1990").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Pick a nickname and play. No ID, password or email.") }
        compose.onNodeWithTag(AD_BANNER_TAG).assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextExists(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
