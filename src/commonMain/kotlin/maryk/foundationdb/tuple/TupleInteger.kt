package maryk.foundationdb.tuple

/** Exact integer value for tuple interoperability. Arithmetic is intentionally outside this API. */
class TupleInteger private constructor(val sign: Int, private val magnitude: ByteArray) : Number(), Comparable<TupleInteger> {
    override fun toLong(): Long {
        var value = 0L
        for (index in (magnitude.size - 8).coerceAtLeast(0) until magnitude.size) value = (value shl 8) or (magnitude[index].toLong() and 255)
        return if (sign < 0) -value else value
    }
    override fun toInt(): Int = toLong().toInt()
    override fun toShort(): Short = toLong().toShort()
    override fun toByte(): Byte = toLong().toByte()
    override fun toFloat(): Float = toString().toFloat()
    override fun toDouble(): Double = toString().toDouble()
    fun magnitudeBytes(): ByteArray = magnitude.copyOf()
    internal val magnitudeSize: Int get() = magnitude.size
    fun toLongExact(): Long {
        if (magnitude.size > 8) throw ArithmeticException("Integer exceeds Long range")
        var value = 0uL
        for (byte in magnitude) value = (value shl 8) or byte.toUByte().toULong()
        val bound = if (sign < 0) 1uL shl 63 else Long.MAX_VALUE.toULong()
        if (value > bound) throw ArithmeticException("Integer exceeds Long range")
        return if (sign < 0) -value.toLong() else value.toLong()
    }
    override fun compareTo(other: TupleInteger): Int {
        if (sign != other.sign) return sign.compareTo(other.sign)
        if (magnitude.size != other.magnitude.size) return sign * magnitude.size.compareTo(other.magnitude.size)
        for (i in magnitude.indices) {
            val comparison = magnitude[i].toUByte().compareTo(other.magnitude[i].toUByte())
            if (comparison != 0) return sign * comparison
        }
        return 0
    }
    override fun equals(other: Any?): Boolean = other is TupleInteger && sign == other.sign && magnitude.contentEquals(other.magnitude)
    override fun hashCode(): Int = 31 * sign + magnitude.contentHashCode()
    override fun toString(): String {
        if (sign == 0) return "0"
        val work = IntArray(magnitude.size) { magnitude[it].toInt() and 255 }
        val digits = StringBuilder()
        while (work.any { it != 0 }) {
            var remainder = 0
            for (i in work.indices) {
                val value = remainder * 256 + work[i]
                work[i] = value / 10
                remainder = value % 10
            }
            digits.append(('0'.code + remainder).toChar())
        }
        if (sign < 0) digits.append('-')
        return digits.reverse().toString()
    }
    companion object {
        fun fromMagnitude(sign: Int, bytes: ByteArray): TupleInteger {
            require(sign in -1..1)
            val first = bytes.indexOfFirst { it != 0.toByte() }
            val magnitude = if (first < 0) byteArrayOf() else bytes.copyOfRange(first, bytes.size)
            require(sign != 0 || magnitude.isEmpty()) { "Nonzero magnitude requires a sign" }
            return TupleInteger(if (magnitude.isEmpty()) 0 else sign, magnitude)
        }
        fun fromLong(value: Long): TupleInteger {
            val absolute = if (value < 0) 0uL - value.toULong() else value.toULong()
            var size = 0
            var remaining = absolute
            while (remaining != 0uL) { size++; remaining = remaining shr 8 }
            return TupleInteger(value.compareTo(0), ByteArray(size) { (absolute shr ((size - 1 - it) * 8)).toByte() })
        }
        fun parse(value: String): TupleInteger {
            require(value.isNotEmpty())
            val negative = value[0] == '-'
            val start = if (negative || value[0] == '+') 1 else 0
            require(start < value.length)
            val bytes = mutableListOf<Int>()
            for (index in start until value.length) {
                require(value[index] in '0'..'9') { "Invalid decimal integer" }
                var carry = value[index] - '0'
                for (i in bytes.indices.reversed()) {
                    val next = bytes[i] * 10 + carry
                    bytes[i] = next and 255
                    carry = next ushr 8
                }
                if (carry != 0) bytes.add(0, carry)
            }
            return fromMagnitude(if (negative) -1 else 1, bytes.map { it.toByte() }.toByteArray())
        }
    }
}
