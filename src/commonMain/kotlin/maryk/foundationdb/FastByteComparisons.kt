package maryk.foundationdb

expect object FastByteComparisons {
    fun compareTo(
        left: ByteArray,
        leftOffset: Int,
        leftLength: Int,
        right: ByteArray,
        rightOffset: Int,
        rightLength: Int
    ): Int

    fun comparator(): Comparator<ByteArray>
}
