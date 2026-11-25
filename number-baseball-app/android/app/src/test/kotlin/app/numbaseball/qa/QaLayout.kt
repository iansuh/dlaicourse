package app.numbaseball.qa

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.numbaseball.R
import app.numbaseball.billing.PurchaseUiState
import app.numbaseball.billing.RemoveAdsState
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.BlockedPlayer
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.Stats
import app.numbaseball.core.protocol.Team
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.settings.AppSettings
import app.numbaseball.ui.age.AgeScreen
import app.numbaseball.ui.age.AgeUiState
import app.numbaseball.ui.age.NotAvailableScreen
import app.numbaseball.ui.chat.ChatSheet
import app.numbaseball.ui.chat.QuickPhraseSheet
import app.numbaseball.ui.chatActions
import app.numbaseball.ui.computer.ComputerChoice
import app.numbaseball.ui.computer.ComputerSetupSheet
import app.numbaseball.ui.design.LocalReduceMotion
import app.numbaseball.ui.design.SheetFrame
import app.numbaseball.ui.firstrun.FirstRunScreen
import app.numbaseball.ui.firstrun.FirstRunUiState
import app.numbaseball.ui.home.HomeScreen
import app.numbaseball.ui.home.HomeUiState
import app.numbaseball.ui.lobby.JoinScreen
import app.numbaseball.ui.lobby.JoinUiState
import app.numbaseball.ui.lobby.LobbyActions
import app.numbaseball.ui.lobby.RoomLobbyScreen
import app.numbaseball.ui.lobby.RoomSettingsSheet
import app.numbaseball.ui.match.MatchActions
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchPhase
import app.numbaseball.ui.match.MatchScreen
import app.numbaseball.ui.match.MatchUiState
import app.numbaseball.ui.match.Opponent
import app.numbaseball.ui.match.OpponentStatus
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.match.PracticeInfo
import app.numbaseball.ui.matchActions
import app.numbaseball.ui.profile.ProfileScreen
import app.numbaseball.ui.profile.ProfileUiState
import app.numbaseball.ui.quickmatch.FoundOpponent
import app.numbaseball.ui.quickmatch.QuickMatchScreen
import app.numbaseball.ui.quickmatch.QuickMatchUiState
import app.numbaseball.ui.report.CardRecord
import app.numbaseball.ui.report.PlayerCard
import app.numbaseball.ui.report.ReportChoice
import app.numbaseball.ui.report.ReportSheet
import app.numbaseball.ui.report.ReportTarget
import app.numbaseball.ui.report.ReportUiState
import app.numbaseball.ui.results.ResultsScreen
import app.numbaseball.ui.rules.HowToPlayScreen
import app.numbaseball.ui.settings.BlockedPlayersScreen
import app.numbaseball.ui.settings.SettingsActions
import app.numbaseball.ui.settings.SettingsScreen
import app.numbaseball.ui.team.BoardMode
import app.numbaseball.ui.team.TeamBoardScreen
import app.numbaseball.ui.terms.TermsGateScreen
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NumBaseballTheme
import app.numbaseball.ui.tutorial.Tutorial
import app.numbaseball.ui.tutorial.TutorialScreen
import app.numbaseball.ui.tutorial.TutorialState
import app.numbaseball.ui.tutorial.TutorialStep
import java.io.File
import java.util.TimeZone

/** A control the player must always be able to reach on a screen (tag, visible text, or content description). */
sealed class Key {
    abstract fun describe(res: Resources): String
    abstract fun matcher(res: Resources): SemanticsMatcher

    data class Tag(val tag: String) : Key() {
        override fun describe(res: Resources) = "tag:$tag"
        override fun matcher(res: Resources) = hasTestTag(tag)
    }

    /** Visible text; [alternatives] are other strings the same control may show (e.g. the short label in compact windows). */
    data class Txt(@StringRes val id: Int, val alternatives: List<Int> = emptyList()) : Key() {
        override fun describe(res: Resources) = "text:${res.getString(id)}"
        override fun matcher(res: Resources): SemanticsMatcher =
            (listOf(id) + alternatives).map { hasText(res.getString(it), substring = true) }.reduce { x, y -> x or y }
    }

    data class Desc(@StringRes val id: Int) : Key() {
        override fun describe(res: Resources) = "desc:${res.getString(id)}"
        override fun matcher(res: Resources) = hasContentDescription(res.getString(id), substring = true)
    }
}

/** One screen/state of the app for the layout sweeps (sample data of the design shots). */
class QaScreen(
    val id: String,
    /** IME height in dp shown below the screen (text-field screens). */
    val ime: Int = 0,
    val keys: List<Key> = emptyList(),
    /** The five screens with a `qa-locale-*` render. */
    val tight: Boolean = false,
    val content: @Composable () -> Unit,
)

private fun MatchActions.withChat() = MatchActions(
    onDigit, onDelete, onClear, onTapSlot, onSubmit, onRandomSecret, onForfeit, onLeave, onMemoDigit, onMemoCell, onMemoClear, onMemoCollapse,
    onChat = {},
)

private fun MatchActions.withToRoom() = MatchActions(
    onDigit, onDelete, onClear, onTapSlot, onSubmit, onRandomSecret, onForfeit, onLeave, onMemoDigit, onMemoCell, onMemoClear, onMemoCollapse,
    onToRoom = {},
)

private fun lobbyActions() = LobbyActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
private fun settingsActions() = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})

private val digitKeys = listOf(Key.Tag("key_0"), Key.Tag("key_4"), Key.Tag("key_9"))

/** Every screen and state with sample data; names follow the screenshot suite. */
object QaScreens {
    private val home = HomeUiState(nickname = "Mina", tag = "4821", registered = true, stats = Stats(12, 8, 3), recentForm = "WWLWDLWWLW")
    private val settingsState = QaDesign.settingsState.copy(nickname = "Mina", language = "en")
    private val price = PurchaseUiState(RemoveAdsState.Available("$4.99"))

    private fun summary(outcome: Outcome = Outcome.WIN, mode: MatchMode = MatchMode.QUICK) = QaDesign.resultSummary(outcome, mode)
    private val resultKeys = listOf(Key.Tag("resultPrimary"), Key.Desc(R.string.home))

    private fun tut(state: TutorialState) = state
    private val explain = Tutorial.pitch(Tutorial.next(TutorialState()).copy(input = Tutorial.FIRST_GUESS))

    val all: List<QaScreen> = listOf(
        QaScreen("age", keys = digitKeys + Key.Txt(R.string.age_confirm)) { AgeScreen(AgeUiState(year = "199"), {}, {}, {}, {}) },
        QaScreen("age-error", keys = digitKeys + Key.Txt(R.string.age_confirm)) { AgeScreen(AgeUiState(year = "2091", month = "01", invalid = true), {}, {}, {}, {}) },
        QaScreen("age-na", keys = listOf(Key.Txt(R.string.privacy_policy))) { NotAvailableScreen(onPrivacyPolicy = {}) },
        QaScreen("nickname", ime = 230, keys = listOf(Key.Tag("nickname"), Key.Txt(R.string.action_start))) { FirstRunScreen(FirstRunUiState(nickname = "Mina"), {}, {}, {}, {}, {}) },
        QaScreen("nickname-transfer", ime = 230, keys = listOf(Key.Tag("transferCode"))) { FirstRunScreen(FirstRunUiState(transferMode = true, transferCode = "7KQ2-M9XA"), {}, {}, {}, {}, {}) },
        QaScreen("tutorial-1", tight = true, keys = listOf(Key.Txt(R.string.tut_next), Key.Txt(R.string.tut_skip))) { TutorialScreen(onFinish = {}) },
        QaScreen("tutorial-2", tight = true, keys = listOf(Key.Tag("key_0"), Key.Tag("key_7"), Key.Tag("key_5"), Key.Txt(R.string.pitch), Key.Txt(R.string.tut_skip))) {
            TutorialScreen(onFinish = {}, initial = TutorialState(step = TutorialStep.FIRST_GUESS, input = "07"))
        },
        QaScreen("tutorial-3", tight = true, keys = listOf(Key.Txt(R.string.tut_next))) { TutorialScreen(onFinish = {}, initial = explain) },
        QaScreen("tutorial-4", tight = true, keys = listOf(Key.Tag("key_4"), Key.Txt(R.string.pitch))) {
            TutorialScreen(onFinish = {}, initial = Tutorial.next(Tutorial.next(explain)).copy(input = "04"))
        },
        QaScreen("tutorial-done", tight = true, keys = listOf(Key.Txt(R.string.tut_start))) {
            TutorialScreen(onFinish = {}, initial = Tutorial.pitch(Tutorial.next(Tutorial.next(explain)).copy(input = Tutorial.SECRET)))
        },
        QaScreen("how-to-play", keys = listOf(Key.Desc(R.string.action_back))) { HowToPlayScreen(onBack = {}) },
        QaScreen("home", tight = true, keys = listOf(Key.Tag("quickMatch"), Key.Txt(R.string.cpu_title), Key.Txt(R.string.create_title), Key.Txt(R.string.join_title), Key.Desc(R.string.home_settings), Key.Desc(R.string.home_help))) {
            HomeScreen(home, {}, {}, {}, {}, {}, {}, {}, {}, {})
        },
        QaScreen("home-offline", tight = true, keys = listOf(Key.Txt(R.string.cpu_title), Key.Desc(R.string.home_settings))) {
            HomeScreen(home.copy(online = false), {}, {}, {}, {}, {}, {}, {}, {}, {})
        },
        QaScreen("home-new", keys = listOf(Key.Tag("quickMatch"), Key.Txt(R.string.cpu_title))) {
            HomeScreen(home.copy(stats = Stats(0, 0, 0), recentForm = ""), {}, {}, {}, {}, {}, {}, {}, {}, {})
        },
        QaScreen("cpu-setup", keys = listOf(Key.Tag("cpuStart"), Key.Desc(R.string.action_close))) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(home, {}, {}, {}, {}, {}, {}, {}, {}, {})
                ComputerSetupSheet(ComputerChoice(3, ComputerLevel.NORMAL, 600), {}, {}, {})
            }
        },
        QaScreen("create-room", keys = listOf(Key.Tag("roomConfirm"))) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(home, {}, {}, {}, {}, {}, {}, {}, {}, {})
                RoomSettingsSheet(title = androidx.compose.ui.res.stringResource(R.string.create_title), actionLabel = androidx.compose.ui.res.stringResource(R.string.create_title), settings = RoomSettings(4, 3, 60), onChange = {}, onConfirm = {}, onDismiss = {})
            }
        },
        QaScreen("join", ime = 230, keys = listOf(Key.Tag("join"))) { JoinScreen(JoinUiState(code = "K7Q"), {}, {}, {}, {}) },
        QaScreen("search", keys = listOf(Key.Txt(R.string.search_cancel))) { QuickMatchScreen(QuickMatchUiState(digits = 4, startedAt = 0), {}, {}, {}, now = { 7_000L }) },
        QaScreen("search-60s", keys = listOf(Key.Txt(R.string.search_cancel))) { QuickMatchScreen(QuickMatchUiState(digits = 4, startedAt = 0), {}, {}, {}, now = { 64_000L }) },
        QaScreen("search-found") { QuickMatchScreen(QuickMatchUiState(digits = 4, found = FoundOpponent("Kestrel", "2048")), {}, {}, {}) },
        QaScreen("match-secret", tight = true, keys = digitKeys + listOf(Key.Txt(R.string.lock_in, listOf(R.string.lock_in_short)), Key.Desc(R.string.action_more), Key.Desc(R.string.leave))) {
            MatchScreen(MatchUiState(4, Opponent.Player("Kestrel", "2048"), MatchMode.QUICK, MatchPhase.CHOOSING_SECRET, input = "047", secondsLeft = 42, roundSeconds = 60, opponentStatus = OpponentStatus.Picking), matchActions().withChat())
        },
        QaScreen("match-guess", tight = true, keys = digitKeys + listOf(Key.Txt(R.string.pitch), Key.Desc(R.string.action_more), Key.Desc(R.string.leave))) { MatchScreen(QaDesign.guessState(), matchActions().withChat()) },
        QaScreen("match-waiting", keys = listOf(Key.Desc(R.string.action_more))) {
            MatchScreen(QaDesign.guessState().copy(phase = MatchPhase.WAITING_FOR_OPPONENT_GUESS, input = "3961", opponentStatus = OpponentStatus.Thinking), matchActions().withChat())
        },
        QaScreen("match-late", tight = true, keys = digitKeys + listOf(Key.Txt(R.string.pitch), Key.Desc(R.string.action_more))) { MatchScreen(QaDesign.lateState(), matchActions().withChat()) },
        QaScreen("match-6digits", keys = digitKeys + listOf(Key.Txt(R.string.pitch))) { MatchScreen(QaDesign.sixDigits(), matchActions().withChat()) },
        QaScreen("match-clock-crit", keys = listOf(Key.Txt(R.string.pitch))) { MatchScreen(QaDesign.guessState().copy(secondsLeft = 4), matchActions().withChat()) },
        QaScreen("match-cpu", keys = digitKeys + listOf(Key.Txt(R.string.pitch))) {
            MatchScreen(QaDesign.guessState().copy(opponent = Opponent.Computer(ComputerLevel.NORMAL), mode = MatchMode.COMPUTER, secondsLeft = 600, roundSeconds = 600, opponentStatus = OpponentStatus.Thinking), matchActions())
        },
        QaScreen("match-practice", keys = listOf(Key.Txt(R.string.pitch))) {
            MatchScreen(
                QaDesign.guessState().copy(opponent = Opponent.Computer(ComputerLevel.NORMAL), mode = MatchMode.PRACTICE, secondsLeft = 600, roundSeconds = 600, opponentStatus = OpponentStatus.Thinking, practice = PracticeInfo("K7Q2M", 3, 6)),
                matchActions().withToRoom(),
            )
        },
        QaScreen("match-opponent-offline", keys = listOf(Key.Txt(R.string.pitch))) { MatchScreen(QaDesign.guessState().copy(opponentStatus = OpponentStatus.Disconnected(41)), matchActions().withChat()) },
        QaScreen("match-reconnect", keys = listOf(Key.Txt(R.string.pitch))) { MatchScreen(QaDesign.guessState().copy(reconnectSeconds = 48), matchActions().withChat()) },
        QaScreen("match-phrases") { Box(Modifier.fillMaxSize()) { MatchScreen(QaDesign.lateState(), matchActions().withChat()); QuickPhraseSheet(enabled = true, onPhrase = {}, onDismiss = {}) } },
        QaScreen("result-win", tight = true, keys = resultKeys) { ResultsScreen(summary(), showBanner = false, onRemoveAds = {}, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = {}) },
        QaScreen("result-lose", tight = true, keys = resultKeys) { ResultsScreen(summary(Outcome.LOSE), showBanner = false, onRemoveAds = {}, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = {}) },
        QaScreen("result-draw", tight = true, keys = resultKeys) {
            ResultsScreen(summary(Outcome.DRAW).copy(reason = EndReason.BOTH_SOLVED, myStrikes = listOf(0, 0, 2, 1, 2, 4)), showBanner = false, onRemoveAds = {}, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = {})
        },
        QaScreen("result-forfeit", keys = resultKeys) {
            ResultsScreen(summary(Outcome.LOSE).copy(reason = EndReason.TIMEOUT, iEnded = true), showBanner = false, onRemoveAds = {}, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = {})
        },
        QaScreen("result-cpu-win", keys = resultKeys) { ResultsScreen(summary(Outcome.WIN, MatchMode.COMPUTER), showBanner = false, onRemoveAds = {}, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = null) },
        QaScreen("lobby", keys = listOf(Key.Desc(R.string.leave_room), Key.Desc(R.string.action_more))) { RoomLobbyScreen(QaDesign.lobbyState(), lobbyActions()) },
        QaScreen("lobby-alone", keys = listOf(Key.Tag("practice"))) { RoomLobbyScreen(QaDesign.lobbyState(players = listOf(QaDesign.me)).copy(chat = emptyList()), lobbyActions()) },
        QaScreen("lobby-reconnect") { RoomLobbyScreen(QaDesign.lobbyState(connection = ConnectionState.Reconnecting(1, 1_000L)), lobbyActions()) },
        QaScreen("team-board") { TeamBoardScreen(QaDesign.teamRoom(final = false), BoardMode.LIVE, {}, {}, offlineSince = mapOf(QaDesign.volna.id to 0L), serverNow = { 19_000L }) },
        QaScreen("team-final", keys = listOf(Key.Txt(R.string.home), Key.Txt(R.string.back_to_room))) { TeamBoardScreen(QaDesign.teamRoom(final = true), BoardMode.FINAL, {}, {}, onRemoveAds = {}) },
        QaScreen("chat", keys = listOf(Key.Tag("chatInput"), Key.Tag("chatSend"))) {
            Box(Modifier.fillMaxSize()) {
                RoomLobbyScreen(QaDesign.lobbyState(), lobbyActions())
                SheetFrame(onDismiss = {}, top = 136.dp) { ChatSheet(QaDesign.chatState(), chatActions()) }
            }
        },
        QaScreen("profile", keys = listOf(Key.Desc(R.string.action_back))) { ProfileScreen(ProfileUiState(nickname = "Mina", tag = "4821", registered = true, stats = Stats(12, 8, 3), recentForm = "WWLWDLWWLW"), {}, {}, {}) },
        QaScreen("settings", tight = true, keys = listOf(Key.Desc(R.string.action_back), Key.Tag("removeAdsRow"), Key.Tag("restorePurchase"), Key.Tag("deleteAccount"))) {
            SettingsScreen(settingsState, true, "1.0.0 (1)", "support@example.com", listOf("ko", "en"), settingsActions(), purchase = price)
        },
        QaScreen("blocked", keys = listOf(Key.Desc(R.string.action_back))) { BlockedPlayersScreen(listOf(BlockedPlayer(QaDesign.gross.id, "Größenwahn", "7731"), BlockedPlayer(QaDesign.volna.id, "Volna", "1187")), {}, {}, {}) },
        QaScreen("report-card", keys = listOf(Key.Tag("cardBlock"))) {
            Box(Modifier.fillMaxSize()) {
                TeamBoardScreen(QaDesign.teamRoom(final = false), BoardMode.LIVE, {}, {})
                SheetFrame(onDismiss = {}) { PlayerCard(ReportTarget(QaDesign.gross.id, "Größenwahn", "7731", team = Team.B), self = false, blocked = false, {}, {}, {}, record = CardRecord.Loaded(Stats(31, 27, 4))) }
            }
        },
        QaScreen("report-reasons", keys = listOf(Key.Tag("reportSend"))) {
            Box(Modifier.fillMaxSize()) {
                TeamBoardScreen(QaDesign.teamRoom(final = false), BoardMode.LIVE, {}, {})
                SheetFrame(onDismiss = {}) { ReportSheet(ReportUiState(ReportTarget(QaDesign.gross.id, "Größenwahn", "7731"), choice = ReportChoice.HARASSMENT), {}, {}, {}, {}, {}) }
            }
        },
        QaScreen("terms", keys = listOf(Key.Tag("termsAgree"), Key.Desc(R.string.action_close))) { TermsGateScreen(updated = false, version = "2026-11-v3", submitting = false, failed = 0, {}, {}, {}, {}) },
        QaScreen("terms-updated", keys = listOf(Key.Tag("termsAgree"), Key.Desc(R.string.action_close))) { TermsGateScreen(updated = true, version = "2026-11-v3", submitting = false, failed = 0, {}, {}, {}, {}) },
    )

    val byId = all.associateBy { it.id }
}

/** A 24 dp status bar and gesture area and an optional IME as real window insets (same frame as the screenshot suite). */
@Composable
fun QaFrame(ime: Dp, bars: Dp, content: @Composable () -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current
    LaunchedEffect(view, ime, bars) {
        val bar = with(density) { bars.roundToPx() }
        val imePx = with(density) { ime.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, bar, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bar))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imePx))
            .setVisible(WindowInsetsCompat.Type.ime(), imePx > 0)
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, insets)
    }
    Box(Modifier.fillMaxSize().background(NbColors.bg)) { content() }
}

fun ComposeContentTestRule.setScreen(screen: QaScreen, small: Boolean = false) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    setContent {
        NumBaseballTheme {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                QaFrame(ime = if (small) 0.dp else screen.ime.dp, bars = if (small) 0.dp else 24.dp) { screen.content() }
            }
        }
    }
    waitForIdle()
}

// ---------------------------------------------------------------------------------------------------------------
// Measuring

/** Resource templates of the active locale, to name the string key behind a rendered text. */
class Templates(res: Resources) {
    private val entries: List<Pair<String, Regex>>

    init {
        val list = mutableListOf<Pair<String, Regex>>()
        fun regexOf(template: String): Regex {
            val parts = Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXc]""").split(template).joinToString(".+?") { Regex.escape(it) }
            return Regex("^$parts$", RegexOption.DOT_MATCHES_ALL)
        }
        for (f in R.string::class.java.fields) {
            val id = f.getInt(null)
            runCatching { res.getString(id) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { list += f.name to regexOf(it) }
        }
        for (f in R.plurals::class.java.fields) {
            val id = f.getInt(null)
            for (q in listOf(0, 1, 2, 3, 5, 11, 21)) {
                runCatching { res.getQuantityString(id, q) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { list += "${f.name}" to regexOf(it) }
            }
        }
        entries = list
    }

    /** Longest literal template that matches, so "Home" wins over a pattern like "%1$s". */
    fun keyOf(text: String): String? = entries.filter { it.second.matches(text) }.minByOrNull { it.second.pattern.count { c -> c == '+' } }?.first
}

/** Player-written content of the sample data (nicknames): shortening it with an ellipsis is intended. */
private val SAMPLE_CONTENT = setOf("Größenwahn", "Kestrel", "Volna", "Mina", "새벽투수", "민트초코", "밤하늘")
private val SAMPLE_PREFIXES = listOf("민트초코", "새벽투수", "Größenwahn", "Kestrel")

data class Finding(val screen: String, val kind: String, val key: String, val text: String, val detail: String) {
    fun line() = "$screen\t$kind\t$key\t${text.replace('\n', ' ')}\t$detail"
}

/** Text nodes that do not fit (ellipsis, clip, or outside the window horizontally). */
fun ComposeContentTestRule.measureText(screen: String, templates: Templates, cjk: Boolean = false): List<Finding> {
    val root = onAllNodes(isRoot()).fetchSemanticsNodes().first()
    val width = root.size.width
    val findings = mutableListOf<Finding>()
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
    for (node in nodes) {
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: continue
        if (text.isBlank()) continue
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.firstOrNull() ?: continue
        val key = templates.keyOf(text) ?: "?"
        // Zero-sized text is content the layout hides on purpose (collapsed notes, legend in a short window).
        if (layout.size.width == 0 || layout.size.height == 0) continue
        if (text in SAMPLE_CONTENT || SAMPLE_PREFIXES.any { text.startsWith(it) }) continue
        val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        val overW = layout.multiParagraph.maxIntrinsicWidth - layout.size.width
        val overH = layout.multiParagraph.height - layout.size.height
        val box = "box=${layout.size.width}x${layout.size.height}"
        // A word broken in the middle by the line wrap (languages that separate words with spaces).
        val brokenWord = !cjk && (0 until layout.lineCount - 1).any { i ->
            val end = layout.getLineEnd(i, visibleEnd = false)
            end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] !in "·-–—/:" && text[end] !in "·-–—/:"
        }
        when {
            ellipsized -> findings += Finding(screen, "ellipsis", key, text, "lines=${layout.lineCount} $box")
            layout.lineCount == 1 && layout.didOverflowWidth && overW > 1f -> findings += Finding(screen, "clipped-width", key, text, "over=${"%.0f".format(overW)}px $box")
            layout.didOverflowHeight && overH > 3f -> findings += Finding(screen, "clipped-height", key, text, "over=${"%.0f".format(overH)}px lines=${layout.lineCount} $box")
            brokenWord -> findings += Finding(screen, "word-break", key, text, "lines=${layout.lineCount} $box")
        }
        val b = node.boundsInRoot
        if (b.width > 0 && (b.left < -2f || b.right > width + 2f)) findings += Finding(screen, "outside-x", key, text, "left=${b.left} right=${b.right} window=$width")
    }
    return findings
}

/** Reachability of the key controls: present, and inside the window (above the IME) after scrolling to it. */
fun ComposeContentTestRule.checkKeys(screen: QaScreen, res: Resources, ime: Int = screen.ime): List<Finding> {
    val root = onAllNodes(isRoot()).fetchSemanticsNodes().first()
    val density = res.displayMetrics.density
    val usableBottom = root.size.height - ime * density
    val findings = mutableListOf<Finding>()
    for (key in screen.keys) {
        val m = key.matcher(res)
        val interaction = onAllNodes(m)
        if (interaction.fetchSemanticsNodes().isEmpty()) {
            findings += Finding(screen.id, "missing", key.describe(res), "", "control not in the semantics tree")
            continue
        }
        runCatching { interaction[0].performScrollTo() }
        waitForIdle()
        val node = onAllNodes(m).fetchSemanticsNodes().first()
        val b = node.boundsInRoot
        val inside = b.left >= -2f && b.right <= root.size.width + 2f && b.top >= -2f && b.bottom <= usableBottom + 2f
        if (!inside) findings += Finding(screen.id, "unreachable", key.describe(res), "", "bounds=$b window=${root.size} usableBottom=$usableBottom")
    }
    return findings
}

/**
 * Writes the current window to `qa-screenshots/qa-locale-<name>.png` (a folder of its own, never `screenshots/`). Always
 * records, whatever the Roborazzi task: these images have no baseline and take no part in `verifyRoborazziDebug`.
 */
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.saveQaImage(name: String) {
    File("qa-screenshots").mkdirs()
    onRoot().captureRoboImage(
        "qa-screenshots/$name.png",
        roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record),
    )
}

/** Findings log of a sweep: `build/qa/<suite>.tsv`. */
fun appendQaLog(suite: String, lines: List<String>) {
    val file = File("build/qa/$suite.tsv").apply { parentFile.mkdirs() }
    synchronized(QaLayoutLock) { file.appendText(lines.joinToString("") { it + "\n" }) }
}

private object QaLayoutLock
