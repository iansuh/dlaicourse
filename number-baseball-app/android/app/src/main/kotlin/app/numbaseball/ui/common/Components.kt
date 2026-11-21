package app.numbaseball.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.numbaseball.R
import app.numbaseball.data.online.ConnectionState
import app.numbaseball.data.online.StopReason
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbLayout
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbSpace

// Phase 1 building blocks: plain Material 3 on the Night Game tokens. Phase 2 replaces them with the design's components.

/**
 * Screen frame: top bar with optional back button, vertically scrollable content (works down to 320dp high split-screen
 * windows), and an optional [bottomBar] (for example the ad banner on calm screens).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = NbColors.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NbColors.bg),
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        ScreenColumn(padding, scrollable, content)
    }
}

@Composable
private fun ScreenColumn(padding: PaddingValues, scrollable: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val compact = rememberWindowHeightDp() < NbLayout.compactHeightBelow
    val margin = if (compact) NbLayout.marginCompact else NbLayout.margin
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = margin, vertical = if (compact) NbSpace.s8 else NbSpace.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = NbLayout.contentMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) NbSpace.s8 else NbSpace.s12),
            content = content,
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = NbSize.buttonHeight),
    ) { Text(text) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = NbSize.buttonHeightSecondary),
    ) { Text(text) }
}

/** Single choice among a few options (digits, level, team size...). */
@Composable
fun <T> ChoiceRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s8)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = NbColors.text2)
}

@Composable
fun ErrorText(error: UiError?) {
    if (error != null) {
        Text(stringResource(error.message()), color = NbColors.out, style = MaterialTheme.typography.bodySmall)
    }
}

/** Status line for the online connection; null when connected or idle. */
@Composable
fun ConnectionStatus(state: ConnectionState, onRetry: () -> Unit) {
    val text = when (state) {
        is ConnectionState.Connecting -> R.string.connection_connecting
        is ConnectionState.Reconnecting -> R.string.connection_reconnecting
        ConnectionState.Paused -> R.string.connection_paused
        is ConnectionState.Stopped -> when (state.reason) {
            StopReason.NO_ACCOUNT -> R.string.connection_no_account
            StopReason.UNAUTHORIZED -> R.string.connection_unauthorized
            StopReason.BANNED -> R.string.connection_banned
            StopReason.SESSION_REPLACED -> R.string.connection_replaced
            StopReason.ACCOUNT_DELETED -> R.string.connection_deleted
        }
        is ConnectionState.Connected, ConnectionState.Idle -> null
    } ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NbSpace.s8)) {
        Text(stringResource(text), color = NbColors.text2, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
        val retryLabel = when {
            state is ConnectionState.Stopped && state.reason == StopReason.SESSION_REPLACED -> R.string.connection_play_here
            state is ConnectionState.Stopped && state.reason == StopReason.NO_ACCOUNT -> R.string.action_retry
            else -> null
        }
        if (retryLabel != null) SecondaryButton(stringResource(retryLabel), onRetry)
    }
}
