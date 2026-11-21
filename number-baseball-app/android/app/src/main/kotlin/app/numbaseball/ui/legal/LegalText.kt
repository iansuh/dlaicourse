package app.numbaseball.ui.legal

import androidx.annotation.RawRes
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.numbaseball.R
import app.numbaseball.ui.common.ScreenScaffold
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbSpace

enum class LegalDocument(@RawRes val raw: Int, val title: Int) {
    TERMS(R.raw.legal_terms, R.string.settings_terms),
    PRIVACY(R.raw.legal_privacy, R.string.settings_privacy_policy),
}

/** One block of the bundled Markdown subset used by docs/play (headings, bullets, paragraphs). */
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
                LegalBlock.Heading(line.drop(level).trim(), level)
            }
            line.startsWith("- ") || line.startsWith("* ") -> LegalBlock.Bullet(line.drop(2).trim())
            else -> LegalBlock.Paragraph(line)
        }
    }

    private fun clean(line: String): String =
        link.replace(anchor.replace(line, "")) { "${it.groupValues[1]} (${it.groupValues[2]})" }.replace("**", "").replace("`", "")
}

/** Terms or privacy policy, bundled and localized (README "App only"). */
@Composable
fun LegalTextScreen(document: LegalDocument, onBack: () -> Unit, onWebVersion: (() -> Unit)? = null) {
    val context = LocalContext.current
    val blocks = remember(document) {
        LegalMarkdown.parse(context.resources.openRawResource(document.raw).bufferedReader().use { it.readText() })
    }
    LegalTextScreen(stringResource(document.title), blocks, onBack, onWebVersion)
}

@Composable
fun LegalTextScreen(title: String, blocks: List<LegalBlock>, onBack: () -> Unit, onWebVersion: (() -> Unit)?) {
    ScreenScaffold(title = title, onBack = onBack) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s8)) {
                blocks.forEach { block ->
                    when (block) {
                        is LegalBlock.Heading -> Text(
                            block.text,
                            style = if (block.level <= 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        )
                        is LegalBlock.Bullet -> Text("•  " + block.text)
                        is LegalBlock.Paragraph -> Text(block.text, color = NbColors.text2)
                    }
                }
            }
        }
        if (onWebVersion != null) TextButton(onClick = onWebVersion) { Text(stringResource(R.string.legal_web_version)) }
    }
}
