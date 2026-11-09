package maryk.foundationdb

/**
 * Inclusive/exclusive key range descriptor.
 */
expect class Range(begin: ByteArray, end: ByteArray) {
    val begin: ByteArray
    val end: ByteArray

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    companion object {
        fun startsWith(prefix: ByteArray): Range
    }
}
