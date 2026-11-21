package app.numbaseball.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NicknameRulesTest {
    @Test fun acceptsLettersAndDigitsInAnyScript() {
        listOf("Mina", "민아", "ミナ", "Мина", "Nguyễn", "ab", "a1b2c3d4e5f6").forEach { assertNull(it, NicknameRules.problem(it)) }
    }

    @Test fun rejectsLengthSymbolsAndEmoji() {
        assertEquals(NicknameProblem.TOO_SHORT, NicknameRules.problem(" a "))
        assertEquals(NicknameProblem.TOO_LONG, NicknameRules.problem("abcdefghijklm"))
        assertEquals(NicknameProblem.INVALID_CHARACTERS, NicknameRules.problem("Mi na"))
        assertEquals(NicknameProblem.INVALID_CHARACTERS, NicknameRules.problem("Mina😀"))
    }

    @Test fun normalizesFullWidthForms() {
        assertEquals("Mina", NicknameRules.normalize(" Ｍｉｎａ "))
    }
}
