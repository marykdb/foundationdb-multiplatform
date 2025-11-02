package maryk.foundationdb

import java.util.ArrayList
import java.util.concurrent.CompletableFuture

internal actual fun ReadTransaction.collectRangeInternal(
    begin: ByteArray,
    end: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<RangeResult> {
    val iterable = delegate.getRange(begin, end, limit, reverse, streamingMode.toJava())
    return collectFromIterable(iterable, limit).toFdbFuture()
}

internal actual fun ReadTransaction.collectRangeInternal(
    begin: KeySelector,
    end: KeySelector,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<RangeResult> {
    val iterable = delegate.getRange(begin.delegate, end.delegate, limit, reverse, streamingMode.toJava())
    return collectFromIterable(iterable, limit).toFdbFuture()
}

private fun collectFromIterable(
    iterable: com.apple.foundationdb.async.AsyncIterable<com.apple.foundationdb.KeyValue>,
    limit: Int
): CompletableFuture<RangeResult> {
    val iterator = iterable.iterator()
    val collected = ArrayList<com.apple.foundationdb.KeyValue>()

    fun completeResult(hasMore: Boolean): RangeResult {
        val kotlinValues = collected.map { KeyValue.fromDelegate(it) }
        val lastKey = kotlinValues.lastOrNull()?.key
        val summary = RangeResultSummary(lastKey, kotlinValues.size, hasMore)
        return RangeResult(kotlinValues, summary)
    }

    fun step(): CompletableFuture<RangeResult> {
        return iterator.onHasNext().thenCompose { hasNext ->
            if (!hasNext) {
                CompletableFuture.completedFuture(completeResult(false))
            } else {
                val kv = iterator.next()
                collected.add(kv)
                if (limit > 0 && collected.size >= limit) {
                    iterator.onHasNext().thenApply { more -> completeResult(more) }
                } else {
                    step()
                }
            }
        }
    }

    return step()
}
