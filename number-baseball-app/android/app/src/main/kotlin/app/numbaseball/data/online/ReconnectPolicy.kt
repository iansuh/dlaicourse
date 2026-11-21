package app.numbaseball.data.online

import kotlin.random.Random

/**
 * Reconnect delays from docs/protocol.md §10: 1 s, 2 s, 4 s, then every 5 s. A small random jitter spreads clients after a
 * server restart. Close code 1008 (flooding) waits at least the longest step.
 */
class ReconnectPolicy(
    private val steps: List<Long> = listOf(1_000, 2_000, 4_000, 5_000),
    private val jitter: Double = 0.1,
    private val random: Random = Random.Default,
) {
    /** Delay before reconnect number [failures] (1 = first try after a drop). */
    fun delayMillis(failures: Int, closeCode: Int? = null): Long {
        val base = steps[(failures.coerceAtLeast(1) - 1).coerceAtMost(steps.lastIndex)]
        val step = if (closeCode == POLICY_VIOLATION) maxOf(base, steps.last()) else base
        return step + (step * jitter * random.nextDouble()).toLong()
    }

    companion object {
        const val POLICY_VIOLATION = 1008
        const val SESSION_REPLACED = 4000
        const val BANNED = 4003
        const val ACCOUNT_DELETED = 4004

        /** Non-null for close codes and HTTP statuses after which the app must not reconnect on its own. */
        fun stopReasonFor(closeCode: Int?, httpStatus: Int?): StopReason? = when {
            httpStatus == 401 -> StopReason.UNAUTHORIZED
            httpStatus == 403 -> StopReason.BANNED
            closeCode == SESSION_REPLACED -> StopReason.SESSION_REPLACED
            closeCode == BANNED -> StopReason.BANNED
            closeCode == ACCOUNT_DELETED -> StopReason.ACCOUNT_DELETED
            else -> null
        }
    }
}
