package app.numbaseball.core.text

import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.Limits
import java.text.Normalizer

/**
 * The chat pipeline (protocol §9.1), identical on server and client:
 * 1. remove emoji (ZWJ sequences, flags, skin tones, keycaps, tags);
 * 2. remove control, format (zero-width, bidi), private-use and invisible filler characters; line breaks and tabs become spaces;
 * 3. NFC-normalize, cap stacked combining marks at [MAX_MARKS] per character;
 * 4. collapse whitespace runs to one space and trim;
 * 5. reject empty text or more than [Limits.CHAT_MAX_LENGTH] code points;
 * 6. mask listed profanity with '*'.
 */
object ChatFilter {
    const val MAX_MARKS = 2

    sealed interface Result {
        /** [text] is what everybody sees; [masked] tells whether profanity was replaced. */
        data class Ok(val text: String, val masked: Boolean) : Result
        /** [code] is [ErrorCodes.CHAT_EMPTY] or [ErrorCodes.CHAT_TOO_LONG]. */
        data class Rejected(val code: String) : Result
    }

    fun clean(raw: String, maxLength: Int = Limits.CHAT_MAX_LENGTH): Result {
        val text = sanitize(raw)
        if (text.isEmpty()) return Result.Rejected(ErrorCodes.CHAT_EMPTY)
        if (text.codePointCount(0, text.length) > maxLength) return Result.Rejected(ErrorCodes.CHAT_TOO_LONG)
        val masked = ProfanityFilter.mask(text)
        return Result.Ok(masked, masked != text)
    }

    /** Steps 1–4 only: text-only, visible, normalized. */
    fun sanitize(raw: String): String {
        val noEmoji = EmojiFilter.strip(raw)
        val visible = StringBuilder(noEmoji.length)
        var i = 0
        while (i < noEmoji.length) {
            val cp = noEmoji.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp == '\n'.code || cp == '\r'.code || cp == '\t'.code || cp == 0x2028 || cp == 0x2029 -> visible.append(' ')
                isRemoved(cp) -> Unit
                else -> visible.appendCodePoint(cp)
            }
        }
        val nfc = Normalizer.normalize(visible, Normalizer.Form.NFC)
        val out = StringBuilder(nfc.length)
        var marks = 0
        var pendingSpace = false
        i = 0
        while (i < nfc.length) {
            val cp = nfc.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = out.isNotEmpty()
                marks = 0
                continue
            }
            val isMark = Character.getType(cp).let {
                it == Character.NON_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt()
            }
            if (isMark) {
                if (out.isEmpty() || pendingSpace || ++marks > MAX_MARKS) continue
            } else {
                marks = 0
            }
            if (pendingSpace) { out.append(' '); pendingSpace = false }
            out.appendCodePoint(cp)
        }
        return out.toString()
    }

    private fun isRemoved(cp: Int): Boolean {
        val type = Character.getType(cp).toByte()
        return type == Character.CONTROL || type == Character.FORMAT || type == Character.PRIVATE_USE ||
            type == Character.SURROGATE || type == Character.UNASSIGNED ||
            Invisible.isInvisible(cp)
    }
}
