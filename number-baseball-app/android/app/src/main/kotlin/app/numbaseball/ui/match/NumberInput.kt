package app.numbaseball.ui.match

import app.numbaseball.core.rules.NumberProblem
import app.numbaseball.core.rules.Rules

/** Keypad editing rules shared by the match view models: digits only, no repeats, at most [digits] long. */
object NumberInput {
    fun append(input: String, digit: Char, digits: Int): String =
        if (digit in '0'..'9' && input.length < digits && digit !in input) input + digit else input

    fun deleteLast(input: String): String = input.dropLast(1)

    /** null when [input] can be submitted. Repeats are impossible through [append], so only length matters in practice. */
    fun problem(input: String, digits: Int): NumberProblem? = Rules.problem(input, digits)
}
