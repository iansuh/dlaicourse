package app.numbaseball.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Languages with translations in the app; keep in sync with res/xml/locales_config.xml. */
object SupportedLanguages {
    val TAGS: List<String> = listOf("ko", "en")
}

/** Per-app language. null means "follow the system". */
interface LanguageSettings {
    fun current(): String?
    fun set(tag: String?)
}

/** AppCompat stores the choice itself (AppLocalesMetadataHolderService on API < 33, the system on 33+). */
class AppCompatLanguageSettings : LanguageSettings {
    override fun current(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else locales[0]?.language
    }

    override fun set(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }
}
