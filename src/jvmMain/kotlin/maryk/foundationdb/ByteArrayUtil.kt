package maryk.foundationdb

actual object ByteArrayUtil {
    private val HEX = charArrayOf('0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f')

    actual fun join(vararg parts: ByteArray): ByteArray =
        joinWithInterlude(null, parts)

    actual fun join(interlude: ByteArray?, parts: List<ByteArray>?): ByteArray =
        joinWithInterlude(interlude, parts?.toTypedArray() ?: emptyArray())

    private fun joinWithInterlude(interlude: ByteArray?, parts: Array<out ByteArray>): ByteArray {
        if (parts.isEmpty()) return ByteArray(0)
        val actualInterlude = interlude ?: ByteArray(0)
        val interludeSize = actualInterlude.size
        var total = if (parts.isEmpty()) 0 else (parts.size - 1) * interludeSize
        parts.forEach { total += it.size }
        val dest = ByteArray(total)
        var offset = 0
        parts.forEachIndexed { index, part ->
            if (part.isNotEmpty()) {
                part.copyInto(dest, offset)
                offset += part.size
            }
            if (index < parts.lastIndex && interludeSize > 0) {
                actualInterlude.copyInto(dest, offset)
                offset += interludeSize
            }
        }
        return dest
    }

    actual fun regionEquals(src: ByteArray?, start: Int, pattern: ByteArray?): Boolean {
        if (src == null) {
            return start == 0 && pattern == null
        }
        require(start >= 0) { "start index after end of src" }
        if (pattern == null) return false
        if (start >= src.size) throw IllegalArgumentException("start index after end of src")
        if (src.size < start + pattern.size) return false
        for (idx in pattern.indices) {
            if (src[start + idx] != pattern[idx]) return false
        }
        return true
    }

    actual fun replace(src: ByteArray?, pattern: ByteArray?, replacement: ByteArray?): ByteArray? =
        byteArrayReplace(src, pattern, replacement)

    actual fun replace(src: ByteArray?, offset: Int, length: Int, pattern: ByteArray?, replacement: ByteArray?): ByteArray? =
        byteArrayReplace(src, offset, length, pattern, replacement)

    actual fun printable(bytes: ByteArray?): String? {
        bytes ?: return null
        if (bytes.isEmpty()) return ""
        val builder = StringBuilder(bytes.size)
        for (byte in bytes) {
            if (byte >= 32 && byte < 127 && byte.toInt() != '\\'.code) {
                builder.append(byte.toInt().toChar())
            } else if (byte.toInt() == '\\'.code) {
                builder.append("\\\\")
            } else {
                builder.append("\\x")
                val value = byte.toInt() and 0xFF
                builder.append(HEX[value ushr 4])
                builder.append(HEX[value and 0x0F])
            }
        }
        return builder.toString()
    }

    actual fun nullCount(bytes: ByteArray): Int =
        bytes.count { it == 0.toByte() }
    actual fun interludeJoin(interlude: ByteArray?, parts: Array<out ByteArray>): ByteArray = byteArrayInterludeJoin(interlude, parts)
    actual fun split(src: ByteArray, delimiter: ByteArray): List<ByteArray> = byteArraySplit(src, delimiter)
    actual fun split(src: ByteArray, offset: Int, length: Int, delimiter: ByteArray): List<ByteArray> = byteArraySplit(src, offset, length, delimiter)
    actual fun compareUnsigned(left: ByteArray, right: ByteArray): Int = byteArrayCompareUnsigned(left, right)
    actual fun comparator(): Comparator<ByteArray> = byteArrayComparator()
    actual fun startsWith(src: ByteArray, prefix: ByteArray): Boolean = byteArrayStartsWith(src, prefix)
    actual fun strinc(key: ByteArray): ByteArray = byteArrayStrinc(key)
    actual fun keyAfter(key: ByteArray): ByteArray = byteArrayKeyAfter(key)
    actual fun encodeInt(value: Long): ByteArray = byteArrayEncodeInt(value)
    actual fun decodeInt(value: ByteArray): Long = byteArrayDecodeInt(value)

}
