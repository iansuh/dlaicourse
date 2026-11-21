package app.numbaseball.core.text

import app.numbaseball.core.protocol.ErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFilterTest {
    private fun ok(raw: String): String = (ChatFilter.clean(raw) as ChatFilter.Result.Ok).text

    @Test fun cleansAndMasks() {
        assertEquals("**** you", ok("fuck you"))
        assertEquals("hello world", ok("  hello \n\t world  "))
        assertEquals("gg", ok("gg😀"))
        assertEquals("ab", ok("a​b"))
        assertEquals("nice guess, 2S1B", ok("nice guess, 2S1B"))
        assertTrue((ChatFilter.clean("shit") as ChatFilter.Result.Ok).masked)
        assertFalse((ChatFilter.clean("hello") as ChatFilter.Result.Ok).masked)
    }

    @Test fun normalizesToNfcAndCapsCombiningMarks() {
        assertEquals("cabrón".let { "******" }, ok("cabrón"))
        assertEquals("é́".let { "é́" }, ok("é́")) // NFC composes the first mark
        val zalgo = "a" + "̶".repeat(20)
        assertEquals(1 + ChatFilter.MAX_MARKS, ok(zalgo).codePointCount(0, ok(zalgo).length))
    }

    @Test fun rejectsEmptyAndTooLong() {
        assertEquals(ChatFilter.Result.Rejected(ErrorCodes.CHAT_EMPTY), ChatFilter.clean("   "))
        assertEquals(ChatFilter.Result.Rejected(ErrorCodes.CHAT_EMPTY), ChatFilter.clean("😀​"))
        assertEquals(ChatFilter.Result.Rejected(ErrorCodes.CHAT_TOO_LONG), ChatFilter.clean("a".repeat(101)))
        assertEquals("a".repeat(100), ok("a".repeat(100)))
        // Length counts code points, not UTF-16 units.
        assertEquals(100, ok("𠀀".repeat(100)).codePointCount(0, 200))
    }
}
