package app.numbaseball.core.qa

import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.text.Nicknames
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/** AC-ACC-NICK-*: `:core` Nicknames.validate against nicknames.tsv (protocol 9.3 + DEF-007 / DEF-035 QA rules). */
class NicknameCorpusQaTest {

    private fun show(s: String) = "<$s> [${QaTextOracle.hex(s)}]"

    private fun okShapeProblems(name: String): List<String> {
        val p = ArrayList<String>()
        val cps = QaTextOracle.codePoints(name)
        if (cps.size !in 2..12) p += "stored length ${cps.size}"
        if (cps.any { QaTextOracle.isInvisible(it) || Character.getType(it) == Character.FORMAT.toInt() }) p += "stored invisible character"
        if (name != name.trim()) p += "stored untrimmed"
        return p
    }

    @Test fun acNick01_corpus() {
        val failures = LinkedHashMap<String, String>()
        val rows = QaCorpus.load("nicknames.tsv")
        for (row in rows) {
            val input = row.text("input")
            val r = try {
                Nicknames.validate(input)
            } catch (e: Exception) {
                failures[row.id] = "threw $e"; continue
            }
            val problems = ArrayList<String>()
            when (row["expect"]) {
                "VALID" -> when (r) {
                    is Nicknames.Result.Ok -> {
                        if (row["stored"] != "-" && r.nickname != row.text("stored")) problems += "stored ${show(r.nickname)}, expected ${show(row.text("stored"))}"
                        problems += okShapeProblems(r.nickname)
                    }
                    is Nicknames.Result.Invalid -> problems += "rejected ${r.code} (${r.reason})"
                }
                "INVALID" -> if (r !is Nicknames.Result.Invalid || r.code != ErrorCodes.INVALID_NICKNAME) problems += "expected invalid_nickname, got $r"
                "PROFANITY" -> if (r !is Nicknames.Result.Invalid || r.code != ErrorCodes.NICKNAME_PROFANITY) problems += "expected nickname_profanity, got $r"
                "EITHER" -> if (r is Nicknames.Result.Ok) problems += okShapeProblems(r.nickname)
            }
            if (problems.isNotEmpty()) failures[row.id] = "in=${show(input)} " + problems.joinToString("; ")
        }
        KnownDefects.check("Nicknames on nicknames.tsv", rows.map { it.id }, failures)
    }

    @Test fun acNick02_acceptedNamesAreStableUnderRevalidation() {
        val failures = LinkedHashMap<String, String>()
        val ids = ArrayList<String>()
        for (row in QaCorpus.load("nicknames.tsv")) {
            val first = Nicknames.validate(row.text("input")) as? Nicknames.Result.Ok ?: continue
            ids += "stable:" + row.id
            val second = Nicknames.validate(first.nickname)
            if (second != first) failures["stable:" + row.id] = "validate(${show(first.nickname)}) = $second"
        }
        KnownDefects.check("Nicknames idempotence", ids, failures)
    }

    @Test fun acNick03_fuzzNeverThrowsAndNeverStoresInvisibleText() {
        val alphabet = intArrayOf('a'.code, 'Z'.code, '1'.code, ' '.code, '#'.code, '_'.code, 0xAC00, 0x3131, 0x314F, 0x3164, 0x1160,
            0xFFA0, 0x200B, 0x202E, 0x0301, 0x0E31, 0x0E2A, 0xFF41, 0x337F, 0xFDFA, 0x1F600, 0xD800, 0x0430, 0x4E00, 0x30FC)
        val rnd = Random(7)
        val failures = LinkedHashMap<String, String>()
        repeat(30_000) { n ->
            val sb = StringBuilder()
            repeat(rnd.nextInt(0, 16)) {
                val cp = alphabet[rnd.nextInt(alphabet.size)]
                if (cp in 0xD800..0xDFFF) sb.append(cp.toChar()) else sb.appendCodePoint(cp)
            }
            val input = sb.toString()
            val r = try {
                Nicknames.validate(input)
            } catch (e: Exception) {
                failures["nfuzz-$n"] = "threw $e for ${show(input)}"; return@repeat
            }
            if (r is Nicknames.Result.Ok) okShapeProblems(r.nickname).takeIf { it.isNotEmpty() }?.let {
                failures["nfuzz-$n"] = "$it for ${show(input)} -> ${show(r.nickname)}"
            }
        }
        KnownDefects.check("Nicknames fuzz", emptyList(), failures)
    }

    @Test fun acNick04_uniquenessKeyIgnoresCaseIndependentOfDeviceLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")) // Turkish dotted/dotless i
            assertEquals("title", Nicknames.key("TITLE"))
            assertEquals(Nicknames.key("Mina"), Nicknames.key("MINA"))
        } finally {
            Locale.setDefault(saved)
        }
        assertEquals("Mina#0420", Nicknames.display("Mina", 420))
        assertEquals("Mina#0001", Nicknames.display("Mina", 1))
    }
}
