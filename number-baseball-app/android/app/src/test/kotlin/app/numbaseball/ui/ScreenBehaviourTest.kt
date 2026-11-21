package app.numbaseball.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.ads.AD_BANNER_TAG
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.testing.SampleData
import app.numbaseball.ui.age.AgeScreen
import app.numbaseball.ui.age.AgeUiState
import app.numbaseball.ui.chat.ChatScreen
import app.numbaseball.ui.chat.ChatUiState
import app.numbaseball.ui.firstrun.FirstRunScreen
import app.numbaseball.ui.firstrun.FirstRunUiState
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchPhase
import app.numbaseball.ui.match.MatchScreen
import app.numbaseball.ui.match.MatchUiState
import app.numbaseball.ui.match.Opponent
import app.numbaseball.ui.theme.NumBaseballTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI behaviour on Robolectric (English resources). */
@RunWith(AndroidJUnit4::class)
class ScreenBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstRunStartIsEnabledOnlyWithANickname() {
        var nickname = ""
        compose.setContent {
            NumBaseballTheme {
                FirstRunScreen(FirstRunUiState(nickname = nickname), { nickname = it }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Start").assertIsNotEnabled()
        compose.onNodeWithTag("nickname").performTextInput("Mina")
        assertEquals("Mina", nickname)
    }

    @Test fun keypadDisablesUsedDigitsAndNoAdIsShownDuringAMatch() {
        val typed = mutableListOf<Char>()
        compose.setContent {
            NumBaseballTheme {
                MatchScreen(
                    state = MatchUiState(3, Opponent.Computer(ComputerLevel.NORMAL), MatchMode.COMPUTER, MatchPhase.CHOOSING_SECRET, input = "12"),
                    onDigit = { typed += it },
                    onDelete = {},
                    onSubmit = {},
                    onRandomSecret = {},
                    onForfeit = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("1").assertIsNotEnabled()
        compose.onNodeWithText("3").assertIsEnabled().performClick()
        assertEquals(listOf('3'), typed)
        compose.onNodeWithTag(AD_BANNER_TAG).assertDoesNotExist()
    }

    @Test fun quickMatchChatOffersOnlyQuickPhrases() {
        val phrases = mutableListOf<String>()
        compose.setContent {
            NumBaseballTheme {
                ChatScreen(ChatUiState(messages = SampleData.chat(), phrasesOnly = true), {}, {}, { phrases += it }, {}, {})
            }
        }
        compose.onNodeWithTag("chatInput").assertDoesNotExist()
        compose.onNodeWithText("Good luck!").performClick()
        assertEquals(listOf("good_luck"), phrases)
        compose.onNodeWithText("Deleted player").assertExists()
    }

    @Test fun ageScreenHasNoDefaultYear() {
        compose.setContent {
            NumBaseballTheme { AgeScreen(AgeUiState(years = (2026 downTo 1926).toList()), {}, {}) }
        }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithTag("birthYear").performClick()
        compose.onNodeWithText("1990").assertExists()
    }
}
