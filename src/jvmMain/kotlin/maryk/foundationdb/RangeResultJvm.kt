package maryk.foundationdb

import java.util.concurrent.CompletableFuture

internal actual fun ReadTransaction.collectRangeInternal(
    begin: ByteArray,
    end: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<RangeResult> {
    val requestedLimit = if (limit > 0) limit + 1 else limit
    val iterable = delegate.getRange(begin, end, requestedLimit, reverse, streamingMode.toJava())
    return collectFromIterable(iterable, limit).toFdbFuture()
}

internal actual fun ReadTransaction.collectRangeInternal(
    begin: KeySelector,
    end: KeySelector,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<RangeResult> {
    val requestedLimit = if (limit > 0) limit + 1 else limit
    val iterable = delegate.getRange(begin.delegate, end.delegate, requestedLimit, reverse, streamingMode.toJava())
    return collectFromIterable(iterable, limit).toFdbFuture()
}

private fun collectFromIterable(
    iterable: com.apple.foundationdb.async.AsyncIterable<com.apple.foundationdb.KeyValue>,
    limit: Int
): CompletableFuture<RangeResult> {
    val iterator = iterable.iterator()
    val collected = ArrayList<com.apple.foundationdb.KeyValue>()

    fun completeResult(hasMoreHint: Boolean): RangeResult {
        val kotlinValues = collected.map { KeyValue.fromDelegate(it) }
        val limitedValues = if (limit > 0) kotlinValues.take(limit) else kotlinValues
        val lastKey = limitedValues.lastOrNull()?.key
        val hasMore = hasMoreHint || (limit > 0 && kotlinValues.size > limitedValues.size)
        val summary = RangeResultSummary(lastKey, limitedValues.size, hasMore)
        return RangeResult(limitedValues, summary)
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

internal actual fun ReadTransaction.collectMappedRangeInternal(
    begin: ByteArray,
    end: ByteArray,
    mapper: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<MappedRangeResult> =
    collectMappedRangeInternal(
        KeySelector.firstGreaterOrEqual(begin),
        KeySelector.firstGreaterOrEqual(end),
        mapper,
        limit,
        reverse,
        streamingMode
    )

internal actual fun ReadTransaction.collectMappedRangeInternal(
    begin: KeySelector,
    end: KeySelector,
    mapper: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<MappedRangeResult> {
    val requestedLimit = if (limit > 0) limit + 1 else limit
    val iterable = delegate.getMappedRange(begin.delegate, end.delegate, mapper, requestedLimit, reverse, streamingMode.toJava())
    return collectMappedFromIterable(iterable, limit).toFdbFuture()
    }

private fun collectMappedFromIterable(
    iterable: com.apple.foundationdb.async.AsyncIterable<com.apple.foundationdb.MappedKeyValue>,
    limit: Int
): CompletableFuture<MappedRangeResult> {
    val iterator = iterable.iterator()
    val collected = ArrayList<com.apple.foundationdb.MappedKeyValue>()

    fun completeResult(hasMoreHint: Boolean): MappedRangeResult {
        val kotlinValues = collected.map { it.toKotlinMappedKeyValue() }
        val limitedValues = if (limit > 0) kotlinValues.take(limit) else kotlinValues
        val lastKey = limitedValues.lastOrNull()?.key
        val hasMore = hasMoreHint || (limit > 0 && kotlinValues.size > limitedValues.size)
        val summary = RangeResultSummary(lastKey, limitedValues.size, hasMore)
        return MappedRangeResult(limitedValues, summary)
    }

    fun step(): CompletableFuture<MappedRangeResult> {
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

internal fun com.apple.foundationdb.MappedKeyValue.toKotlinMappedKeyValue(): MappedKeyValue {
    val mappedRangeResult = this.rangeResult.map { KeyValue.fromDelegate(it) }
    return MappedKeyValue(
        key = this.key,
        value = this.value,
        rangeBegin = this.rangeBegin,
        rangeEnd = this.rangeEnd,
        rangeResult = mappedRangeResult
    )
}
