package app.numbaseball.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfanityFilterTest {
    private fun masked(s: String) = ProfanityFilter.mask(s) != s

    @Test fun masksEvasions() {
        for (s in listOf(
            "fuck", "FUCKING", "f u c k", "f.u.c.k", "fuuuuck", "f​uck", "ｆｕｃｋ", "fück", "fuсk", "sh1t", "s1ut",
            "a\$\$hole", "시발", "시 발", "시.발", "ㅅㅂ", "ㅅㅣㅂㅏㄹ", "씨발놈아", "сука", "cyka", "сyка", "х у й", "傻逼", "傻 逼",
            "死ね", "ｼﾈ", "バカ", "ばーか", "putain", "Scheiße", "caralho", "địt", "đ m", "fuck시발", "我fuck了",
        )) assertTrue(s, masked(s))
    }

    @Test fun leavesInnocentTextAlone() {
        for (s in listOf(
            "Scunthorpe", "Asuka", "Fukuoka", "push it", "this hit", "assassin", "cocktail", "shiitake", "snigger", "Niger",
            "시발점", "발표 시 발음", "새끼 고양이", "병신년", "닥쳐오는 위기", "ばかりです", "バカンス", "シネマ", "ほくそ笑む",
            "妈妈的手机", "体操你别", "computadora", "salopette", "Hurenkind", "застрахуйте", "употреблять", "команда",
            "các bạn ơi", "lồng đèn", "đậm đà", "123", "1818", "012345", "ㅋㅋㅋ 3스트라이크!", "kkkkk", ")))",
        )) assertEquals(s, s, ProfanityFilter.mask(s))
    }

    @Test fun masksOneStarPerCharacterAndKeepsSurroundings() {
        assertEquals("you are a ********* idiot", ProfanityFilter.mask("you are a fuckinggg idiot"))
        assertEquals("* * * *", ProfanityFilter.mask("f u c k"))
        assertEquals("hijo de ****", ProfanityFilter.mask("hijo de puta"))
        assertEquals("**** **** ****", ProfanityFilter.mask("fuck shit fuck"))
    }

    @Test fun strictModeFindsGluedWords() {
        assertFalse(ProfanityFilter.containsProfanity("xfuckx"))
        assertTrue(ProfanityFilter.containsProfanity("xfuckx", strict = true))
        assertFalse(ProfanityFilter.containsProfanity("Scunthorpe", strict = true))
        assertFalse(ProfanityFilter.containsProfanity("Asuka", strict = true)) // short whole-word entries stay whole-word
    }
}
