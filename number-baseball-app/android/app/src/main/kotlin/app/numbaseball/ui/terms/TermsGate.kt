package app.numbaseball.ui.terms

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.numbaseball.R
import app.numbaseball.ui.design.ChaseLamps
import app.numbaseball.ui.design.exactLineHeight
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbIcon
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.ToastHost
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.rememberToastState
import app.numbaseball.ui.design.rowClick
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType

/**
 * Terms gate (13): full screen, `close` = later. Title, lead, four points, links to the in-app reader, version, and the
 * pinned "Agree and continue". The age itself was checked on the first-launch screen; the number is never shown here.
 */
@Composable
fun TermsGateScreen(
    updated: Boolean,
    version: String?,
    submitting: Boolean,
    failed: Int,
    onReadTerms: () -> Unit,
    onReadPrivacy: () -> Unit,
    onAgree: () -> Unit,
    onClose: () -> Unit,
) {
    val toast = rememberToastState()
    val failure = stringResource(R.string.error_network)
    androidx.compose.runtime.LaunchedEffect(failed) { if (failed > 0) toast.show(failure) }
    NbScreen(
        topBar = { TopBar(null, R.drawable.ic_close, stringResource(R.string.action_close), onClose) },
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                Hairline()
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                    NbButton(
                        stringResource(R.string.terms_agree),
                        onAgree,
                        enabled = !submitting && version != null,
                        modifier = Modifier.fillMaxWidth().testTag("termsAgree"),
                        content = if (submitting) ({ ChaseLamps() }) else null,
                    )
                    VSpacer(8.dp)
                    Text(stringResource(R.string.terms_decline_note), style = NbType.caption, color = NbColors.text3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        overlay = { ToastHost(toast, bottomOffset = 120.dp) },
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            VSpacer(8.dp)
            Text(
                stringResource(if (updated) R.string.terms_changed_title else R.string.terms_gate_title),
                style = NbType.headline,
                color = NbColors.chalk,
                maxLines = 2,
                modifier = Modifier.exactLineHeight(NbType.headline).semantics { heading() },
            )
            VSpacer(8.dp)
            Text(stringResource(R.string.terms_gate_lead), style = NbType.body, color = NbColors.text2)
            VSpacer(24.dp)
            Hairline()
            Point(R.drawable.ic_person, R.string.terms_age_title, R.string.terms_age_line)
            Hairline()
            Point(R.drawable.ic_forum, R.string.terms_respect_title, R.string.terms_respect_line)
            Hairline()
            Point(R.drawable.ic_shield, R.string.terms_child_title, R.string.terms_child_line)
            Hairline()
            Point(R.drawable.ic_flag, R.string.terms_report_title, R.string.terms_report_line)
            Hairline()
            VSpacer(8.dp)
            LinkRow(stringResource(R.string.terms_full), onReadTerms)
            LinkRow(stringResource(R.string.privacy_policy), onReadPrivacy)
            if (version != null) {
                VSpacer(8.dp)
                Text(stringResource(R.string.terms_version, version), style = NbType.numTag, color = NbColors.text3)
            }
            VSpacer(16.dp)
        }
    }
}

@Composable
private fun Point(@DrawableRes icon: Int, title: Int, line: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Box(Modifier.width(40.dp)) { NbIcon(icon, null, NbColors.text2) }
        Column {
            Text(stringResource(title), style = NbType.bodyStrong, color = NbColors.chalk)
            Text(stringResource(line), style = NbType.caption, color = NbColors.text3)
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).rowClick(onClick = onClick).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = NbType.labelSmall, color = NbColors.chalk)
        NbIcon(R.drawable.ic_chevron_right, null, NbColors.text2, 18.dp)
    }
}

/** Server config not loaded yet (or offline): calm wait with retry. */
@Composable
fun TermsLoadingScreen(onRetry: () -> Unit, onClose: () -> Unit) {
    NbScreen(topBar = { TopBar(null, R.drawable.ic_close, stringResource(R.string.action_close), onClose) }) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            ChaseLamps()
            VSpacer(16.dp)
            Text(stringResource(R.string.connection_connecting), style = NbType.body, color = NbColors.text2, textAlign = TextAlign.Center)
            VSpacer(8.dp)
            NbButton(stringResource(R.string.action_retry), onRetry, kind = app.numbaseball.ui.design.ButtonKind.TEXT)
        }
    }
}
