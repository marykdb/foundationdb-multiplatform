package maryk.foundationdb

/**
 * Identifies a key in the database relative to another key.
 */
expect class KeySelector(key: ByteArray, orEqual: Boolean, offset: Int) {
    val key: ByteArray
    val orEqual: Boolean
    val offset: Int

    fun add(offset: Int): KeySelector

    companion object {
        fun lastLessThan(key: ByteArray): KeySelector
        fun lastLessOrEqual(key: ByteArray): KeySelector
        fun firstGreaterThan(key: ByteArray): KeySelector
        fun firstGreaterOrEqual(key: ByteArray): KeySelector
    }
}
