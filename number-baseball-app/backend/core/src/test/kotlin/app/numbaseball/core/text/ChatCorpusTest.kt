package app.numbaseball.core.text

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the QA chat corpora (docs/qa/corpora, owned by QA) through [ChatFilter] with a simple oracle:
 * MASK rows must change and leave nothing detectable, CLEAN rows must come back unchanged.
 * QA's own tests apply a stricter skeleton oracle.
 */
class ChatCorpusTest {
    @Test fun corpora() {
        val rows = CorpusTestSupport.load("chat_profanity.tsv") + CorpusTestSupport.load("chat_false_positives.tsv")
        val failures = rows.mapNotNull { r ->
            val out = when (val res = ChatFilter.clean(r.input)) {
                is ChatFilter.Result.Ok -> res.text
                is ChatFilter.Result.Rejected -> "<${res.code}>"
            }
            val bad = when (r.expect) {
                "MASK" -> out == r.input || ProfanityFilter.containsProfanity(out)
                "CLEAN" -> out != r.input
                else -> false
            }
            if (bad) "${r.id} ${r.expect}: [${r.input}] -> [$out]" else null
        }
        println("Chat corpus: ${rows.size} rows, ${failures.size} failures")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
