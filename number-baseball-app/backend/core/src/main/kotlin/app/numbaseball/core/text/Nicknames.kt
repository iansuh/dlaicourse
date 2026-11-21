package app.numbaseball.core.text

import app.numbaseball.core.protocol.ErrorCodes
import app.numbaseball.core.protocol.Limits
import java.text.Normalizer
import java.util.Locale

/**
 * Nickname rules (protocol §9.3): after trimming and NFKC normalization ([normalize]), 2–12 code points, each a Unicode
 * letter or digit (no leftover combining marks), no invisible fillers, no listed profanity anywhere, not reserved.
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
     * Trim, NFKC, trim — except that Hangul compatibility jamo ("ㅋㅋ", "ㄱㅏ") are kept as typed (NFKC would turn them
     * into conjoining jamo and compose "ㄱㅏ" into "가"); half-width Hangul letters become compatibility jamo, and
     * conjoining jamo that do not compose into a syllable are shown as compatibility jamo.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val out = StringBuilder(trimmed.length)
        val run = StringBuilder()
        fun flush() {
            if (run.isEmpty()) return
            out.append(Normalizer.normalize(run, Normalizer.Form.NFKC))
            run.setLength(0)
        }
        var i = 0
        while (i < trimmed.length) {
            val cp = trimmed.codePointAt(i)
            i += Character.charCount(cp)
            val jamo = when (cp) {
                in 0x3131..0x318E -> cp
                in 0xFFA1..0xFFDC -> HALFWIDTH_JAMO[cp - 0xFFA1].code.takeIf { it != '?'.code }
                else -> null
            }
            if (jamo != null) { flush(); out.appendCodePoint(jamo) } else run.appendCodePoint(cp)
        }
        flush()
        val nfkc = out.toString().trim()
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

    /** U+FFA1..U+FFDC -> compatibility jamo ('?' = unassigned slot). */
    private const val HALFWIDTH_JAMO =
        "ㄱㄲㄳㄴㄵㄶㄷㄸㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅃㅄㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ???ㅏㅐㅑㅒㅓㅔ??ㅕㅖㅗㅘㅙㅚ??ㅛㅜㅝㅞㅟㅠ??ㅡㅢㅣ"

    private const val LEAD_COMPAT = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val TAIL_COMPAT = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    fun validate(raw: String): Result {
        val name = normalize(raw)
        val length = name.codePointCount(0, name.length)
        if (length < Limits.NICKNAME_MIN) return invalid("too short")
        if (length > Limits.NICKNAME_MAX) return invalid("too long")
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            i += Character.charCount(cp)
            val type = Character.getType(cp).toByte()
            // Combining marks left after NFKC (stacked accents, zalgo) are refused; Vietnamese etc. compose fully.
            val ok = Character.isLetterOrDigit(cp) && !Invisible.isInvisible(cp) && !EmojiFilter.isEmoji(cp)
            if (!ok) return invalid("only letters and digits")
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
