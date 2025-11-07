package maryk.foundationdb

actual object FastByteComparisons {
    actual fun compareTo(
        left: ByteArray,
        leftOffset: Int,
        leftLength: Int,
        right: ByteArray,
        rightOffset: Int,
        rightLength: Int
    ): Int {
        val minLength = minOf(leftLength, rightLength)
        for (idx in 0 until minLength) {
            val leftByte = left[leftOffset + idx].toInt() and 0xFF
            val rightByte = right[rightOffset + idx].toInt() and 0xFF
            if (leftByte != rightByte) {
                return leftByte - rightByte
            }
        }
        return leftLength - rightLength
    }

    actual fun comparator(): Comparator<ByteArray> =
        Comparator { a, b -> compareTo(a, 0, a.size, b, 0, b.size) }
}
