package maryk.foundationdb
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
class ByteSliceSafetyTest {
    @Test fun replacementNeverMatchesOutsideTheRequestedSlice() {
        assertContentEquals(byteArrayOf(1, 2), ByteArrayUtil.replace(byteArrayOf(1, 2, 3), 0, 2, byteArrayOf(2, 3), byteArrayOf(9)))
        assertContentEquals(byteArrayOf(2), ByteArrayUtil.replace(byteArrayOf(1, 2, 3), 1, 1, byteArrayOf(2, 3), byteArrayOf(9)))
    }
    @Test fun overflowingSliceLengthsAreRejected() {
        assertFailsWith<IllegalArgumentException> { ByteArrayUtil.replace(byteArrayOf(1, 2, 3), 1, Int.MAX_VALUE, byteArrayOf(2), byteArrayOf(9)) }
    }
    @Test fun matchesAtSliceEdgesAndNullReplacementRemainSupported() {
        val input = byteArrayOf(1, 2, 1, 2, 3)
        assertContentEquals(byteArrayOf(9, 9), ByteArrayUtil.replace(input, 0, 4, byteArrayOf(1, 2), byteArrayOf(9)))
        assertContentEquals(byteArrayOf(3), ByteArrayUtil.replace(input, byteArrayOf(1, 2), null))
        assertContentEquals(byteArrayOf(2, 1), ByteArrayUtil.replace(input, 1, 2, byteArrayOf(), byteArrayOf(9)))
        assertContentEquals(byteArrayOf(1, 2, 1, 2, 3), input)
        assertContentEquals(byteArrayOf(), ByteArrayUtil.replace(input, input.size, 0, byteArrayOf(1), null))
    }

}
