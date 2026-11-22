package app.numbaseball

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.data.account.AccountStoreFiles
import app.numbaseball.data.settings.SupportedLanguages
import app.numbaseball.ui.legal.LegalBlock
import app.numbaseball.ui.legal.LegalDocument
import app.numbaseball.ui.legal.LegalMarkdown
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class ResourcesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun XmlResourceParser.collect(tag: String, attribute: String): List<String> {
        val values = mutableListOf<String>()
        while (next() != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && name == tag) {
                values += (0 until attributeCount).first { getAttributeName(it) == attribute }.let { getAttributeValue(it) }
            }
        }
        return values
    }

    @Test fun testsRunOnTheTestApplication() {
        assertTrue(context is TestNumBaseballApp)
    }

    @Test fun backupRulesIncludeExactlyTheAccountTokenStore() {
        val tokenFile = context.preferencesDataStoreFile(AccountStoreFiles.ACCOUNT)
        val relative = tokenFile.relativeTo(context.filesDir).path.replace(File.separatorChar, '/')
        assertEquals("datastore/account.preferences_pb", relative)

        assertEquals(listOf(relative), context.resources.getXml(R.xml.backup_rules).collect("include", "path"))
        // cloud-backup and device-transfer
        assertEquals(listOf(relative, relative), context.resources.getXml(R.xml.data_extraction_rules).collect("include", "path"))
    }

    @Test fun termsAndPrivacyAreBundledFromDocsPlay() {
        LegalDocument.entries.forEach { document ->
            val text = context.resources.openRawResource(document.raw).bufferedReader().use { it.readText() }
            val blocks = LegalMarkdown.parse(text)
            assertTrue(document.name, blocks.first() is LegalBlock.Heading)
            assertTrue(document.name, blocks.count { it is LegalBlock.Heading } > 5)
            assertTrue(document.name, text.none { it == '{' })
        }
    }

    @Test fun localesConfigListsEveryTranslatedLanguage() {
        val locales = context.resources.getXml(R.xml.locales_config).collect("locale", "name")
        assertEquals(SupportedLanguages.TAGS.sorted(), locales.sorted())
        val translated = File("src/main/res").listFiles()!!
            .map { it.name }
            .filter { it.matches(Regex("values-[a-z]{2}(-r[A-Z]{2})?")) && File("src/main/res/$it/strings.xml").exists() }
            .map { it.removePrefix("values-").replace("-r", "-") }
        assertEquals((translated + "en").sorted(), locales.sorted())
    }
}
