package app.numbaseball.ads

/**
 * Frequency cap from the README / policy review: at most one interstitial per [matchesBetween] finished vs-computer
 * matches, at least [minIntervalMillis] apart, and only when leaving a result that was visible for [minResultVisibleMillis].
 * Process start counts as "just shown", so nothing appears soon after launch. Not thread-safe; use from the main thread.
 */
class InterstitialPolicy(
    private val now: () -> Long,
    private val matchesBetween: Int = 3,
    private val minIntervalMillis: Long = 4 * 60_000L,
    private val minResultVisibleMillis: Long = 1_000L,
) {
    private var finishedSinceLastAd = 0
    private var lastAdAt = now()

    fun onComputerMatchFinished() {
        finishedSinceLastAd++
    }

    /** Load ahead during the last match before one becomes due, so it is ready when the player leaves the result. */
    fun shouldPreload(): Boolean = finishedSinceLastAd >= matchesBetween - 1

    /** Due when leaving now (ignoring how long the result has been visible). */
    fun isDue(): Boolean = finishedSinceLastAd >= matchesBetween && now() - lastAdAt >= minIntervalMillis

    fun shouldShow(resultVisibleMillis: Long): Boolean =
        resultVisibleMillis >= minResultVisibleMillis &&
            finishedSinceLastAd >= matchesBetween &&
            now() - lastAdAt >= minIntervalMillis

    fun onShown() {
        finishedSinceLastAd = 0
        lastAdAt = now()
    }
}
