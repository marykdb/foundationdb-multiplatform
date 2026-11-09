package maryk.foundationdb

internal fun byteArrayInterludeJoin(interlude: ByteArray?, parts: Array<out ByteArray>): ByteArray = ByteArrayUtil.join(interlude, parts.toList())
internal fun byteArraySplit(src: ByteArray, delimiter: ByteArray): List<ByteArray> = byteArraySplit(src, 0, src.size, delimiter)
internal fun byteArraySplit(src: ByteArray, offset: Int, length: Int, delimiter: ByteArray): List<ByteArray> {
        require(offset >= 0 && length >= 0 && offset <= src.size - length)
        require(delimiter.isNotEmpty()) { "Delimiter cannot be empty" }
        val result = mutableListOf<ByteArray>()
        val end = offset + length
        var cursor = offset
        var start = offset
        while (cursor <= end - delimiter.size) {
            if (delimiter.indices.all { src[cursor + it] == delimiter[it] }) {
                result += src.copyOfRange(start, cursor)
                cursor += delimiter.size
                start = cursor
            } else cursor++
        }
        result += src.copyOfRange(start, end)
        return result
    }
internal fun byteArrayCompareUnsigned(left: ByteArray, right: ByteArray): Int =
        FastByteComparisons.compareTo(left, 0, left.size, right, 0, right.size).compareTo(0)
internal fun byteArrayComparator(): Comparator<ByteArray> = Comparator(::byteArrayCompareUnsigned)
internal fun byteArrayStartsWith(src: ByteArray, prefix: ByteArray): Boolean =
        prefix.size <= src.size && prefix.indices.all { src[it] == prefix[it] }
internal fun byteArrayStrinc(key: ByteArray): ByteArray {
        for (index in key.indices.reversed()) if (key[index] != 0xff.toByte()) {
            return key.copyOf(index + 1).also { it[index] = (it[index] + 1).toByte() }
        }
        throw IllegalArgumentException("Key has no lexicographic successor")
    }
internal fun byteArrayKeyAfter(key: ByteArray): ByteArray = key + byteArrayOf(0)
internal fun byteArrayEncodeInt(value: Long): ByteArray = ByteArray(8) { (value ushr (it * 8)).toByte() }
internal fun byteArrayDecodeInt(value: ByteArray): Long {
        require(value.size == 8) { "Source array must be of length 8" }
        return value.indices.fold(0L) { result, index -> result or ((value[index].toLong() and 255) shl (index * 8)) }
    }

/** Replace non-overlapping matches wholly contained in the selected slice. */
internal fun byteArrayReplace(src: ByteArray?, pattern: ByteArray?, replacement: ByteArray?): ByteArray? =
    if (src == null) null else byteArrayReplace(src, 0, src.size, pattern, replacement)

internal fun byteArrayReplace(src: ByteArray?, offset: Int, length: Int, pattern: ByteArray?, replacement: ByteArray?): ByteArray? {
    if (src == null) return null
    require(offset >= 0 && length >= 0 && offset <= src.size - length) { "Invalid source slice" }
    val end = offset + length
    if (pattern == null || pattern.isEmpty()) return src.copyOfRange(offset, end)
    val insert = replacement ?: byteArrayOf()
    val first = pattern[0]
    var cursor = offset
    var previous = offset
    var size = 0L
    while (cursor < end) {
        if (src[cursor] == first && matchesWithin(src, cursor, end, pattern)) {
            size += cursor - previous + insert.size.toLong()
            cursor += pattern.size
            previous = cursor
        } else cursor++
    }
    size += end - previous
    require(size <= Int.MAX_VALUE) { "Replacement result is too large" }
    val result = ByteArray(size.toInt())
    var output = 0
    previous = offset
    cursor = offset
    while (cursor < end) {
        if (src[cursor] == first && matchesWithin(src, cursor, end, pattern)) {
            src.copyInto(result, output, previous, cursor)
            output += cursor - previous
            insert.copyInto(result, output)
            output += insert.size
            cursor += pattern.size
            previous = cursor
        } else cursor++
    }
    src.copyInto(result, output, previous, end)
    return result
}

private fun matchesWithin(src: ByteArray, position: Int, end: Int, pattern: ByteArray): Boolean {
    if (pattern.size > end - position) return false
    for (index in 1 until pattern.size) if (src[position + index] != pattern[index]) return false
    return true
}
