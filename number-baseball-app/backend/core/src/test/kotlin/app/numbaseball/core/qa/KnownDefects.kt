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
    val OPEN: Map<String, Set<String>> = mapOf(
        // text symbols that are not emoji (music note, black star) and text-presentation symbols without U+FE0F
        "DEF-006" to setOf("E-117", "E-118", "E-120", "E-123", "E-124", "E-138", "E-139", "E-140"),
        // stacked combining marks accepted in nicknames (protocol 9.3 says letters and digits only)
        "DEF-007" to setOf("N-121"),
        "DEF-031" to setOf("judge:12a/123", "judge:112/123", "judge:123/1 3"),
        // NFKC turns Hangul compatibility jamo into conjoining jamo / composes them
        "DEF-035" to setOf("N-016", "N-032"),
        // U+2800 braille blank survives chat cleaning: blank-looking lines
        "DEF-037" to setOf("S-021", "fuzz:invisible U+2800"),
        // half-width katakana variant of a listed insult not caught in nicknames
        "DEF-039" to setOf("N-425"),
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
