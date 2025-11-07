package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StringUtilTest {
    @Test
    fun compareUtf8OrdersUnicodeStrings() {
        val a = "a\uD83D\uDE00" // a + 😀
        val b = "a\uD83D\uDE01" // a + 😁
        assertTrue(StringUtil.compareUtf8(a, b) < 0)
        assertEquals(0, StringUtil.compareUtf8("plain", "plain"))
    }

    @Test
    fun validateRejectsBrokenSurrogates() {
        assertFailsWith<IllegalArgumentException> {
            StringUtil.validate("\uD83D")
        }
        StringUtil.validate("ok\uD83D\uDE00")
    }

    @Test
    fun packedSizeMatchesUtf8Lengths() {
        assertEquals(2, StringUtil.packedSize("\u00A2")) // 2-byte code point
        assertEquals(4, StringUtil.packedSize("\uD83D\uDE00")) // surrogate pair -> 4 bytes
        assertEquals(2, StringUtil.packedSize("\u0000")) // null encoded as 0x00,0xFF
    }
}
