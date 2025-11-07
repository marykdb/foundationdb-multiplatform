package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TupleUtilTest {
    @Test
    fun floatEncodingIsReversible() {
        val encoded = TupleUtil.encodeFloatBits(-123.25f)
        val decoded = TupleUtil.decodeFloatBits(encoded)
        assertEquals(-123.25f, decoded)
        assertTrue(encoded > TupleUtil.encodeFloatBits(-200f))
    }

    @Test
    fun doubleEncodingIsReversible() {
        val encoded = TupleUtil.encodeDoubleBits(42.5)
        val decoded = TupleUtil.decodeDoubleBits(encoded)
        assertEquals(42.5, decoded)
        assertTrue(encoded < TupleUtil.encodeDoubleBits(100.0))
    }

    @Test
    fun minimalByteCountMatchesMagnitude() {
        assertEquals(1, TupleUtil.minimalByteCount(0))
        assertEquals(1, TupleUtil.minimalByteCount(7))
        assertEquals(1, TupleUtil.minimalByteCount(128))
        assertEquals(8, TupleUtil.minimalByteCount(Long.MIN_VALUE))
    }
}
