package app.numbaseball.ui.design

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.SoundCue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.clickable
import androidx.compose.runtime.staticCompositionLocalOf
import app.numbaseball.R
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbMotion
import app.numbaseball.ui.theme.NbRadius
import app.numbaseball.ui.theme.NbSize
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.delay

/**
 * Top bar (DESIGN_SYSTEM 12.1): 56 dp (48 compact) on `bg`; scrolled = a 1 dp `line` bottom edge appears (no fill
 * change). Leading icon button at x = 4, title at x = 64 (16 without an icon), trailing buttons end 4 dp from the edge.
 */
@Composable
fun TopBar(
    title: String?,
    @DrawableRes navIcon: Int?,
    navDescription: String?,
    onNav: () -> Unit,
    modifier: Modifier = Modifier,
    scrolled: Boolean = false,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
    titleGap: Dp = 12.dp,
    /** Row height; the match bar is 54 dp + its 2 dp clock line (05 §1). */
    height: Dp = 56.dp,
    /** Row height in height-compact windows (the match bar is 46 dp + its 2 dp clock line, ADAPTIVE.md §1). */
    compactHeight: Dp = 48.dp,
) {
    val compact = isCompactHeight()
    Column(modifier.fillMaxWidth().background(NbColors.bg)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (compact) compactHeight else height).padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navIcon != null) {
                IconButton48(navIcon, navDescription, onNav)
                HSpacer(titleGap)
            } else {
                HSpacer(12.dp)
            }
            Box(Modifier.weight(1f)) {
                if (titleContent != null) {
                    titleContent()
                } else if (title != null) {
                    Text(title, style = NbType.title, color = NbColors.ink, maxLines = 2, modifier = Modifier.semantics { heading() })
                }
            }
            actions()
        }
        if (bottom != null) bottom() else if (scrolled) Hairline()
    }
}

/**
 * Screen frame: `bg`, optional top bar, a weighted body, optional pinned bottom area and the navigation-bar inset below
 * it (filled with `bg`). [statusBarPadding] for screens without a top bar.
 */
@Composable
fun NbScreen(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    statusBarPadding: Boolean = topBar == null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    wash: Color? = null,
    /** The IME pushes the content (text-field screens); its inset replaces the navigation-bar inset, never adds to it. */
    imeAware: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(NbColors.bg)
            .then(
                // Win wash (06 §1): [wash] from the window top fading to transparent at 360 dp.
                if (wash != null) {
                    Modifier.drawBehind { drawRect(Brush.verticalGradient(listOf(wash, Color.Transparent), endY = 360.dp.toPx())) }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (statusBarPadding) Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            topBar?.invoke()
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            bottomBar?.invoke()
            Spacer(Modifier.windowInsetsBottomHeight(if (imeAware) WindowInsets.navigationBars.union(WindowInsets.ime) else WindowInsets.navigationBars))
        }
        overlay?.invoke(this)
    }
}

/** Centres content at max 560 dp on wide windows (ADAPTIVE.md 3). */
@Composable
fun ContentColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), content = content)
    }
}

/**
 * Connection banner (DESIGN_SYSTEM 12.17, 14 §2): full-width `surface2` band, 10 × 16 dp padding, 12 dp gaps: three 8 dp
 * chase dots · title + line · countdown in `strike`. Pushes content down, never overlays.
 */
@Composable
fun ConnectionBanner(title: String, line: String?, countdown: String?) {
    // Small windows: one 40 dp line (title + countdown); the explanation stays for TalkBack only (ADAPTIVE.md §1, D2-27).
    val compact = isCompactHeight()
    val spoken = listOfNotNull(title, line, countdown).joinToString(". ")
    Column(
        Modifier
            .fillMaxWidth()
            .background(NbColors.surface2)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Assertive
                if (compact) contentDescription = spoken
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.heightIn(min = 39.dp).padding(horizontal = 12.dp) else Modifier.padding(horizontal = 16.dp, vertical = 10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChaseLamps(NbSize.dot)
            HSpacer(12.dp)
            // Large text (D2-37, ADAPTIVE §2.2): at most two title lines and the countdown; the explanation is read by
            // TalkBack only, so the banner never pushes the log below its latest row.
            val large = isLargeFont()
            Column(Modifier.weight(1f).then(if (large) Modifier.semantics { contentDescription = spoken } else Modifier)) {
                Text(title, style = NbType.bodyStrong, color = NbColors.ink, maxLines = if (compact) 1 else if (large) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis, fitMinScale = 0.5f)
                if (line != null && !compact && !large) Text(line, style = NbType.caption, color = NbColors.text2)
            }
            if (countdown != null) {
                HSpacer(12.dp)
                Text(countdown, style = NbType.numTimer, color = NbColors.strike, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Sheet body (DESIGN_SYSTEM 12.17): white `surface3`, top radius 16, grab handle 32 × 4 dp `dotOff` 12 dp from the top, no shadow. */
@Composable
fun SheetSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(NbColors.surface3, RoundedCornerShape(topStart = NbRadius.l, topEnd = NbRadius.l)),
    ) {
        SheetHandle()
        // Content may fill the sheet (chat); the navigation inset always stays below it.
        Column(Modifier.weight(1f, fill = false), content = content)
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp, 4.dp).background(NbColors.dotOff, RoundedCornerShape(2.dp)))
    }
}

/** Sheet title row: 56 dp (48 for the chat), `title` at 16 dp, optional trailing content (close button, switch). */
@Composable
fun SheetTitle(title: String, height: Dp = 56.dp, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (isCompactHeight()) 48.dp else height).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = NbType.title, color = NbColors.ink, fitMinScale = 0.5f, modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }
}

/**
 * Bottom space of a modal sheet whose total bottom (space + the navigation inset the sheet window adds) is [total]:
 * the reference shots draw the sheet's last 24 dp over the gesture area, so the inset counts towards it.
 */
@Composable
fun SheetBottomSpace(total: Dp = 24.dp) {
    val nav = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Spacer(Modifier.height((total - nav).coerceAtLeast(0.dp)))
}

/** Modal bottom sheet with the scrim, `sheet` spring and the design's surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NbModalSheet(onDismiss: () -> Unit, skipPartiallyExpanded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = NbColors.surface3,
        contentColor = NbColors.ink,
        scrimColor = NbColors.scrim,
        shape = RoundedCornerShape(topStart = NbRadius.l, topEnd = NbRadius.l),
        tonalElevation = 0.dp,
        dragHandle = { SheetHandle() },
        content = content,
    )
}

/**
 * Dialog (DESIGN_SYSTEM 12.16): 24 dp side margins (max 400 dp), radius 16, padding 24 / 24 / 16, title `title`, body
 * `body` `text2` 12 dp below, text-button actions right-aligned 24 dp below. [dismissible] false for alert dialogs.
 */
@Composable
fun NbDialog(
    title: String,
    body: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    destructive: Boolean = false,
    dismissible: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = dismissible),
    ) {
        ScrimDim()
        DialogSurface(title, body, confirmLabel, onConfirm, dismissLabel, onDismiss, destructive)
    }
}

/**
 * Server error / unknown failure (DESIGN_SYSTEM 12.17, 14 §1, D2-43): "문제가 생겼어요" / "잠시 후 다시 시도해 주세요." with
 * 다시 시도 (`accent`) and 홈으로. Back or a tap outside only closes it.
 */
@Composable
fun ServerErrorDialog(onRetry: () -> Unit, onHome: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ScrimDim()
        DialogSurface(
            title = androidx.compose.ui.res.stringResource(R.string.server_error_title),
            body = androidx.compose.ui.res.stringResource(R.string.server_error_body),
            confirmLabel = androidx.compose.ui.res.stringResource(R.string.action_retry),
            onConfirm = onRetry,
            dismissLabel = androidx.compose.ui.res.stringResource(R.string.home),
            onDismiss = onHome,
            destructive = false,
        )
    }
}

/**
 * Dialog windows dim the screen with the `scrim` token (40 % ink) instead of the platform's 60 % (D2-23). Call
 * inside a `Dialog` content.
 */
@Composable
fun ScrimDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(NbColors.scrim.alpha) }
}

@Composable
fun DialogSurface(
    title: String,
    body: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String?,
    onDismiss: () -> Unit,
    destructive: Boolean,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .background(NbColors.surface3, RoundedCornerShape(NbRadius.l))
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        ) {
            Text(title, style = NbType.title, color = NbColors.ink, modifier = Modifier.semantics { heading() })
            if (body != null) {
                VSpacer(12.dp)
                Text(body, style = NbType.body, color = NbColors.text2)
            }
            VSpacer(24.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                // Text-button actions (DESIGN_SYSTEM 12.17): `accent`, destructive `danger`.
                if (dismissLabel != null) DialogAction(dismissLabel, NbColors.accent, onDismiss)
                DialogAction(confirmLabel, if (destructive) NbColors.danger else NbColors.accent, onConfirm)
            }
        }
    }
}

/** Dialog action (prototype `.dialog .acts .btn`): text button 48 dp tall, 16 dp side padding, `label`. */
@Composable
private fun DialogAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(NbRadius.m))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NbType.label, color = color, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** Toast state: one message at a time, visible 2.8 s (DESIGN_SYSTEM 12.16). */
@Stable
class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var actionLabel by mutableStateOf<String?>(null)
        private set
    /** An error toast fires the `error_shown` hook (DESIGN_SYSTEM 8.3 E: `sfx_error`). */
    var error by mutableStateOf(false)
        private set
    private var action: (() -> Unit)? = null
    private var serial by mutableStateOf(0)

    fun show(text: String, actionLabel: String? = null, error: Boolean = false, action: (() -> Unit)? = null) {
        message = text
        this.error = error
        this.actionLabel = actionLabel
        this.action = action
        serial++
    }

    fun runAction() {
        action?.invoke()
        message = null
    }

    internal fun key() = serial
    internal fun hide() {
        message = null
    }
}

@Composable
fun rememberToastState() = remember { ToastState() }

/** Toast: 16 dp side margins, [bottomOffset] above the keypad or bottom bar, min 48 dp, radius 12, `inverse`, `body` `onInverse`. */
@Composable
fun BoxScope.ToastHost(state: ToastState, bottomOffset: Dp = 16.dp) {
    val message = state.message
    val audio = LocalGameAudio.current
    LaunchedEffect(state.key()) {
        if (message != null) {
            if (state.error) audio.play(SoundCue.ERROR)
            delay(NbMotion.toastVisible)
            state.hide()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 4 },
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = bottomOffset),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(NbColors.inverse, RoundedCornerShape(NbRadius.m))
                .padding(start = 16.dp, end = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message.orEmpty(), style = NbType.body, color = NbColors.onInverse, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            state.actionLabel?.let { NbButton(it, { state.runAction() }, kind = ButtonKind.TEXT, textColorOverride = NbColors.inverseAccent) }
        }
    }
}

/** Centered informative note with an icon (ad notice, watching note). */
@Composable
fun IconNote(@DrawableRes icon: Int, text: String, iconSize: Dp = 16.dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        NbIcon(icon, null, NbColors.text3, iconSize)
        HSpacer(8.dp)
        Text(text, style = NbType.caption, color = NbColors.text3, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun FullWidthBand(color: Color, modifier: Modifier = Modifier, height: Dp? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().background(color).then(if (height != null) Modifier.height(height) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun ScreenPadding(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = screenMargin()), content = content)
}

data class MenuItem(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** `more_vert` icon button with its menu (`surface3`, radius 12, 1 dp `line` border, no shadow; destructive items in `danger`). */
@Composable
fun OverflowMenu(items: List<MenuItem>, description: String) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton48(R.drawable.ic_more_vert, description, { expanded = true }, tint = NbColors.text2)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = NbColors.surface3,
            shape = RoundedCornerShape(NbRadius.m),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, NbColors.line),
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = NbType.body, color = if (item.destructive) NbColors.danger else NbColors.ink) },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

/** App-wide toast (block with undo, reconnected, search stopped): shown above every screen by NbApp. */
val LocalAppToast = staticCompositionLocalOf { ToastState() }

/**
 * A sheet hosted by a dialog destination: `scrim` over the previous screen (tap = dismiss) and a bottom-anchored sheet
 * surface. [top] pins the sheet top (chat: 136 dp on phones); otherwise it wraps its content. The IME pushes the sheet.
 */
@Composable
fun SheetFrame(onDismiss: () -> Unit, top: Dp? = null, content: @Composable ColumnScope.() -> Unit) {
    // Small windows: sheets open at full window height (ADAPTIVE.md §1).
    val top = if (top != null && isCompactHeight()) 0.dp else top
    Box(
        Modifier
            .fillMaxSize()
            .background(NbColors.scrim)
            .clickable(interactionSource = null, indication = null, onClick = onDismiss)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .then(if (top != null) Modifier.padding(top = top).fillMaxHeight() else Modifier.statusBarsPadding().padding(top = if (isCompactHeight()) 0.dp else 48.dp))
                .clickable(interactionSource = null, indication = null) {},
            contentAlignment = Alignment.BottomCenter,
        ) {
            SheetSurface(if (top != null) Modifier.fillMaxHeight() else Modifier, content = content)
        }
    }
}
