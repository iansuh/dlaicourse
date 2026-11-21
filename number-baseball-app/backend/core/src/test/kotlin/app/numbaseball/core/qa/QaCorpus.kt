package app.numbaseball.core.qa

import java.io.File

/**
 * Loader for the QA corpora in `docs/qa/corpora/` (owned by QA, see docs/qa/TEST_PLAN.md section 11).
 *
 * Format: UTF-8, tab-separated, lines starting with '#' are comments, the first other line is the header.
 * Text cells use these escapes (see [decode]): `\uXXXX`, `\U{X..X}`, `\n`, `\r`, `\t`, `\0`, `\s` (space), `\\`,
 * and `\R[N:TEXT]` (TEXT repeated N times, `\]` inside TEXT is a literal ']').
 */
object QaCorpus {
    val dir: File by lazy {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            val candidate = File(d, "docs/qa/corpora")
            if (candidate.isDirectory) return@lazy candidate
            d = d.parentFile
        }
        error("docs/qa/corpora not found above ${System.getProperty("user.dir")}")
    }

    /** All corpus files QA maintains. [QaCorporaWellFormedTest] checks each of them. */
    val FILES = listOf(
        "chat_profanity.tsv",
        "chat_false_positives.tsv",
        "chat_emoji.tsv",
        "chat_sanitize.tsv",
        "nicknames.tsv",
        "protocol_frames.tsv",
        "rest_requests.tsv",
    )

    class Row(val file: String, val line: Int, private val cells: Map<String, String>) {
        operator fun get(column: String): String = cells[column] ?: error("$file:$line has no column '$column'")
        fun has(column: String): Boolean = column in cells
        /** Decoded text of [column]; '-' stays '-' (callers decide whether '-' means "none"). */
        fun text(column: String): String = decode(get(column))
        /** '|' separated list in [column], decoded; '-' gives an empty list. */
        fun list(column: String): List<String> = get(column).let { if (it == "-") emptyList() else it.split('|').map(::decode) }
        val id: String get() = get("id")
        val columns: Set<String> get() = cells.keys
        override fun toString(): String = "$file:$line ${cells["id"]}"
    }

    private val cache = HashMap<String, List<Row>>()

    fun load(name: String): List<Row> = synchronized(cache) {
        cache.getOrPut(name) { parse(name, lines(name)) }
    }

    fun lines(name: String): List<String> = File(dir, name).readLines(Charsets.UTF_8)

    fun header(name: String): List<String> = lines(name).first { it.isNotEmpty() && !it.startsWith("#") }.split('\t')

    fun parse(name: String, lines: List<String>): List<Row> {
        var header: List<String>? = null
        val rows = ArrayList<Row>()
        lines.forEachIndexed { idx, line ->
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val cells = line.split('\t')
            val h = header
            if (h == null) {
                header = cells
                return@forEachIndexed
            }
            require(cells.size == h.size) { "$name:${idx + 1}: ${cells.size} cells but the header has ${h.size}" }
            rows += Row(name, idx + 1, h.zip(cells).toMap())
        }
        return rows
    }

    /** Resolves the corpus escapes. Throws [IllegalArgumentException] on a malformed escape. */
    fun decode(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            require(i + 1 < s.length) { "dangling backslash in <$s>" }
            when (s[i + 1]) {
                'n' -> { out.append('\n'); i += 2 }
                'r' -> { out.append('\r'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                '0' -> { out.append(0.toChar()); i += 2 }
                's' -> { out.append(' '); i += 2 }
                '\\' -> { out.append('\\'); i += 2 }
                ']' -> { out.append(']'); i += 2 }
                'u' -> {
                    require(i + 6 <= s.length) { "short \\u escape in <$s>" }
                    out.append(s.substring(i + 2, i + 6).toInt(16).toChar())
                    i += 6
                }
                'U' -> {
                    require(i + 2 < s.length && s[i + 2] == '{') { "\\U needs {hex} in <$s>" }
                    val close = s.indexOf('}', i + 3)
                    require(close > i + 3) { "unterminated \\U{ in <$s>" }
                    val hex = s.substring(i + 3, close)
                    require(hex.length in 1..6) { "bad \\U{$hex}" }
                    out.appendCodePoint(hex.toInt(16))
                    i = close + 1
                }
                'R' -> {
                    require(i + 2 < s.length && s[i + 2] == '[') { "\\R needs [N:TEXT] in <$s>" }
                    val colon = s.indexOf(':', i + 3)
                    require(colon > i + 3) { "\\R needs a count in <$s>" }
                    val n = s.substring(i + 3, colon).toInt()
                    var j = colon + 1
                    val body = StringBuilder()
                    while (true) {
                        require(j < s.length) { "unterminated \\R[ in <$s>" }
                        val ch = s[j]
                        if (ch == ']') break
                        if (ch == '\\') {
                            require(j + 1 < s.length) { "dangling backslash in \\R body" }
                            body.append(ch).append(s[j + 1])
                            j += 2
                        } else {
                            body.append(ch)
                            j++
                        }
                    }
                    val unit = decode(body.toString())
                    repeat(n) { out.append(unit) }
                    i = j + 1
                }
                else -> throw IllegalArgumentException("unknown escape \\${s[i + 1]} in <$s>")
            }
        }
        return out.toString()
    }
}
