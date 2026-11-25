package app.numbaseball.server.qa

import org.junit.Assert.fail

/**
 * Strict expected-failure bookkeeping for SERVER defects confirmed by the QA scenarios and logged in docs/qa/DEFECTS.md
 * (server-side twin of the core `KnownDefects`; core test classes are not visible to this module).
 *
 * Policy (TEST_PLAN.md section 4): an assertion is never weakened. While a defect is open, the full scenario still runs and
 * its [AssertionError] is reported on stdout instead of breaking the build. Any OTHER failure still fails the test, and as
 * soon as a listed scenario passes it fails until the entry is removed here (a fix is never silently lost).
 * Release gate: no Blocker or Major defect may keep entries in [OPEN].
 */
object KnownServerDefects {
    /** Defect id -> scenario keys (test method names) that fail because of it. Keep in sync with DEFECTS.md. */
    val OPEN: Map<String, Set<String>> = mapOf(
        "DEF-048" to setOf("acRev03_botGamesRecordNoStatsForAnyone"),
    )

    private fun defectOf(key: String): String? = OPEN.entries.firstOrNull { key in it.value }?.key

    /** Runs the scenario [body] under the known-defect policy. */
    fun scenario(key: String, body: () -> Unit) {
        val defect = defectOf(key)
        try {
            body()
        } catch (e: AssertionError) {
            if (defect == null) throw e
            println("[KNOWN DEFECT] $key ($defect) still fails: ${e.message?.lineSequence()?.firstOrNull()}")
            return
        }
        if (defect != null) fail("$key: now passing; remove it from KnownServerDefects.OPEN ($defect) and update DEFECTS.md")
    }
}
