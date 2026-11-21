package app.numbaseball.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPolicyTest {
    private var now = 0L
    private val policy = InterstitialPolicy(now = { now })

    private fun finish(matches: Int) = repeat(matches) { policy.onComputerMatchFinished() }

    @Test fun needsThreeFinishedMatchesAndFourMinutes() {
        finish(2)
        now = 10 * 60_000
        assertFalse(policy.shouldShow(resultVisibleMillis = 5_000))
        finish(1)
        assertTrue(policy.shouldShow(resultVisibleMillis = 5_000))
    }

    @Test fun nothingSoonAfterLaunchEvenAfterThreeQuickMatches() {
        finish(3)
        now = 3 * 60_000
        assertFalse(policy.shouldShow(resultVisibleMillis = 5_000))
        now = 4 * 60_000
        assertTrue(policy.shouldShow(resultVisibleMillis = 5_000))
    }

    @Test fun onlyAfterTheResultWasVisibleForASecond() {
        finish(3)
        now = 10 * 60_000
        assertFalse(policy.shouldShow(resultVisibleMillis = 999))
        assertTrue(policy.shouldShow(resultVisibleMillis = 1_000))
    }

    @Test fun showingResetsBothCaps() {
        finish(3)
        now = 10 * 60_000
        policy.onShown()
        finish(3)
        now += 3 * 60_000
        assertFalse(policy.shouldShow(resultVisibleMillis = 5_000))
        now += 60_000
        assertTrue(policy.shouldShow(resultVisibleMillis = 5_000))
    }

    @Test fun preloadsDuringTheLastMatchBeforeOneIsDue() {
        finish(1)
        assertFalse(policy.shouldPreload())
        finish(1)
        assertTrue(policy.shouldPreload())
    }
}
