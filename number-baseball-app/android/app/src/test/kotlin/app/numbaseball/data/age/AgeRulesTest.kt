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
    @Test fun birthYearAndMonthRule() {
        // DEF-051 owner decision: full years as of the current month (November 2026); the birth month counts as passed.
        fun band(y: Int, m: Int, min: Int = 14) = AgeRules.bandForBirth(y, m, 2026, 11, minimumAge = min)
        assertEquals(AgeBand.UNDER_MINIMUM, band(2013, 1))
        assertEquals(AgeBand.CHILD, band(2012, 11)) // turns 14 this month
        assertEquals(AgeBand.UNDER_MINIMUM, band(2012, 12)) // still 13 until next month
        assertEquals(AgeBand.CHILD, band(2012, 1))
        assertEquals(AgeBand.CHILD, band(2011, 12))
        assertEquals(AgeBand.TEEN, band(2010, 11))
        assertEquals(AgeBand.CHILD, band(2010, 12))
        assertEquals(AgeBand.TEEN, band(2009, 1))
        assertEquals(AgeBand.ADULT, band(2008, 11))
        assertEquals(AgeBand.TEEN, band(2008, 12))
        assertEquals(AgeBand.CHILD, band(2013, 11, min = 13))
        assertTrue(AgeRules.isValidBirth(2026, 11, 2026, 11))
        assertFalse(AgeRules.isValidBirth(2026, 12, 2026, 11))
        assertFalse(AgeRules.isValidBirth(1990, 0, 2026, 11))
        assertFalse(AgeRules.isValidBirth(1990, 13, 2026, 11))
        assertFalse(AgeRules.isValidBirth(1899, 5, 2026, 11))
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
        val repo = AgeRepository(store, { AgeSignal(16, 17) }, minimumAge = 14, scope = backgroundScope, currentMonth = { java.time.YearMonth.of(2026, 11) })
        assertEquals(AgeState.Unknown, repo.state.value)
        assertEquals(AgeBand.ADULT, repo.submitBirth(1990, 5))
        repo.refreshSignals()
        val state = repo.state.value as AgeState.Known
        assertEquals(AgeBand.ADULT, state.declared)
        assertEquals(AdAgeTreatment.TEEN, state.adTreatment)
    }
}
