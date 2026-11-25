package app.numbaseball.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.numbaseball.LocalAppContainer
import app.numbaseball.R
import app.numbaseball.ads.FinishedMatchKind
import app.numbaseball.ads.LocalTouchTracker
import app.numbaseball.ads.TouchTracker
import app.numbaseball.ads.rememberInterstitialGate
import app.numbaseball.ads.trackTouches
import app.numbaseball.billing.AdEntitlement
import app.numbaseball.billing.BillingEvent
import app.numbaseball.billing.LocalBilling
import app.numbaseball.billing.PurchaseUiState
import app.numbaseball.billing.RemoveAdsState
import app.numbaseball.data.online.isReviewRoom
import app.numbaseball.audio.HapticCue
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.LocalGameHaptics
import app.numbaseball.audio.SoundCue
import app.numbaseball.core.ai.ComputerLevel
import app.numbaseball.core.protocol.ChatKind
import app.numbaseball.core.protocol.ChatMessage
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.core.protocol.RoomPhase
import app.numbaseball.core.protocol.RoomSettings
import app.numbaseball.core.protocol.Team
import app.numbaseball.data.account.AccountRepository
import app.numbaseball.data.account.AccountState
import app.numbaseball.data.account.ApiResult
import app.numbaseball.data.age.AgeBand
import app.numbaseball.data.age.AgeRepository
import app.numbaseball.data.age.AgeState
import app.numbaseball.data.settings.SupportedLanguages
import app.numbaseball.ui.age.AgeScreen
import app.numbaseball.ui.age.AgeViewModel
import app.numbaseball.ui.age.NotAvailableScreen
import app.numbaseball.ui.chat.ChatActions
import app.numbaseball.ui.chat.ChatSheet
import app.numbaseball.ui.chat.ChatViewModel
import app.numbaseball.ui.chat.PHRASE_COOLDOWN_MS
import app.numbaseball.ui.chat.QuickPhraseSheet
import app.numbaseball.ui.chat.phraseText
import app.numbaseball.ui.common.containerViewModel
import app.numbaseball.ui.common.findActivity
import app.numbaseball.ui.computer.ComputerChoice
import app.numbaseball.ui.computer.ComputerSetupSheet
import app.numbaseball.ui.design.LocalAppToast
import app.numbaseball.ui.design.SheetFrame
import app.numbaseball.ui.design.ToastHost
import app.numbaseball.ui.design.ToastState
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.firstrun.FirstRunScreen
import app.numbaseball.ui.firstrun.FirstRunViewModel
import app.numbaseball.ui.home.HomeScreen
import app.numbaseball.ui.home.HomeViewModel
import app.numbaseball.ui.legal.LegalDocument
import app.numbaseball.ui.legal.LegalTextScreen
import app.numbaseball.ui.lobby.JoinScreen
import app.numbaseball.ui.lobby.JoinViewModel
import app.numbaseball.ui.lobby.LobbyActions
import app.numbaseball.ui.lobby.RoomLobbyScreen
import app.numbaseball.ui.lobby.RoomLobbyViewModel
import app.numbaseball.ui.lobby.RoomSettingsSheet
import app.numbaseball.ui.match.ComputerMatchViewModel
import app.numbaseball.ui.match.MatchActions
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchPhase
import app.numbaseball.ui.match.MatchScreen
import app.numbaseball.ui.match.MatchSummary
import app.numbaseball.ui.match.OnlineMatchViewModel
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.match.PhraseBubble
import app.numbaseball.ui.match.PracticeInfo
import app.numbaseball.ui.profile.ProfileScreen
import app.numbaseball.ui.profile.ProfileViewModel
import app.numbaseball.ui.quickmatch.QuickMatchScreen
import app.numbaseball.ui.quickmatch.QuickMatchViewModel
import app.numbaseball.ui.report.BlockDialog
import app.numbaseball.ui.report.CardRecord
import app.numbaseball.ui.report.PlayerCard
import app.numbaseball.ui.report.ReportSheet
import app.numbaseball.ui.report.ReportTarget
import app.numbaseball.ui.report.ReportViewModel
import app.numbaseball.ui.results.ResultsScreen
import app.numbaseball.ui.rules.HowToPlayScreen
import app.numbaseball.ui.tutorial.TutorialScreen
import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.Stats
import app.numbaseball.ui.settings.BlockedPlayersScreen
import app.numbaseball.ui.settings.LICENCES
import app.numbaseball.ui.settings.LicenceTextScreen
import app.numbaseball.ui.settings.LicencesScreen
import app.numbaseball.ui.settings.SettingsActions
import app.numbaseball.ui.settings.SettingsScreen
import app.numbaseball.ui.settings.SettingsViewModel
import app.numbaseball.ui.team.BoardMode
import app.numbaseball.ui.team.TeamBoardScreen
import app.numbaseball.ui.terms.TermsGate
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbLayout
import app.numbaseball.ui.theme.NbMotion
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App entry: the neutral age screen comes first (before any ad SDK call or account), then ads consent, then first run
 * (no nickname yet) or home. Windows smaller than 320 × 320 dp get a notice over the (still running) app (ADAPTIVE.md).
 */
@Composable
fun NbApp(ageRepository: AgeRepository, accountRepository: AccountRepository) {
    val toast = remember { ToastState() }
    val touches = remember { TouchTracker() }
    CompositionLocalProvider(LocalAppToast provides toast, LocalTouchTracker provides touches) {
        BillingToasts()
        BoxWithConstraints(Modifier.fillMaxSize().background(NbColors.bg).trackTouches(touches)) {
            AppContent(ageRepository, accountRepository)
            ToastHost(toast)
            if (maxWidth < NbLayout.minWindowDp.dp || maxHeight < NbLayout.minWindowDp.dp) WindowTooSmall()
        }
    }
}

@Composable
private fun AppContent(ageRepository: AgeRepository, accountRepository: AccountRepository) {
    val age by ageRepository.state.collectAsStateWithLifecycle()
    val account by accountRepository.state.collectAsStateWithLifecycle()
    when (val current = age) {
        AgeState.Loading -> Blank()
        AgeState.Unknown -> {
            val vm = containerViewModel { AgeViewModel(ageRepository) }
            val state by vm.state.collectAsStateWithLifecycle()
            AgeScreen(state, vm::onDigit, vm::onDelete, vm::onClear, vm::onConfirm)
        }
        is AgeState.Known -> if (current.band == AgeBand.UNDER_MINIMUM) {
            var privacy by rememberSaveable { mutableStateOf(false) }
            if (privacy) {
                LegalTextScreen(LegalDocument.PRIVACY, onBack = { privacy = false })
            } else {
                NotAvailableScreen(onPrivacyPolicy = { privacy = true })
            }
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

/** ADAPTIVE.md: below 320 × 320 dp a calm notice covers the app; the game keeps its state underneath. */
@Composable
internal fun WindowTooSmall() {
    Box(Modifier.fillMaxSize().background(NbColors.bg).padding(16.dp), contentAlignment = Alignment.Center) {
        // DESIGN_SYSTEM 4.1 (D2-47): brand mark, `title`, then the `caption` line.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            app.numbaseball.ui.design.BrandMark()
            VSpacer(16.dp)
            Text(stringResource(R.string.window_too_small_title), style = NbType.title, color = NbColors.chalk, textAlign = TextAlign.Center)
            VSpacer(8.dp)
            Text(stringResource(R.string.window_too_small_body), style = NbType.caption, color = NbColors.text3, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Starts UMP + Mobile Ads only now that the age treatment is known, and refines it with Play Age Signals. Waits for the
 * remove-ads entitlement (cache or Play, at most a few seconds): an owner never gets the UMP form or a Mobile Ads init
 * (POLICY_REVIEW Android item 13). If the purchase disappears later (refund), consent starts then.
 */
@Composable
private fun AdsAfterAgeCheck(age: AgeState.Known) {
    val container = LocalAppContainer.current
    val activity = LocalContext.current.findActivity()
    val entitlement by container.billing.entitlement.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.ageRepository.refreshSignals() }
    LaunchedEffect(entitlement) {
        if (entitlement == AdEntitlement.ADS_ALLOWED) activity?.let { container.ads.gatherConsent(it, age.adTreatment) }
    }
    LaunchedEffect(age.adTreatment) { container.ads.updateAgeTreatment(age.adTreatment) }
}

/** Toasts for purchase and restore results (11 §1.1, 06 §1), wherever the player started them. */
@Composable
private fun BillingToasts() {
    val billing = LocalBilling.current
    val toast = LocalAppToast.current
    val resources = LocalResources.current
    LaunchedEffect(billing) {
        billing.events.collect { event ->
            val (text, error) = when (event) {
                BillingEvent.PURCHASED -> R.string.ads_removed_toast to false
                BillingEvent.PENDING -> R.string.purchase_pending_toast to false
                BillingEvent.PURCHASE_FAILED -> R.string.purchase_failed_toast to true
                BillingEvent.RESTORED -> R.string.restore_done_toast to false
                BillingEvent.NOTHING_TO_RESTORE -> R.string.restore_none_toast to false
                BillingEvent.RESTORE_FAILED -> R.string.restore_failed_toast to true
            }
            toast.show(resources.getString(text), error = error)
        }
    }
}

/** Sheets hosted by dialog destinations draw their own scrim and reach behind the system bars. */
private val SheetDialog = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

@Composable
private fun NoWindowDim() {
    val view = LocalView.current
    LaunchedEffect(view) { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
}

/**
 * `nav_forward` / `nav_back` (DESIGN_SYSTEM 8.3 E, 10): `sfx_ui_navigate` on a screen push, `sfx_ui_back` on a pop. Not
 * for sheets (dialog destinations) and not into or out of a match, where the music crossfade covers the change.
 */
@Composable
private fun NavSounds(navController: NavHostController) {
    val audio = LocalGameAudio.current
    LaunchedEffect(navController) {
        var previous: NavBackStackEntry? = null
        navController.currentBackStackEntryFlow.collect { entry ->
            val before = previous
            previous = entry
            if (before == null || before.id == entry.id) return@collect
            val sheet = entry.destination is DialogNavigator.Destination || before.destination is DialogNavigator.Destination
            fun NavDestination.isMatch() = hasRoute<ComputerMatchRoute>() || hasRoute<OnlineMatchRoute>()
            if (sheet || entry.destination.isMatch() || before.destination.isMatch()) return@collect
            val forward = navController.previousBackStackEntry?.id == before.id
            audio.play(if (forward) SoundCue.NAV_FORWARD else SoundCue.NAV_BACK)
        }
    }
}

@Composable
fun NbNavHost(startOnFirstRun: Boolean, navController: NavHostController = rememberNavController()) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val toast = LocalAppToast.current
    val scope = rememberCoroutineScope()
    val back: () -> Unit = { navController.popBackStack() }
    val readTerms: () -> Unit = { navController.navigate(LegalRoute(LegalDocument.TERMS.name)) }
    val readPrivacy: () -> Unit = { navController.navigate(LegalRoute(LegalDocument.PRIVACY.name)) }
    val home: () -> Unit = { navController.navigate(HomeRoute) { popUpTo<HomeRoute> { inclusive = false } } }
    val copy: (String, String) -> Unit = { label, text ->
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
    // Online results feed Home's "last 10" (no server endpoint for the recent form).
    val recordResult: (MatchSummary) -> Unit = { summary ->
        if (summary.mode.online && summary.reason != EndReason.CANCELLED) {
            val letter = when (summary.outcome) {
                Outcome.WIN -> 'W'
                Outcome.LOSE -> 'L'
                Outcome.DRAW -> 'D'
            }
            scope.launch { container.settingsRepository.update { it.copy(recentForm = (it.recentForm + letter).takeLast(10)) } }
        }
    }

    NavSounds(navController)
    NavHost(navController, startDestination = if (startOnFirstRun) FirstRunRoute else HomeRoute) {
        composable<FirstRunRoute> {
            val vm = containerViewModel { FirstRunViewModel(accountRepository) }
            val state by vm.state.collectAsStateWithLifecycle()
            // First run: age → nickname → tutorial → Home (FLOW, 01 §2).
            LaunchedEffect(state.done) { if (state.done) navController.resetTo(TutorialRoute(firstRun = true)) }
            FirstRunScreen(state, vm::onNicknameChange, vm::onTransferCodeChange, vm::onTransferModeChange, vm::onSubmit, readPrivacy)
        }

        composable<HomeRoute> { HomeDestination(navController) }

        composable<HowToPlayRoute> { HowToPlayScreen(onBack = back) }

        composable<TutorialRoute> { entry ->
            val route = entry.toRoute<TutorialRoute>()
            TutorialScreen(onFinish = { if (route.firstRun) navController.resetTo(HomeRoute) else back() })
        }

        composable<ComputerMatchRoute> { entry ->
            val route = entry.toRoute<ComputerMatchRoute>()
            val vm = containerViewModel {
                ComputerMatchViewModel(route.digits, ComputerLevel.valueOf(route.level), route.practice, offlineGame, roundSeconds = route.roundSeconds)
            }
            val state by vm.state.collectAsStateWithLifecycle()
            val room by container.onlineSession.room.collectAsStateWithLifecycle()
            val gate = rememberInterstitialGate()
            if (route.practice) SwitchToRealMatchWhenStarted(navController)
            val practice = if (route.practice) {
                room?.let { r -> PracticeInfo(r.code.orEmpty(), r.players.count { !it.left }, r.settings.teamSize * 2) }
            } else {
                null
            }
            MatchScreen(
                state = state.copy(practice = practice),
                actions = MatchActions(
                    onDigit = vm::onDigit,
                    onDelete = vm::onDelete,
                    onClear = vm::onClear,
                    onTapSlot = vm::onTapSlot,
                    onSubmit = vm::onSubmit,
                    onRandomSecret = vm::onRandomSecret,
                    onForfeit = vm::onForfeit,
                    onLeave = back,
                    onMemoDigit = vm::onMemoDigit,
                    onMemoCell = vm::onMemoCell,
                    onMemoClear = vm::onMemoClear,
                    onMemoCollapse = vm::onMemoCollapse,
                    onHowToPlay = { navController.navigate(HowToPlayRoute) },
                    onToRoom = if (route.practice) back else null,
                    onFinished = { summary ->
                        // 06 §3: the interstitial (if due) sits between the match end and the results; never for practice.
                        gate.run(if (route.practice) FinishedMatchKind.PRACTICE else FinishedMatchKind.COMPUTER, summary.reason) { adShown ->
                            navController.navigate(summary.toRoute(afterAd = adShown)) { popUpTo<ComputerMatchRoute> { inclusive = true } }
                        }
                    },
                ),
            )
        }

        composable<QuickMatchRoute> {
            TermsGate(onClose = back, onReadTerms = readTerms, onReadPrivacy = readPrivacy) {
                val vm = containerViewModel { QuickMatchViewModel(onlineSession, settingsRepository, network) }
                val state by vm.state.collectAsStateWithLifecycle()
                val audio = LocalGameAudio.current
                val haptics = LocalGameHaptics.current
                val stopped = stringResource(R.string.search_stopped)
                LaunchedEffect(state.found) {
                    if (state.found != null) {
                        audio.play(SoundCue.MATCH_FOUND)
                        haptics.perform(HapticCue.MATCH_FOUND)
                        delay(NbMotion.matchFound.toLong())
                        navController.navigate(OnlineMatchRoute) { popUpTo<QuickMatchRoute> { inclusive = true } }
                    }
                }
                LaunchedEffect(state.stopped) {
                    if (state.stopped) {
                        toast.show(stopped, error = true)
                        vm.onCancel()
                        home()
                    }
                }
                val cancel: () -> Unit = {
                    vm.onCancel()
                    back()
                }
                BackHandler(onBack = cancel)
                QuickMatchScreen(
                    state = state,
                    onDigitsChange = vm::onDigitsChange,
                    onCancel = cancel,
                    onVsComputer = {
                        vm.onCancel()
                        scope.launch {
                            val prefs = container.settingsRepository.settings.first()
                            navController.navigate(ComputerMatchRoute(state.digits, prefs.cpuLevel, roundSeconds = prefs.cpuRoundSeconds)) {
                                popUpTo<HomeRoute> { inclusive = false }
                            }
                        }
                    },
                )
            }
        }

        composable<JoinRoute> {
            TermsGate(onClose = back, onReadTerms = readTerms, onReadPrivacy = readPrivacy) {
                val vm = containerViewModel { JoinViewModel(onlineSession) }
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.joined) {
                    if (state.joined) navController.navigate(RoomLobbyRoute()) { popUpTo<JoinRoute> { inclusive = true } }
                }
                JoinScreen(state, vm::onCodeChange, vm::onJoin, back, vm::onErrorShown)
            }
        }

        composable<RoomLobbyRoute> { entry ->
            val route = entry.toRoute<RoomLobbyRoute>()
            TermsGate(onClose = back, onReadTerms = readTerms, onReadPrivacy = readPrivacy) {
                LobbyDestination(route, navController, home, copy)
            }
        }

        composable<OnlineMatchRoute> { OnlineMatchDestination(navController, home, recordResult) }

        composable<TeamBoardRoute> { entry ->
            val route = entry.toRoute<TeamBoardRoute>()
            val mode = BoardMode.valueOf(route.mode)
            val room by container.onlineSession.room.collectAsStateWithLifecycle()
            val current = room
            val gate = rememberInterstitialGate()
            val billingState by container.billing.state.collectAsStateWithLifecycle()
            // On the live board the team result appears when the last duel ends (06 §4), after the interstitial if one
            // is due (06 §3: one per team match, at its very end, never on the live board).
            var finalReached by rememberSaveable { mutableStateOf(false) }
            var afterAd by rememberSaveable { mutableStateOf(route.afterAd) }
            val matchResult = current?.match?.result
            LaunchedEffect(matchResult != null) {
                if (mode == BoardMode.LIVE && matchResult != null && !finalReached) {
                    val review = current.isReviewRoom
                    gate.run(
                        if (review) FinishedMatchKind.REVIEW_ROOM else FinishedMatchKind.TEAM_MATCH,
                        null,
                        chatOpen = { navController.currentDestination?.hasRoute<ChatRoute>() == true },
                    ) { adShown ->
                        afterAd = adShown
                        finalReached = true
                    }
                }
            }
            if (current?.match == null) {
                LaunchedEffect(Unit) { home() }
                return@composable
            }
            val shown = if (mode == BoardMode.LIVE && finalReached) BoardMode.FINAL else mode
            val backToRoom: () -> Unit = {
                val code = current.code
                if (code != null) {
                    navController.navigate(RoomLobbyRoute(joinCode = code)) { popUpTo<HomeRoute> { inclusive = false } }
                } else {
                    home()
                }
            }
            val offlineSince by container.onlineSession.offlineSince.collectAsStateWithLifecycle()
            TeamBoardScreen(
                room = current,
                mode = shown,
                offlineSince = offlineSince,
                serverNow = container.onlineSession::serverNow,
                onBack = back,
                onLeave = {
                    container.onlineSession.leaveRoom()
                    home()
                },
                onChat = { navController.navigate(ChatRoute) },
                onBackToRoom = backToRoom,
                onHome = {
                    container.onlineSession.leaveRoom()
                    home()
                },
                showBanner = !afterAd,
                onRemoveAds = removeAdsAction(billingState, review = current.isReviewRoom),
            )
        }

        composable<ResultsRoute> { entry ->
            val route = entry.toRoute<ResultsRoute>()
            ResultsDestination(route.toSummary(), afterAd = route.afterAd, reviewRoom = route.reviewRoom, navController, home)
        }

        dialog<ChatRoute>(dialogProperties = SheetDialog) {
            NoWindowDim()
            TermsGate(onClose = back, onReadTerms = readTerms, onReadPrivacy = readPrivacy) {
                ChatDestination(navController, copy)
            }
        }

        dialog<PlayerCardRoute>(dialogProperties = SheetDialog) { entry ->
            NoWindowDim()
            val route = entry.toRoute<PlayerCardRoute>()
            PlayerCardDestination(route, navController)
        }

        dialog<ReportRoute>(dialogProperties = SheetDialog) { entry ->
            NoWindowDim()
            val route = entry.toRoute<ReportRoute>()
            val target = ReportTarget(
                playerId = route.playerId,
                nickname = route.nickname,
                tag = route.tag,
                roomId = route.roomId.ifEmpty { null },
                messageId = route.messageId.takeIf { it >= 0 },
                messageText = route.messageText.ifEmpty { null },
            )
            val vm = containerViewModel { ReportViewModel(target, safety) }
            val state by vm.state.collectAsStateWithLifecycle()
            SheetFrame(onDismiss = back) {
                ReportSheet(state, vm::onChoice, vm::onNoteChange, vm::onSubmit, vm::onBlock, back)
            }
        }

        composable<ProfileRoute> {
            val vm = containerViewModel { ProfileViewModel(accountRepository, settingsRepository, network) }
            val state by vm.state.collectAsStateWithLifecycle()
            ProfileScreen(state, vm::onRename, vm::onRenameShown, back)
        }

        composable<SettingsRoute> { SettingsDestination(navController, back, copy) }

        composable<BlockedPlayersRoute> {
            val blocked by container.safety.blocked.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { container.safety.loadBlocked() }
            BlockedPlayersScreen(
                blocked = blocked,
                onUnblock = { scope.launch { container.safety.unblock(it.id) } },
                onUndo = { scope.launch { container.safety.block(it.id, it.nickname, it.tag) } },
                onBack = back,
            )
        }

        composable<LicencesRoute> {
            LicencesScreen(onOpen = { navController.navigate(LicenceTextRoute(LICENCES.indexOf(it))) }, onBack = back)
        }

        composable<LicenceTextRoute> { entry ->
            LicenceTextScreen(LICENCES[entry.toRoute<LicenceTextRoute>().index], onBack = back)
        }

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
    }
}

@Composable
private fun HomeDestination(navController: NavHostController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val vm = containerViewModel { HomeViewModel(accountRepository, ageRepository, settingsRepository, network) }
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    var cpuSheet by rememberSaveable { mutableStateOf(false) }
    var roomSheet by rememberSaveable { mutableStateOf(false) }
    HomeScreen(
        state = state,
        onQuickDigits = vm::onQuickDigits,
        onQuickMatch = { navController.navigate(QuickMatchRoute) },
        onVsComputer = { cpuSheet = true },
        onCreateRoom = { roomSheet = true },
        onJoinRoom = { navController.navigate(JoinRoute) },
        // Home's help replays the interactive tutorial (02, 01 §2); matches and the lobby keep the rules page.
        onHowToPlay = { navController.navigate(TutorialRoute()) },
        onProfile = { navController.navigate(ProfileRoute) },
        onSettings = { navController.navigate(SettingsRoute) },
        onChooseNickname = { navController.resetTo(FirstRunRoute) },
    )
    val p = prefs ?: return
    if (cpuSheet) {
        val choice = ComputerChoice(p.cpuDigits, runCatching { ComputerLevel.valueOf(p.cpuLevel) }.getOrDefault(ComputerLevel.NORMAL), p.cpuRoundSeconds)
        ComputerSetupSheet(
            choice = choice,
            onChange = { next ->
                scope.launch {
                    container.settingsRepository.update { it.copy(cpuDigits = next.digits, cpuLevel = next.level.name, cpuRoundSeconds = next.roundSeconds) }
                }
            },
            onStart = {
                cpuSheet = false
                navController.navigate(ComputerMatchRoute(choice.digits, choice.level.name, roundSeconds = choice.roundSeconds))
            },
            onDismiss = { cpuSheet = false },
        )
    }
    if (roomSheet) {
        val settings = RoomSettings(p.roomDigits, p.roomTeamSize, p.roomRoundSeconds)
        RoomSettingsSheet(
            title = stringResource(R.string.create_title),
            actionLabel = stringResource(R.string.room_title),
            settings = settings,
            onChange = { next ->
                scope.launch {
                    container.settingsRepository.update { it.copy(roomDigits = next.digits, roomTeamSize = next.teamSize, roomRoundSeconds = next.roundSeconds) }
                }
            },
            onConfirm = {
                roomSheet = false
                navController.navigate(RoomLobbyRoute(digits = settings.digits, teamSize = settings.teamSize, roundSeconds = settings.roundSeconds))
            },
            onDismiss = { roomSheet = false },
        )
    }
}

@Composable
private fun LobbyDestination(route: RoomLobbyRoute, navController: NavHostController, home: () -> Unit, copy: (String, String) -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val create = route.takeIf { it.digits > 0 }?.let { RoomSettings(it.digits, it.teamSize, it.roundSeconds) }
    val vm = containerViewModel { RoomLobbyViewModel(create, route.joinCode, onlineSession, settingsRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    ChatCue(prefs?.chatMuted ?: true)
    LaunchedEffect(vm) { vm.matchStarted.collect { navController.navigate(OnlineMatchRoute) { launchSingleTop = true } } }
    val appName = stringResource(R.string.app_name_main)
    RoomLobbyScreen(
        state = state,
        actions = LobbyActions(
            onBack = { navController.popBackStack() },
            onLeave = {
                vm.onLeave()
                home()
            },
            onCopy = { copy("code", it) },
            onShare = { code ->
                val text = resources.getString(R.string.share_text, appName, code)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, null))
            },
            onSettingsChange = vm::onSettingsChange,
            onMove = vm::onMove,
            onPractice = {
                state.room?.let { navController.navigate(ComputerMatchRoute(it.settings.digits, ComputerLevel.NORMAL.name, practice = true)) }
            },
            onOpenMatch = { navController.navigate(OnlineMatchRoute) { launchSingleTop = true } },
            onChat = { navController.navigate(ChatRoute) },
            onHowToPlay = { navController.navigate(HowToPlayRoute) },
            onPlayerCard = { player ->
                val room = state.room
                navController.navigate(
                    PlayerCardRoute(
                        player.id,
                        player.nickname,
                        player.tag,
                        team = if ((room?.settings?.teamSize ?: 1) > 1) player.team.name else "",
                        roomId = room?.id.orEmpty(),
                    ),
                )
            },
            onRetryConnection = vm::onRetryConnection,
            onErrorShown = vm::onErrorShown,
        ),
    )
}

@Composable
private fun OnlineMatchDestination(navController: NavHostController, home: () -> Unit, recordResult: (MatchSummary) -> Unit) {
    val container = LocalAppContainer.current
    val session = container.onlineSession
    val vm = containerViewModel { OnlineMatchViewModel(onlineSession) }
    val state by vm.state.collectAsStateWithLifecycle()
    val room by session.room.collectAsStateWithLifecycle()
    val chat by session.chat.collectAsStateWithLifecycle()
    val prefs by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val muted = prefs?.chatMuted ?: true
    val scope = rememberCoroutineScope()
    var phraseSheet by rememberSaveable { mutableStateOf(false) }
    var lastPhraseAt by remember { mutableStateOf(0L) }
    var bubble by remember { mutableStateOf<PhraseBubble?>(null) }
    val gate = rememberInterstitialGate()
    val chatOpen = { phraseSheet || navController.currentDestination?.hasRoute<ChatRoute>() == true }
    val current = state
    if (current == null) {
        LaunchedEffect(Unit) { home() }
        return
    }
    val quick = current.mode == MatchMode.QUICK
    if (!quick) ChatCue(muted)
    // Quick phrases appear as bubbles under the duel strip for 2.8 s (05 §2.8); hidden while chat is muted.
    val latestPhrase = chat.lastOrNull { it.kind == ChatKind.PHRASE }
    val phraseLabel = latestPhrase?.phraseId?.let { phraseText(it) }
    val me = stringResource(R.string.tag_me)
    LaunchedEffect(latestPhrase?.id, muted) {
        if (latestPhrase == null || muted || !quick || phraseLabel == null) return@LaunchedEffect
        val sender = if (latestPhrase.senderId == room?.youId) me else latestPhrase.senderNickname.orEmpty()
        bubble = PhraseBubble(latestPhrase.id, sender, phraseLabel)
        delay(2_800)
        bubble = null
    }
    // My duel is over but the team match is not: the board replaces the match screen (08 §1).
    LaunchedEffect(current.phase, current.summary) {
        if (current.mode == MatchMode.TEAM && current.phase == MatchPhase.FINISHED && current.summary == null) {
            navController.navigate(TeamBoardRoute(BoardMode.LIVE.name)) { popUpTo<OnlineMatchRoute> { inclusive = true } }
        }
    }
    val opponentCard: (() -> Unit)? = room?.let { r ->
        val duel = r.match?.myDuel ?: return@let null
        val opponent = r.players.firstOrNull { it.id == duel.opponentId } ?: return@let null
        {
            navController.navigate(
                PlayerCardRoute(opponent.id, opponent.nickname, opponent.tag, team = if (r.settings.teamSize > 1) opponent.team.name else "", roomId = r.id, quick = r.kind == RoomKind.QUICK),
            )
        }
    }
    MatchScreen(
        state = current,
        bubble = bubble,
        actions = MatchActions(
            onDigit = vm::onDigit,
            onDelete = vm::onDelete,
            onClear = vm::onClear,
            onTapSlot = vm::onTapSlot,
            onSubmit = vm::onSubmit,
            onRandomSecret = vm::onRandomSecret,
            onForfeit = vm::onForfeit,
            onLeave = {
                session.leaveRoom()
                home()
            },
            onMemoDigit = vm::onMemoDigit,
            onMemoCell = vm::onMemoCell,
            onMemoClear = vm::onMemoClear,
            onMemoCollapse = vm::onMemoCollapse,
            onChat = if (quick) ({ phraseSheet = true }) else ({ navController.navigate(ChatRoute) }),
            onReport = opponentCard,
            onHowToPlay = { navController.navigate(HowToPlayRoute) },
            onTeamBoard = if (current.mode == MatchMode.TEAM) ({ navController.navigate(TeamBoardRoute(BoardMode.OVERLAY.name)) }) else null,
            onFinished = { summary ->
                recordResult(summary)
                val finishedRoom = room
                val review = finishedRoom?.isReviewRoom == true
                val number = finishedRoom?.match?.number
                val kind = when {
                    review -> FinishedMatchKind.REVIEW_ROOM
                    summary.teamMatch -> FinishedMatchKind.TEAM_MATCH
                    else -> FinishedMatchKind.ONLINE_DUEL
                }
                // 06 §3: after the whole match only (a team match reaches this when my duel was the last one), never when
                // a rematch already started, with chat or keyboard open, or after the player left this screen.
                gate.run(
                    kind,
                    summary.reason,
                    chatOpen = chatOpen,
                    nextMatchStarting = { session.room.value?.match.let { it != null && it.number != number && it.result == null } },
                ) { adShown ->
                    if (summary.teamMatch) {
                        navController.navigate(TeamBoardRoute(BoardMode.FINAL.name, afterAd = adShown)) { popUpTo<OnlineMatchRoute> { inclusive = true } }
                    } else {
                        navController.navigate(summary.toRoute(afterAd = adShown, reviewRoom = review)) { popUpTo<OnlineMatchRoute> { inclusive = true } }
                    }
                }
            },
        ),
    )
    if (phraseSheet) {
        val now = SystemClock.elapsedRealtime()
        QuickPhraseSheet(
            enabled = now - lastPhraseAt >= PHRASE_COOLDOWN_MS,
            onPhrase = { id ->
                phraseSheet = false
                lastPhraseAt = SystemClock.elapsedRealtime()
                container.audio.play(SoundCue.QUICK_PHRASE)
                scope.launch { session.sendPhrase(id) }
            },
            onDismiss = { phraseSheet = false },
        )
    }
}

/** `chat_message` cue for new lines from others while the chat sheet is closed (09 §5); the audio layer caps it at one per 2 s. */
@Composable
private fun ChatCue(muted: Boolean) {
    val container = LocalAppContainer.current
    val chat by container.onlineSession.chat.collectAsStateWithLifecycle()
    val room by container.onlineSession.room.collectAsStateWithLifecycle()
    var seen by remember { mutableStateOf(chat.lastOrNull()?.id ?: -1L) }
    val last = chat.lastOrNull()
    LaunchedEffect(last?.id) {
        if (last == null) return@LaunchedEffect
        // System lines never play `chat_message` (09 §2.1).
        if (last.id > seen && !muted && last.kind != ChatKind.SYSTEM && last.senderId != room?.youId) container.audio.play(SoundCue.CHAT_MESSAGE)
        seen = maxOf(seen, last.id)
    }
}

@Composable
private fun ChatDestination(navController: NavHostController, copy: (String, String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val toast = LocalAppToast.current
    val vm = containerViewModel { ChatViewModel(onlineSession, settingsRepository, accountRepository, accountRepository.serverInfo, safety.blocked) }
    val state by vm.state.collectAsStateWithLifecycle()
    var blockTarget by remember { mutableStateOf<ChatMessage?>(null) }
    val blocked = stringResource(R.string.blocked_toast)
    val undo = stringResource(R.string.action_undo)
    val close: () -> Unit = { navController.popBackStack() }
    SheetFrame(onDismiss = close, top = 136.dp) {
        ChatSheet(
            state = state,
            actions = ChatActions(
                onInputChange = vm::onInputChange,
                onSend = vm::onSend,
                onPhrase = { id ->
                    container.audio.play(SoundCue.QUICK_PHRASE)
                    vm.onPhrase(id)
                },
                onMuteChange = vm::onMuteChange,
                onClose = close,
                onCopy = { copy("message", it) },
                onReportMessage = { message ->
                    val senderId = message.senderId ?: return@ChatActions
                    navController.navigate(
                        ReportRoute(
                            senderId,
                            message.senderNickname.orEmpty(),
                            message.senderTag.orEmpty(),
                            roomId = state.roomId.orEmpty(),
                            messageId = message.id,
                            messageText = message.text.orEmpty(),
                        ),
                    )
                },
                onBlock = { blockTarget = it },
                onPlayerCard = { message ->
                    val senderId = message.senderId ?: return@ChatActions
                    navController.navigate(
                        PlayerCardRoute(
                            senderId,
                            message.senderNickname.orEmpty(),
                            message.senderTag.orEmpty(),
                            team = if (state.teamRoom) message.senderTeam?.name.orEmpty() else "",
                            roomId = state.roomId.orEmpty(),
                            self = senderId == state.myId,
                        ),
                    )
                },
            ),
        )
    }
    blockTarget?.let { message ->
        val id = message.senderId ?: return@let
        val name = "${message.senderNickname.orEmpty()}#${message.senderTag.orEmpty()}"
        BlockDialog(
            name = name,
            onConfirm = {
                blockTarget = null
                scope.launch {
                    if (container.safety.block(id, message.senderNickname.orEmpty(), message.senderTag.orEmpty()) is ApiResult.Success) {
                        toast.show(blocked, undo) { scope.launch { container.safety.unblock(id) } }
                    }
                }
            },
            onDismiss = { blockTarget = null },
        )
    }
}

@Composable
private fun PlayerCardDestination(route: PlayerCardRoute, navController: NavHostController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val toast = LocalAppToast.current
    val blockedList by container.safety.blocked.collectAsStateWithLifecycle()
    var confirmBlock by remember { mutableStateOf(false) }
    val target = ReportTarget(route.playerId, route.nickname, route.tag, team = route.team.takeIf { it.isNotEmpty() }?.let { Team.valueOf(it) }, roomId = route.roomId.ifEmpty { null })
    val blockedText = stringResource(R.string.blocked_toast)
    val undo = stringResource(R.string.action_undo)
    val close: () -> Unit = { navController.popBackStack() }
    // The record comes from `GET /api/v1/players/{id}` (protocol 1.1), fetched when the card opens and kept for its
    // lifetime; 404 = unknown, deleted, banned, bot or blocked (12 §2).
    var record by remember { mutableStateOf<CardRecord>(CardRecord.Loading) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(route.playerId, attempt) {
        record = CardRecord.Loading
        record = when (val r = container.safety.playerRecord(route.playerId)) {
            is ApiResult.Success -> CardRecord.Loaded(r.value)
            is ApiResult.Failure -> if (r.code == ErrorCodes.NOT_FOUND) CardRecord.NotAvailable else CardRecord.Failed
            ApiResult.NetworkError -> CardRecord.Failed
        }
    }
    SheetFrame(onDismiss = close) {
        PlayerCard(
            record = record,
            onRetryRecord = { attempt++ },
            target = target,
            self = route.self,
            blocked = blockedList.any { it.id == route.playerId },
            onReport = {
                navController.navigate(ReportRoute(route.playerId, route.nickname, route.tag, roomId = route.roomId)) {
                    popUpTo<PlayerCardRoute> { inclusive = true }
                }
            },
            onBlock = { confirmBlock = true },
            onUnblock = { scope.launch { container.safety.unblock(route.playerId) } },
        )
    }
    if (confirmBlock) {
        BlockDialog(
            name = target.displayName,
            onConfirm = {
                confirmBlock = false
                scope.launch {
                    if (container.safety.block(route.playerId, route.nickname, route.tag) is ApiResult.Success) {
                        toast.show(blockedText, undo) { scope.launch { container.safety.unblock(route.playerId) } }
                        close()
                    }
                }
            },
            onDismiss = { confirmBlock = false },
        )
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

/**
 * "광고 제거" on the result screens (06 §1): only while Play offers the product (not owned, not pending, billing
 * answered), never in the review room. Tapping opens Play's purchase sheet directly; the result stays underneath.
 */
@Composable
private fun removeAdsAction(state: PurchaseUiState, review: Boolean): (() -> Unit)? {
    val billing = LocalBilling.current
    val activity = LocalContext.current.findActivity()
    if (review || state.removeAds !is RemoveAdsState.Available || activity == null) return null
    return { billing.purchase(activity) }
}

@Composable
private fun ResultsDestination(summary: MatchSummary, afterAd: Boolean, reviewRoom: Boolean, navController: NavHostController, home: () -> Unit) {
    val container = LocalAppContainer.current
    val session = container.onlineSession
    val billingState by container.billing.state.collectAsStateWithLifecycle()
    // The interstitial, if any, came before this screen (06 §3); its buttons ("다시 하기", "홈으로", "방으로 돌아가기")
    // never trigger one, so a rematch or the next duel never waits behind an ad.
    if (summary.mode == MatchMode.PRACTICE) SwitchToRealMatchWhenStarted(navController)
    val room by session.room.collectAsStateWithLifecycle()
    val goHome: () -> Unit = {
        if (summary.mode.online) session.leaveRoom()
        home()
    }
    BackHandler(onBack = goHome)
    val rematch: (() -> Unit)? = when (summary.mode) {
        MatchMode.COMPUTER, MatchMode.PRACTICE -> {
            {
                val level = (summary.level ?: ComputerLevel.NORMAL).name
                navController.navigate(ComputerMatchRoute(summary.digits, level, practice = summary.mode == MatchMode.PRACTICE, roundSeconds = summary.roundSeconds ?: 600)) {
                    popUpTo<ResultsRoute> { inclusive = true }
                }
            }
        }
        MatchMode.QUICK -> {
            {
                session.leaveRoom()
                navController.navigate(QuickMatchRoute) { popUpTo<HomeRoute> { inclusive = false } }
            }
        }
        else -> null
    }
    val roomCode = room?.code
    val backToRoom: (() -> Unit)? = when {
        summary.mode == MatchMode.PRACTICE -> {
            { navController.popBackStack() }
        }
        summary.mode == MatchMode.ROOM && roomCode != null -> {
            { navController.navigate(RoomLobbyRoute(joinCode = roomCode)) { popUpTo<HomeRoute> { inclusive = false } } }
        }
        else -> null
    }
    val report: (() -> Unit)? = if (summary.mode.online) {
        room?.let { r ->
            val opponent = r.match?.myDuel?.opponentId?.let { id -> r.players.firstOrNull { it.id == id } } ?: return@let null
            { navController.navigate(PlayerCardRoute(opponent.id, opponent.nickname, opponent.tag, roomId = r.id, quick = r.kind == RoomKind.QUICK)) }
        }
    } else {
        null
    }
    ResultsScreen(
        summary = summary,
        showBanner = !afterAd,
        onRemoveAds = removeAdsAction(billingState, review = reviewRoom || summary.mode == MatchMode.PRACTICE),
        onRematch = rematch,
        onHome = goHome,
        onBackToRoom = backToRoom,
        onReport = report,
    )
}

@Composable
private fun SettingsDestination(navController: NavHostController, back: () -> Unit, copy: (String, String) -> Unit) {
    val container = LocalAppContainer.current
    val toast = LocalAppToast.current
    val vm = containerViewModel { SettingsViewModel(settingsRepository, accountRepository, languageSettings, safety) }
    val state by vm.state.collectAsStateWithLifecycle()
    val privacyOptionsRequired by container.ads.privacyOptionsRequired.collectAsStateWithLifecycle()
    val purchase by container.billing.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val deletedText = stringResource(R.string.account_deleted)
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            toast.show(deletedText)
            navController.resetTo(FirstRunRoute)
        }
    }
    SettingsScreen(
        state = state,
        // UMP's REQUIRED status (11 §1.1). A `remove_ads` owner never runs UMP, so the row stays hidden for them unless
        // consent was gathered earlier in this process (then they can still change it).
        showPrivacyOptions = privacyOptionsRequired,
        purchase = purchase,
        versionName = container.config.versionName,
        contactEmail = container.config.contactEmail,
        languageTags = SupportedLanguages.TAGS,
        actions = SettingsActions(
            onBack = back,
            onSettingsChange = vm::onSettingsChange,
            onLanguageChange = vm::onLanguageChange,
            onBlocked = { navController.navigate(BlockedPlayersRoute) },
            onRename = vm::onRename,
            onRenameShown = vm::onRenameShown,
            onTransferCode = vm::onTransferCode,
            onDismissCode = vm::onDismissCode,
            onCopy = copy,
            onDeleteConfirm = vm::onDeleteConfirm,
            onPrivacyOptions = { activity?.let { container.ads.showPrivacyOptions(it) } },
            onPrivacyPolicy = { navController.navigate(LegalRoute(LegalDocument.PRIVACY.name)) },
            onTerms = { navController.navigate(LegalRoute(LegalDocument.TERMS.name)) },
            onLicences = { navController.navigate(LicencesRoute) },
            onErrorShown = vm::onErrorShown,
            onRemoveAds = { activity?.let { container.billing.purchase(it) } },
            onRetryBilling = container.billing::retry,
            onRestorePurchase = container.billing::restore,
        ),
    )
}

/** Clears the whole back stack (first run after deletion, home after first run). */
private fun NavHostController.resetTo(route: Any) {
    navigate(route) { popUpTo(graph.id) { inclusive = true } }
}

private fun strikesText(strikes: List<Int?>) = strikes.joinToString(",") { it?.toString() ?: "-" }

private fun strikesOf(text: String): List<Int?> = if (text.isEmpty()) emptyList() else text.split(",").map { it.toIntOrNull() }

private fun MatchSummary.toRoute(afterAd: Boolean = false, reviewRoom: Boolean = false) = ResultsRoute(
    mode = mode.name,
    outcome = outcome.name,
    reason = reason.name,
    rounds = rounds,
    opponentSecret = opponentSecret,
    digits = digits,
    level = level?.name.orEmpty(),
    mySecret = mySecret,
    opponentName = opponentName,
    myStrikes = strikesText(myStrikes),
    opponentStrikes = strikesText(opponentStrikes),
    durationSeconds = durationSeconds ?: 0,
    roundSeconds = roundSeconds ?: 0,
    afterAd = afterAd,
    reviewRoom = reviewRoom,
)

private fun ResultsRoute.toSummary() = MatchSummary(
    mode = MatchMode.valueOf(mode),
    outcome = Outcome.valueOf(outcome),
    reason = EndReason.valueOf(reason),
    rounds = rounds,
    opponentSecret = opponentSecret,
    digits = digits,
    level = level.takeIf { it.isNotEmpty() }?.let { ComputerLevel.valueOf(it) },
    mySecret = mySecret,
    opponentName = opponentName,
    myStrikes = strikesOf(myStrikes),
    opponentStrikes = strikesOf(opponentStrikes),
    durationSeconds = durationSeconds.takeIf { it > 0 },
    roundSeconds = roundSeconds.takeIf { it > 0 },
)
