package app.numbaseball.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.RoundEntry
import app.numbaseball.core.protocol.Stats
import app.numbaseball.core.protocol.Team
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.testing.SampleData
import app.numbaseball.ui.age.AgeScreen
import app.numbaseball.ui.age.AgeUiState
import app.numbaseball.ui.age.NotAvailableScreen
import app.numbaseball.ui.chat.ChatScreen
import app.numbaseball.ui.chat.ChatUiState
import app.numbaseball.ui.computer.ComputerSetupScreen
import app.numbaseball.ui.firstrun.FirstRunScreen
import app.numbaseball.ui.firstrun.FirstRunUiState
import app.numbaseball.ui.home.HomeScreen
import app.numbaseball.ui.home.HomeUiState
import app.numbaseball.ui.legal.LegalBlock
import app.numbaseball.ui.legal.LegalTextScreen
import app.numbaseball.ui.lobby.LobbyPlayer
import app.numbaseball.ui.lobby.LobbyUiState
import app.numbaseball.ui.lobby.RoomLobbyScreen
import app.numbaseball.ui.match.LocalInput
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchPhase
import app.numbaseball.ui.match.MatchScreen
import app.numbaseball.ui.match.MatchSummary
import app.numbaseball.ui.match.MatchUiState
import app.numbaseball.ui.match.Opponent
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.match.toMatchUiState
import app.numbaseball.ui.profile.ProfileScreen
import app.numbaseball.ui.profile.ProfileUiState
import app.numbaseball.ui.quickmatch.QuickMatchScreen
import app.numbaseball.ui.quickmatch.QuickMatchUiState
import app.numbaseball.ui.report.ReportScreen
import app.numbaseball.ui.report.ReportTarget
import app.numbaseball.ui.report.ReportUiState
import app.numbaseball.ui.results.ResultsScreen
import app.numbaseball.ui.rules.HowToPlayScreen
import app.numbaseball.ui.settings.SettingsScreen
import app.numbaseball.ui.settings.SettingsUiState
import app.numbaseball.ui.team.TeamBoardScreen
import app.numbaseball.ui.terms.TermsGateScreen
import app.numbaseball.ui.theme.NumBaseballTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen with sample state. Recorded locally (`./gradlew :app:recordRoborazziDebug`) into app/screenshots and
 * verified on CI (`verifyRoborazziDebug`). Ads, audio and haptics are the disabled defaults, so images are deterministic.
 */
abstract class ScreenshotSuite(private val variant: String) {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { NumBaseballTheme { content() } }
        compose.onRoot().captureRoboImage("screenshots/${name}_$variant.png", roborazziOptions = OPTIONS)
    }

    @Test fun age() = capture("01_age") { AgeScreen(AgeUiState(years = (2026 downTo 1926).toList()), {}, {}) }

    @Test fun notAvailable() = capture("02_not_available") { NotAvailableScreen(minimumAge = 14) }

    @Test fun firstRun() = capture("03_first_run") { FirstRunScreen(FirstRunUiState(nickname = "Mina"), {}, {}, {}, {}) }

    @Test fun home() = capture("04_home") {
        HomeScreen(
            state = HomeUiState(displayName = "Mina#0420", registered = true, stats = Stats(12, 7, 3), roomCode = "K7Q"),
            onRoomCodeChange = {}, onVsComputer = {}, onQuickMatch = {}, onCreateRoom = {}, onJoinRoom = {},
            onHowToPlay = {}, onProfile = {}, onSettings = {}, onChooseNickname = {},
        )
    }

    @Test fun computerSetup() = capture("05_computer_setup") { ComputerSetupScreen(4, ComputerLevel.NORMAL, {}, {}, {}, {}) }

    @Test fun matchSecret() = capture("06_match_secret") {
        Match(MatchUiState(3, Opponent.Computer(ComputerLevel.NORMAL), MatchMode.COMPUTER, MatchPhase.CHOOSING_SECRET, input = "04"))
    }

    @Test fun matchGuessing() = capture("07_match_guessing") {
        Match(
            MatchUiState(
                digits = 3,
                opponent = Opponent.Computer(ComputerLevel.HARD),
                mode = MatchMode.COMPUTER,
                phase = MatchPhase.GUESSING,
                input = "7",
                mySecret = "042",
                round = 4,
                myRounds = listOf(RoundEntry(1, "123", 0, 1), RoundEntry(2, "456", 0, 0), RoundEntry(3, "719", 2, 0)),
                opponentRounds = listOf(RoundEntry(1, "089", 0, 1), RoundEntry(2, "401", 1, 1), RoundEntry(3, "024", 1, 2)),
            ),
        )
    }

    @Test fun matchOnline() = capture("08_match_online") {
        Match(SampleData.quickRoom().toMatchUiState(LocalInput(text = "35", round = 3), serverNow = 1_795_000_030_000)!!)
    }

    @Test fun quickMatch() = capture("09_quick_match") {
        QuickMatchScreen(QuickMatchUiState(digits = 4, searching = true, connection = ConnectionState.Connecting(1)), {}, {}, {}, {}, {})
    }

    @Test fun lobbyNew() = capture("10_lobby_new") {
        Lobby(LobbyUiState(draft = RoomSettings(digits = 4, teamSize = 2, roundSeconds = 120)))
    }

    @Test fun lobby() = capture("11_lobby") {
        val room = SampleData.privateLobby()
        Lobby(
            LobbyUiState(
                room = room,
                players = listOf(
                    LobbyPlayer(SampleData.ME, "Mina#0420", Team.A, host = true, you = true, connected = true),
                    LobbyPlayer(SampleData.OPPONENT, "Leo#1234", Team.B, host = false, you = false, connected = false),
                ),
            ),
        )
    }

    @Test fun teamBoard() = capture("12_team_board") { TeamBoardScreen(SampleData.teamMatch(), onBack = {}) }

    @Test fun resultsComputer() = capture("13_results") {
        ResultsScreen(
            MatchSummary(MatchMode.COMPUTER, Outcome.WIN, EndReason.SOLVED, rounds = 6, opponentSecret = "5813", digits = 4, level = ComputerLevel.NORMAL),
            onPlayAgain = {}, onHome = {}, onBackToRoom = null,
        )
    }

    @Test fun resultsTeam() = capture("14_results_team") {
        ResultsScreen(
            MatchSummary(MatchMode.ONLINE, Outcome.DRAW, EndReason.BOTH_SOLVED, 5, "719", 3, teamMatch = true, scoreA = 1, scoreB = 1),
            onPlayAgain = null, onHome = {}, onBackToRoom = {},
        )
    }

    @Test fun chat() = capture("15_chat") {
        ChatScreen(ChatUiState(roomId = "r", myId = SampleData.ME, messages = SampleData.chat(), phrasesOnly = false, input = "rematch?"), {}, {}, {}, {}, {})
    }

    @Test fun chatQuickMatch() = capture("16_chat_quick") {
        ChatScreen(ChatUiState(roomId = "r", myId = SampleData.ME, messages = SampleData.chat().take(2), phrasesOnly = true), {}, {}, {}, {}, {})
    }

    @Test fun profile() = capture("17_profile") { ProfileScreen(ProfileUiState("Mina#0420", registered = true, stats = Stats(12, 7, 3)), onBack = {}) }

    @Test fun settings() = capture("18_settings") {
        SettingsScreen(
            state = SettingsUiState(registered = true),
            showPrivacyOptions = true,
            versionName = "0.1.0",
            contactEmail = "support@example.com",
            onSettingsChange = {}, onLanguageChange = {}, onPrivacyOptions = {}, onPrivacyPolicy = {}, onTerms = {},
            onCopyContact = {}, onTransferCode = {}, onDismissCode = {}, onDeleteRequest = {}, onDeleteCancel = {},
            onDeleteConfirm = {}, onBack = {},
        )
    }

    @Test fun report() = capture("19_report") {
        ReportScreen(
            ReportUiState(ReportTarget(SampleData.OPPONENT, "Leo#1234", "r", 3), reason = app.numbaseball.core.protocol.ReportReason.HARASSMENT),
            {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun howToPlay() = capture("20_how_to_play") { HowToPlayScreen(onBack = {}) }

    @Test fun termsGate() = capture("21_terms_gate") { TermsGateScreen(updated = false, submitting = false, error = null, {}, {}, {}) }

    @Test fun legalText() = capture("22_legal_text") {
        LegalTextScreen(
            title = "Terms of service",
            blocks = listOf(
                LegalBlock.Heading("Terms of Use", 1),
                LegalBlock.Heading("1. Who can use the app", 3),
                LegalBlock.Bullet("Sample bullet line."),
                LegalBlock.Paragraph("Sample paragraph. The real text is bundled from docs/play at build time."),
            ),
            onBack = {},
            onWebVersion = {},
        )
    }

    @Composable
    private fun Match(state: MatchUiState) = MatchScreen(state, {}, {}, {}, {}, {}, {})

    @Composable
    private fun Lobby(state: LobbyUiState) = RoomLobbyScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private companion object {
        /** Tolerates tiny anti-aliasing differences between machines when CI verifies the committed images. */
        val OPTIONS = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class PhoneEnglishScreenshots : ScreenshotSuite("phone_en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w360dp-h800dp-xhdpi")
class PhoneKoreanScreenshots : ScreenshotSuite("phone_ko")

/** Split-screen / pop-up window size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h320dp-xhdpi")
class SmallEnglishScreenshots : ScreenshotSuite("small_en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w360dp-h320dp-xhdpi")
class SmallKoreanScreenshots : ScreenshotSuite("small_ko")
