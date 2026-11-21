package app.numbaseball.core.text

import java.text.Normalizer
import java.util.Locale

/**
 * Word-list profanity detection and masking for ko / en / ja / zh / es / fr / de / pt / ru / vi.
 *
 * This is hygiene, not moderation. It catches the listed words and the usual evasions:
 * - spacing: punctuation between letters ("f.u.c.k", "시.발") and runs of single characters separated by spaces
 *   ("f u c k", "시 발"); multi-letter fragments are never joined ("push it", "발표 시 발음" stay clean);
 * - stretched letters in alphabetic scripts ("fuuuck", "сууука");
 * - zero-width, joiner, bidi and filler characters; full-width, circled, mathematical and half-width forms (NFKC);
 * - accents and combining marks ("fück", "f̷u̷c̷k̷"), Hangul spelled in jamo ("ㅅㅣㅂㅏㄹ");
 * - leetspeak ("sh1t", "s1ut", "$hit", "put@") and look-alike letters from other scripts ("fuсk" with Cyrillic с,
 *   "cуka" with Latin letters in a Russian word).
 * Word boundaries are script-aware, so "fuck시발" and "我fuck了" are caught while "Scunthorpe", "Asuka",
 * "シネマ" or "닥쳐오는" are not.
 *
 * How it works: the text is split into segments (a base character plus its combining marks). Every segment is folded
 * three ways (plain Latin, exact, Cyrillic) into skeletons whose characters remember their segments, so a match found
 * in a skeleton can be masked in the original text.
 */
object ProfanityFilter {

    internal enum class Mode {
        /** Word start, any ending ("fucking"). */
        PREFIX,
        /** Whole word only. */
        WORD,
        /** Anywhere (Hangul / Han / kana compounds). */
        ANYWHERE,
    }

    internal enum class Fold {
        /** NFKD, accents dropped, lower case, look-alikes folded to Latin. Latin-script words. */
        PLAIN,
        /** NFKC, lower case, accents kept, jamo composed. Vietnamese, Spanish ñ, Hangul, kana, Han. */
        EXACT,
        /** NFKC, lower case, Latin look-alikes folded to Cyrillic. Russian. */
        CYRILLIC,
    }

    internal enum class Family { LATIN, CYRILLIC, HANGUL, KANA, HAN, OTHER }

    internal class Entry(val word: String, val mode: Mode, val fold: Fold) {
        /** Folded code points; [SPACE] marks "optional separators here". */
        val chars: IntArray = foldWord(word, fold)
        val family: Family = chars.firstOrNull { it != SPACE }?.let { familyOf(it) } ?: Family.OTHER
        val letterCount: Int = chars.count { it != SPACE }
        /** Repeated letters ("fuuuck") are tolerated only in alphabetic scripts. */
        val stretchable: Boolean = family == Family.LATIN || family == Family.CYRILLIC
    }

    private const val SPACE = -1
    private const val MAX_GAP = 2
    private const val MAX_SPACE_GAP = 3
    private const val MAX_SUFFIX = 16

    // ---------------------------------------------------------------- word lists

    private val PREFIX_PLAIN = listOf(
        // en
        "fuck", "fvck", "motherfuck", "dumbfuck", "shit", "bullshit", "dipshit", "horseshit", "bitch",
        "cunt", "cvnt", "asshole", "arsehole", "bastard", "dickhead", "nigger", "nigga", "faggot", "whore",
        "slut", "retard", "wanker",
        // de
        "fick", "wichser", "missgeburt", "schlampe",
    )
    private val WORD_PLAIN = listOf(
        // en
        "fuk", "fuking", "fukin", "fuker", "fck", "fcking", "fag", "fags", "twat", "twats", "pussy", "kys", "stfu",
        // es
        "puta", "putas", "puto", "putos", "mierda", "joder", "jodete", "pendejo", "pendeja", "pendejos",
        "cabron", "cabrona", "cabrones", "gilipollas", "maricon", "maricones", "hijueputa", "hdp", "chinga", "chingada",
        "verga", "culero",
        // fr
        "merde", "putain", "connard", "connasse", "salope", "encule", "pute", "putes", "batard", "nique", "ntm", "fdp",
        // de
        "scheisse", "scheiße", "arschloch", "arschlocher", "fotze", "hure", "hurensohn", "hurensohne", "hurensohns", "spast",
        // pt
        "porra", "caralho", "karalho", "krl", "foda", "fodase", "fodasse", "merda", "buceta", "viado", "cuzao", "arrombado",
        "pqp", "vsf", "vtnc", "tomar no cu",
        // ru / ko / zh romanised
        "cyka", "suka", "blyat", "blyad", "pizdec", "nahui", "nahuy", "sibal", "ssibal", "shibal", "tlqkf", "qudtls",
        "cnm", "nmsl",
    )
    private val WORD_EXACT = listOf(
        // es
        "coño",
        // vi
        "địt", "đ1t", "đụ", "đụ má", "lồn", "cặc", "đéo", "đĩ", "buồi", "đm", "đmm", "đcm", "vcl", "vkl", "clm", "óc chó",
        // ko (whole word only: 닥쳐오는 'impending' is innocent)
        "닥쳐",
        // ja (kana words: ばかり, バカンス, シネマ, ほくそ笑む are innocent)
        "しね", "シネ", "くそ", "クソ", "ばか", "バカ",
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
        "시발", "씨발", "씨바", "씨팔", "시팔", "쓰발", "씌발", "씨이발", "시이발", "썅", "병신", "븅신", "지랄", "좆", "존나",
        "개새끼", "개새", "개새기", "개색기", "새끼야", "미친놈", "미친년", "엿먹", "느금", "니애미", "창녀",
        "씹새끼", "씹새", "씹할", "씹년", "씹놈", "씹창", "염병", "섹스", "김치녀", "한남충", "ㅅㅂ", "ㅆㅂ", "ㅂㅅ", "ㅈㄹ", "ㅈㄴ", "ㅗ",
        // ja
        "死ね", "氏ね", "くたばれ", "ばかやろう", "ばかやろー", "バカヤロウ", "バカヤロー", "ちんこ", "チンコ", "ちんぽ", "チンポ", "まんこ", "マンコ",
        "きちがい", "キチガイ", "ガイジ",
        // zh
        "傻逼", "傻屄", "傻b", "煞笔", "操你妈", "草你妈", "操你娘", "肏", "他妈的", "他媽的", "妈的", "媽的", "特么的",
        "草泥马", "草泥馬", "你妈的", "你媽的", "尼玛", "婊子", "贱人", "賤人", "去死", "王八蛋", "鸡巴", "雞巴", "狗娘养的",
        "幹你娘", "干你娘",
    )

    /** Innocent words that contain a listed word; a match lying inside one of these is ignored. */
    private val EXCEPTIONS = listOf(
        "시발점", "시발역", "병신년", "妈妈的", "媽媽的", "shiitake", "shitake", "scunthorpe",
    )

    private val allEntries: List<Entry> by lazy {
        buildList {
        PREFIX_PLAIN.forEach { add(Entry(it, Mode.PREFIX, Fold.PLAIN)) }
        WORD_PLAIN.forEach { add(Entry(it, Mode.WORD, Fold.PLAIN)) }
        WORD_EXACT.forEach { add(Entry(it, Mode.WORD, Fold.EXACT)) }
        PREFIX_CYRILLIC.forEach { add(Entry(it, Mode.PREFIX, Fold.CYRILLIC)) }
        WORD_CYRILLIC.forEach { add(Entry(it, Mode.WORD, Fold.CYRILLIC)) }
        ANYWHERE_CYRILLIC.forEach { add(Entry(it, Mode.ANYWHERE, Fold.CYRILLIC)) }
        ANYWHERE_EXACT.forEach { add(Entry(it, Mode.ANYWHERE, Fold.EXACT)) }
        }
    }

    /** Entries per fold, indexed by first folded code point. */
    private val byFirst: Map<Fold, Map<Int, List<Entry>>> by lazy {
        Fold.entries.associateWith { f -> allEntries.filter { it.fold == f && it.chars.isNotEmpty() }.groupBy { it.chars[0] } }
    }

    private val exceptionChars: Map<Fold, List<IntArray>> by lazy {
        Fold.entries.associateWith { f -> EXCEPTIONS.map { foldWord(it, f) }.filter { it.isNotEmpty() } }
    }

    // ---------------------------------------------------------------- public API

    /** True if [text] contains a listed word. [strict] (nicknames) also finds words glued inside longer words. */
    fun containsProfanity(text: String, strict: Boolean = false): Boolean = findSegments(Segments(text), strict).any { it }

    /**
     * Replaces every character of a listed word with one '*' (a base character together with its combining marks, e.g.
     * "f̷" or half-width "ﾊﾞ", is one character); whitespace inside a spaced-out word stays and invisible characters inside
     * a match are dropped. Text without listed words is returned unchanged.
     */
    fun mask(text: String): String {
        val segs = Segments(text)
        val masked = findSegments(segs, strict = false)
        if (masked.none { it }) return text
        val sb = StringBuilder(text.length)
        for (s in 0 until segs.count) {
            val cp = text.codePointAt(segs.start[s])
            when {
                !masked[s] -> sb.append(text, segs.start[s], segs.end[s])
                isIgnorable(cp) -> Unit
                isSpace(cp) -> sb.appendCodePoint(cp)
                else -> sb.append('*')
            }
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

    /** Folded characters; character k came from segments [from[k], to[k]]. */
    private class Skeleton(val chars: IntArray, val from: IntArray, val to: IntArray) {
        val size: Int get() = chars.size
    }

    private fun skeleton(segs: Segments, fold: Fold): Skeleton {
        val chars = ArrayList<Int>(segs.count)
        val seg = ArrayList<Int>(segs.count)
        for (s in 0 until segs.count) {
            for (cp in foldSegment(segs.text.substring(segs.start[s], segs.end[s]), fold)) {
                chars += cp
                seg += s
            }
        }
        val sk = Skeleton(chars.toIntArray(), seg.toIntArray(), seg.toIntArray())
        return if (fold == Fold.EXACT) composeJamo(sk) else sk
    }

    private fun foldWord(word: String, fold: Fold): IntArray {
        val out = ArrayList<Int>()
        val segs = Segments(word)
        for (s in 0 until segs.count) {
            val piece = word.substring(segs.start[s], segs.end[s])
            if (piece == " ") { out += SPACE; continue }
            foldSegment(piece, fold).forEach { out += it }
        }
        val arr = out.toIntArray()
        return if (fold == Fold.EXACT) composeJamo(Skeleton(arr, IntArray(arr.size), IntArray(arr.size))).chars else arr
    }

    private fun foldSegment(piece: String, fold: Fold): IntArray {
        if (isIgnorable(piece.codePointAt(0))) return IntArray(0)
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
                Fold.PLAIN -> cp = TO_LATIN[cp] ?: cp
                Fold.CYRILLIC -> cp = TO_CYRILLIC[cp] ?: cp
                Fold.EXACT -> Unit
            }
            out += cp
        }
        return out.toIntArray()
    }

    /** Composes conjoining jamo sequences (from jamo-spelled or decomposed text) into Hangul syllables. */
    private fun composeJamo(sk: Skeleton): Skeleton {
        val c = sk.chars
        if (c.none { it in 0x1100..0x11FF }) return sk
        val chars = ArrayList<Int>(c.size)
        val from = ArrayList<Int>(c.size)
        val to = ArrayList<Int>(c.size)
        var i = 0
        while (i < c.size) {
            val l = c[i] - 0x1100
            val v = if (i + 1 < c.size) c[i + 1] - 0x1161 else -1
            if (l in 0..18 && v in 0..20) {
                var t = 0
                var used = 2
                if (i + 2 < c.size) {
                    val next = c[i + 2]
                    val nextIsVowel = i + 3 < c.size && c[i + 3] in 0x1161..0x1175
                    if (next in 0x11A8..0x11C2) { t = next - 0x11A7; used = 3 }
                    else if (next in 0x1100..0x1112 && !nextIsVowel && LEAD_TO_TAIL[next - 0x1100] > 0) {
                        t = LEAD_TO_TAIL[next - 0x1100]; used = 3
                    }
                }
                chars += 0xAC00 + (l * 21 + v) * 28 + t
                from += sk.from[i]
                to += sk.to[i + used - 1]
                i += used
            } else {
                chars += c[i]; from += sk.from[i]; to += sk.to[i]
                i++
            }
        }
        return Skeleton(chars.toIntArray(), from.toIntArray(), to.toIntArray())
    }

    /** Choseong index -> jongseong index (0 = cannot be a final). */
    private val LEAD_TO_TAIL = intArrayOf(1, 2, 4, 7, 0, 8, 16, 17, 0, 19, 20, 21, 22, 0, 23, 24, 25, 26, 27)

    private fun map(vararg pairs: Pair<Char, Char>): Map<Int, Int> = pairs.associate { it.first.code to it.second.code }

    /** Cyrillic, Greek and Armenian letters that look like Latin ones. */
    private val TO_LATIN: Map<Int, Int> = map(
        'а' to 'a', 'с' to 'c', 'е' to 'e', 'ё' to 'e', 'о' to 'o', 'р' to 'p', 'х' to 'x', 'к' to 'k',
        'ѕ' to 's', 'і' to 'i', 'ї' to 'i', 'ј' to 'j', 'ԁ' to 'd', 'һ' to 'h', 'ӏ' to 'l', 'ԛ' to 'q', 'ԝ' to 'w',
        'α' to 'a', 'ο' to 'o', 'ε' to 'e', 'ι' to 'i', 'κ' to 'k', 'ν' to 'v', 'ρ' to 'p', 'τ' to 't', 'υ' to 'u',
        'χ' to 'x',
        'ս' to 'u', 'օ' to 'o', 'ո' to 'n', 'հ' to 'h', 'ց' to 'g', 'զ' to 'q',
    )

    /** Latin letters that look like Cyrillic ones (and ё -> е). */
    private val TO_CYRILLIC: Map<Int, Int> = map(
        'a' to 'а', 'b' to 'в', 'c' to 'с', 'e' to 'е', 'h' to 'н', 'k' to 'к', 'm' to 'м', 'n' to 'п', 'o' to 'о',
        'p' to 'р', 'r' to 'г', 't' to 'т', 'u' to 'и', 'w' to 'ш', 'x' to 'х', 'y' to 'у', 'z' to 'з', 'ё' to 'е',
    )

    /** Leetspeak: a digit or symbol in the text may stand for these letters (matched, never rewritten). */
    private val LEET: Map<Fold, Map<Int, IntArray>> = mapOf(
        Fold.PLAIN to mapOf(
            '0' to "o", '1' to "il", '3' to "e", '4' to "a", '5' to "s", '7' to "t",
            '@' to "a", '$' to "s", '!' to "il", '|' to "li", '€' to "e",
            // Cyrillic letters that stand in for more than one Latin letter ("fуck" with Cyrillic у).
            'у' to "yu", 'п' to "n", 'ш' to "w", 'в' to "b", 'и' to "u",
        ).entries.associate { (k, v) -> k.code to v.codePoints().toArray() },
        Fold.CYRILLIC to mapOf(
            '0' to "о", '3' to "з", '6' to "б", '4' to "ч", '@' to "ая",
        ).entries.associate { (k, v) -> k.code to v.codePoints().toArray() },
        Fold.EXACT to emptyMap(),
    )

    private fun matches(text: Int, entry: Int, leet: Map<Int, IntArray>): Boolean =
        text == entry || leet[text]?.contains(entry) == true

    private fun isCombining(cp: Int): Boolean {
        val t = Character.getType(cp)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.ENCLOSING_MARK.toInt() ||
            t == Character.COMBINING_SPACING_MARK.toInt() || cp == 0xFF9E || cp == 0xFF9F
    }

    /** Invisible characters are dropped before matching (zero-width, joiners, bidi controls, fillers, ...). */
    internal fun isIgnorable(cp: Int): Boolean = Invisible.isInvisible(cp)

    private fun isSpace(cp: Int): Boolean = Character.isWhitespace(cp) || Character.isSpaceChar(cp)

    private fun isSeparator(cp: Int): Boolean {
        if (cp == SPACE || isSpace(cp)) return true
        return when (Character.getType(cp).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
            Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION, Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL,
            Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL, Character.CONTROL -> true
            else -> false
        }
    }

    /** Characters an entry may skip between two of its letters (never whitespace: "push it" must not join). */
    private fun isGap(cp: Int, e: Entry): Boolean {
        if (isSpace(cp)) return false
        if (isSeparator(cp)) return true
        return when (e.family) {
            // "시1발", "ばーか": digits and the long-vowel mark are filler in CJK words.
            Family.HANGUL, Family.KANA, Family.HAN -> cp in '0'.code..'9'.code || cp == 0x30FC || cp == 0x301C || cp == 0xFF5E
            else -> false
        }
    }

    private fun familyOf(cp: Int): Family = when (Character.UnicodeScript.of(cp)) {
        Character.UnicodeScript.LATIN -> Family.LATIN
        Character.UnicodeScript.CYRILLIC -> Family.CYRILLIC
        Character.UnicodeScript.HANGUL -> Family.HANGUL
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> Family.KANA
        Character.UnicodeScript.HAN -> Family.HAN
        else -> Family.OTHER
    }

    /** A letter that glues to a word of [family] (word boundaries are per script). */
    private fun isWordLetter(cp: Int, family: Family): Boolean = Character.isLetter(cp) && familyOf(cp) == family

    // ---------------------------------------------------------------- matching

    private fun findSegments(segs: Segments, strict: Boolean): BooleanArray {
        val masked = BooleanArray(segs.count)
        for (fold in Fold.entries) {
            val sk = skeleton(segs, fold)
            if (sk.size == 0) continue
            val index = byFirst.getValue(fold)
            val leet = LEET.getValue(fold)
            val exceptions = exceptionRanges(sk, fold)
            fun mark(first: Int, last: Int) { // skeleton indices, inclusive
                if (exceptions.any { first >= it.first && last <= it.last }) return
                for (s in sk.from[first]..sk.to[last]) masked[s] = true
            }
            fun candidates(cp: Int): List<Entry> {
                val direct = index[cp].orEmpty()
                val viaLeet = leet[cp] ?: return direct
                return direct + viaLeet.flatMap { index[it].orEmpty() }
            }
            // Compact matches.
            for (i in 0 until sk.size) {
                for (e in candidates(sk.chars[i])) {
                    val end = matchAt(sk.chars, sk.size, i, e, leet, compact = true) ?: continue
                    val prevGlued = i > 0 && isWordLetter(sk.chars[i - 1], e.family)
                    when (e.mode) {
                        Mode.ANYWHERE -> {
                            // Alphabetic roots ("пизд") take the rest of the word with them.
                            var stop = end
                            if (e.stretchable) while (stop < sk.size && stop - end < MAX_SUFFIX && isWordLetter(sk.chars[stop], e.family)) stop++
                            mark(i, stop - 1)
                        }
                        Mode.PREFIX -> {
                            if (prevGlued && !strict) continue
                            var stop = end
                            while (stop < sk.size && stop - end < MAX_SUFFIX && isWordLetter(sk.chars[stop], e.family)) stop++
                            mark(i, stop - 1)
                        }
                        Mode.WORD -> {
                            val glued = prevGlued || (end < sk.size && isWordLetter(sk.chars[end], e.family))
                            if (glued && !(strict && e.letterCount >= 5)) continue
                            mark(i, end - 1)
                        }
                    }
                }
            }
            // Spaced-out words: runs of single characters separated by spaces or punctuation ("f u c k", "시 발").
            for (run in spacedRuns(sk)) {
                val letters = IntArray(run.size) { sk.chars[run[it]] }
                for (i in letters.indices) {
                    for (e in candidates(letters[i])) {
                        val end = matchAt(letters, letters.size, i, e, leet, compact = false) ?: continue
                        mark(run[i], run[end - 1])
                    }
                }
            }
        }
        return masked
    }

    /**
     * Matches [e] at [start] and returns the exclusive end, or null. In compact mode up to [MAX_GAP] gap characters
     * may sit between two letters. Letters of alphabetic entries may repeat ("fuuuck") unless the entry itself
     * doubles that letter next. At least one matched character must be a real letter (digits alone never match).
     */
    private fun matchAt(chars: IntArray, size: Int, start: Int, e: Entry, leet: Map<Int, IntArray>, compact: Boolean): Int? {
        val w = e.chars
        var j = start
        var k = 0
        var realLetter = false
        while (k < w.size) {
            val c = w[k]
            if (c == SPACE) {
                var g = 0
                while (j < size && g < MAX_SPACE_GAP && isSeparator(chars[j])) { j++; g++ }
                k++
                continue
            }
            if (compact && k > 0 && w[k - 1] != SPACE) {
                var g = 0
                while (j < size && g < MAX_GAP && isGap(chars[j], e) && !matches(chars[j], c, leet)) { j++; g++ }
            }
            if (j >= size || !matches(chars[j], c, leet)) return null
            if (Character.isLetter(chars[j])) realLetter = true
            j++
            if (e.stretchable && (k + 1 >= w.size || w[k + 1] != c)) {
                while (j < size && matches(chars[j], c, leet)) j++
            }
            k++
        }
        return if (realLetter) j else null
    }

    /** Index lists of runs (≥ 2) of isolated letters with 1–3 separators between them. */
    private fun spacedRuns(sk: Skeleton): List<IntArray> {
        val runs = ArrayList<IntArray>()
        val n = sk.size
        fun isolated(p: Int): Boolean =
            Character.isLetter(sk.chars[p]) &&
                (p == 0 || isSeparator(sk.chars[p - 1])) &&
                (p == n - 1 || isSeparator(sk.chars[p + 1]))
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
            if (run.size >= 2) runs += run.toIntArray()
            p = run.last() + 1
        }
        return runs
    }

    private fun exceptionRanges(sk: Skeleton, fold: Fold): List<IntRange> {
        val out = ArrayList<IntRange>()
        for (ex in exceptionChars.getValue(fold)) {
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
