package app.numbaseball.data.account

import app.numbaseball.core.protocol.Limits
import app.numbaseball.core.text.ProfanityFilter
import java.text.Normalizer

enum class NicknameProblem { TOO_SHORT, TOO_LONG, INVALID_CHARACTERS, PROFANITY }

/**
 * Client-side pre-check of docs/protocol.md §9.3 so most mistakes never reach the server. The server stays authoritative
 * (reserved names, uniqueness).
 * TODO(core): replace with `Nicknames.validate` from :core once it lands (protocol.md §9.3 refers to it).
 */
object NicknameRules {
    fun normalize(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()

    fun problem(raw: String): NicknameProblem? {
        val name = normalize(raw)
        val length = name.codePointCount(0, name.length)
        return when {
            length < Limits.NICKNAME_MIN -> NicknameProblem.TOO_SHORT
            length > Limits.NICKNAME_MAX -> NicknameProblem.TOO_LONG
            !name.codePoints().allMatch { Character.isLetterOrDigit(it) } -> NicknameProblem.INVALID_CHARACTERS
            ProfanityFilter.containsProfanity(name, strict = true) -> NicknameProblem.PROFANITY
            else -> null
        }
    }
}
