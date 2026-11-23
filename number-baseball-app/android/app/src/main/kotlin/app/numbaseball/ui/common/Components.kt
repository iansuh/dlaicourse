package app.numbaseball.ui.common

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.numbaseball.R
import app.numbaseball.core.protocol.Limits
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.StopReason
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.ConnectionBanner
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.formatClock
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.delay

/** True while an online screen has no live connection (seats grey out, chat send disabled). */
val ConnectionState.offline: Boolean get() = this !is ConnectionState.Connected && this != ConnectionState.Idle

/**
 * Connection banner for lobby and other online screens (14 §1–2): chase lamps, what happened, what happens next and a
 * 60 s countdown. Stopped states (no automatic reconnect) show their reason and the one action that helps.
 */
@Composable
fun OnlineConnectionBanner(state: ConnectionState, onRetry: () -> Unit, @StringRes line: Int = R.string.reconnecting_room) {
    var since by remember { mutableLongStateOf(0L) }
    var left by remember { mutableIntStateOf((Limits.DISCONNECT_GRACE_MS / 1000).toInt()) }
    val lost = state is ConnectionState.Reconnecting || (state is ConnectionState.Connecting && state.attempt > 0)
    LaunchedEffect(lost) {
        if (!lost) return@LaunchedEffect
        since = SystemClock.elapsedRealtime()
        while (true) {
            left = ((Limits.DISCONNECT_GRACE_MS - (SystemClock.elapsedRealtime() - since)) / 1000).toInt().coerceAtLeast(0)
            delay(1000)
        }
    }
    when (state) {
        is ConnectionState.Reconnecting, is ConnectionState.Connecting -> if (lost) {
            ConnectionBanner(stringResource(R.string.reconnecting_title), stringResource(line), formatClock(left))
        }
        ConnectionState.Paused -> ConnectionBanner(stringResource(R.string.connection_paused), null, null)
        is ConnectionState.Stopped -> StoppedBanner(state.reason, onRetry)
        is ConnectionState.Connected, ConnectionState.Idle -> Unit
    }
}

@Composable
private fun StoppedBanner(reason: StopReason, onRetry: () -> Unit) {
    val text = when (reason) {
        StopReason.NO_ACCOUNT -> R.string.connection_no_account
        StopReason.UNAUTHORIZED -> R.string.connection_unauthorized
        StopReason.BANNED -> R.string.connection_banned
        StopReason.SESSION_REPLACED -> R.string.connection_replaced
        StopReason.ACCOUNT_DELETED -> R.string.connection_deleted
    }
    val action = when (reason) {
        StopReason.SESSION_REPLACED -> R.string.connection_play_here
        StopReason.NO_ACCOUNT -> R.string.action_retry
        else -> null
    }
    Column(Modifier.fillMaxWidth().background(NbColors.surface3).semantics { liveRegion = LiveRegionMode.Assertive }) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(text), style = NbType.caption, color = NbColors.chalk, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            if (action != null) NbButton(stringResource(action), onRetry, kind = ButtonKind.TEXT)
        }
        Hairline()
    }
}
