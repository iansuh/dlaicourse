package app.numbaseball.qa

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.R
import app.numbaseball.data.settings.SupportedLanguages
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.w3c.dom.Element

/** The ten supported languages: resource directory, Robolectric qualifier, the language code the platform reports. */
object QaLocales {
    data class L(val dir: String, val qualifier: String, val language: String, val tag: String)

    val all = listOf(
        L("values", "en", "en", "en"),
        L("values-ko", "ko", "ko", "ko"),
        L("values-ja", "ja", "ja", "ja"),
        L("values-b+zh+Hans", "b+zh+Hans", "zh", "zh-Hans"),
        L("values-es", "es", "es", "es"),
        L("values-fr", "fr", "fr", "fr"),
        L("values-de", "de", "de", "de"),
        L("values-pt", "pt", "pt", "pt"),
        L("values-ru", "ru", "ru", "ru"),
        L("values-vi", "vi", "vi", "vi"),
    )
    val tight = all.filter { it.language in setOf("de", "ru", "fr", "vi", "ja") }
}

/** Parsed `strings.xml`: plain strings and plurals (quantity -> text). */
class StringFile(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>) {
    companion object {
        fun parse(dir: String): StringFile {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
            val strings = linkedMapOf<String, String>()
            val plurals = linkedMapOf<String, Map<String, String>>()
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                when (e.tagName) {
                    "string" -> strings[e.getAttribute("name")] = e.textContent
                    "plurals" -> plurals[e.getAttribute("name")] = (0 until e.childNodes.length)
                        .mapNotNull { e.childNodes.item(it) as? Element }
                        .associate { it.getAttribute("quantity") to it.textContent }
                }
            }
            return StringFile(strings, plurals)
        }
    }
}

private val FORMAT = Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXc%]""")

/** Static checks over the XML (AC-L10N-01/02): the same keys, placeholders and plural forms in all ten languages. */
class LocaleResourcesStaticQaTest {
    private val english = StringFile.parse("values")

    @Test fun everyLanguageHasEveryKeyAndNoExtraKeys() {
        for (l in QaLocales.all.drop(1)) {
            val f = StringFile.parse(l.dir)
            assertEquals("${l.dir}: missing strings", emptySet<String>(), english.strings.keys - f.strings.keys)
            assertEquals("${l.dir}: extra strings", emptySet<String>(), f.strings.keys - english.strings.keys)
            assertEquals("${l.dir}: missing plurals", emptySet<String>(), english.plurals.keys - f.plurals.keys)
            assertEquals("${l.dir}: extra plurals", emptySet<String>(), f.plurals.keys - english.plurals.keys)
        }
    }

    @Test fun placeholdersMatchTheEnglishTemplateByIndexAndType() {
        val problems = mutableListOf<String>()
        fun conv(s: String) = FORMAT.findAll(s).map { it.value }.sorted().toList()
        for (l in QaLocales.all.drop(1)) {
            val f = StringFile.parse(l.dir)
            english.strings.forEach { (k, v) -> if (conv(v) != conv(f.strings.getValue(k))) problems += "${l.dir}:$k  en=${conv(v)} here=${conv(f.strings.getValue(k))}" }
            english.plurals.forEach { (k, items) ->
                // Every quantity of a plural uses the same arguments as the English "other" form (a "one" form may drop %d).
                val reference = conv(items.getValue("other")).filter { it.contains("$") }
                f.plurals.getValue(k).forEach { (q, text) ->
                    val args = conv(text).filter { it.contains("$") }
                    if (args != reference) problems += "${l.dir}:$k[$q] $args vs $reference"
                }
            }
        }
        assertTrue("placeholder mismatches:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun noEmptyTodoOrHalfTranslatedStrings() {
        val problems = mutableListOf<String>()
        for (l in QaLocales.all) {
            val f = StringFile.parse(l.dir)
            (f.strings + f.plurals.flatMap { (k, v) -> v.map { "$k[${it.key}]" to it.value } }).forEach { (k, v) ->
                if (v.isBlank()) problems += "${l.dir}:$k empty"
                if (Regex("""\bTODO\b|FIXME|XXX|(?i)lorem ipsum""").containsMatchIn(v)) problems += "${l.dir}:$k placeholder text '$v'"
                if (v != v.trim() && !k.startsWith("space")) problems += "${l.dir}:$k leading/trailing whitespace"
                if (v.contains("  ")) problems += "${l.dir}:$k double space"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun pluralFormsCoverTheLanguagesRules() {
        val required = mapOf(
            "en" to setOf("one", "other"), "de" to setOf("one", "other"),
            "es" to setOf("one", "other"), "fr" to setOf("one", "other"), "pt" to setOf("one", "other"),
            "ru" to setOf("one", "few", "many", "other"),
            "ja" to setOf("other"), "ko" to setOf("other"), "zh" to setOf("other"), "vi" to setOf("other"),
        )
        for (l in QaLocales.all) {
            val f = StringFile.parse(l.dir)
            f.plurals.forEach { (k, items) ->
                assertTrue("${l.dir}:$k lacks ${required.getValue(l.language) - items.keys}", items.keys.containsAll(required.getValue(l.language)))
            }
        }
    }

    @Test fun textThatIsStillEnglishIsOnlyLoanWordsAndBrandNames() {
        // Informational guard: more than 5 % identical-to-English strings in a language means a file was not translated.
        for (l in QaLocales.all.drop(1)) {
            val f = StringFile.parse(l.dir)
            val same = english.strings.filter { (k, v) -> f.strings[k] == v && Regex("[A-Za-z]{3,}").containsMatchIn(v) }
            assertTrue("${l.dir}: ${same.size} strings equal English: ${same.keys}", same.size <= english.strings.size * 0.05)
        }
    }

    @Test fun supportedLanguagesMatchTheTenShippedFiles() {
        assertEquals(QaLocales.all.map { it.tag }.sorted(), SupportedLanguages.TAGS.sorted())
    }
}

/** Loading every string in every locale through Android resources (AC-L10N-01): no exception, nothing blank. */
@RunWith(AndroidJUnit4::class)
class LocaleResourcesLoadQaTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun resources(qualifier: String): Resources {
        RuntimeEnvironment.setQualifiers("+$qualifier")
        return ApplicationProvider.getApplicationContext<Context>().resources
    }

    private fun args(template: String): Array<Any> {
        val conv = FORMAT.findAll(template).map { it.value }.filter { it != "%%" }.toList()
        val byIndex = conv.mapIndexed { i, c -> (Regex("""%(\d+)\$""").find(c)?.groupValues?.get(1)?.toInt() ?: (i + 1)) to c }.toMap()
        val n = byIndex.keys.maxOrNull() ?: 0
        return Array(n) { i ->
            val c = byIndex[i + 1].orEmpty()
            when {
                c.endsWith("d") -> 12
                c.endsWith("f") -> 1.5
                else -> "Mina#0420"
            }
        }
    }

    @Test fun everyStringAndPluralLoadsAndFormatsInAllTenLocales() {
        val english = StringFile.parse("values")
        val stringFields = R.string::class.java.fields
        val pluralFields = R.plurals::class.java.fields
        val homeEnglish = resources("en").getString(R.string.home)
        for (l in QaLocales.all) {
            val res = resources(l.qualifier)
            assertEquals("${l.dir}: platform language", l.language, res.configuration.locales[0].language)
            if (l.language != "en") assertNotEquals("${l.dir} loads its own file", homeEnglish, res.getString(R.string.home))
            var checked = 0
            for (f in stringFields) {
                val id = f.getInt(null)
                val template = english.strings[f.name] ?: continue // library strings
                val text = res.getString(id, *args(template))
                assertFalse("${l.dir}:${f.name} blank", text.isBlank())
                assertFalse("${l.dir}:${f.name} unformatted '%'", Regex("""%\d?\$?[sd]""").containsMatchIn(text))
                checked++
            }
            assertEquals("${l.dir}: every app string was loaded", english.strings.size, checked)
            for (f in pluralFields) {
                val id = f.getInt(null)
                val items = english.plurals[f.name] ?: continue
                val template = items.getValue("other")
                for (q in listOf(0, 1, 2, 3, 4, 5, 11, 12, 21, 22, 100)) {
                    val text = res.getQuantityString(id, q, *(if (FORMAT.containsMatchIn(template)) arrayOf<Any>(q) else emptyArray()))
                    assertFalse("${l.dir}:${f.name}($q) blank", text.isBlank())
                }
            }
        }
    }

    @Test fun theAppNameAndStoreNameAreLocalised() {
        val names = QaLocales.all.associate { it.tag to resources(it.qualifier).getString(R.string.app_name) }
        assertTrue(names.toString(), names.values.all { it.isNotBlank() })
        assertTrue("Korean launcher name", names.getValue("ko").contains("숫자야구"))
    }
}
