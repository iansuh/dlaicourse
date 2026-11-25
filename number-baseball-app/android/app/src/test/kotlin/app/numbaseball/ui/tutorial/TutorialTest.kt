package app.numbaseball.ui.tutorial

import app.numbaseball.ui.design.MemoMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The interactive tutorial's script (01 §2): fixed secret 0 4 7, coached first guess, automatic OUT, free solve. */
class TutorialTest {
    private fun type(state: TutorialState, digits: String) = digits.fold(state) { s, d -> Tutorial.digit(s, d) }

    @Test fun walksThroughTheFourSteps() {
        var s = TutorialState()
        assertEquals(1, s.step.number)
        assertFalse(Tutorial.typing(s))
        assertEquals(s, Tutorial.digit(s, '1'))

        s = Tutorial.next(s)
        assertEquals(TutorialStep.FIRST_GUESS, s.step)
        assertEquals('0', Tutorial.expectedKey(s))
        s = type(s, "07")
        assertEquals('5', Tutorial.expectedKey(s))
        assertFalse(Tutorial.canPitch(s))
        s = Tutorial.digit(s, '5')
        assertTrue(Tutorial.canPitch(s))
        s = Tutorial.pitch(s)
        assertEquals(TutorialStep.EXPLAIN, s.step)
        assertEquals(1 to 1, s.rounds.single().let { it.strikes to it.balls })

        s = Tutorial.next(s)
        assertEquals(TutorialStep.OUT, s.step)
        assertEquals("123", s.rounds.last().guess)
        assertEquals(0 to 0, s.rounds.last().let { it.strikes to it.balls })

        s = Tutorial.next(s)
        assertEquals(TutorialStep.SOLVE, s.step)
        assertEquals(MemoMark.IN, s.memo.cells[0][0])
        assertEquals(MemoMark.OUT, s.memo.digitMarks[5])

        s = Tutorial.pitch(type(s, "074"))
        assertEquals(TutorialStep.SOLVE, s.step)
        assertEquals(3, s.rounds.size)
        s = Tutorial.pitch(type(s, "047"))
        assertEquals(TutorialStep.DONE, s.step)
        assertEquals(3, s.rounds.last().strikes)
    }

    @Test fun theCoachedGuessIsTheOnlyPitchInStepTwo() {
        var s = Tutorial.next(TutorialState())
        s = type(s, "123")
        assertNull(Tutorial.expectedKey(s))
        assertFalse(Tutorial.canPitch(s))
        assertEquals(s, Tutorial.pitch(s))
        // Digits stay distinct and at most three.
        assertEquals("123", Tutorial.digit(s, '4').input)
        assertEquals("12", Tutorial.digit(Tutorial.delete(s), '1').input)
    }
}
