package app.numbaseball.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test fun followsTheProtocolSteps() {
        val policy = ReconnectPolicy(jitter = 0.0)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 5_000L, 5_000L), (1..5).map { policy.delayMillis(it) })
    }

    @Test fun flooding1008WaitsTheLongestStep() {
        assertEquals(5_000L, ReconnectPolicy(jitter = 0.0).delayMillis(1, closeCode = 1008))
    }

    @Test fun jitterOnlyAddsALittle() {
        val delay = ReconnectPolicy(jitter = 0.1).delayMillis(1)
        assertTrue(delay in 1_000L..1_100L)
    }

    @Test fun neverReconnectsAfterAuthOrSessionEnd() {
        assertEquals(StopReason.UNAUTHORIZED, ReconnectPolicy.stopReasonFor(null, 401))
        assertEquals(StopReason.BANNED, ReconnectPolicy.stopReasonFor(null, 403))
        assertEquals(StopReason.SESSION_REPLACED, ReconnectPolicy.stopReasonFor(4000, null))
        assertEquals(StopReason.BANNED, ReconnectPolicy.stopReasonFor(4003, null))
        assertEquals(StopReason.ACCOUNT_DELETED, ReconnectPolicy.stopReasonFor(4004, null))
        assertNull(ReconnectPolicy.stopReasonFor(1001, null))
        assertNull(ReconnectPolicy.stopReasonFor(1008, null))
        assertNull(ReconnectPolicy.stopReasonFor(null, 502))
    }
}
