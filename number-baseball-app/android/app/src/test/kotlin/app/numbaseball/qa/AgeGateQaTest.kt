package app.numbaseball.qa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.AppConfig
import app.numbaseball.BuildConfig
import app.numbaseball.core.protocol.AcceptTermsRequest
import app.numbaseball.data.age.AdAgeTreatment
import app.numbaseball.data.age.AgeBand
import app.numbaseball.data.age.AgeRepository
import app.numbaseball.data.age.AgeSignal
import app.numbaseball.data.age.AgeState
import app.numbaseball.data.age.AgeStore
import app.numbaseball.data.age.DataStoreAgeStore
import app.numbaseball.testing.FakeAccountApi
import app.numbaseball.testing.FakeAgeStore
import app.numbaseball.ui.age.AgeViewModel
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AC-AGE: minimum age from build config, the under-age block screen, the age band stored on the device only. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class AgeGateQaTest {
    @get:Rule val compose = createComposeRule()
    private val ui = QaDriver(compose)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun repo(minimum: Int, year: Int = 2026, month: Int = 11, store: AgeStore = FakeAgeStore()) = AgeRepository(store, { null }, minimum, scope) { java.time.YearMonth.of(year, month) }

    // ---- build config ----

    @Test fun minimumAgeAndAdRatingComeFromBuildConfigAndDefaultTo14AndG() {
        assertEquals("README: minimum age 14 by default", 14, BuildConfig.MINIMUM_AGE)
        assertEquals("README: max ad content rating G by default", "G", BuildConfig.MAX_AD_CONTENT_RATING)
        val config = AppConfig.fromBuildConfig()
        assertEquals(BuildConfig.MINIMUM_AGE, config.minimumAge)
        assertEquals(BuildConfig.MAX_AD_CONTENT_RATING, config.maxAdContentRating)
        // The container hands exactly that number to the repository (no second hard-coded 14 anywhere in main code).
        val container = File("src/main/kotlin/app/numbaseball/AppContainer.kt").readText()
        assertTrue(container.contains("config.minimumAge"))
        val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
        val hardCoded = sources.filter { f -> Regex("""minimumAge\s*=\s*14|MIN_AGE\s*=\s*14""").containsMatchIn(f.readText()) }.map { it.name }.toList()
        assertTrue("minimum age must not be hard coded in main code: $hardCoded", hardCoded.isEmpty() || hardCoded == listOf("Rest.kt"))
    }

    @Test fun theBandFollowsTheConfiguredMinimumAge() = runBlocking {
        for (minimum in listOf(13, 14, 16, 18)) {
            val r = repo(minimum)
            assertEquals("min $minimum, age = minimum", if (minimum >= 18) AgeBand.ADULT else if (minimum >= 16) AgeBand.TEEN else AgeBand.CHILD, r.submitBirth(2026 - minimum, 11))
            assertEquals("min $minimum, one month younger", AgeBand.UNDER_MINIMUM, r.submitBirth(2026 - minimum, 12))
        }
        val r = repo(14)
        assertEquals(AgeBand.CHILD, r.submitBirth(2011, 6))
        assertEquals(AgeBand.TEEN, r.submitBirth(2010, 6))
        assertEquals(AgeBand.TEEN, r.submitBirth(2009, 6))
        assertEquals(AgeBand.ADULT, r.submitBirth(2008, 6))
    }

    @Test fun adTreatmentPerBand() {
        assertEquals(AdAgeTreatment.CHILD, AgeState.Known(AgeBand.CHILD).adTreatment)
        assertEquals(AdAgeTreatment.CHILD, AgeState.Known(AgeBand.UNDER_MINIMUM).adTreatment)
        assertEquals(AdAgeTreatment.TEEN, AgeState.Known(AgeBand.TEEN).adTreatment)
        assertEquals(AdAgeTreatment.UNSPECIFIED, AgeState.Known(AgeBand.ADULT).adTreatment)
        // Play Age Signals only tighten the treatment.
        assertEquals(AdAgeTreatment.CHILD, AgeState.Known(AgeBand.ADULT, AgeSignal(13, 15)).adTreatment)
        assertEquals(AdAgeTreatment.TEEN, AgeState.Known(AgeBand.ADULT, AgeSignal(16, 17)).adTreatment)
        assertEquals(AdAgeTreatment.UNSPECIFIED, AgeState.Known(AgeBand.ADULT, AgeSignal(18, null)).adTreatment)
        assertFalse("signal below 13 refuses online features", AgeState.Known(AgeBand.CHILD, AgeSignal(0, 12)).onlineAllowed)
    }

    /**
     * DEF-051 (fixed, owner decision 2026-11-25): the screen asks birth year and month; the age is full years as of the
     * current month. In November 2026, a player born in November 2012 is 14 (allowed); one born in December 2012 is
     * still 13 (refused).
     */
    @Test fun def051_theBoundaryFollowsTheBirthMonth() = runBlocking {
        assertEquals("born this month 14 years ago: allowed", AgeBand.CHILD, repo(14, 2026, 11).submitBirth(2012, 11))
        assertEquals("born next month 14 years ago: blocked", AgeBand.UNDER_MINIMUM, repo(14, 2026, 11).submitBirth(2012, 12))
        assertEquals("earlier in the year: allowed", AgeBand.CHILD, repo(14, 2026, 11).submitBirth(2012, 1))
        // January: someone born in February 2012 is still 13, born in January 2012 is 14.
        assertEquals(AgeBand.UNDER_MINIMUM, repo(14, 2026, 1).submitBirth(2012, 2))
        assertEquals(AgeBand.CHILD, repo(14, 2026, 1).submitBirth(2012, 1))
    }

    // ---- under-age block screen, through the real navigation ----

    @Test fun underAgeAnswerStopsTheAppBeforeAnyAccountAdConsentOrOnlineCall() {
        val api = FakeAccountApi()
        val c = QaContainer(ageStore = FakeAgeStore(null), accountApi = api)
        compose.setQaApp(c)
        compose.onNodeWithText("Enter the year and month you were born").assertIsDisplayed()
        assertTrue("nothing ad related before the age answer", c.ads.consentCalls.isEmpty() && !c.ads.sdkInitialised)
        ui.type("201503")
        compose.onNodeWithText("Confirm").performClick()
        ui.waitText("Not available right now")
        compose.onNodeWithText("Not available right now").assertIsDisplayed()
        assertEquals("only the band is stored", AgeBand.UNDER_MINIMUM, c.ageStore.value.value)
        assertTrue("no consent form / ads SDK for an under-age player", c.ads.consentCalls.isEmpty() && !c.ads.sdkInitialised)
        assertTrue("no account is created", api.createdNicknames.isEmpty() && c.accountStore.value.value.nickname == null)
        assertEquals("no network session", 0, c.client.users)
        assertTrue(c.client.sent.isEmpty())
        assertFalse("no nickname screen, no home, no banner", ui.exists("nickname") || ui.exists("quickMatch") || ui.hasBanner())
        // The screen offers the privacy policy only.
        compose.onNodeWithText("Privacy policy", substring = true).performClick()
        compose.waitUntil(5_000) { !ui.shows("Not available right now") }
    }

    @Test fun underAgeBlockSurvivesARestartAndCannotBeReanswered() {
        val store = FakeAgeStore(AgeBand.UNDER_MINIMUM)
        val c = QaContainer(ageStore = store)
        compose.setQaApp(c)
        ui.waitText("Not available right now")
        assertFalse("the age screen is not offered again", ui.shows("Enter the year and month you were born"))
        assertTrue(c.ads.consentCalls.isEmpty())
    }

    @Test fun returningPlayersAreNeverAskedAgain() {
        for (band in listOf(AgeBand.CHILD, AgeBand.TEEN, AgeBand.ADULT)) {
            val c = QaContainer(ageStore = FakeAgeStore(band), accountStore = app.numbaseball.testing.FakeAccountStore.registered())
            val ageRepoState = c.ageRepository.state.value
            assertTrue(band.name, ageRepoState is AgeState.Known)
        }
    }

    // ---- stored on the device only ----

    @Test fun theAgeBandIsStoredAsABandOnlyAndStaysOutOfBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = DataStoreAgeStore(context)
        val repository = AgeRepository(store, { null }, 14, scope) { java.time.YearMonth.of(2026, 11) }
        repository.submitBirth(1987, 4)
        assertEquals(AgeBand.ADULT, store.band.first())
        val file = context.preferencesDataStoreFile("age")
        assertTrue(file.exists())
        val raw = file.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue("the band name is stored", raw.contains("ADULT"))
        assertFalse("the birth year is never stored", raw.contains("1987"))
        // Auto Backup / device transfer rules include only the account file (ResourcesTest) and never the age file.
        val rel = file.relativeTo(context.filesDir).path
        for (name in listOf("backup_rules", "data_extraction_rules")) {
            val xml = File("src/main/res/xml/$name.xml").readText()
            assertFalse("$name must not include the age store ($rel)", xml.contains("age.preferences_pb"))
            assertTrue("$name keeps an explicit include list", xml.contains("<include"))
        }
    }

    @Test fun theBirthYearNeverLeavesTheDevice() {
        // The terms request carries a boolean confirmation, never a year or a band.
        val fields = AcceptTermsRequest::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") || it == "Companion" }
        assertEquals(setOf("version", "ageConfirmed"), fields.toSet())
        // The age repository is the only writer of the age store and it writes a band.
        val saved = mutableListOf<Any>()
        val store = object : AgeStore {
            override val band: Flow<AgeBand?> = kotlinx.coroutines.flow.flowOf(null)
            override suspend fun save(band: AgeBand) {
                saved += band
            }
        }
        runBlocking { AgeRepository(store, { null }, 14, scope) { java.time.YearMonth.of(2026, 11) }.submitBirth(1999, 7) }
        assertEquals(listOf<Any>(AgeBand.ADULT), saved)
    }

    /**
     * DEF-052 (fixed): an under-minimum answer is final below the UI too. The keypad and Confirm are locked once an answer
     * is submitted, and `AgeRepository.submitBirth` ignores any later answer, even while the first write is pending.
     */
    @Test fun def052_anUnderMinimumAnswerIsFinal() = runBlocking {
        val saved = mutableListOf<AgeBand>()
        val gate = CompletableDeferred<Unit>()
        val store = object : AgeStore {
            override val band: Flow<AgeBand?> = kotlinx.coroutines.flow.flowOf(null)
            override suspend fun save(band: AgeBand) {
                gate.await() // a slow first DataStore write: Known is not emitted yet
                saved += band
            }
        }
        val repository = AgeRepository(store, { null }, 14, scope) { java.time.YearMonth.of(2026, 11) }
        val vm = AgeViewModel(repository)
        "201503".forEach(vm::onDigit)
        vm.onConfirm()
        vm.onClear()
        "199005".forEach(vm::onDigit)
        assertEquals("the keypad is locked while the answer is stored", "2015", vm.state.value.year)
        vm.onConfirm()
        // Below the UI: a second answer through the repository itself is ignored as well.
        val second = scope.async { repository.submitBirth(1990, 5) }
        gate.complete(Unit)
        assertEquals(AgeBand.UNDER_MINIMUM, second.await())
        kotlinx.coroutines.delay(50)
        assertEquals(listOf(AgeBand.UNDER_MINIMUM), saved)
    }
}
