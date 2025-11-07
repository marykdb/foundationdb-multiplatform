package maryk.foundationdb

actual object TupleUtil {
    actual fun encodeFloatBits(value: Float): Int {
        val bits = value.toRawBits()
        return if (bits < 0) bits.inv() else bits xor Int.MIN_VALUE
    }

    actual fun decodeFloatBits(bits: Int): Float {
        val raw = if (bits >= 0) bits.inv() else bits xor Int.MIN_VALUE
        return Float.fromBits(raw)
    }

    actual fun encodeDoubleBits(value: Double): Long {
        val bits = value.toRawBits()
        return if (bits < 0) bits.inv() else bits xor Long.MIN_VALUE
    }

    actual fun decodeDoubleBits(bits: Long): Double {
        val raw = if (bits >= 0) bits.inv() else bits xor Long.MIN_VALUE
        return Double.fromBits(raw)
    }

    actual fun minimalByteCount(value: Long): Int {
        if (value == Long.MIN_VALUE) return 8
        val magnitude = if (value >= 0) value else -value
        if (magnitude == 0L) return 1
        var bits = 0
        var temp = magnitude
        while (temp != 0L) {
            bits++
            temp = temp ushr 1
        }
        return (bits + 7) / 8
    }
}
