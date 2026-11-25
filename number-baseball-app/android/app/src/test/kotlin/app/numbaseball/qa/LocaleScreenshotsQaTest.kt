package app.numbaseball.qa

import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the tightest screens (home, match, settings, results, tutorial) in de, ru, fr, vi, ja to
 * `android/app/qa-screenshots/qa-locale-<screen>-<lang>.png`. A folder and prefix of their own: never `screenshots/`
 * (Roborazzi baselines), so `verifyRoborazziDebug` is unaffected.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class LocaleShots(private val screenId: String, private val lang: String) {
    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = QaScreens.all.filter { it.tight }.map { arrayOf<Any>(it.id) }
    }

    @get:Rule val compose = createComposeRule()

    @Test fun render() {
        val screen = QaScreens.byId.getValue(screenId)
        compose.setScreen(screen)
        compose.saveQaImage("qa-locale-$screenId-$lang")
        assertTrue(File("qa-screenshots/qa-locale-$screenId-$lang.png").length() > 1000)
    }
}

@Config(qualifiers = "de-w360dp-h800dp-xhdpi") class ShotsDe(id: String) : LocaleShots(id, "de")
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi") class ShotsRu(id: String) : LocaleShots(id, "ru")
@Config(qualifiers = "fr-w360dp-h800dp-xhdpi") class ShotsFr(id: String) : LocaleShots(id, "fr")
@Config(qualifiers = "vi-w360dp-h800dp-xhdpi") class ShotsVi(id: String) : LocaleShots(id, "vi")
@Config(qualifiers = "ja-w360dp-h800dp-xhdpi") class ShotsJa(id: String) : LocaleShots(id, "ja")
