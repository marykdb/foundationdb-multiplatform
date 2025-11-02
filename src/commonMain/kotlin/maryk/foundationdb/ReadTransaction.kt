package maryk.foundationdb

import maryk.foundationdb.async.AsyncIterable

/**
 * Read-only view of a FoundationDB transaction.
 */
expect open class ReadTransaction {
    fun get(key: ByteArray): FdbFuture<ByteArray?>
    fun getRange(begin: ByteArray, end: ByteArray): AsyncIterable<KeyValue>
    fun getRange(begin: ByteArray, end: ByteArray, limit: Int): AsyncIterable<KeyValue>
    fun getRange(begin: ByteArray, end: ByteArray, limit: Int, reverse: Boolean): AsyncIterable<KeyValue>
    fun getRange(begin: ByteArray, end: ByteArray, limit: Int, reverse: Boolean, streamingMode: StreamingMode): AsyncIterable<KeyValue>
    fun getRange(range: Range): AsyncIterable<KeyValue>
    fun getRange(range: Range, limit: Int): AsyncIterable<KeyValue>
    fun getRange(range: Range, limit: Int, reverse: Boolean): AsyncIterable<KeyValue>
    fun getRange(range: Range, limit: Int, reverse: Boolean, streamingMode: StreamingMode): AsyncIterable<KeyValue>
    fun getRange(begin: KeySelector, end: KeySelector): AsyncIterable<KeyValue>
    fun getRange(begin: KeySelector, end: KeySelector, limit: Int, reverse: Boolean): AsyncIterable<KeyValue>
    fun getRange(begin: KeySelector, end: KeySelector, limit: Int, reverse: Boolean, streamingMode: StreamingMode): AsyncIterable<KeyValue>
    fun getReadVersion(): FdbFuture<Long>
    fun setReadVersion(version: Long)
    fun addReadConflictKeyIfNotSnapshot(key: ByteArray): Boolean
    fun addReadConflictRangeIfNotSnapshot(beginKey: ByteArray, endKey: ByteArray): Boolean
    fun snapshot(): ReadTransaction
    open fun options(): TransactionOptions

    companion object {
        val ROW_LIMIT_UNLIMITED: Int
    }
}
