package maryk.foundationdb

actual object StringUtil {
    private const val HIGH_WITHOUT_LOW_ERR =
        "malformed UTF-16 string contains high surrogate that is not followed by low surrogate"
    private const val LOW_WITHOUT_HIGH_ERR =
        "malformed UTF-16 string contains low surrogate without prior high surrogate"
    private val SURROGATE_COUNT = (Char.MAX_LOW_SURROGATE - Char.MIN_HIGH_SURROGATE + 1)
    private val ABOVE_SURROGATES = (Char.MAX_VALUE - Char.MAX_LOW_SURROGATE)

    actual fun validate(string: String) {
        val length = string.length
        var index = 0
        while (index < length) {
            val ch = string[index]
            when {
                ch.isHighSurrogate() -> {
                    if (index + 1 >= length || !string[index + 1].isLowSurrogate()) {
                        throw IllegalArgumentException(HIGH_WITHOUT_LOW_ERR)
                    }
                    index += 2
                }
                ch.isLowSurrogate() -> throw IllegalArgumentException(LOW_WITHOUT_HIGH_ERR)
                else -> index++
            }
        }
    }

    actual fun compareUtf8(a: String, b: String): Int {
        val len = minOf(a.length, b.length)
        var pos = 0
        while (pos < len && a[pos] == b[pos]) {
            pos++
        }
        if (pos >= a.length || pos >= b.length) {
            return a.length.compareTo(b.length)
        }
        var c1 = a[pos]
        var c2 = b[pos]
        if (c1 >= Char.MIN_HIGH_SURROGATE) {
            c1 = adjustForSurrogates(c1, a, pos)
        }
        if (c2 >= Char.MIN_HIGH_SURROGATE) {
            c2 = adjustForSurrogates(c2, b, pos)
        }
        return c1.compareTo(c2)
    }

    actual fun packedSize(string: String): Int {
        val length = string.length
        var size = 0
        var pos = 0
        while (pos < length) {
            val ch = string[pos]
            when {
                ch == '\u0000' -> size += 2
                ch <= '\u007f' -> size += 1
                ch <= '\u07ff' -> size += 2
                ch.isHighSurrogate() -> {
                    if (pos + 1 < length && string[pos + 1].isLowSurrogate()) {
                        size += 4
                        pos += 1
                    } else {
                        throw IllegalArgumentException(HIGH_WITHOUT_LOW_ERR)
                    }
                }
                ch.isLowSurrogate() -> throw IllegalArgumentException(LOW_WITHOUT_HIGH_ERR)
                else -> size += 3
            }
            pos++
        }
        return size
    }

    private fun adjustForSurrogates(ch: Char, source: String, position: Int): Char {
        return if (ch > Char.MAX_LOW_SURROGATE) {
            (ch.code - SURROGATE_COUNT).toChar()
        } else {
            if (ch.isHighSurrogate()) {
                if (position + 1 >= source.length || !source[position + 1].isLowSurrogate()) {
                    throw IllegalArgumentException(HIGH_WITHOUT_LOW_ERR)
                }
            } else if (ch.isLowSurrogate()) {
                if (position == 0 || !source[position - 1].isHighSurrogate()) {
                    throw IllegalArgumentException(LOW_WITHOUT_HIGH_ERR)
                }
            }
            (ch.code + ABOVE_SURROGATES).toChar()
        }
    }

    private fun Char.isHighSurrogate(): Boolean =
        this in Char.MIN_HIGH_SURROGATE..Char.MAX_HIGH_SURROGATE

    private fun Char.isLowSurrogate(): Boolean =
        this in Char.MIN_LOW_SURROGATE..Char.MAX_LOW_SURROGATE
}
