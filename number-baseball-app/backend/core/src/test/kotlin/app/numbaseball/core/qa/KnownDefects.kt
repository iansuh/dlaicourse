package app.numbaseball.core.qa

import org.junit.Assert.fail

/**
 * Strict expected-failure bookkeeping for defects that QA tests have CONFIRMED and logged in docs/qa/DEFECTS.md.
 *
 * Policy (TEST_PLAN.md section 4): a failing QA assertion is never weakened. While its defect is open, the listed
 * items are reported on stdout instead of breaking everybody's build; any OTHER failure still fails the test, and as
 * soon as a listed item passes the test fails until the item is removed here (so a fix is never silently lost).
 * Release gate: no Blocker or Major defect may keep items in [OPEN].
 */
object KnownDefects {
    /** Defect id -> the exact failing items (corpus row ids or case keys) it explains. Keep in sync with DEFECTS.md. */
    val OPEN: Map<String, Set<String>> = mapOf<String, Set<String>>(
        // Fixed and removed so far: DEF-006, DEF-007 (marks), DEF-031, DEF-035 (partly), DEF-037, DEF-039.
    )

    private fun defectOf(item: String): String? = OPEN.entries.firstOrNull { item in it.value }?.key

    /**
     * [checked] = every item key this test evaluated; [failures] = item key -> what went wrong.
     */
    fun check(what: String, checked: Collection<String>, failures: Map<String, String>) {
        val unexpected = failures.filterKeys { defectOf(it) == null }
        val tolerated = failures.keys.filter { defectOf(it) != null }
        val fixed = checked.filter { defectOf(it) != null && it !in failures }
        val msg = StringBuilder()
        if (unexpected.isNotEmpty()) {
            msg.append("$what: ${unexpected.size} failure(s):\n")
            unexpected.forEach { (k, v) -> msg.append("  $k: $v\n") }
        }
        if (fixed.isNotEmpty()) {
            msg.append("$what: now passing, remove from KnownDefects.OPEN and update DEFECTS.md: ")
            msg.append(fixed.joinToString { "$it (${defectOf(it)})" }).append('\n')
        }
        if (tolerated.isNotEmpty()) {
            println("[KNOWN DEFECTS] $what: ${tolerated.size} item(s) still fail: " +
                tolerated.groupBy { defectOf(it) }.entries.joinToString { "${it.key}=${it.value}" })
            tolerated.forEach { println("    $it: ${failures[it]}") }
        }
        if (msg.isNotEmpty()) fail(msg.toString())
    }
}
