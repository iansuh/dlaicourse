package app.numbaseball.server

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory token buckets keyed by string (for example "create:<ip>"). Nothing is persisted: IP addresses used
 * as keys never reach the database or the logs, and buckets are forgotten after they refill.
 */
class RateLimiter(private val clock: Clock) {
    private class Bucket(var tokens: Double, var updated: Long)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    /**
     * Takes one token from [key]'s bucket of size [capacity] that refills completely over [windowMillis].
     * Returns 0 when allowed, otherwise the milliseconds until the next token.
     */
    fun acquire(key: String, capacity: Int, windowMillis: Long): Long {
        val now = clock.now()
        val rate = capacity.toDouble() / windowMillis
        val bucket = buckets.computeIfAbsent(key) { Bucket(capacity.toDouble(), now) }
        synchronized(bucket) {
            bucket.tokens = minOf(capacity.toDouble(), bucket.tokens + (now - bucket.updated) * rate)
            bucket.updated = now
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                return 0
            }
            return ((1.0 - bucket.tokens) / rate).toLong().coerceAtLeast(1)
        }
    }

    /** Peeks without taking a token: true when [key] currently has no token left. */
    fun isExhausted(key: String, capacity: Int, windowMillis: Long): Boolean {
        val bucket = buckets[key] ?: return false
        synchronized(bucket) {
            val tokens = minOf(capacity.toDouble(), bucket.tokens + (clock.now() - bucket.updated) * capacity.toDouble() / windowMillis)
            return tokens < 1.0
        }
    }

    /** Drops full buckets (called by the maintenance job). */
    fun sweep() {
        val now = clock.now()
        buckets.entries.removeIf { now - it.value.updated > 24 * 3600 * 1000L }
    }
}
