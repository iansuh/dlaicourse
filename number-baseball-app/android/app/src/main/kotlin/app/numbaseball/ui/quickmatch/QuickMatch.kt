package app.numbaseball.ui.quickmatch

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numbaseball.R
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.MusicTrack
import app.numbaseball.audio.ScreenMusic
import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.protocol.RoomKind
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.NetworkMonitor
import app.numbaseball.data.online.OnlineSession
import app.numbaseball.data.settings.SettingsRepository
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.exactLineHeight
import app.numbaseball.ui.design.ChaseLamps
import app.numbaseball.ui.design.Avatar
import app.numbaseball.ui.design.Dot
import app.numbaseball.ui.theme.NbSize
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.numbaseball.ui.design.NameWithTag
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.NumberUnitLabel
import app.numbaseball.ui.design.QuestionPlate
import app.numbaseball.ui.design.Segmented
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.formatClock
import app.numbaseball.ui.design.isCompactHeight
import app.numbaseball.ui.design.LocalReduceMotion
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbMotion
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoundOpponent(val nickname: String, val tag: String)

data class QuickMatchUiState(
    val digits: Int = 3,
    /** elapsedRealtime when the current search (re)started; the clock resets when the count changes. */
    val startedAt: Long = 0L,
    val found: FoundOpponent? = null,
    /** The connection was lost: toast and back to Home (04). */
    val stopped: Boolean = false,
)

class QuickMatchViewModel(
    private val session: OnlineSession,
    private val settings: SettingsRepository,
    network: NetworkMonitor,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    private val local = MutableStateFlow(QuickMatchUiState(startedAt = clock()))

    val state: StateFlow<QuickMatchUiState> = combine(local, session.room, session.connection, network.online) { ui, room, connection, online ->
        val opponent = room?.takeIf { it.kind == RoomKind.QUICK }?.players?.firstOrNull { it.id != room.youId }
        ui.copy(
            found = opponent?.let { FoundOpponent(it.nickname, it.tag) },
            stopped = ui.stopped || (opponent == null && (!online || connection is ConnectionState.Stopped)),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        session.acquire()
        viewModelScope.launch {
            val digits = settings.settings.first().quickDigits
            local.update { it.copy(digits = digits, startedAt = clock()) }
            // Sent now if connected, otherwise on the next welcome (the queue does not survive reconnects).
            session.startQuickMatch(digits)
        }
    }

    /** Changing the count restarts the search immediately; no confirmation (04). */
    fun onDigitsChange(digits: Int) {
        if (digits == local.value.digits || state.value.found != null) return
        session.cancelQuickMatch()
        session.startQuickMatch(digits)
        local.update { it.copy(digits = digits, startedAt = clock()) }
        viewModelScope.launch { settings.update { it.copy(quickDigits = digits) } }
    }

    fun onCancel() = session.cancelQuickMatch()

    override fun onCleared() {
        if (state.value.found == null) session.cancelQuickMatch()
        session.release()
    }
}

private const val SLOW_SEARCH_SECONDS = 60

/** Quick match search (04): calm wait, change the count without leaving; found state hands off after 900 ms. */
@Composable
fun QuickMatchScreen(
    state: QuickMatchUiState,
    onDigitsChange: (Int) -> Unit,
    onCancel: () -> Unit,
    onVsComputer: () -> Unit,
    now: () -> Long = SystemClock::elapsedRealtime,
) {
    ScreenMusic(MusicTrack.MATCH, LocalGameAudio.current)
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.startedAt, state.found) {
        while (state.found == null) {
            elapsed = ((now() - state.startedAt) / 1000).toInt().coerceAtLeast(0)
            delay(1000)
        }
    }
    val compact = isCompactHeight()
    val slow = elapsed >= SLOW_SEARCH_SECONDS && state.found == null
    NbScreen(
        topBar = {
            TopBar(stringResource(R.string.quick_title), R.drawable.ic_close, stringResource(R.string.search_cancel), onCancel)
        },
        bottomBar = {
            if (state.found == null) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (compact) 12.dp else 24.dp)) {
                    if (slow) {
                        NbButton(stringResource(R.string.cpu_title), onVsComputer, kind = ButtonKind.TONAL, modifier = Modifier.fillMaxWidth())
                        VSpacer(8.dp)
                    }
                    NbButton(stringResource(R.string.search_cancel), onCancel, kind = ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth().testTag("cancelSearch"))
                }
            } else {
                // Cancel is hidden once found; its padded area stays so the block does not jump (04, `.actions`).
                VSpacer(if (compact) 12.dp else 40.dp)
            }
        },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Text(stringResource(R.string.search_digits_label), style = NbType.labelSmall, color = NbColors.text3)
            VSpacer(8.dp)
            val unit = pluralStringResource(R.plurals.digit_unit, 2)
            Segmented(Limits.DIGITS.toList(), state.digits, onDigitsChange, enabled = state.found == null) { d, selected ->
                NumberUnitLabel(d.toString(), unit, selected)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = state.found,
                transitionSpec = { fadeIn(tween(NbMotion.medium)) togetherWith fadeOut(tween(NbMotion.short)) },
                label = "found",
                modifier = Modifier.padding(horizontal = 24.dp),
            ) { found ->
                if (found == null) {
                    Searching(state.digits, elapsed, slow, compact)
                } else {
                    Found(found)
                }
            }
        }
    }
}

@Composable
private fun Searching(digits: Int, elapsed: Int, slow: Boolean, compact: Boolean) {
    val reduceMotion = LocalReduceMotion.current
    var sweep by remember { mutableIntStateOf(0) }
    LaunchedEffect(digits, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(300)
            sweep = (sweep + 1) % digits
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (!compact) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // DESIGN_SYSTEM 12.4 width rule: 56 dp, shrinking to 44 dp so six plates fit.
                val width = minOf(56.dp, (maxWidth - 8.dp * (digits - 1)) / digits).coerceAtLeast(44.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(digits) { QuestionPlate(lit = it == sweep, width = width, height = 64.dp, color = NbColors.textDisabled) }
                }
            }
            VSpacer(20.dp)
        }
        ChaseLamps(NbSize.dot)
        VSpacer(20.dp)
        Text(stringResource(R.string.search_title), style = NbType.title, color = NbColors.ink, textAlign = TextAlign.Center)
        VSpacer(20.dp)
        Text(formatClock(elapsed), style = NbType.numTimer, color = NbColors.text2)
        VSpacer(20.dp)
        Text(
            stringResource(if (slow) R.string.search_slow else R.string.search_hint),
            style = NbType.caption,
            color = NbColors.text3,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun Found(opponent: FoundOpponent) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        // Found (04 v2): avatar 48 · `VS` · name + tag (`headline`) · three lit `accent` dots · title · sub.
        Avatar(opponent.nickname, NbSize.avatarL)
        VSpacer(12.dp)
        Text("VS", style = NbType.numLabel.copy(letterSpacing = 0.12.sp * 13), color = NbColors.text3)
        VSpacer(6.dp)
        NameWithTag(opponent.nickname, opponent.tag, nameStyle = NbType.headline, tagStyle = NbType.numTag.copy(fontSize = 22.sp, lineHeight = 34.sp))
        VSpacer(20.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clearAndSetSemantics {}) { repeat(3) { Dot(NbColors.accent, NbSize.dot) } }
        VSpacer(20.dp)
        Text(stringResource(R.string.found_title), style = NbType.title, color = NbColors.ink, textAlign = TextAlign.Center)
        VSpacer(20.dp)
        Text(stringResource(R.string.found_sub), style = NbType.caption, color = NbColors.text3, textAlign = TextAlign.Center)
    }
}
