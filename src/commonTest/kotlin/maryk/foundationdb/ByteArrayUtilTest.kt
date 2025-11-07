package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ByteArrayUtilTest {
    @Test
    fun joinConcatenatesBytes() {
        val joined = ByteArrayUtil.join("ab".encodeToByteArray(), "cd".encodeToByteArray())
        assertContentEquals("abcd".encodeToByteArray(), joined)
    }

    @Test
    fun joinWithInterludeInsertsSeparator() {
        val parts = listOf("a".encodeToByteArray(), "b".encodeToByteArray(), "c".encodeToByteArray())
        val result = ByteArrayUtil.join("-".encodeToByteArray(), parts)
        assertContentEquals("a-b-c".encodeToByteArray(), result)
    }

    @Test
    fun regionEqualsValidatesInput() {
        val data = "abcdef".encodeToByteArray()
        assertTrue(ByteArrayUtil.regionEquals(data, 2, "cd".encodeToByteArray()))
        assertFalse(ByteArrayUtil.regionEquals(data, 2, "ce".encodeToByteArray()))
        assertFailsWith<IllegalArgumentException> {
            ByteArrayUtil.regionEquals(data, 10, "ce".encodeToByteArray())
        }
        assertTrue(ByteArrayUtil.regionEquals(null, 0, null))
    }

    @Test
    fun replaceSwapsPatterns() {
        val data = "axaxa".encodeToByteArray()
        val replaced = ByteArrayUtil.replace(data, "x".encodeToByteArray(), "yz".encodeToByteArray())
        assertContentEquals("ayzayza".encodeToByteArray(), replaced)

        val offset = ByteArrayUtil.replace(data, 1, 3, "xa".encodeToByteArray(), "q".encodeToByteArray())
        assertContentEquals("qq".encodeToByteArray(), offset)
    }

    @Test
    fun printableShowsEscapes() {
        val printable = ByteArrayUtil.printable(byteArrayOf(0x41, 0x1.toByte(), '\\'.code.toByte()))
        assertEquals("A\\x01\\\\", printable)
    }
}
