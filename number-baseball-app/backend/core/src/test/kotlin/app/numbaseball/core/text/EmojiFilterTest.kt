package app.numbaseball.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiFilterTest {
    @Test fun stripsAllKindsOfEmoji() {
        assertEquals("hi ", EmojiFilter.strip("hi 😀"))                          // 😀
        assertEquals("ok", EmojiFilter.strip("o👍🏽k"))                // 👍🏽 skin tone
        assertEquals("fam", EmojiFilter.strip("fam👨‍👩‍👧")) // 👨‍👩‍👧 ZWJ
        assertEquals("kr", EmojiFilter.strip("kr🇰🇷"))                // 🇰🇷 flag
        assertEquals("go", EmojiFilter.strip("go🏴󠁧󠁢󠁳󠁣󠁴󠁿")) // 🏴 tag flag
        assertEquals("no ", EmojiFilter.strip("no 1️⃣"))                          // 1️⃣ keycap
        assertEquals("x", EmojiFilter.strip("x#⃣"))                                     // keycap without VS16
        assertEquals("love", EmojiFilter.strip("love❤️"))                          // ❤️
        assertEquals("", EmojiFilter.strip("⭐🫠"))                            // ⭐ 🫠
    }

    @Test fun keepsPlainText() {
        for (s in listOf("^^", ":)", "ㅋㅋㅋ", "123 2S1B", "안녕하세요", "こんにちは", "你好", "Привет", "Xin chào", "a#b", "1*2")) {
            assertEquals(s, EmojiFilter.strip(s))
            assertFalse(EmojiFilter.containsEmoji(s))
        }
        assertTrue(EmojiFilter.containsEmoji("a😀"))
    }
}
