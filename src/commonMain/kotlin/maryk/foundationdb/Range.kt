package maryk.foundationdb

/**
 * Inclusive/exclusive key range descriptor.
 */
expect class Range(begin: ByteArray, end: ByteArray) {
    val begin: ByteArray
    val end: ByteArray

    companion object {
        fun startsWith(prefix: ByteArray): Range
    }
}
