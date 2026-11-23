package app.numbaseball

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
        compose.onNodeWithText("Enter the year you were born").assertExists()
        "1990".forEach { compose.onNodeWithTag("key_$it").performClick() }
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("nickname")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(AD_BANNER_TAG).assertDoesNotExist()
    }
}
