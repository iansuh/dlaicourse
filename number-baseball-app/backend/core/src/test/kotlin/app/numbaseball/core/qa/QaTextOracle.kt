package app.numbaseball.core.qa

import java.text.Normalizer
import java.util.Locale

/**
 * QA oracle for text rules: what a cleaned chat line may never contain, a "skeleton" used to check that a masked word is
 * really gone, and the structural nickname rule. Written from README, protocol 9.1 / 9.3 and DEFECTS.md DEF-006/007.
 * Code points are written as numbers on purpose: no invisible characters in QA sources.
 */
object QaTextOracle {
    /** Emoji_Presentation=Yes (Unicode 15.0, generated with JDK 21 Character.isEmojiPresentation). Inclusive pairs. */
    private val EMOJI_PRESENTATION = intArrayOf(
        0x231A, 0x231B, 0x23E9, 0x23EC, 0x23F0, 0x23F0, 0x23F3, 0x23F3, 0x25FD, 0x25FE, 0x2614, 0x2615,
        0x2648, 0x2653, 0x267F, 0x267F, 0x2693, 0x2693, 0x26A1, 0x26A1, 0x26AA, 0x26AB, 0x26BD, 0x26BE,
        0x26C4, 0x26C5, 0x26CE, 0x26CE, 0x26D4, 0x26D4, 0x26EA, 0x26EA, 0x26F2, 0x26F3, 0x26F5, 0x26F5,
        0x26FA, 0x26FA, 0x26FD, 0x26FD, 0x2705, 0x2705, 0x270A, 0x270B, 0x2728, 0x2728, 0x274C, 0x274C,
        0x274E, 0x274E, 0x2753, 0x2755, 0x2757, 0x2757, 0x2795, 0x2797, 0x27B0, 0x27B0, 0x27BF, 0x27BF,
        0x2B1B, 0x2B1C, 0x2B50, 0x2B50, 0x2B55, 0x2B55,
    )

    fun isEmojiPresentation(cp: Int): Boolean {
        var i = 0
        while (i < EMOJI_PRESENTATION.size) {
            if (cp in EMOJI_PRESENTATION[i]..EMOJI_PRESENTATION[i + 1]) return true
            i += 2
        }
        // Every Emoji_Presentation code point above the BMP lies in U+1F000..U+1FAFF, handled below.
        return false
    }

    /**
     * QA definition of "emoji" for the chat ban (DEF-006): Emoji_Presentation characters, the whole supplementary
     * pictograph area U+1F000..U+1FAFF (covers future emoji too), and the emoji-only components.
     * Text-presentation symbols without U+FE0F (heart suit, copyright, music note, stars) are NOT forbidden.
     */
    fun isForbiddenInChat(cp: Int): Boolean =
        isEmojiPresentation(cp) || cp in 0x1F000..0x1FAFF ||
            cp == 0xFE0F || cp == 0x20E3 || cp == 0x200D || cp in 0xE0000..0xE007F

    fun isBidiControl(cp: Int): Boolean =
        cp in 0x202A..0x202E || cp in 0x2066..0x2069 || cp == 0x200E || cp == 0x200F || cp == 0x061C

    /** Characters that render as nothing (or as blank) and can hide or fake text. */
    fun isInvisible(cp: Int): Boolean =
        cp in 0x200B..0x200F || cp in 0x2060..0x2064 || cp == 0xFEFF || cp == 0x00AD || cp == 0x034F ||
            cp == 0x180E || cp == 0x3164 || cp == 0x115F || cp == 0x1160 || cp == 0xFFA0 || cp == 0x2800 ||
            cp in 0x17B4..0x17B5 || cp in 0xFE00..0xFE0F || cp in 0xE0000..0xE0FFF || cp in 0x1D173..0x1D17A ||
            isBidiControl(cp)

    private fun isMark(cp: Int): Boolean {
        val t = Character.getType(cp)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.ENCLOSING_MARK.toInt()
    }

    fun codePoints(s: String): IntArray = s.codePoints().toArray()

    fun hex(s: String): String = codePoints(s).joinToString(" ") { "U+%04X".format(it) }

    /** Every universal chat invariant the cleaned text violates (empty list = fine). protocol 9.1 + QA proposals. */
    fun chatViolations(text: String, maxLength: Int = 100): List<String> {
        val v = ArrayList<String>()
        if (text.isEmpty()) v += "empty text was accepted"
        if (text.isNotEmpty() && (text.first().isWhitespace() || text.last().isWhitespace())) v += "not trimmed"
        for (i in text.indices) {
            val c = text[i]
            if (Character.isHighSurrogate(c) && (i + 1 >= text.length || !Character.isLowSurrogate(text[i + 1]))) v += "lone high surrogate at $i"
            if (Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(text[i - 1]))) v += "lone low surrogate at $i"
        }
        val cps = codePoints(text)
        if (cps.size > maxLength) v += "longer than $maxLength code points (${cps.size})"
        var marks = 0
        var prevSpace = false
        for (cp in cps) {
            val type = Character.getType(cp)
            when {
                type == Character.CONTROL.toInt() -> v += "control U+%04X".format(cp)
                type == Character.FORMAT.toInt() -> v += "format (invisible/bidi) U+%04X".format(cp)
                isInvisible(cp) -> v += "invisible U+%04X".format(cp)
                isForbiddenInChat(cp) -> v += "emoji U+%04X".format(cp)
            }
            val space = Character.isWhitespace(cp) || Character.isSpaceChar(cp)
            if (space && prevSpace) v += "whitespace not collapsed"
            if (space && cp != ' '.code) v += "whitespace other than U+0020: U+%04X".format(cp)
            prevSpace = space
            marks = if (isMark(cp)) marks + 1 else 0
            if (marks > 4) v += "more than 4 stacked combining marks"
        }
        if (!Normalizer.isNormalized(text, Normalizer.Form.NFC)) v += "not NFC"
        return v.distinct()
    }

    // ------------------------------------------------------------------ skeletons for the "masked word is gone" check

    private val LOOKALIKE: Map<Int, Char> = buildMap {
        // Cyrillic
        put(0x0430, 'a'); put(0x0435, 'e'); put(0x043E, 'o'); put(0x0440, 'p'); put(0x0441, 'c'); put(0x0443, 'y')
        put(0x0445, 'x'); put(0x043A, 'k'); put(0x0456, 'i'); put(0x0455, 's'); put(0x0458, 'j'); put(0x0501, 'd')
        put(0x04CF, 'l'); put(0x043C, 'm'); put(0x0442, 't'); put(0x0432, 'b'); put(0x043D, 'h')
        // Greek
        put(0x03B1, 'a'); put(0x03B2, 'b'); put(0x03B5, 'e'); put(0x03B9, 'i'); put(0x03BA, 'k'); put(0x03BD, 'v')
        put(0x03BF, 'o'); put(0x03C1, 'p'); put(0x03C4, 't'); put(0x03C5, 'u'); put(0x03C7, 'x')
        // Armenian
        put(0x057D, 'u'); put(0x0585, 'o'); put(0x0570, 'h'); put(0x0578, 'n')
        // Latin specials
        put(0x0111, 'd'); put(0x00F8, 'o'); put(0x0142, 'l')
    }
    private val LEET: Map<Char, Char> = mapOf(
        '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's', '!' to 'i',
    )

    /**
     * Two skeletons of [s]: NFKC, lower case, accents/marks/format characters dropped, look-alikes folded to Latin,
     * then (1) leet folded and only letters kept, (2) only letters kept; repeated letters collapsed in both.
     */
    fun skeletons(s: String): Set<String> {
        val nfkc = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace("ß", "ss")
        val nfkd = Normalizer.normalize(nfkc, Normalizer.Form.NFKD)
        val folded = StringBuilder()
        for (cp in codePoints(nfkd)) {
            val t = Character.getType(cp)
            if (t == Character.NON_SPACING_MARK.toInt() || t == Character.ENCLOSING_MARK.toInt() ||
                t == Character.FORMAT.toInt() || isInvisible(cp)
            ) continue
            val l = LOOKALIKE[cp]
            if (l != null) folded.append(l) else folded.appendCodePoint(cp)
        }
        val base = Normalizer.normalize(folded, Normalizer.Form.NFC)
        val leet = StringBuilder()
        val letters = StringBuilder()
        for (cp in codePoints(base)) {
            val mapped = if (cp < 128) LEET[cp.toChar()]?.code ?: cp else cp
            if (Character.isLetter(mapped)) leet.appendCodePoint(mapped)
            if (Character.isLetter(cp)) letters.appendCodePoint(cp)
        }
        return setOf(collapse(leet.toString()), collapse(letters.toString()))
    }

    private fun collapse(s: String): String {
        val out = StringBuilder()
        var prev = -1
        for (cp in codePoints(s)) {
            if (cp != prev) out.appendCodePoint(cp)
            prev = cp
        }
        return out.toString()
    }

    /** True if no skeleton of [cleaned] contains the letters-only skeleton of [term]. */
    fun termIsGone(cleaned: String, term: String): Boolean {
        val t = skeletons(term).minByOrNull { it.length } ?: return true
        if (t.isEmpty()) return true
        return skeletons(cleaned).none { it.contains(t) }
    }

    // ------------------------------------------------------------------ nickname structure (protocol 9.3 + DEF-007)

    enum class NickProblem { LENGTH, CHARSET, INVISIBLE }

    /** protocol 9.3: trim, NFKC, trim. */
    fun normalizeNickname(raw: String): String = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).trim()

    /**
     * Structural rule only (no profanity, no reserved names): 2..12 code points after normalization, every code point a
     * Unicode letter or decimal digit, nothing invisible (Hangul fillers are letters but render blank).
     */
    fun nicknameProblem(raw: String): NickProblem? {
        val name = normalizeNickname(raw)
        for (i in name.indices) if (Character.isSurrogate(name[i])) {
            val ok = Character.isHighSurrogate(name[i]) && i + 1 < name.length && Character.isLowSurrogate(name[i + 1]) ||
                Character.isLowSurrogate(name[i]) && i > 0 && Character.isHighSurrogate(name[i - 1])
            if (!ok) return NickProblem.CHARSET
        }
        val cps = codePoints(name)
        if (cps.any { isInvisible(it) || Character.getType(it) == Character.FORMAT.toInt() }) return NickProblem.INVISIBLE
        if (cps.any { !Character.isLetter(it) && Character.getType(it) != Character.DECIMAL_DIGIT_NUMBER.toInt() }) {
            return NickProblem.CHARSET
        }
        if (cps.size !in 2..12) return NickProblem.LENGTH
        return null
    }
}
