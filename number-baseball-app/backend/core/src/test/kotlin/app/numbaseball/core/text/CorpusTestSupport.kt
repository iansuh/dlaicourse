package app.numbaseball.core.text

import java.io.File

/** Reads the QA-owned chat corpora in docs/qa/corpora (read-only use; QA owns the files and their own tests). */
internal object CorpusTestSupport {
    data class Row(val id: String, val lang: String, val expect: String, val term: String, val input: String)

    fun corpusDir(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val c = File(dir, "docs/qa/corpora")
            if (c.isDirectory) return c
            dir = dir.parentFile
        }
        return null
    }

    fun load(name: String): List<Row> {
        val f = File(corpusDir() ?: return emptyList(), name)
        if (!f.isFile) return emptyList()
        var header = true
        return f.readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            if (header) { header = false; return@mapNotNull null }
            val c = line.split('\t')
            if (c.size < 5) null else Row(c[0], c[1], c[2], c[3], decode(c[4]))
        }
    }

    fun decode(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch != '\\' || i + 1 >= s.length) { sb.append(ch); i++; continue }
            when (val n = s[i + 1]) {
                'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6 }
                'U' -> { val end = s.indexOf('}', i); sb.appendCodePoint(s.substring(i + 3, end).toInt(16)); i = end + 1 }
                'n' -> { sb.append('\n'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                '0' -> { sb.append('\u0000'); i += 2 }
                's' -> { sb.append(' '); i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                'R' -> {
                    val colon = s.indexOf(':', i)
                    val count = s.substring(i + 3, colon).toInt()
                    var j = colon + 1
                    val text = StringBuilder()
                    while (s[j] != ']') { if (s[j] == '\\' && s[j + 1] == ']') { text.append(']'); j += 2 } else { text.append(s[j]); j++ } }
                    repeat(count) { sb.append(decode(text.toString())) }
                    i = j + 1
                }
                else -> { sb.append(n); i += 2 }
            }
        }
        return sb.toString()
    }
}
