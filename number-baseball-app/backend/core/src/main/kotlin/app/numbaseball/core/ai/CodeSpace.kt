package app.numbaseball.core.ai

import app.numbaseball.core.rules.Rules

/**
 * Every number of [digits] distinct digits, packed for fast judging: digit i sits in bits 4i..4i+3
 * and [masks] holds the 10-bit set of digits used. P(10, 6) = 151 200 codes at most (~1.2 MB for both arrays).
 */
internal class CodeSpace private constructor(val digits: Int) {
    val codes: IntArray
    val masks: IntArray

    init {
        val total = (0 until digits).fold(1) { acc, i -> acc * (10 - i) }
        codes = IntArray(total)
        masks = IntArray(total)
        var n = 0
        fun rec(pos: Int, packed: Int, mask: Int) {
            if (pos == digits) {
                codes[n] = packed; masks[n] = mask; n++; return
            }
            for (d in 0..9) {
                if (mask and (1 shl d) == 0) rec(pos + 1, packed or (d shl (4 * pos)), mask or (1 shl d))
            }
        }
        rec(0, 0, 0)
    }

    val size: Int get() = codes.size

    fun format(code: Int): String {
        val sb = StringBuilder(digits)
        for (i in 0 until digits) sb.append('0' + ((code ushr (4 * i)) and 0xF))
        return sb.toString()
    }

    companion object {
        private val cache = arrayOfNulls<CodeSpace>(7)

        fun of(digits: Int): CodeSpace {
            require(Rules.isValidDigitCount(digits))
            synchronized(cache) {
                return cache[digits] ?: CodeSpace(digits).also { cache[digits] = it }
            }
        }

        fun pack(value: String): Int {
            var code = 0
            for (i in value.indices) code = code or ((value[i] - '0') shl (4 * i))
            return code
        }

        fun maskOf(value: String): Int = value.fold(0) { m, c -> m or (1 shl (c - '0')) }

        /** Feedback as strikes * 8 + balls (fits in 0..55). */
        fun feedback(a: Int, maskA: Int, b: Int, maskB: Int, digits: Int): Int {
            var x = a xor b
            var strikes = 0
            for (i in 0 until digits) {
                if (x and 0xF == 0) strikes++
                x = x ushr 4
            }
            return strikes * 8 + Integer.bitCount(maskA and maskB) - strikes
        }

        fun feedback(strikes: Int, balls: Int): Int = strikes * 8 + balls
    }
}
