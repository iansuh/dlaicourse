package app.numbaseball.core.text

/**
 * Emoji removal for chat (text only). Removes Extended_Pictographic characters, regional-indicator flags,
 * skin-tone modifiers, ZWJ / variation selectors, tag sequences (subdivision flags) and whole keycap
 * sequences such as "1\uFE0F\u20E3". Plain text emoticons like ^^ or :) are kept.
 *
 * The Extended_Pictographic table was generated from Unicode 15 data (JDK 21) so it also works on Android,
 * where `Character.isExtendedPictographic` is not available.
 */
object EmojiFilter {
    /** Inclusive [start, end] pairs, sorted. */
    private val PICTOGRAPHIC = intArrayOf(
        0xA9, 0xA9, 0xAE, 0xAE, 0x203C, 0x203C, 0x2049, 0x2049, 0x2122, 0x2122, 0x2139, 0x2139,
        0x2194, 0x2199, 0x21A9, 0x21AA, 0x231A, 0x231B, 0x2328, 0x2328, 0x2388, 0x2388, 0x23CF, 0x23CF,
        0x23E9, 0x23F3, 0x23F8, 0x23FA, 0x24C2, 0x24C2, 0x25AA, 0x25AB, 0x25B6, 0x25B6, 0x25C0, 0x25C0,
        0x25FB, 0x25FE, 0x2600, 0x2605, 0x2607, 0x2612, 0x2614, 0x2685, 0x2690, 0x2705, 0x2708, 0x2712,
        0x2714, 0x2714, 0x2716, 0x2716, 0x271D, 0x271D, 0x2721, 0x2721, 0x2728, 0x2728, 0x2733, 0x2734,
        0x2744, 0x2744, 0x2747, 0x2747, 0x274C, 0x274C, 0x274E, 0x274E, 0x2753, 0x2755, 0x2757, 0x2757,
        0x2763, 0x2767, 0x2795, 0x2797, 0x27A1, 0x27A1, 0x27B0, 0x27B0, 0x27BF, 0x27BF, 0x2934, 0x2935,
        0x2B05, 0x2B07, 0x2B1B, 0x2B1C, 0x2B50, 0x2B50, 0x2B55, 0x2B55, 0x3030, 0x3030, 0x303D, 0x303D,
        0x3297, 0x3297, 0x3299, 0x3299, 0x1F000, 0x1F0FF, 0x1F10D, 0x1F10F, 0x1F12F, 0x1F12F, 0x1F16C, 0x1F171,
        0x1F17E, 0x1F17F, 0x1F18E, 0x1F18E, 0x1F191, 0x1F19A, 0x1F1AD, 0x1F1E5, 0x1F201, 0x1F20F, 0x1F21A, 0x1F21A,
        0x1F22F, 0x1F22F, 0x1F232, 0x1F23A, 0x1F23C, 0x1F23F, 0x1F249, 0x1F3FA, 0x1F400, 0x1F53D, 0x1F546, 0x1F64F,
        0x1F680, 0x1F6FF, 0x1F774, 0x1F77F, 0x1F7D5, 0x1F7FF, 0x1F80C, 0x1F80F, 0x1F848, 0x1F84F, 0x1F85A, 0x1F85F,
        0x1F888, 0x1F88F, 0x1F8AE, 0x1F8FF, 0x1F90C, 0x1F93A, 0x1F93C, 0x1F945, 0x1F947, 0x1FAFF, 0x1FC00, 0x1FFFD,
    )

    fun isPictographic(cp: Int): Boolean {
        var lo = 0
        var hi = PICTOGRAPHIC.size / 2 - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val start = PICTOGRAPHIC[2 * mid]
            val end = PICTOGRAPHIC[2 * mid + 1]
            when {
                cp < start -> hi = mid - 1
                cp > end -> lo = mid + 1
                else -> return true
            }
        }
        return false
    }

    /** Code points that only make sense as part of an emoji and are dropped on their own. */
    fun isEmojiComponent(cp: Int): Boolean =
        cp == 0x200D ||                    // zero width joiner
            cp == 0xFE0E || cp == 0xFE0F ||    // text / emoji variation selectors
            cp == 0x20E3 ||                    // combining enclosing keycap
            cp in 0x1F1E6..0x1F1FF ||          // regional indicators (flags)
            cp in 0x1F3FB..0x1F3FF ||          // skin tones
            cp in 0x1F9B0..0x1F9B3 ||          // hair components
            cp in 0xE0020..0xE007F             // tag characters (subdivision flags)

    fun isEmoji(cp: Int): Boolean = isPictographic(cp) || isEmojiComponent(cp)

    fun containsEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isEmoji(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun strip(text: String): String {
        val cps = text.codePoints().toArray()
        val out = StringBuilder(text.length)
        var i = 0
        while (i < cps.size) {
            val cp = cps[i]
            // Keycap: [0-9#*] (FE0F)? 20E3 -> drop the whole sequence.
            if (cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code) {
                var j = i + 1
                if (j < cps.size && (cps[j] == 0xFE0F || cps[j] == 0xFE0E)) j++
                if (j < cps.size && cps[j] == 0x20E3) { i = j + 1; continue }
            }
            if (!isEmoji(cp)) out.appendCodePoint(cp)
            i++
        }
        return out.toString()
    }
}
