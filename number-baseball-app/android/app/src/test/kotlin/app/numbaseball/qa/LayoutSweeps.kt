package app.numbaseball.qa

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.numbaseball.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout sweeps (AC-L10N-04/05, AC-WIN-*, AC-FONT-01): every screen and state of the sample data, rendered in one
 * language / window / font scale per class, measuring clipped or overflowing text (`TextLayoutResult.hasVisualOverflow`,
 * ellipsis, outside the window) and the reachability of the key controls. Findings go to `build/qa/<suite>.tsv`; the
 * ones logged in docs/qa/DEFECTS.md are the baseline (`src/test/resources/qa/layout-baseline.tsv`): the test fails on a
 * new finding and on a baseline entry that no longer occurs (fixed, update the baseline and DEFECTS.md).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class LayoutSweep(private val screenId: String, private val suite: String, private val small: Boolean, private val language: String) {
    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = QaScreens.all.map { arrayOf<Any>(it.id) }

        private val templates = mutableMapOf<String, Templates>()
    }

    @get:Rule val compose = createComposeRule()

    @Test fun sweep() {
        val screen = QaScreens.byId.getValue(screenId)
        compose.setScreen(screen, small)
        val res = RuntimeEnvironment.getApplication().resources
        val t = synchronized(templates) { templates.getOrPut(language) { Templates(res) } }
        val findings = compose.measureText(screenId, t, language in setOf("ja", "zh", "ko")) + compose.checkKeys(screen, res, if (small) 0 else screen.ime)
        appendQaLog(suite, findings.map { it.line() })
        val actual = findings.map { "${it.kind}|${it.key}" }.toSortedSet()
        val known = LayoutBaseline.known(suite, screenId)
        val unexpected = actual - known
        val fixed = known - actual
        assertTrue("$suite $screenId: NEW findings (log them in DEFECTS.md, then add to the baseline): $unexpected\n" + findings.filter { "${it.kind}|${it.key}" in unexpected }.joinToString("\n") { it.line() }, unexpected.isEmpty())
        assertTrue("$suite $screenId: baseline entries no longer occur (fixed? remove them): $fixed", fixed.isEmpty())
    }
}

/** The findings already logged as defects: `suite<TAB>screen<TAB>kind|key` per line. */
object LayoutBaseline {
    private val lines: List<List<String>> by lazy {
        val stream = LayoutBaseline::class.java.getResourceAsStream("/qa/layout-baseline.tsv") ?: return@lazy emptyList()
        stream.bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
    }

    fun known(suite: String, screen: String): Set<String> = lines.filter { it[0] == suite && it[1] == screen }.map { it[2] }.toSet()
}

private const val PHONE = "w360dp-h800dp-xhdpi"
private const val NARROW = "w320dp-h568dp-xhdpi"
private const val SMALL = "w360dp-h320dp-xhdpi"

// ---- phone 360 x 800, all ten languages -------------------------------------------------------------------------
@Config(qualifiers = "en-$PHONE") class PhoneEn(id: String) : LayoutSweep(id, "phone-en", false, "en")
@Config(qualifiers = "ko-$PHONE") class PhoneKo(id: String) : LayoutSweep(id, "phone-ko", false, "ko")
@Config(qualifiers = "ja-$PHONE") class PhoneJa(id: String) : LayoutSweep(id, "phone-ja", false, "ja")
@Config(qualifiers = "b+zh+Hans-$PHONE") class PhoneZh(id: String) : LayoutSweep(id, "phone-zh", false, "zh")
@Config(qualifiers = "es-$PHONE") class PhoneEs(id: String) : LayoutSweep(id, "phone-es", false, "es")
@Config(qualifiers = "fr-$PHONE") class PhoneFr(id: String) : LayoutSweep(id, "phone-fr", false, "fr")
@Config(qualifiers = "de-$PHONE") class PhoneDe(id: String) : LayoutSweep(id, "phone-de", false, "de")
@Config(qualifiers = "pt-$PHONE") class PhonePt(id: String) : LayoutSweep(id, "phone-pt", false, "pt")
@Config(qualifiers = "ru-$PHONE") class PhoneRu(id: String) : LayoutSweep(id, "phone-ru", false, "ru")
@Config(qualifiers = "vi-$PHONE") class PhoneVi(id: String) : LayoutSweep(id, "phone-vi", false, "vi")

// ---- narrowest supported width 320 dp, the five tightest languages ------------------------------------------------
@Config(qualifiers = "ja-$NARROW") class NarrowJa(id: String) : LayoutSweep(id, "narrow-ja", false, "ja")
@Config(qualifiers = "fr-$NARROW") class NarrowFr(id: String) : LayoutSweep(id, "narrow-fr", false, "fr")
@Config(qualifiers = "de-$NARROW") class NarrowDe(id: String) : LayoutSweep(id, "narrow-de", false, "de")
@Config(qualifiers = "ru-$NARROW") class NarrowRu(id: String) : LayoutSweep(id, "narrow-ru", false, "ru")
@Config(qualifiers = "vi-$NARROW") class NarrowVi(id: String) : LayoutSweep(id, "narrow-vi", false, "vi")

// ---- split-screen / pop-up window 360 x 320 -------------------------------------------------------------------
@Config(qualifiers = "en-$SMALL") class SmallEn(id: String) : LayoutSweep(id, "small-en", true, "en")
@Config(qualifiers = "ko-$SMALL") class SmallKo(id: String) : LayoutSweep(id, "small-ko", true, "ko")
@Config(qualifiers = "ja-$SMALL") class SmallJa(id: String) : LayoutSweep(id, "small-ja", true, "ja")
@Config(qualifiers = "fr-$SMALL") class SmallFr(id: String) : LayoutSweep(id, "small-fr", true, "fr")
@Config(qualifiers = "de-$SMALL") class SmallDe(id: String) : LayoutSweep(id, "small-de", true, "de")
@Config(qualifiers = "ru-$SMALL") class SmallRu(id: String) : LayoutSweep(id, "small-ru", true, "ru")
@Config(qualifiers = "vi-$SMALL") class SmallVi(id: String) : LayoutSweep(id, "small-vi", true, "vi")

// ---- font scale 200 % ------------------------------------------------------------------------------------------
@Config(qualifiers = "en-$PHONE", fontScale = 2.0f) class Font200En(id: String) : LayoutSweep(id, "font200-en", false, "en")
@Config(qualifiers = "ko-$PHONE", fontScale = 2.0f) class Font200Ko(id: String) : LayoutSweep(id, "font200-ko", false, "ko")
@Config(qualifiers = "ja-$PHONE", fontScale = 2.0f) class Font200Ja(id: String) : LayoutSweep(id, "font200-ja", false, "ja")
@Config(qualifiers = "fr-$PHONE", fontScale = 2.0f) class Font200Fr(id: String) : LayoutSweep(id, "font200-fr", false, "fr")
@Config(qualifiers = "de-$PHONE", fontScale = 2.0f) class Font200De(id: String) : LayoutSweep(id, "font200-de", false, "de")
@Config(qualifiers = "ru-$PHONE", fontScale = 2.0f) class Font200Ru(id: String) : LayoutSweep(id, "font200-ru", false, "ru")
@Config(qualifiers = "vi-$PHONE", fontScale = 2.0f) class Font200Vi(id: String) : LayoutSweep(id, "font200-vi", false, "vi")

// ---- the worst case: 200 % font in the 360 x 320 window ---------------------------------------------------------
@Config(qualifiers = "en-$SMALL", fontScale = 2.0f) class SmallFont200En(id: String) : LayoutSweep(id, "small-font200-en", true, "en")
@Config(qualifiers = "ko-$SMALL", fontScale = 2.0f) class SmallFont200Ko(id: String) : LayoutSweep(id, "small-font200-ko", true, "ko")
@Config(qualifiers = "de-$SMALL", fontScale = 2.0f) class SmallFont200De(id: String) : LayoutSweep(id, "small-font200-de", true, "de")
