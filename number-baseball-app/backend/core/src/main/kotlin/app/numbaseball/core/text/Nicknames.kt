package app.numbaseball.core.text

import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.Limits
import java.text.Normalizer
import java.util.Locale

/**
 * Nickname rules (protocol §9.3): after trimming and NFKC normalization, 2–12 code points, each a Unicode letter or
 * digit (combining marks allowed after a letter), no invisible fillers, no listed profanity anywhere, not reserved.
 * The display form is `nickname#tag` with a four-digit tag.
 */
object Nicknames {
    sealed interface Result {
        data class Ok(val nickname: String) : Result
        /** [code] is [ErrorCodes.INVALID_NICKNAME] or [ErrorCodes.NICKNAME_PROFANITY]. */
        data class Invalid(val code: String, val reason: String) : Result
    }

    private val RESERVED = setOf(
        "admin", "administrator", "moderator", "mod", "system", "server", "staff", "support", "official", "deleted",
        "deletedplayer", "numberbaseball", "anonymous", "guest", "null", "undefined",
        "관리자", "운영자", "운영진", "시스템", "管理员", "管理者", "運営", "администратор", "модератор",
    )

    /**
     * Trim, NFKC, trim. NFKC turns compatibility jamo ("ㅋㅋ") into conjoining jamo, which render badly on their own,
     * so jamo left standalone after composition are mapped back to their compatibility forms.
     */
    fun normalize(raw: String): String {
        val nfkc = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).trim()
        if (nfkc.none { it in '\u1100'..'\u11FF' }) return nfkc
        val sb = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            sb.append(
                when (ch) {
                    in '\u1100'..'\u1112' -> LEAD_COMPAT[ch - '\u1100']
                    in '\u1161'..'\u1175' -> '\u314F' + (ch - '\u1161')
                    in '\u11A8'..'\u11C2' -> TAIL_COMPAT[ch - '\u11A8']
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    private const val LEAD_COMPAT = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val TAIL_COMPAT = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    fun validate(raw: String): Result {
        val name = normalize(raw)
        val length = name.codePointCount(0, name.length)
        if (length < Limits.NICKNAME_MIN) return invalid("too short")
        if (length > Limits.NICKNAME_MAX) return invalid("too long")
        var i = 0
        var first = true
        while (i < name.length) {
            val cp = name.codePointAt(i)
            i += Character.charCount(cp)
            val type = Character.getType(cp).toByte()
            val mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
            val ok = (Character.isLetterOrDigit(cp) || (mark && !first)) && !ProfanityFilter.isIgnorable(cp) && !EmojiFilter.isEmoji(cp)
            if (!ok) return invalid("only letters and digits")
            first = false
        }
        val key = key(name)
        if (key in RESERVED || key.trimEnd { it.isDigit() } in RESERVED) return invalid("reserved")
        if (ProfanityFilter.containsProfanity(name, strict = true)) {
            return Result.Invalid(ErrorCodes.NICKNAME_PROFANITY, "profanity")
        }
        return Result.Ok(name)
    }

    /** Case-insensitive comparison key (for `nickname#tag` uniqueness). */
    fun key(nickname: String): String = nickname.lowercase(Locale.ROOT)

    fun formatTag(tag: Int): String {
        require(tag in 1..9999) { "tag" }
        return tag.toString().padStart(4, '0')
    }

    fun display(nickname: String, tag: Int): String = "$nickname#${formatTag(tag)}"

    private fun invalid(reason: String) = Result.Invalid(ErrorCodes.INVALID_NICKNAME, reason)
}
