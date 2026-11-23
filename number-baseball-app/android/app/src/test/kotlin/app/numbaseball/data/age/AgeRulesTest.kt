package app.numbaseball.data.age

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgeRulesTest {
    @Test fun birthYearRuleFollowsTheDesign() {
        // 01 §0: allowed when current year − birth year ≥ 14 (they turn 14 at some point this year).
        assertEquals(AgeBand.UNDER_MINIMUM, AgeRules.bandForBirthYear(2013, 2026, minimumAge = 14))
        assertEquals(AgeBand.CHILD, AgeRules.bandForBirthYear(2012, 2026, minimumAge = 14))
        assertEquals(AgeBand.CHILD, AgeRules.bandForBirthYear(2011, 2026, minimumAge = 14))
        assertEquals(AgeBand.TEEN, AgeRules.bandForBirthYear(2010, 2026, minimumAge = 14))
        assertEquals(AgeBand.TEEN, AgeRules.bandForBirthYear(2009, 2026, minimumAge = 14))
        assertEquals(AgeBand.ADULT, AgeRules.bandForBirthYear(2008, 2026, minimumAge = 14))
        assertEquals(AgeBand.CHILD, AgeRules.bandForBirthYear(2013, 2026, minimumAge = 13))
    }

    @Test fun adTreatmentByBand() {
        assertEquals(AdAgeTreatment.CHILD, AgeRules.adTreatment(AgeBand.CHILD))
        assertEquals(AdAgeTreatment.TEEN, AgeRules.adTreatment(AgeBand.TEEN))
        assertEquals(AdAgeTreatment.UNSPECIFIED, AgeRules.adTreatment(AgeBand.ADULT))
    }

    @Test fun playAgeSignalsOnlyTighten() {
        assertEquals(AgeBand.TEEN, AgeState.Known(AgeBand.ADULT, AgeSignal(16, 17)).band)
        assertEquals(AgeBand.CHILD, AgeState.Known(AgeBand.TEEN, AgeSignal(13, 15)).band)
        assertEquals(AgeBand.CHILD, AgeState.Known(AgeBand.CHILD, AgeSignal(18, null)).band)
        assertEquals(AgeBand.ADULT, AgeState.Known(AgeBand.ADULT, AgeSignal(null, null)).band)
    }

    @Test fun signalsUnder13RefuseOnlineFeatures() {
        assertFalse(AgeState.Known(AgeBand.ADULT, AgeSignal(0, 12)).onlineAllowed)
        assertTrue(AgeState.Known(AgeBand.ADULT, AgeSignal(13, 15)).onlineAllowed)
        assertTrue(AgeState.Known(AgeBand.CHILD).onlineAllowed)
    }

    @Test fun repositoryKeepsTheBandAndMergesSignals() = runTest(UnconfinedTestDispatcher()) {
        val store = object : AgeStore {
            val value = kotlinx.coroutines.flow.MutableStateFlow<AgeBand?>(null)
            override val band = value
            override suspend fun save(band: AgeBand) {
                value.value = band
            }
        }
        val repo = AgeRepository(store, { AgeSignal(16, 17) }, minimumAge = 14, scope = backgroundScope, currentYear = { 2026 })
        assertEquals(AgeState.Unknown, repo.state.value)
        assertEquals(AgeBand.ADULT, repo.submitBirthYear(1990))
        repo.refreshSignals()
        val state = repo.state.value as AgeState.Known
        assertEquals(AgeBand.ADULT, state.declared)
        assertEquals(AdAgeTreatment.TEEN, state.adTreatment)
    }
}
