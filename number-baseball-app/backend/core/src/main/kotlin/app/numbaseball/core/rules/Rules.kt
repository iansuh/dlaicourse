package app.numbaseball.core.rules

import app.numbaseball.core.protocol.Limits
import kotlin.random.Random

/** Result of one guess. "Out" is no strike and no ball. */
data class Judgement(val strikes: Int, val balls: Int) {
    val isOut: Boolean get() = strikes == 0 && balls == 0
    fun isSolved(digits: Int): Boolean = strikes == digits
}

enum class NumberProblem { WRONG_LENGTH, NOT_DIGITS, REPEATED_DIGIT }

/** Game rules: numbers of 3–6 different digits, 0 may lead. */
object Rules {
    fun isValidDigitCount(digits: Int): Boolean = digits in Limits.DIGITS

    /** null when [value] is a valid secret or guess for [digits], otherwise the first problem found. */
    fun problem(value: String, digits: Int): NumberProblem? {
        if (value.length != digits) return NumberProblem.WRONG_LENGTH
        var seen = 0
        for (c in value) {
            if (c !in '0'..'9') return NumberProblem.NOT_DIGITS
            val bit = 1 shl (c - '0')
            if (seen and bit != 0) return NumberProblem.REPEATED_DIGIT
            seen = seen or bit
        }
        return null
    }

    fun isValid(value: String, digits: Int): Boolean = isValidDigitCount(digits) && problem(value, digits) == null

    /** Judges [guess] against [secret]; both must be valid numbers of the same length. */
    fun judge(secret: String, guess: String): Judgement {
        require(secret.length == guess.length) { "length mismatch" }
        var strikes = 0
        var secretMask = 0
        var guessMask = 0
        for (i in secret.indices) {
            if (secret[i] == guess[i]) strikes++
            secretMask = secretMask or (1 shl (secret[i] - '0'))
            guessMask = guessMask or (1 shl (guess[i] - '0'))
        }
        return Judgement(strikes, Integer.bitCount(secretMask and guessMask) - strikes)
    }

    fun randomNumber(digits: Int, random: Random = Random.Default): String {
        require(isValidDigitCount(digits))
        val pool = MutableList(10) { it }
        val sb = StringBuilder(digits)
        repeat(digits) { sb.append('0' + pool.removeAt(random.nextInt(pool.size))) }
        return sb.toString()
    }
}
