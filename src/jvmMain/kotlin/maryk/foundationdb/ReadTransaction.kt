package maryk.foundationdb

import maryk.foundationdb.async.AsyncIterable
import maryk.foundationdb.toFdbFuture

actual open class ReadTransaction internal constructor(
    internal open val delegate: com.apple.foundationdb.ReadTransaction
) {
    private fun wrap(iterable: com.apple.foundationdb.async.AsyncIterable<*>): AsyncIterable<KeyValue> =
        AsyncIterable(iterable) { KeyValue(it as com.apple.foundationdb.KeyValue) }

    actual fun get(key: ByteArray): FdbFuture<ByteArray?> =
        delegate.get(key).toFdbFuture()

    actual fun getRange(begin: ByteArray, end: ByteArray): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin, end))

    actual fun getRange(begin: ByteArray, end: ByteArray, limit: Int): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin, end, limit))

    actual fun getRange(begin: ByteArray, end: ByteArray, limit: Int, reverse: Boolean): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin, end, limit, reverse))

    actual fun getRange(
        begin: ByteArray,
        end: ByteArray,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin, end, limit, reverse, streamingMode.toJava()))

    actual fun getRange(range: Range): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(range.delegate))

    actual fun getRange(range: Range, limit: Int): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(range.delegate, limit))

    actual fun getRange(range: Range, limit: Int, reverse: Boolean): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(range.delegate, limit, reverse))

    actual fun getRange(
        range: Range,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(range.delegate, limit, reverse, streamingMode.toJava()))

    actual fun getRange(begin: KeySelector, end: KeySelector): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin.delegate, end.delegate))

    actual fun getRange(begin: KeySelector, end: KeySelector, limit: Int, reverse: Boolean): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin.delegate, end.delegate, limit, reverse))

    actual fun getRange(
        begin: KeySelector,
        end: KeySelector,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin.delegate, end.delegate, limit, reverse, streamingMode.toJava()))

    actual fun getReadVersion(): FdbFuture<Long> =
        delegate.getReadVersion().toFdbFuture()

    actual fun setReadVersion(version: Long) {
        delegate.setReadVersion(version)
    }

    actual fun addReadConflictKeyIfNotSnapshot(key: ByteArray): Boolean =
        delegate.addReadConflictKeyIfNotSnapshot(key)

    actual fun addReadConflictRangeIfNotSnapshot(beginKey: ByteArray, endKey: ByteArray): Boolean =
        delegate.addReadConflictRangeIfNotSnapshot(beginKey, endKey)

    actual fun snapshot(): ReadTransaction =
        ReadTransaction(delegate.snapshot())

    actual open fun options(): TransactionOptions = TransactionOptions(delegate.options())

    actual companion object {
        actual val ROW_LIMIT_UNLIMITED: Int = com.apple.foundationdb.ReadTransaction.ROW_LIMIT_UNLIMITED
    }
}
