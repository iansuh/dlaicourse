package app.numbaseball.core.text

import java.text.Normalizer
import java.util.Locale

/**
 * Word-list profanity detection and masking for ko / en / ja / zh / es / fr / de / pt / ru / vi.
 *
 * This is hygiene, not moderation: it catches listed words and the usual evasions —
 * spacing ("f u c k", "f.u.c.k", "시 발"), stretched letters ("fuuuck"), zero-width and invisible filler
 * characters, full-width and other compatibility forms ("ｆｕｃｋ"), accents and combining marks ("fück"),
 * simple leetspeak ("sh1t", "$hit") and Latin look-alikes inside Cyrillic words ("cyкa").
 *
 * How it works: the text is split into segments (a base character plus its combining marks). Every segment is
 * folded three ways (plain Latin, exact, Cyrillic look-alike) into "skeletons" whose characters remember the
 * segment they came from, so a match can be masked in the original text.
 */
object ProfanityFilter {

    internal enum class Mode {
        /** Word start, any ending ("fucking"). */
        PREFIX,
        /** Whole word only. */
        WORD,
        /** Anywhere, separators allowed between characters (Hangul, kana, Han). */
        ANYWHERE,
    }

    internal enum class Fold {
        /** Accents stripped, lower case, leetspeak digits/symbols as letters. Most Latin-script words. */
        PLAIN,
        /** NFKC + lower case, accents kept. Vietnamese, Spanish ñ, CJK. */
        EXACT,
        /** EXACT plus Latin/digit look-alikes mapped to Cyrillic. Russian. */
        CYRILLIC,
    }

    internal class Entry(val word: String, val mode: Mode, val fold: Fold) {
        /** Folded code points; SPACE marks "optional separators here". */
        val chars: IntArray = foldWord(word, fold)
    }

    private const val SPACE = -1
    private const val MAX_GAP = 2
    private const val MAX_SPACE_GAP = 3
    private const val MAX_SUFFIX = 16

    // ---------------------------------------------------------------- word lists

    private val PREFIX_PLAIN = listOf(
        // en
        "fuck", "fuk", "fvck", "motherfuck", "dumbfuck", "shit", "bullshit", "dipshit", "horseshit", "bitch",
        "cunt", "cvnt", "asshole", "arsehole", "bastard", "dickhead", "nigger", "nigga", "faggot", "whore",
        "slut", "retard", "wanker",
        // de
        "fick", "huren", "wichser", "missgeburt", "schlampe",
    )
    private val WORD_PLAIN = listOf(
        // en
        "fck", "fcking", "fag", "fags", "twat", "twats", "pussy", "kys", "stfu",
        // es
        "puta", "putas", "puto", "putos", "mierda", "joder", "jodete", "pendejo", "pendeja", "pendejos",
        "cabron", "cabrona", "cabrones", "gilipollas", "maricon", "hijueputa", "hdp", "chinga", "chingada",
        "verga", "culero",
        // fr
        "merde", "putain", "connard", "connasse", "salope", "encule", "pute", "putes", "batard", "nique", "ntm", "fdp",
        // de
        "scheisse", "scheiße", "arschloch", "arschlocher", "fotze", "hure", "spast",
        // pt
        "porra", "caralho", "foda", "fodase", "fodasse", "merda", "buceta", "viado", "cuzao", "arrombado", "pqp", "vsf",
        // ru transliterations
        "cyka", "suka", "blyat", "blyad", "pizdec", "nahui", "nahuy",
    )
    private val WORD_EXACT = listOf(
        // es
        "coño",
        // vi
        "địt", "đụ", "đụ má", "lồn", "cặc", "đéo", "đĩ", "buồi", "đm", "đmm", "đcm", "vcl", "vkl", "clm", "óc chó",
    )
    private val PREFIX_CYRILLIC = listOf(
        "хуй", "хуе", "хуё", "хуя", "хуи", "нахуй", "похуй", "нихуя", "охуе", "ебат", "ебан", "ебал", "ёбан", "еблан",
        "заеб", "уеб", "выеб", "долбоеб", "долбоёб", "бляд", "блят", "мудак", "мудил", "пидор", "пидар", "пидр",
        "гандон", "залуп", "шлюх", "сучк", "сучар",
    )
    private val WORD_CYRILLIC = listOf("сука", "суки", "суку", "суке", "сукой", "бля")
    private val ANYWHERE_CYRILLIC = listOf("пизд")
    private val ANYWHERE_EXACT = listOf(
        // ko
        "시발", "씨발", "씨바", "씨팔", "시팔", "쓰발", "썅", "병신", "븅신", "지랄", "좆", "존나", "졸라", "개새끼", "개새", "새끼",
        "미친놈", "미친년", "닥쳐", "꺼져", "엿먹", "느금", "니애미", "니미", "애미", "창녀", "씹새", "씹할", "씹년", "씹놈", "씹창",
        "염병", "섹스", "김치녀", "한남충", "ㅅㅂ", "ㅆㅂ", "ㅂㅅ", "ㅈㄹ", "ㅈㄴ", "ㅗ",
        // ja
        "死ね", "氏ね", "くたばれ", "クソ", "くそ", "バカ", "ばかやろう", "ちんこ", "チンコ", "ちんぽ", "チンポ", "まんこ", "マンコ",
        "きちがい", "キチガイ", "ガイジ",
        // zh
        "傻逼", "傻b", "煞笔", "操你", "肏", "他妈的", "他媽的", "特么的", "草泥马", "草泥馬", "你妈的", "你媽的", "操你妈",
        "尼玛", "婊子", "贱人", "賤人", "去死", "王八蛋", "鸡巴", "雞巴", "狗娘养的", "日你",
    )

    /** Innocent words that contain a listed word; a match inside one of these is ignored. */
    private val EXCEPTIONS = listOf(
        "시발점", "shiitake", "shitake", "scunthorpe",
    )

    private val allEntries: List<Entry> = buildList {
        PREFIX_PLAIN.forEach { add(Entry(it, Mode.PREFIX, Fold.PLAIN)) }
        WORD_PLAIN.forEach { add(Entry(it, Mode.WORD, Fold.PLAIN)) }
        WORD_EXACT.forEach { add(Entry(it, Mode.WORD, Fold.EXACT)) }
        PREFIX_CYRILLIC.forEach { add(Entry(it, Mode.PREFIX, Fold.CYRILLIC)) }
        WORD_CYRILLIC.forEach { add(Entry(it, Mode.WORD, Fold.CYRILLIC)) }
        ANYWHERE_CYRILLIC.forEach { add(Entry(it, Mode.ANYWHERE, Fold.CYRILLIC)) }
        ANYWHERE_EXACT.forEach { add(Entry(it, Mode.ANYWHERE, Fold.EXACT)) }
    }

    /** Entries per fold, indexed by first folded code point. */
    private val byFirst: Map<Fold, Map<Int, List<Entry>>> =
        Fold.entries.associateWith { f -> allEntries.filter { it.fold == f && it.chars.isNotEmpty() }.groupBy { it.chars[0] } }

    private val exceptionChars: Map<Fold, List<IntArray>> =
        Fold.entries.associateWith { f -> EXCEPTIONS.map { foldWord(it, f) } }

    // ---------------------------------------------------------------- public API

    /** True if [text] contains a listed word. [strict] (for nicknames) also finds words glued inside longer words. */
    fun containsProfanity(text: String, strict: Boolean = false): Boolean = findSegments(text, strict).any { it }

    /** Replaces each character of a listed word with '*'. Whitespace inside a match is kept. */
    fun mask(text: String): String {
        val segs = Segments(text)
        val masked = findSegments(segs, strict = false)
        if (masked.none { it }) return text
        val sb = StringBuilder(text.length)
        for (s in 0 until segs.count) {
            val start = segs.start[s]
            val end = segs.end[s]
            val cp = text.codePointAt(start)
            if (masked[s] && !Character.isWhitespace(cp) && !Character.isSpaceChar(cp) && !isIgnorable(cp)) sb.append('*')
            else if (!masked[s] || !isIgnorable(cp)) sb.append(text, start, end)
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- segments and skeletons

    /** A base code point plus the combining marks after it. */
    private class Segments(val text: String) {
        val start: IntArray
        val end: IntArray
        val count: Int

        init {
            val s = ArrayList<Int>()
            val e = ArrayList<Int>()
            var i = 0
            while (i < text.length) {
                val segStart = i
                i += Character.charCount(text.codePointAt(i))
                while (i < text.length) {
                    val cp = text.codePointAt(i)
                    if (!isCombining(cp)) break
                    i += Character.charCount(cp)
                }
                s += segStart
                e += i
            }
            start = s.toIntArray()
            end = e.toIntArray()
            count = s.size
        }
    }

    private class Skeleton(val chars: IntArray, val seg: IntArray) {
        val size: Int get() = chars.size
    }

    private fun skeleton(segs: Segments, fold: Fold): Skeleton {
        val chars = ArrayList<Int>(segs.count)
        val seg = ArrayList<Int>(segs.count)
        for (s in 0 until segs.count) {
            val piece = segs.text.substring(segs.start[s], segs.end[s])
            for (cp in foldSegment(piece, fold)) {
                chars += cp
                seg += s
            }
        }
        return Skeleton(chars.toIntArray(), seg.toIntArray())
    }

    private fun foldWord(word: String, fold: Fold): IntArray {
        val out = ArrayList<Int>()
        val segs = Segments(word)
        for (s in 0 until segs.count) {
            val piece = word.substring(segs.start[s], segs.end[s])
            if (piece == " ") { out += SPACE; continue }
            foldSegment(piece, fold).forEach { out += it }
        }
        return out.toIntArray()
    }

    private fun foldSegment(piece: String, fold: Fold): IntArray {
        val first = piece.codePointAt(0)
        if (isIgnorable(first)) return IntArray(0)
        val normalized = when (fold) {
            Fold.PLAIN -> Normalizer.normalize(piece, Normalizer.Form.NFKD)
            Fold.EXACT, Fold.CYRILLIC -> Normalizer.normalize(piece, Normalizer.Form.NFKC)
        }.lowercase(Locale.ROOT)
        val out = ArrayList<Int>(2)
        var i = 0
        while (i < normalized.length) {
            var cp = normalized.codePointAt(i)
            i += Character.charCount(cp)
            if (isCombining(cp) || isIgnorable(cp)) continue
            when (fold) {
                Fold.PLAIN -> cp = LEET[cp] ?: cp
                Fold.CYRILLIC -> cp = CYRILLIC_LOOKALIKE[cp] ?: cp
                Fold.EXACT -> Unit
            }
            out += cp
        }
        return out.toIntArray()
    }

    private val LEET: Map<Int, Int> = mapOf(
        '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's', '!' to 'i',
    ).entries.associate { it.key.code to it.value.code }

    private val CYRILLIC_LOOKALIKE: Map<Int, Int> = mapOf(
        'a' to 'а', 'b' to 'в', 'c' to 'с', 'e' to 'е', 'h' to 'н', 'k' to 'к', 'm' to 'м', 'n' to 'п', 'o' to 'о',
        'p' to 'р', 'r' to 'г', 't' to 'т', 'u' to 'и', 'w' to 'ш', 'x' to 'х', 'y' to 'у', 'ё' to 'е',
        '0' to 'о', '3' to 'з', '6' to 'б',
    ).entries.associate { it.key.code to it.value.code }

    private fun isCombining(cp: Int): Boolean {
        val t = Character.getType(cp)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.ENCLOSING_MARK.toInt() ||
            t == Character.COMBINING_SPACING_MARK.toInt() || cp == 0xFF9E || cp == 0xFF9F
    }

    /** Invisible characters that are dropped before matching (zero-width, joiners, fillers, BOM, soft hyphen). */
    internal fun isIgnorable(cp: Int): Boolean =
        Character.getType(cp) == Character.FORMAT.toInt() ||
            cp == 0x034F || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 ||
            cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF

    private fun isSeparator(cp: Int): Boolean {
        if (cp == SPACE) return true
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) return true
        return when (Character.getType(cp).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
            Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION, Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL,
            Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL -> true
            else -> false
        }
    }

    private fun isSpace(cp: Int): Boolean = Character.isWhitespace(cp) || Character.isSpaceChar(cp)

    /** A letter of the script family the fold is about (word boundaries are script-aware: "ㅋㅋfuck" still matches). */
    private fun isWordLetter(cp: Int, fold: Fold): Boolean {
        if (!Character.isLetter(cp)) return false
        val script = Character.UnicodeScript.of(cp)
        return when (fold) {
            Fold.PLAIN, Fold.EXACT -> script == Character.UnicodeScript.LATIN
            Fold.CYRILLIC -> script == Character.UnicodeScript.CYRILLIC
        }
    }

    // ---------------------------------------------------------------- matching

    private fun findSegments(text: String, strict: Boolean): BooleanArray = findSegments(Segments(text), strict)

    private fun findSegments(segs: Segments, strict: Boolean): BooleanArray {
        val masked = BooleanArray(segs.count)
        for (fold in Fold.entries) {
            val sk = skeleton(segs, fold)
            val index = byFirst.getValue(fold)
            val exceptions = exceptionRanges(sk, fold)
            fun mark(from: Int, to: Int) { // skeleton [from, to)
                if (to <= from) return
                if (exceptions.any { from >= it.first && to <= it.last + 1 }) return
                for (s in sk.seg[from]..sk.seg[to - 1]) masked[s] = true
            }
            // Compact matches (no whitespace inside a word except for ANYWHERE entries).
            for (i in 0 until sk.size) {
                val candidates = index[sk.chars[i]] ?: continue
                for (e in candidates) {
                    val end = matchCompact(sk.chars, sk.size, i, e) ?: continue
                    when (e.mode) {
                        Mode.ANYWHERE -> mark(i, end)
                        Mode.PREFIX -> {
                            if (!strict && i > 0 && isWordLetter(sk.chars[i - 1], fold)) continue
                            var stop = end
                            while (stop < sk.size && stop - end < MAX_SUFFIX && isWordLetter(sk.chars[stop], fold)) stop++
                            mark(i, stop)
                        }
                        Mode.WORD -> {
                            val glued = (i > 0 && isWordLetter(sk.chars[i - 1], fold)) ||
                                (end < sk.size && isWordLetter(sk.chars[end], fold))
                            if (glued && !(strict && e.chars.size >= 5)) continue
                            mark(i, end)
                        }
                    }
                }
            }
            // Spaced-out words: runs of single letters separated by separators ("f u c k", "f. u. c. k").
            if (fold != Fold.EXACT) for (run in spacedRuns(sk)) {
                val letters = IntArray(run.size) { sk.chars[run[it]] }
                for (i in letters.indices) {
                    val candidates = index[letters[i]] ?: continue
                    for (e in candidates) {
                        if (e.mode == Mode.ANYWHERE) continue
                        val end = matchCompact(letters, letters.size, i, e) ?: continue
                        mark(run[i], run[end - 1] + 1)
                    }
                }
            }
        }
        return masked
    }

    /**
     * Matches entry [e] at [start]. Between letters up to [MAX_GAP] separators are skipped (whitespace only for
     * ANYWHERE entries or where the entry has a space); a letter may repeat ("fuuuck") unless the entry itself
     * doubles it next. Returns the exclusive end index or null.
     */
    private fun matchCompact(chars: IntArray, size: Int, start: Int, e: Entry): Int? {
        val w = e.chars
        var j = start
        var k = 0
        while (k < w.size) {
            val c = w[k]
            if (c == SPACE) {
                var g = 0
                while (j < size && g < MAX_SPACE_GAP && isSeparator(chars[j])) { j++; g++ }
                k++
                continue
            }
            if (k > 0 && w[k - 1] != SPACE) {
                var g = 0
                while (j < size && g < MAX_GAP && isSeparator(chars[j]) &&
                    (e.mode == Mode.ANYWHERE || !isSpace(chars[j]))
                ) { j++; g++ }
            }
            if (j >= size || chars[j] != c) return null
            j++
            if (k + 1 >= w.size || w[k + 1] != c) {
                while (j < size && chars[j] == c) j++
            }
            k++
        }
        return j
    }

    /** Index lists of runs (≥ 3) of isolated letters with 1–3 separators between them. */
    private fun spacedRuns(sk: Skeleton): List<IntArray> {
        val runs = ArrayList<IntArray>()
        val n = sk.size
        fun isolated(p: Int): Boolean {
            val c = sk.chars[p]
            if (!Character.isLetter(c)) return false
            val prevOk = p == 0 || isSeparator(sk.chars[p - 1])
            val nextOk = p == n - 1 || isSeparator(sk.chars[p + 1])
            return prevOk && nextOk
        }
        var p = 0
        while (p < n) {
            if (!isolated(p)) { p++; continue }
            val run = arrayListOf(p)
            var q = p + 1
            while (true) {
                var gap = 0
                while (q < n && isSeparator(sk.chars[q]) && gap <= MAX_SPACE_GAP) { q++; gap++ }
                if (gap in 1..MAX_SPACE_GAP && q < n && isolated(q)) { run += q; q++ } else break
            }
            if (run.size >= 3) runs += run.toIntArray()
            p = run.last() + 1
        }
        return runs
    }

    private fun exceptionRanges(sk: Skeleton, fold: Fold): List<IntRange> {
        val out = ArrayList<IntRange>()
        for (ex in exceptionChars.getValue(fold)) {
            if (ex.isEmpty()) continue
            var i = 0
            while (i + ex.size <= sk.size) {
                var ok = true
                for (k in ex.indices) if (sk.chars[i + k] != ex[k]) { ok = false; break }
                if (ok) out += i until i + ex.size
                i++
            }
        }
        return out
    }
}
