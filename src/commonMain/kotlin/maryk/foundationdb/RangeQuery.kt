package maryk.foundationdb

import maryk.foundationdb.async.AsyncIterator

/**
 * Convenience wrapper around [AsyncIterable]-based range reads that provides both iterator-style and
 * list-style access to the read results. This mirrors the Java client's `RangeQuery` helper while
 * reusing the existing multiplatform [AsyncIterable] plumbing.
 */
class RangeQuery internal constructor(
    private val iterable: maryk.foundationdb.async.AsyncIterable<KeyValue>
) {
    fun iterator(): AsyncIterator<KeyValue> = iterable.iterator()
    fun asList(): FdbFuture<List<KeyValue>> = iterable.asList()
}

fun Transaction.rangeQuery(
    begin: ByteArray,
    end: ByteArray,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): RangeQuery = RangeQuery(getRange(begin, end, limit, reverse, streamingMode))

fun Transaction.rangeQuery(
    begin: KeySelector,
    end: KeySelector,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): RangeQuery = RangeQuery(getRange(begin, end, limit, reverse, streamingMode))

fun Transaction.rangeQuery(
    range: Range,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): RangeQuery = RangeQuery(getRange(range, limit, reverse, streamingMode))

fun ReadTransaction.rangeQuery(
    begin: ByteArray,
    end: ByteArray,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): RangeQuery = RangeQuery(getRange(begin, end, limit, reverse, streamingMode))

fun ReadTransaction.rangeQuery(
    begin: KeySelector,
    end: KeySelector,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): RangeQuery = RangeQuery(getRange(begin, end, limit, reverse, streamingMode))

fun ReadTransaction.rangeQuery(
    range: Range,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): RangeQuery = RangeQuery(getRange(range, limit, reverse, streamingMode))
