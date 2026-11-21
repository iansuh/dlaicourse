package app.numbaseball.core.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The corpora are test inputs: they get the same scrutiny as code. */
class QaCorporaWellFormedTest {

    private val expectedHeaders = mapOf(
        "chat_profanity.tsv" to listOf("id", "lang", "expect", "term", "input", "note"),
        "chat_false_positives.tsv" to listOf("id", "lang", "expect", "term", "input", "note"),
        "chat_emoji.tsv" to listOf("id", "expect", "input", "must_remain", "exact", "note"),
        "chat_sanitize.tsv" to listOf("id", "expect", "input", "must_remain", "exact", "note"),
        "nicknames.tsv" to listOf("id", "expect", "reason", "input", "stored", "note"),
        "protocol_frames.tsv" to listOf("id", "layer", "state", "expect", "frame", "note"),
        "rest_requests.tsv" to listOf("id", "method", "path", "auth", "body", "status", "code", "note"),
    )

    private val expectValues = mapOf(
        "chat_profanity.tsv" to setOf("MASK", "CLEAN", "EITHER"),
        "chat_false_positives.tsv" to setOf("CLEAN", "EITHER"),
        "chat_emoji.tsv" to setOf("STRIP", "KEEP", "REJECT_EMPTY"),
        "chat_sanitize.tsv" to setOf("CLEANED", "KEEP", "REJECT_EMPTY", "REJECT_LONG"),
        "nicknames.tsv" to setOf("VALID", "INVALID", "PROFANITY", "EITHER"),
    )

    private val languages = setOf("ko", "en", "ja", "zh-CN", "es", "fr", "de", "pt-BR", "ru", "vi")

    @Test fun everyFileHasTheDocumentedHeader() {
        assertEquals(expectedHeaders.keys, QaCorpus.FILES.toSet())
        for ((file, header) in expectedHeaders) assertEquals(file, header, QaCorpus.header(file))
    }

    @Test fun idsAreUniqueAcrossAllFilesAndEveryCellDecodes() {
        val seen = HashMap<String, String>()
        for (file in QaCorpus.FILES) {
            val rows = QaCorpus.load(file)
            assertTrue("$file is empty", rows.isNotEmpty())
            for (row in rows) {
                val previous = seen.put(row.id, file)
                if (previous != null) fail("duplicate id ${row.id} in $file and $previous")
                for (c in row.columns) try {
                    QaCorpus.decode(row[c])
                } catch (e: IllegalArgumentException) {
                    fail("$row column $c: ${e.message}")
                }
            }
        }
    }

    @Test fun expectColumnsUseTheVocabulary() {
        for ((file, allowed) in expectValues) for (row in QaCorpus.load(file)) {
            assertTrue("$row expect=${row["expect"]}", row["expect"] in allowed)
        }
        for (row in QaCorpus.load("protocol_frames.tsv")) {
            assertTrue("$row layer", row["layer"] in setOf("DECODE", "TOLERATED", "SERVER", "SIZE"))
            assertTrue("$row state", row["state"] in setOf("ANY", "QUEUED", "LOBBY_HOST", "CHOOSING", "GUESSING"))
        }
    }

    @Test fun noRawInvisibleOrControlCharactersInCorpusFiles() {
        // Everything invisible must be written as an escape so reviewers can see it.
        for (file in QaCorpus.FILES) QaCorpus.lines(file).forEachIndexed { i, line ->
            for (cp in line.codePoints().toArray()) {
                val t = Character.getType(cp)
                val bad = cp != '\t'.code && (
                    t == Character.CONTROL.toInt() || t == Character.FORMAT.toInt() || t == Character.NON_SPACING_MARK.toInt() ||
                        t == Character.ENCLOSING_MARK.toInt() || t == Character.SURROGATE.toInt() || t == Character.PRIVATE_USE.toInt() ||
                        t == Character.LINE_SEPARATOR.toInt() || t == Character.PARAGRAPH_SEPARATOR.toInt() || QaTextOracle.isInvisible(cp)
                    )
                if (bad) fail("$file:${i + 1} contains raw U+%04X; write it as an escape".format(cp))
            }
        }
    }

    @Test fun everyLanguageHasEvasionsAndTraps() {
        val profanity = QaCorpus.load("chat_profanity.tsv")
        val traps = QaCorpus.load("chat_false_positives.tsv")
        for (lang in languages) {
            val masks = profanity.count { it["lang"] == lang && it["expect"] == "MASK" }
            val cleans = traps.count { it["lang"] == lang && it["expect"] == "CLEAN" }
            assertTrue("$lang has only $masks MASK rows", masks >= 10)
            assertTrue("$lang has only $cleans CLEAN trap rows", cleans >= 5)
        }
        for (row in profanity + traps) assertTrue("$row lang", row["lang"] in languages + "mixed")
    }

    @Test fun nicknameCorpusAgreesWithTheStructuralOracle() {
        for (row in QaCorpus.load("nicknames.tsv")) {
            val input = row.text("input")
            val problem = QaTextOracle.nicknameProblem(input)
            when (row["expect"]) {
                "VALID", "PROFANITY" -> assertEquals("$row is structurally valid", null, problem)
                "INVALID" -> if (row["reason"] in setOf("LENGTH", "CHARSET", "INVISIBLE")) {
                    assertTrue("$row must be structurally invalid", problem != null)
                }
            }
            if (row["expect"] == "VALID" && row["stored"] != "-") {
                assertEquals("$row stored form", row.text("stored"), QaTextOracle.normalizeNickname(input))
            }
        }
    }

    @Test fun keepRowsAlreadySatisfyTheChatInvariants() {
        for (file in listOf("chat_emoji.tsv", "chat_sanitize.tsv")) for (row in QaCorpus.load(file)) {
            if (row["expect"] == "KEEP") {
                assertEquals("$row exact", row.text("input"), row.text("exact"))
                assertEquals("$row", emptyList<String>(), QaTextOracle.chatViolations(row.text("input")))
            }
        }
        for (file in listOf("chat_profanity.tsv", "chat_false_positives.tsv")) for (row in QaCorpus.load(file)) {
            if (row["expect"] == "CLEAN") assertEquals("$row", emptyList<String>(), QaTextOracle.chatViolations(row.text("input")))
        }
    }

    @Test fun maskRowsReallyContainTheirTerm() {
        // Guards the skeleton oracle: an unmasked input must FAIL the "term is gone" check, or the check proves nothing.
        val weak = QaCorpus.load("chat_profanity.tsv").filter { it["expect"] == "MASK" }
            .filter { row -> row.list("term").all { QaTextOracle.termIsGone(row.text("input"), it) } }
            .map { it.id }
        // Evasions the skeleton cannot see through are still guarded by "output != input" in the filter test.
        val allowed = setOf("P-KO-007", "P-KO-008", "P-KO-009", "P-KO-019", "P-KO-022", "P-KO-047", "P-EN-024",
            "P-JA-005", "P-JA-014", "P-PT-004", "P-RU-009", "P-ZH-002", "P-ZH-003", "P-VI-002", "P-MX-006", "P-EN-050")
        val unexpected = weak - allowed
        assertTrue("skeleton does not see the term in: $unexpected", unexpected.isEmpty())
    }

    @Test fun decoderHandlesEveryEscape() {
        assertEquals("a b", QaCorpus.decode("a\\sb"))
        assertEquals("aaa", QaCorpus.decode("\\R[3:a]"))
        assertEquals("[[]]", QaCorpus.decode("\\R[2:[]\\R[2:\\]]"))
        assertEquals(String(Character.toChars(0x1F600)), QaCorpus.decode("\\U{1F600}"))
        assertEquals(1, QaCorpus.decode("\\U{D800}").length)
        assertEquals("\\", QaCorpus.decode("\\\\"))
        assertEquals("x\ny\tz", QaCorpus.decode("x\\ny\\tz"))
    }
}
