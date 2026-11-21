package app.numbaseball.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RulesTest {
    @Test fun validation() {
        assertTrue(Rules.isValid("012", 3))
        assertTrue(Rules.isValid("0123", 4))
        assertTrue(Rules.isValid("987654", 6))
        assertNull(Rules.problem("102", 3))
        assertEquals(NumberProblem.WRONG_LENGTH, Rules.problem("12", 3))
        assertEquals(NumberProblem.WRONG_LENGTH, Rules.problem("1234", 3))
        assertEquals(NumberProblem.REPEATED_DIGIT, Rules.problem("112", 3))
        assertEquals(NumberProblem.NOT_DIGITS, Rules.problem("1a2", 3))
        assertEquals(NumberProblem.NOT_DIGITS, Rules.problem("１２３", 3)) // full-width digits are not ASCII digits
        assertFalse(Rules.isValid("012", 2)) // digit count out of range
        assertFalse(Rules.isValid("0123456", 7))
    }

    @Test fun judge() {
        assertEquals(Judgement(3, 0), Rules.judge("123", "123"))
        assertEquals(Judgement(0, 3), Rules.judge("123", "312"))
        assertEquals(Judgement(1, 1), Rules.judge("123", "135"))
        assertEquals(Judgement(0, 0), Rules.judge("123", "456"))
        assertTrue(Rules.judge("123", "456").isOut)
        assertEquals(Judgement(2, 2), Rules.judge("0123", "0132"))
        assertEquals(Judgement(1, 0), Rules.judge("0987", "0123"))
        assertTrue(Rules.judge("012345", "012345").isSolved(6))
    }

    @Test fun randomNumbersAreValid() {
        val rnd = Random(1)
        for (d in 3..6) repeat(200) { assertTrue(Rules.isValid(Rules.randomNumber(d, rnd), d)) }
        // A leading zero must be possible.
        assertTrue((1..500).any { Rules.randomNumber(3, rnd).startsWith("0") })
    }
}
