package app.numbaseball.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.numbaseball.LocalAppContainer
import app.numbaseball.R
import app.numbaseball.audio.SoundCue
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.AccountState
import app.numbaseball.data.age.AgeBand
import app.numbaseball.data.age.AgeRepository
import app.numbaseball.data.age.AgeState
import app.numbaseball.ui.age.AgeScreen
import app.numbaseball.ui.age.AgeViewModel
import app.numbaseball.ui.age.NotAvailableScreen
import app.numbaseball.ui.chat.ChatScreen
import app.numbaseball.ui.chat.ChatViewModel
import app.numbaseball.ui.common.ConnectionStatus
import app.numbaseball.ui.common.SecondaryButton
import app.numbaseball.ui.common.containerViewModel
import app.numbaseball.ui.common.findActivity
import app.numbaseball.ui.computer.ComputerSetupRoute
import app.numbaseball.ui.firstrun.FirstRunScreen
import app.numbaseball.ui.firstrun.FirstRunViewModel
import app.numbaseball.ui.home.HomeScreen
import app.numbaseball.ui.home.HomeViewModel
import app.numbaseball.ui.legal.LegalDocument
import app.numbaseball.ui.legal.LegalTextScreen
import app.numbaseball.ui.lobby.RoomLobbyScreen
import app.numbaseball.ui.lobby.RoomLobbyViewModel
import app.numbaseball.ui.match.ComputerMatchViewModel
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchScreen
import app.numbaseball.ui.match.MatchSummary
import app.numbaseball.ui.match.OnlineMatchViewModel
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.profile.ProfileScreen
import app.numbaseball.ui.profile.ProfileViewModel
import app.numbaseball.ui.quickmatch.QuickMatchScreen
import app.numbaseball.ui.quickmatch.QuickMatchViewModel
import app.numbaseball.ui.report.ReportScreen
import app.numbaseball.ui.report.ReportTarget
import app.numbaseball.ui.report.ReportViewModel
import app.numbaseball.ui.results.ResultsScreen
import app.numbaseball.ui.rules.HowToPlayScreen
import app.numbaseball.ui.settings.SettingsScreen
import app.numbaseball.ui.settings.SettingsViewModel
import app.numbaseball.ui.team.TeamBoardScreen
import app.numbaseball.ui.terms.TermsGate
import app.numbaseball.ui.theme.NbColors

/**
 * App entry: the neutral age screen comes first (before any ad SDK call or account), then ads consent, then first run
 * (no nickname yet) or home.
 */
@Composable
fun NbApp(ageRepository: AgeRepository, accountRepository: AccountRepository) {
    val container = LocalAppContainer.current
    val age by ageRepository.state.collectAsStateWithLifecycle()
    val account by accountRepository.state.collectAsStateWithLifecycle()
    when (val current = age) {
        AgeState.Loading -> Blank()
        AgeState.Unknown -> {
            val vm = containerViewModel { AgeViewModel(ageRepository) }
            val state by vm.state.collectAsStateWithLifecycle()
            AgeScreen(state, vm::onYearSelected, vm::onContinue)
        }
        is AgeState.Known -> if (current.band == AgeBand.UNDER_MINIMUM) {
            NotAvailableScreen(container.config.minimumAge)
        } else {
            AdsAfterAgeCheck(current)
            if (account == AccountState.Loading) {
                Blank()
            } else {
                val startOnFirstRun = remember { account == AccountState.NeedsNickname }
                NbNavHost(startOnFirstRun)
            }
        }
    }
}

@Composable
private fun Blank() = Box(Modifier.fillMaxSize().background(NbColors.bg))

/** Starts UMP + Mobile Ads only now that the age treatment is known, and refines it with Play Age Signals. */
@Composable
private fun AdsAfterAgeCheck(age: AgeState.Known) {
    val container = LocalAppContainer.current
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(Unit) {
        activity?.let { container.ads.gatherConsent(it, age.adTreatment) }
        container.ageRepository.refreshSignals()
    }
    LaunchedEffect(age.adTreatment) { container.ads.updateAgeTreatment(age.adTreatment) }
}

@Composable
fun NbNavHost(startOnFirstRun: Boolean, navController: NavHostController = rememberNavController()) {
    val container = LocalAppContainer.current
    val back: () -> Unit = { navController.popBackStack() }
    val readTerms: () -> Unit = { navController.navigate(LegalRoute(LegalDocument.TERMS.name)) }
    val home: () -> Unit = { navController.navigate(HomeRoute) { popUpTo<HomeRoute> { inclusive = false } } }

    NavHost(navController, startDestination = if (startOnFirstRun) FirstRunRoute else HomeRoute) {
        composable<FirstRunRoute> {
            val vm = containerViewModel { FirstRunViewModel(accountRepository) }
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.done) { if (state.done) navController.resetTo(HomeRoute) }
            FirstRunScreen(state, vm::onNicknameChange, vm::onTransferCodeChange, vm::onTransferModeChange, vm::onSubmit)
        }

        composable<HomeRoute> {
            val vm = containerViewModel { HomeViewModel(accountRepository, ageRepository) }
            val state by vm.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onRoomCodeChange = vm::onRoomCodeChange,
                onVsComputer = { navController.navigate(ComputerSetupRoute) },
                onQuickMatch = { navController.navigate(QuickMatchRoute) },
                onCreateRoom = { navController.navigate(RoomLobbyRoute()) },
                onJoinRoom = { navController.navigate(RoomLobbyRoute(joinCode = it)) },
                onHowToPlay = { navController.navigate(HowToPlayRoute) },
                onProfile = { navController.navigate(ProfileRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
                onChooseNickname = { navController.resetTo(FirstRunRoute) },
            )
        }

        composable<HowToPlayRoute> { HowToPlayScreen(onBack = back) }

        composable<ComputerSetupRoute> {
            ComputerSetupRoute(
                onStart = { digits, level ->
                    navController.navigate(ComputerMatchRoute(digits, level.name)) { popUpTo<ComputerSetupRoute> { inclusive = true } }
                },
                onBack = back,
            )
        }

        composable<ComputerMatchRoute> { entry ->
            val route = entry.toRoute<ComputerMatchRoute>()
            val vm = containerViewModel {
                ComputerMatchViewModel(route.digits, ComputerLevel.valueOf(route.level), route.practice, offlineGame, ads.interstitials)
            }
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.summary) {
                state.summary?.let { navController.navigate(it.toRoute()) { popUpTo<ComputerMatchRoute> { inclusive = true } } }
            }
            if (route.practice) SwitchToRealMatchWhenStarted(navController)
            MatchScreen(
                state = state,
                onDigit = vm::onDigit,
                onDelete = vm::onDelete,
                onSubmit = vm::onSubmit,
                onRandomSecret = vm::onRandomSecret,
                onForfeit = vm::onForfeit,
                onBack = back,
                banner = { if (route.practice) Text(stringResource(R.string.match_practice_note), color = NbColors.text2) },
            )
        }

        composable<QuickMatchRoute> {
            TermsGate(onBack = back, onReadTerms = readTerms) {
                val vm = containerViewModel { QuickMatchViewModel(onlineSession) }
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.matchFound) {
                    if (state.matchFound) {
                        container.audio.play(SoundCue.MATCH_FOUND)
                        navController.navigate(OnlineMatchRoute) { popUpTo<QuickMatchRoute> { inclusive = true } }
                    }
                }
                QuickMatchScreen(state, vm::onDigitsChange, vm::onSearch, vm::onCancel, vm::onRetryConnection, back)
            }
        }

        composable<RoomLobbyRoute> { entry ->
            val route = entry.toRoute<RoomLobbyRoute>()
            TermsGate(onBack = back, onReadTerms = readTerms) {
                val vm = containerViewModel { RoomLobbyViewModel(route.joinCode, onlineSession) }
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(vm) { vm.matchStarted.collect { navController.navigate(OnlineMatchRoute) { launchSingleTop = true } } }
                RoomLobbyScreen(
                    state = state,
                    onDraftChange = vm::onDraftChange,
                    onCreate = vm::onCreate,
                    onSwitchTeam = vm::onSwitchTeam,
                    onReset = vm::onReset,
                    onPractice = {
                        state.room?.let { navController.navigate(ComputerMatchRoute(it.settings.digits, ComputerLevel.NORMAL.name, practice = true)) }
                    },
                    onOpenMatch = { navController.navigate(OnlineMatchRoute) { launchSingleTop = true } },
                    onChat = { navController.navigate(ChatRoute) },
                    onBoard = { navController.navigate(TeamBoardRoute) },
                    onLeave = {
                        vm.onLeave()
                        home()
                    },
                    onPlayerLongPress = { player ->
                        navController.navigate(ReportRoute(player.id, player.displayName, roomId = state.room?.id.orEmpty()))
                    },
                    onRetryConnection = vm::onRetryConnection,
                    onBack = back,
                )
            }
        }

        composable<OnlineMatchRoute> {
            val vm = containerViewModel { OnlineMatchViewModel(onlineSession) }
            val state by vm.state.collectAsStateWithLifecycle()
            val connection by vm.connection.collectAsStateWithLifecycle()
            val room by container.onlineSession.room.collectAsStateWithLifecycle()
            val current = state
            if (current == null) {
                LaunchedEffect(Unit) { home() }
                return@composable
            }
            LaunchedEffect(current.summary) {
                current.summary?.let { navController.navigate(it.toRoute()) { popUpTo<OnlineMatchRoute> { inclusive = true } } }
            }
            MatchScreen(
                state = current,
                onDigit = vm::onDigit,
                onDelete = vm::onDelete,
                onSubmit = vm::onSubmit,
                onRandomSecret = vm::onRandomSecret,
                onForfeit = vm::onForfeit,
                onBack = back,
                footer = {
                    ConnectionStatus(connection, vm::retryConnection)
                    SecondaryButton(stringResource(R.string.lobby_chat), { navController.navigate(ChatRoute) })
                    if ((room?.settings?.teamSize ?: 1) > 1) {
                        SecondaryButton(stringResource(R.string.lobby_board), { navController.navigate(TeamBoardRoute) })
                    }
                },
            )
        }

        composable<TeamBoardRoute> {
            val room by container.onlineSession.room.collectAsStateWithLifecycle()
            TeamBoardScreen(room, onBack = back)
        }

        composable<ResultsRoute> { entry ->
            val summary = entry.toRoute<ResultsRoute>().toSummary()
            ResultsRoute(summary, navController, home)
        }

        composable<ChatRoute> {
            TermsGate(onBack = back, onReadTerms = readTerms) {
                val vm = containerViewModel { ChatViewModel(onlineSession, settingsRepository, accountRepository.serverInfo) }
                val state by vm.state.collectAsStateWithLifecycle()
                ChatScreen(
                    state = state,
                    onInputChange = vm::onInputChange,
                    onSend = vm::onSend,
                    onPhrase = vm::onPhrase,
                    onReport = { message ->
                        val senderId = message.senderId ?: return@ChatScreen
                        val name = "${message.senderNickname.orEmpty()}#${message.senderTag.orEmpty()}"
                        navController.navigate(ReportRoute(senderId, name, state.roomId.orEmpty(), message.id))
                    },
                    onBack = back,
                )
            }
        }

        composable<ProfileRoute> {
            val vm = containerViewModel { ProfileViewModel(accountRepository) }
            val state by vm.state.collectAsStateWithLifecycle()
            ProfileScreen(state, onBack = back)
        }

        composable<SettingsRoute> { SettingsRoute(navController, back) }

        composable<LegalRoute> { entry ->
            val document = LegalDocument.valueOf(entry.toRoute<LegalRoute>().document)
            val uriHandler = LocalUriHandler.current
            LegalTextScreen(
                document = document,
                onBack = back,
                // The only web link left in the app (README "App only"): the public privacy policy URL Play requires.
                onWebVersion = if (document == LegalDocument.PRIVACY) {
                    { uriHandler.openUri(container.config.privacyUrl) }
                } else {
                    null
                },
            )
        }

        composable<ReportRoute> { entry ->
            val route = entry.toRoute<ReportRoute>()
            val target = ReportTarget(
                playerId = route.playerId,
                playerName = route.playerName,
                roomId = route.roomId.ifEmpty { null },
                messageId = route.messageId.takeIf { it >= 0 },
            )
            val vm = containerViewModel { ReportViewModel(target, safety) }
            val state by vm.state.collectAsStateWithLifecycle()
            ReportScreen(state, vm::onReasonChange, vm::onNoteChange, vm::onBlockTooChange, vm::onSubmit, vm::onBlock, back)
        }
    }
}

/** Waiting-room practice ends the moment the room fills and the real match starts (README "Online rules"). */
@Composable
private fun SwitchToRealMatchWhenStarted(navController: NavHostController) {
    val room by LocalAppContainer.current.onlineSession.room.collectAsStateWithLifecycle()
    LaunchedEffect(room?.phase) {
        if (room?.phase == RoomPhase.PLAYING) {
            navController.navigate(OnlineMatchRoute) {
                popUpTo<RoomLobbyRoute> { inclusive = false }
                launchSingleTop = true
            }
        }
    }
}

@Composable
private fun ResultsRoute(summary: MatchSummary, navController: NavHostController, home: () -> Unit) {
    val container = LocalAppContainer.current
    val activity = LocalContext.current.findActivity()
    val shownAt = remember { SystemClock.elapsedRealtime() }
    val session = container.onlineSession
    // Interstitials only when leaving a vs-computer result (never after online matches or practice, never on start/exit).
    val leave: (() -> Unit) -> Unit = { next ->
        if (summary.mode == MatchMode.COMPUTER && activity != null) {
            container.ads.interstitials.showIfDue(activity, SystemClock.elapsedRealtime() - shownAt, next)
        } else {
            next()
        }
    }
    if (summary.mode == MatchMode.PRACTICE) SwitchToRealMatchWhenStarted(navController)
    val room by session.room.collectAsStateWithLifecycle()
    val playAgain: (() -> Unit)? = when (summary.mode) {
        MatchMode.COMPUTER, MatchMode.PRACTICE -> {
            {
                leave {
                    val level = (summary.level ?: ComputerLevel.NORMAL).name
                    navController.navigate(ComputerMatchRoute(summary.digits, level, practice = summary.mode == MatchMode.PRACTICE)) {
                        popUpTo<ResultsRoute> { inclusive = true }
                    }
                }
            }
        }
        MatchMode.ONLINE -> if (room?.kind == RoomKind.QUICK) {
            {
                session.leaveRoom()
                navController.navigate(QuickMatchRoute) { popUpTo<HomeRoute> { inclusive = false } }
            }
        } else {
            null
        }
    }
    val roomCode = room?.code
    val backToRoom: (() -> Unit)? = when {
        summary.mode == MatchMode.PRACTICE -> {
            { navController.popBackStack() }
        }
        summary.mode == MatchMode.ONLINE && room?.kind == RoomKind.PRIVATE && roomCode != null -> {
            { navController.navigate(RoomLobbyRoute(roomCode)) { popUpTo<HomeRoute> { inclusive = false } } }
        }
        else -> null
    }
    ResultsScreen(
        summary = summary,
        onPlayAgain = playAgain,
        onHome = {
            leave {
                if (summary.mode != MatchMode.COMPUTER) session.leaveRoom()
                home()
            }
        },
        onBackToRoom = backToRoom,
    )
}

@Composable
private fun SettingsRoute(navController: NavHostController, back: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = containerViewModel { SettingsViewModel(settingsRepository, accountRepository, languageSettings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val privacyOptionsRequired by container.ads.privacyOptionsRequired.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    LaunchedEffect(state.deleted) { if (state.deleted) navController.resetTo(FirstRunRoute) }
    SettingsScreen(
        state = state,
        showPrivacyOptions = privacyOptionsRequired,
        versionName = container.config.versionName,
        contactEmail = container.config.contactEmail,
        onSettingsChange = vm::onSettingsChange,
        onLanguageChange = vm::onLanguageChange,
        onPrivacyOptions = { activity?.let { container.ads.showPrivacyOptions(it) } },
        onPrivacyPolicy = { navController.navigate(LegalRoute(LegalDocument.PRIVACY.name)) },
        onTerms = { navController.navigate(LegalRoute(LegalDocument.TERMS.name)) },
        onCopyContact = {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("email", container.config.contactEmail))
        },
        onTransferCode = vm::onTransferCode,
        onDismissCode = vm::onDismissCode,
        onDeleteRequest = vm::onDeleteRequest,
        onDeleteCancel = vm::onDeleteCancel,
        onDeleteConfirm = vm::onDeleteConfirm,
        onBack = back,
    )
}

/** Clears the whole back stack (first run after deletion, home after first run). */
private fun NavHostController.resetTo(route: Any) {
    navigate(route) { popUpTo(graph.id) { inclusive = true } }
}

private fun MatchSummary.toRoute() = ResultsRoute(
    mode = mode.name,
    outcome = outcome.name,
    reason = reason.name,
    rounds = rounds,
    opponentSecret = opponentSecret,
    digits = digits,
    level = level?.name.orEmpty(),
    teamMatch = teamMatch,
    scoreA = scoreA,
    scoreB = scoreB,
)

private fun ResultsRoute.toSummary() = MatchSummary(
    mode = MatchMode.valueOf(mode),
    outcome = Outcome.valueOf(outcome),
    reason = EndReason.valueOf(reason),
    rounds = rounds,
    opponentSecret = opponentSecret,
    digits = digits,
    level = level.takeIf { it.isNotEmpty() }?.let { ComputerLevel.valueOf(it) },
    teamMatch = teamMatch,
    scoreA = scoreA,
    scoreB = scoreB,
)
