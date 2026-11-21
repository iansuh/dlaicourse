package app.numbaseball.core.text

import app.numbaseball.core.protocol.ErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class NicknamesTest {
    private fun code(raw: String) = (Nicknames.validate(raw) as? Nicknames.Result.Invalid)?.code

    @Test fun acceptsUnicodeLettersAndDigits() {
        for (n in listOf("Mina", "민아", "みな", "小明", "Ваня", "Nguyễn", "José", "ab", "player12345x", "A1")) {
            assertEquals(n, Nicknames.Result.Ok(n), Nicknames.validate(n))
        }
        assertEquals(Nicknames.Result.Ok("Mina"), Nicknames.validate("  Ｍｉｎａ  ")) // NFKC + trim
    }

    @Test fun rejectsBadShapes() {
        assertEquals(ErrorCodes.INVALID_NICKNAME, code("a"))
        assertEquals(ErrorCodes.INVALID_NICKNAME, code("abcdefghijklm"))
        assertEquals(ErrorCodes.INVALID_NICKNAME, code("Mi na"))
        assertEquals(ErrorCodes.INVALID_NICKNAME, code("Mina!"))
        assertEquals(ErrorCodes.INVALID_NICKNAME, code("Mina😀"))
        assertEquals(ErrorCodes.INVALID_NICKNAME, code("Mi​na"))
        assertEquals(ErrorCodes.INVALID_NICKNAME, code("Miㅤna")) // Hangul filler is a "letter" but invisible
        assertEquals(ErrorCodes.INVALID_NICKNAME, code("admin"))
        assertEquals(ErrorCodes.INVALID_NICKNAME, code("Admin7"))
        assertEquals(ErrorCodes.INVALID_NICKNAME, code("관리자"))
    }

    @Test fun rejectsProfanityAnywhere() {
        assertEquals(ErrorCodes.NICKNAME_PROFANITY, code("fuckboy"))
        assertEquals(ErrorCodes.NICKNAME_PROFANITY, code("xXshitXx"))
        assertEquals(ErrorCodes.NICKNAME_PROFANITY, code("시발"))
        assertEquals(ErrorCodes.NICKNAME_PROFANITY, code("сука"))
        assertEquals(null, code("Scunthorpe"))
    }

    @Test fun tags() {
        assertEquals("Mina#0420", Nicknames.display("Mina", 420))
        assertEquals("9999", Nicknames.formatTag(9999))
        assertEquals(Nicknames.key("MINA"), Nicknames.key("mina"))
    }
}

class NicknameNormalizationTest {
    @Test fun jamoStayCompatibilityJamo() {
        assertEquals(Nicknames.Result.Ok("ㅋㅋ"), Nicknames.validate("ㅋㅋ"))
        // Conjoining jamo sequences compose into syllables; length is counted afterwards.
        val conjoining = "가나"
        assertEquals(Nicknames.Result.Ok("가나"), Nicknames.validate(conjoining))
        assertEquals(Nicknames.Result.Ok("Nguyễn"), Nicknames.validate("Nguyễn"))
    }
}
