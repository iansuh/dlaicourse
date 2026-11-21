package app.numbaseball.core.qa

import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.text.ChatFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * AC-CHAT-*: the shared `:core` ChatFilter (the server is authoritative and uses the same object, protocol 9.1)
 * against every QA chat corpus, plus universal invariants, idempotence and a fuzz run.
 */
class ChatFilterCorpusQaTest {

    private fun show(s: String) = "<$s> [${QaTextOracle.hex(s)}]"

    /**
     * protocol 9.1: profanity is replaced with '*', one per character. "Character" is read as a user-perceived character
     * (extended grapheme cluster, so "f" + combining overlay or half-width "ﾊﾞ" is one). Every cluster of the sanitized
     * text must appear unchanged or as exactly one '*'; nothing else may change (DEF-017 asks the spec to say this).
     */
    private fun maskAlignment(input: String, output: String): String? {
        val sanitized = ChatFilter.sanitize(input)
        val it = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
        it.setText(sanitized)
        var pos = 0
        var start = it.first()
        var end = it.next()
        while (end != java.text.BreakIterator.DONE) {
            val cluster = sanitized.substring(start, end)
            pos = when {
                output.startsWith(cluster, pos) -> pos + cluster.length
                pos < output.length && output[pos] == '*' -> pos + 1
                else -> return "cluster '${cluster}' at $start neither kept nor replaced by one '*'"
            }
            start = end
            end = it.next()
        }
        return if (pos == output.length) null else "output has extra text after position $pos"
    }

    private fun runProfanityFile(file: String) {
        val failures = LinkedHashMap<String, String>()
        val rows = QaCorpus.load(file)
        for (row in rows) {
            val input = row.text("input")
            val result = try {
                ChatFilter.clean(input)
            } catch (e: Exception) {
                failures[row.id] = "threw $e"; continue
            }
            val problems = ArrayList<String>()
            when (result) {
                is ChatFilter.Result.Rejected -> if (row["expect"] != "EITHER") problems += "rejected with ${result.code}"
                is ChatFilter.Result.Ok -> {
                    problems += QaTextOracle.chatViolations(result.text)
                    maskAlignment(input, result.text)?.let { problems += it }
                    when (row["expect"]) {
                        "MASK" -> {
                            if (result.text == input) problems += "not masked"
                            if (!result.masked) problems += "masked flag is false"
                            for (term in row.list("term")) if (!QaTextOracle.termIsGone(result.text, term)) problems += "'$term' still readable"
                        }
                        "CLEAN" -> {
                            if (result.text != input) problems += "changed"
                            if (result.masked) problems += "masked flag is true"
                        }
                    }
                    if (problems.isNotEmpty()) problems += "out=${show(result.text)}"
                }
            }
            if (problems.isNotEmpty()) failures[row.id] = "in=${show(input)} " + problems.joinToString("; ")
        }
        KnownDefects.check("ChatFilter on $file", rows.map { it.id }, failures)
    }

    @Test fun acChat01_profanityEvasionsAreMasked() = runProfanityFile("chat_profanity.tsv")

    @Test fun acChat02_innocentTextIsNeverMasked() = runProfanityFile("chat_false_positives.tsv")

    private fun runTextFile(file: String) {
        val failures = LinkedHashMap<String, String>()
        val rows = QaCorpus.load(file)
        for (row in rows) {
            val input = row.text("input")
            val must = row.list("must_remain")
            val exact = row["exact"].let { if (it == "-") null else row.text("exact") }
            val result = try {
                ChatFilter.clean(input)
            } catch (e: Exception) {
                failures[row.id] = "threw $e"; continue
            }
            val problems = ArrayList<String>()
            val expect = row["expect"]
            when (result) {
                is ChatFilter.Result.Rejected -> when {
                    expect == "REJECT_EMPTY" && result.code == ErrorCodes.CHAT_EMPTY -> Unit
                    expect == "REJECT_LONG" && result.code == ErrorCodes.CHAT_TOO_LONG -> Unit
                    expect == "STRIP" && must.isEmpty() && result.code == ErrorCodes.CHAT_EMPTY -> Unit
                    else -> problems += "rejected with ${result.code}, expected $expect"
                }
                is ChatFilter.Result.Ok -> {
                    val out = result.text
                    when (expect) {
                        "REJECT_EMPTY", "REJECT_LONG" -> problems += "accepted, expected $expect"
                        "KEEP" -> if (out != input) problems += "changed"
                    }
                    problems += QaTextOracle.chatViolations(out)
                    for (cp in QaTextOracle.codePoints(out)) if (QaTextOracle.isForbiddenInChat(cp)) problems += "emoji U+%04X left".format(cp)
                    for (m in must) if (!out.contains(m)) problems += "lost '${m}'"
                    if (exact != null && out != exact) problems += "expected exactly ${show(exact)}"
                    if (problems.isNotEmpty()) problems += "out=${show(out)}"
                }
            }
            if (problems.isNotEmpty()) failures[row.id] = "in=${show(input).take(300)} " + problems.distinct().joinToString("; ")
        }
        KnownDefects.check("ChatFilter on $file", rows.map { it.id }, failures)
    }

    @Test fun acChat03_emojiAreRemovedAndTextSymbolsSurvive() = runTextFile("chat_emoji.tsv")

    @Test fun acChat04_controlsInvisibleWhitespaceAndLimits() = runTextFile("chat_sanitize.tsv")

    @Test fun acChat05_cleaningIsIdempotent() {
        val failures = LinkedHashMap<String, String>()
        val ids = ArrayList<String>()
        for (file in listOf("chat_profanity.tsv", "chat_false_positives.tsv", "chat_emoji.tsv", "chat_sanitize.tsv")) {
            for (row in QaCorpus.load(file)) {
                ids += "idem:" + row.id
                val first = ChatFilter.clean(row.text("input")) as? ChatFilter.Result.Ok ?: continue
                val second = ChatFilter.clean(first.text)
                if (second != ChatFilter.Result.Ok(first.text, false) && second != ChatFilter.Result.Ok(first.text, true)) {
                    failures["idem:" + row.id] = "clean(clean(x)) = $second, first = ${show(first.text)}"
                } else if ((second as ChatFilter.Result.Ok).text != first.text) {
                    failures["idem:" + row.id] = "second pass changed ${show(first.text)} to ${show(second.text)}"
                }
            }
        }
        KnownDefects.check("ChatFilter idempotence", ids, failures)
    }

    @Test fun acChat06_fuzzNeverThrowsAndAlwaysKeepsTheInvariants() {
        val alphabet = intArrayOf(
            'a'.code, 'f'.code, 'u'.code, 'c'.code, 'k'.code, ' '.code, '.'.code, '*'.code, '1'.code, '\n'.code, '\t'.code, 0,
            0xAC00, 0xC2DC, 0xBC1C, 0x3145, 0x3142, 0x1109, 0x1175, 0x3164, 0x115F, 0xFFA0, 0x2800,
            0x0441, 0x0443, 0x0445, 0x0439, 0x0301, 0x0337, 0x20E3, 0xFE0F, 0xFE0E, 0x200B, 0x200D, 0x202E, 0x2066,
            0xFEFF, 0x00AD, 0x2028, 0x3000, 0x00A0, 0xD800, 0xDC00, 0x1F600, 0x1F3FD, 0x1F1F0, 0x1F1F7, 0xE0067,
            0x1FAE9, 0x2665, 0x266A, 0x00A9, 0x2B50, 0x6B7B, 0x306D, 0xFF46, 0x1D41F, 0x0111, 0x0323, 0xE000,
        )
        val rnd = Random(20261121)
        val failures = LinkedHashMap<String, String>()
        repeat(30_000) { n ->
            val len = rnd.nextInt(0, 140)
            val sb = StringBuilder()
            repeat(len) {
                val cp = alphabet[rnd.nextInt(alphabet.size)]
                if (cp in 0xD800..0xDFFF) sb.append(cp.toChar()) else sb.appendCodePoint(cp)
            }
            val input = sb.toString()
            val r = try {
                ChatFilter.clean(input)
            } catch (e: Exception) {
                failures["fuzz-$n"] = "threw $e for ${show(input)}"; return@repeat
            }
            if (r is ChatFilter.Result.Ok) {
                // Key failures by kind of violation so a known root cause is one item, not thousands.
                for (v in QaTextOracle.chatViolations(r.text)) {
                    failures.getOrPut("fuzz:$v") { "e.g. in=${show(input).take(400)} out=${show(r.text).take(400)}" }
                }
            }
        }
        KnownDefects.check("ChatFilter fuzz", listOf("fuzz:invisible U+2800"), failures)
    }

    @Test fun acChat07_limitIsCodePointsAfterCleaning() {
        val hundred = "a".repeat(100)
        assertEquals(ChatFilter.Result.Ok(hundred, false), ChatFilter.clean(hundred))
        assertEquals(ChatFilter.Result.Rejected(ErrorCodes.CHAT_TOO_LONG), ChatFilter.clean(hundred + "a"))
        val supplementary = String(Character.toChars(0x20000)).repeat(100)
        assertTrue(ChatFilter.clean(supplementary) is ChatFilter.Result.Ok)
        assertEquals(ChatFilter.Result.Rejected(ErrorCodes.CHAT_EMPTY), ChatFilter.clean(""))
    }
}
