package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.TupleInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TupleIntegerBoundsNativeTest {
    @Test
    fun overwideJavaIntegersAreDecodedExactly() {
        assertEquals("9223372036854775808", Tuple.fromBytes(byteArrayOf(0x1c, 0x80.toByte()) + ByteArray(7)).getBigInteger(0).toString())
        assertEquals("-9223372036854775809", Tuple.fromBytes(byteArrayOf(0x0c, 0x7f) + ByteArray(6) { 0xff.toByte() } + byteArrayOf(0xfe.toByte())).getBigInteger(0).toString())
        assertEquals("18446744073709551616", Tuple.fromBytes(byteArrayOf(0x1d, 9, 1) + ByteArray(8)).getBigInteger(0).toString())
        assertFailsWith<IllegalArgumentException> { Tuple.fromBytes(byteArrayOf(0x1c, 1)) }
        assertEquals(Long.MAX_VALUE, Tuple.fromBytes(byteArrayOf(0x1c, 0x7f) + ByteArray(7) { 0xff.toByte() }).getLong(0))
        assertEquals(Long.MIN_VALUE, Tuple.fromBytes(byteArrayOf(0x0c, 0x7f) + ByteArray(7) { 0xff.toByte() }).getLong(0))
    }
}
