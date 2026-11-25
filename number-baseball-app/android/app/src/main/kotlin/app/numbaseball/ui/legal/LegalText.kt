package app.numbaseball.ui.legal

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import app.numbaseball.ui.design.HSpacer
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import app.numbaseball.ui.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.numbaseball.R
import app.numbaseball.ui.design.ButtonKind
import app.numbaseball.ui.design.Hairline
import app.numbaseball.ui.design.NbButton
import app.numbaseball.ui.design.NbScreen
import app.numbaseball.ui.design.TopBar
import app.numbaseball.ui.design.VSpacer
import app.numbaseball.ui.design.isCompactHeight
import app.numbaseball.ui.design.rowClick
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbType
import kotlinx.coroutines.launch

enum class LegalDocument(@RawRes val raw: Int, val title: Int) {
    TERMS(R.raw.legal_terms, R.string.terms_of_use),
    PRIVACY(R.raw.legal_privacy, R.string.privacy_policy),
}

/** One block of the bundled Markdown subset used by docs/play (headings, bullets, paragraphs); `**bold**` is kept. */
sealed interface LegalBlock {
    data class Heading(val text: String, val level: Int) : LegalBlock
    data class Bullet(val text: String) : LegalBlock
    data class Paragraph(val text: String) : LegalBlock
}

object LegalMarkdown {
    private val anchor = Regex("""\s*\{#[^}]*}""")
    private val link = Regex("""\[([^\]]+)]\((https?://[^)]+)\)""")

    fun parse(markdown: String): List<LegalBlock> = markdown.lines().mapNotNull { raw ->
        val line = clean(raw.trim())
        when {
            line.isEmpty() || line.startsWith(">") -> null
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                LegalBlock.Heading(line.drop(level).trim().replace("**", ""), level)
            }
            line.startsWith("- ") || line.startsWith("* ") -> LegalBlock.Bullet(line.drop(2).trim())
            else -> LegalBlock.Paragraph(line)
        }
    }

    /** URLs stay as plain text (15: no external links inside the text). */
    private fun clean(line: String): String =
        link.replace(anchor.replace(line, "")) { "${it.groupValues[1]} (${it.groupValues[2]})" }.replace("`", "")

    /** `**bold**` → 700 spans. */
    fun styled(text: String): AnnotatedString = buildAnnotatedString {
        text.split("**").forEachIndexed { i, part ->
            if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
        }
    }
}

/** The reader's view of a document: title dropped (it is in the top bar), meta line, contents and body. */
data class LegalPage(val meta: String?, val contents: List<Pair<String, String>>, val body: List<LegalBlock>)

fun legalPage(blocks: List<LegalBlock>): LegalPage {
    val withoutTitle = blocks.dropWhile { it is LegalBlock.Heading && it.level == 1 }
    val meta = (withoutTitle.firstOrNull() as? LegalBlock.Paragraph)?.text?.takeIf { it.startsWith("버전") || it.startsWith("Version") }
    val body = if (meta != null) withoutTitle.drop(1) else withoutTitle
    val top = body.filterIsInstance<LegalBlock.Heading>().minOfOrNull { it.level }
    val contents = body.filterIsInstance<LegalBlock.Heading>().filter { it.level == top }.mapIndexed { i, h ->
        val number = h.text.substringBefore(". ", "").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        (number ?: (i + 1).toString()) to (if (number != null) h.text.substringAfter(". ") else h.text)
    }
    return LegalPage(meta, contents, body)
}

/** Terms or privacy policy, bundled and localized (README "App only"). */
@Composable
fun LegalTextScreen(document: LegalDocument, onBack: () -> Unit, onWebVersion: (() -> Unit)? = null) {
    val resources = LocalResources.current
    val page = remember(document, resources) {
        legalPage(LegalMarkdown.parse(resources.openRawResource(document.raw).bufferedReader().use { it.readText() }))
    }
    LegalTextScreen(stringResource(document.title), page, onBack, onWebVersion)
}

/**
 * In-app reader (15): meta line, contents (tap scrolls to the heading), body at 15 / 24 sp, headings with `heading()`
 * semantics, max width 560 dp, selectable text. [onWebVersion]: the public privacy URL row Play requires (privacy only).
 */
@Composable
fun LegalTextScreen(title: String, page: LegalPage, onBack: () -> Unit, onWebVersion: (() -> Unit)?) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    val margin = if (isCompactHeight()) 12.dp else 16.dp
    // Item index of each top-level heading: 2 header items (meta, contents) precede the body.
    val top = page.body.filterIsInstance<LegalBlock.Heading>().minOfOrNull { it.level }
    val headingItems = page.body.withIndex().filter { (_, b) -> b is LegalBlock.Heading && b.level == top }.map { it.index + 2 }
    NbScreen(topBar = { TopBar(title, R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack, scrolled = scrolled) }) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            SelectionContainer {
                LazyColumn(
                    state = list,
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(start = margin, end = margin, bottom = 32.dp),
                ) {
                    item {
                        if (page.meta != null) {
                            VSpacer(4.dp)
                            // Mixed caption + `numTag` line: the prototype's line box is 22 dp (D2-50).
                            Text(metaText(page.meta), style = NbType.caption.copy(lineHeight = 22.sp), color = NbColors.text3)
                        }
                        VSpacer(16.dp)
                    }
                    item {
                        if (page.contents.isNotEmpty()) {
                            Hairline()
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(stringResource(R.string.legal_contents), style = NbType.labelSmall, color = NbColors.text3, modifier = Modifier.padding(vertical = 4.dp))
                                page.contents.forEachIndexed { i, (number, heading) ->
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 48.dp).rowClick {
                                            headingItems.getOrNull(i)?.let { scope.launch { list.animateScrollToItem(it) } }
                                        },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(number, style = NbType.numLabel, color = NbColors.text3, modifier = Modifier.width(16.dp))
                                        HSpacer(12.dp)
                                        Text(heading, style = NbType.body, color = NbColors.text2)
                                    }
                                }
                            }
                            Hairline()
                        }
                        VSpacer(8.dp)
                    }
                    // Spacing follows the prototype's collapsed CSS margins: heading 28 above / 8 below, paragraphs 12
                    // apart, list items 6 apart.
                    itemsIndexed(page.body) { i, block ->
                        val previous = page.body.getOrNull(i - 1)
                        val gap = when (block) {
                            is LegalBlock.Heading -> 28.dp
                            is LegalBlock.Bullet -> if (previous is LegalBlock.Bullet) 6.dp else 8.dp
                            is LegalBlock.Paragraph -> when (previous) {
                                null -> 0.dp
                                is LegalBlock.Paragraph -> 12.dp
                                else -> 8.dp
                            }
                        }
                        Column(Modifier.padding(top = gap)) {
                            when (block) {
                                is LegalBlock.Heading -> Text(
                                    block.text,
                                    style = if (block.level == top) NbType.subtitle else NbType.bodyStrong,
                                    color = NbColors.chalk,
                                    modifier = Modifier.semantics { heading() },
                                )
                                is LegalBlock.Bullet -> Row(Modifier.padding(start = 4.dp)) {
                                    // 5 dp `text3` disc centred on the first line's x-height (prototype `list-style: disc`, D2-50).
                                    androidx.compose.foundation.layout.Box(Modifier.width(16.dp).height(24.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.padding(start = 2.dp, top = 2.dp).size(5.dp).background(NbColors.text3, androidx.compose.foundation.shape.CircleShape),
                                        )
                                    }
                                    Text(LegalMarkdown.styled(block.text), style = NbType.reading, color = NbColors.chalk)
                                }
                                is LegalBlock.Paragraph -> Text(LegalMarkdown.styled(block.text), style = NbType.reading, color = NbColors.chalk)
                            }
                        }
                    }
                    if (onWebVersion != null) {
                        item {
                            VSpacer(16.dp)
                            NbButton(
                                stringResource(R.string.privacy_web),
                                onWebVersion,
                                kind = ButtonKind.TEXT,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The meta line with its version ("2026-11-v1") in `numTag` (15 Layout, D2-28). */
private val VERSION = Regex("""\d{4}-\d{2}-v\d+""")

private fun metaText(meta: String) = androidx.compose.ui.text.buildAnnotatedString {
    var last = 0
    VERSION.findAll(meta).forEach { m ->
        append(meta.substring(last, m.range.first))
        pushStyle(NbType.numTag.toSpanStyle())
        append(m.value)
        pop()
        last = m.range.last + 1
    }
    append(meta.substring(last))
}
