package app.numbaseball.core.text

/**
 * Emoji removal for chat (text only), protocol §9.1.
 *
 * Always removed: Emoji_Presentation characters, everything in U+1F000–U+1FAFF (emoji blocks, flags' regional
 * indicators, skin tones), ZWJ (U+200D), variation selectors, the keycap mark U+20E3 together with its base
 * ("1\uFE0F\u20E3"), and tag characters (subdivision flags).
 * Text-presentation symbols such as ♥ © ™ ♪ ★ ↔ ‼ are text and survive, unless U+FE0F asks for the emoji form.
 * Plain emoticons (^^, :), (╯°□°)╯) are untouched.
 *
 * Tables were generated from Unicode 15 data (JDK 21) so they also work on Android.
 */
object EmojiFilter {
    /** Extended_Pictographic, inclusive [start, end] pairs. */
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

    /** Emoji_Presentation outside U+1F000–U+1FAFF, inclusive pairs. */
    private val PRESENTATION = intArrayOf(
        0x231A, 0x231B, 0x23E9, 0x23EC, 0x23F0, 0x23F0, 0x23F3, 0x23F3, 0x25FD, 0x25FE, 0x2614, 0x2615,
        0x2648, 0x2653, 0x267F, 0x267F, 0x2693, 0x2693, 0x26A1, 0x26A1, 0x26AA, 0x26AB, 0x26BD, 0x26BE,
        0x26C4, 0x26C5, 0x26CE, 0x26CE, 0x26D4, 0x26D4, 0x26EA, 0x26EA, 0x26F2, 0x26F3, 0x26F5, 0x26F5,
        0x26FA, 0x26FA, 0x26FD, 0x26FD, 0x2705, 0x2705, 0x270A, 0x270B, 0x2728, 0x2728, 0x274C, 0x274C,
        0x274E, 0x274E, 0x2753, 0x2755, 0x2757, 0x2757, 0x2795, 0x2797, 0x27B0, 0x27B0, 0x27BF, 0x27BF,
        0x2B1B, 0x2B1C, 0x2B50, 0x2B50, 0x2B55, 0x2B55,
    )

    private fun inRanges(table: IntArray, cp: Int): Boolean {
        var lo = 0
        var hi = table.size / 2 - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                cp < table[2 * mid] -> hi = mid - 1
                cp > table[2 * mid + 1] -> lo = mid + 1
                else -> return true
            }
        }
        return false
    }

    fun isPictographic(cp: Int): Boolean = inRanges(PICTOGRAPHIC, cp)

    /** Emoji no matter what follows. */
    fun isAlwaysEmoji(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || inRanges(PRESENTATION, cp) ||
            cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3 || cp in 0xE0000..0xE007F

    /** Any code point that can be part of an emoji (for nickname checks). */
    fun isEmoji(cp: Int): Boolean = isAlwaysEmoji(cp) || isPictographic(cp)

    fun containsEmoji(text: String): Boolean = strip(text) != text

    fun strip(text: String): String {
        val cps = text.codePoints().toArray()
        val out = StringBuilder(text.length)
        var i = 0
        while (i < cps.size) {
            val cp = cps[i]
            val next = if (i + 1 < cps.size) cps[i + 1] else -1
            // Keycap: [0-9#*] (FE0F)? 20E3 -> drop the whole sequence.
            if (cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code) {
                var j = i + 1
                if (j < cps.size && (cps[j] == 0xFE0F || cps[j] == 0xFE0E)) j++
                if (j < cps.size && cps[j] == 0x20E3) { i = j + 1; continue }
            }
            when {
                isAlwaysEmoji(cp) -> Unit
                // A text symbol turned into an emoji by VS16 ("\u2764\uFE0F") goes with it.
                isPictographic(cp) && next == 0xFE0F -> Unit
                else -> out.appendCodePoint(cp)
            }
            i++
        }
        return out.toString()
    }
}
