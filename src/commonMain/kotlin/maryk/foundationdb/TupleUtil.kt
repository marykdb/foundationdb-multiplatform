package maryk.foundationdb

expect object TupleUtil {
    fun encodeFloatBits(value: Float): Int
    fun decodeFloatBits(bits: Int): Float

    fun encodeDoubleBits(value: Double): Long
    fun decodeDoubleBits(bits: Long): Double

    fun minimalByteCount(value: Long): Int
}
