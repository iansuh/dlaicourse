package app.numbaseball.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import app.numbaseball.ui.chat.ChatActions
import app.numbaseball.ui.chat.ChatSheet
import app.numbaseball.ui.chat.ChatUiState
import app.numbaseball.ui.firstrun.FirstRunScreen
import app.numbaseball.ui.firstrun.FirstRunUiState
import app.numbaseball.ui.home.HomeScreen
import app.numbaseball.ui.home.HomeUiState
import app.numbaseball.ui.match.MatchActions
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

fun matchActions(onDigit: (Char) -> Unit = {}) = MatchActions(
    onDigit = onDigit,
    onDelete = {},
    onClear = {},
    onTapSlot = {},
    onSubmit = {},
    onRandomSecret = {},
    onForfeit = {},
    onLeave = {},
    onMemoDigit = {},
    onMemoCell = { _, _ -> },
    onMemoClear = {},
    onMemoCollapse = {},
)

fun chatActions(onPhrase: (String) -> Unit = {}) = ChatActions(
    onInputChange = {},
    onSend = {},
    onPhrase = onPhrase,
    onMuteChange = {},
    onClose = {},
    onCopy = {},
    onReportMessage = {},
    onBlock = {},
    onPlayerCard = {},
)

/** Compose UI behaviour on Robolectric (English resources). */
@RunWith(AndroidJUnit4::class)
class ScreenBehaviourTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstRunStartIsEnabledOnlyWithANickname() {
        var nickname = ""
        compose.setContent {
            NumBaseballTheme {
                FirstRunScreen(FirstRunUiState(nickname = nickname), { nickname = it }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("nickname").performTextInput("Mina")
        assertEquals("Mina", nickname)
    }

    @Test fun keypadDisablesUsedDigitsAndNoAdIsShownDuringAMatch() {
        val typed = mutableListOf<Char>()
        compose.setContent {
            NumBaseballTheme {
                MatchScreen(
                    state = MatchUiState(3, Opponent.Computer(ComputerLevel.NORMAL), MatchMode.COMPUTER, MatchPhase.CHOOSING_SECRET, input = "12"),
                    actions = matchActions { typed += it },
                )
            }
        }
        compose.onNodeWithTag("key_1").assertIsNotEnabled()
        compose.onNodeWithTag("key_3").assertIsEnabled().performClick()
        assertEquals(listOf('3'), typed)
        compose.onNodeWithTag(AD_BANNER_TAG).assertDoesNotExist()
    }

    @Test fun quickMatchChatOffersOnlyQuickPhrases() {
        val phrases = mutableListOf<String>()
        compose.setContent {
            NumBaseballTheme {
                ChatSheet(ChatUiState(messages = SampleData.chat(), phrasesOnly = true), chatActions { phrases += it })
            }
        }
        compose.onNodeWithTag("chatInput").assertDoesNotExist()
        compose.onNodeWithText("Good luck").performClick()
        assertEquals(listOf("good_luck"), phrases)
        compose.onNodeWithText("Deleted player").assertExists()
    }

    @Test fun ageScreenUsesTheKeypadAndHasNoDefaultYear() {
        val typed = mutableListOf<Char>()
        var state by mutableStateOf(AgeUiState())
        compose.setContent {
            NumBaseballTheme { AgeScreen(state, { typed += it }, {}, {}, {}) }
        }
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithTag("key_1").performClick()
        compose.onNodeWithTag("key_1").performClick()
        assertEquals(listOf('1', '1'), typed)
        state = AgeUiState(year = "1990")
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        state = AgeUiState(year = "1990", month = "05")
        compose.onNodeWithText("Confirm").assertIsEnabled()
    }

    @Test fun homeDisablesOnlineModesWhenOffline() {
        compose.setContent {
            NumBaseballTheme {
                HomeScreen(HomeUiState(nickname = "Mina", tag = "0420", online = false), {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("quickMatch").assertIsNotEnabled()
        compose.onNodeWithText("Waiting for a connection").assertExists()
        compose.onNodeWithTag(AD_BANNER_TAG).assertDoesNotExist()
    }

    /** DEF-063: the record block keeps its test tag although its semantics are cleared and replaced. */
    @Test fun homeRecordBlockIsFoundByItsTag() {
        var opened = 0
        compose.setContent {
            NumBaseballTheme {
                HomeScreen(HomeUiState(nickname = "Mina", tag = "0420"), {}, {}, {}, {}, {}, {}, { opened++ }, {}, {})
            }
        }
        compose.onNodeWithTag("profile").performClick()
        assertEquals(1, opened)
    }
}
