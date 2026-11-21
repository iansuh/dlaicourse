package app.numbaseball.core.text

/** Characters that render as nothing (or blank) and can hide or fake text. Removed from chat, refused in nicknames. */
object Invisible {
    fun isInvisible(cp: Int): Boolean =
        Character.getType(cp) == Character.FORMAT.toInt() ||        // ZWSP, ZWJ, bidi controls, BOM, soft hyphen, tags
            cp == 0x034F ||                                          // combining grapheme joiner
            cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 || // Hangul fillers
            cp == 0x2800 ||                                          // braille blank
            cp == 0x17B4 || cp == 0x17B5 ||                          // Khmer inherent vowels
            cp == 0x180E ||                                          // Mongolian vowel separator
            cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF ||        // variation selectors
            cp in 0xE0000..0xE0FFF ||                                // tags and reserved
            cp in 0x1D173..0x1D17A                                   // musical formatting
}
