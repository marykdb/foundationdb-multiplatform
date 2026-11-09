package maryk.foundationdb

import maryk.foundationdb.async.AsyncIterable

actual open class ReadTransaction internal constructor(
    internal open val delegate: com.apple.foundationdb.ReadTransaction
) : ReadTransactionContext {
    private fun wrap(iterable: com.apple.foundationdb.async.AsyncIterable<*>): AsyncIterable<KeyValue> =
        AsyncIterable(iterable) { KeyValue(it as com.apple.foundationdb.KeyValue) }

    private fun wrapMapped(iterable: com.apple.foundationdb.async.AsyncIterable<*>): AsyncIterable<MappedKeyValue> =
        AsyncIterable(iterable) { value ->
            val mapped = value as com.apple.foundationdb.MappedKeyValue
            mapped.toKotlinMappedKeyValue()
        }

    actual fun get(key: ByteArray): FdbFuture<ByteArray?> =
        delegate.get(key).toOperationFdbFuture()

    actual fun getRange(begin: ByteArray, end: ByteArray): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin, end))

    actual fun getRange(begin: ByteArray, end: ByteArray, limit: Int): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin, end, checkedRangeLimit(limit)))

    actual fun getRange(begin: ByteArray, end: ByteArray, limit: Int, reverse: Boolean): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin, end, checkedRangeLimit(limit), reverse))

    actual fun getRange(
        begin: ByteArray,
        end: ByteArray,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin, end, checkedRangeLimit(limit), reverse, streamingMode.toJava()))

    actual fun getMappedRange(begin: ByteArray, end: ByteArray, mapper: ByteArray): AsyncIterable<MappedKeyValue> =
        getMappedRange(begin, end, mapper, limit = 0)

    actual fun getMappedRange(begin: ByteArray, end: ByteArray, mapper: ByteArray, limit: Int): AsyncIterable<MappedKeyValue> =
        getMappedRange(begin, end, mapper, limit, reverse = false)

    actual fun getMappedRange(
        begin: ByteArray,
        end: ByteArray,
        mapper: ByteArray,
        limit: Int,
        reverse: Boolean
    ): AsyncIterable<MappedKeyValue> =
        getMappedRange(begin, end, mapper, limit, reverse, StreamingMode.ITERATOR)

    actual fun getMappedRange(
        begin: ByteArray,
        end: ByteArray,
        mapper: ByteArray,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<MappedKeyValue> =
        getMappedRange(
            KeySelector.firstGreaterOrEqual(begin),
            KeySelector.firstGreaterOrEqual(end),
            mapper,
            limit,
            reverse,
            streamingMode
        )

    actual fun getRange(range: Range): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(range.delegate))

    actual fun getRange(range: Range, limit: Int): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(range.delegate, checkedRangeLimit(limit)))

    actual fun getRange(range: Range, limit: Int, reverse: Boolean): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(range.delegate, checkedRangeLimit(limit), reverse))

    actual fun getRange(
        range: Range,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(range.delegate, checkedRangeLimit(limit), reverse, streamingMode.toJava()))

    actual fun getMappedRange(range: Range, mapper: ByteArray): AsyncIterable<MappedKeyValue> =
        getMappedRange(range, mapper, limit = 0)

    actual fun getMappedRange(range: Range, mapper: ByteArray, limit: Int): AsyncIterable<MappedKeyValue> =
        getMappedRange(range, mapper, limit, reverse = false)

    actual fun getMappedRange(
        range: Range,
        mapper: ByteArray,
        limit: Int,
        reverse: Boolean
    ): AsyncIterable<MappedKeyValue> =
        getMappedRange(range, mapper, limit, reverse, StreamingMode.ITERATOR)

    actual fun getMappedRange(
        range: Range,
        mapper: ByteArray,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<MappedKeyValue> =
        getMappedRange(range.begin, range.end, mapper, limit, reverse, streamingMode)

    actual fun getRange(begin: KeySelector, end: KeySelector): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin.delegate, end.delegate))

    actual fun getRange(begin: KeySelector, end: KeySelector, limit: Int, reverse: Boolean): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin.delegate, end.delegate, checkedRangeLimit(limit), reverse))

    actual fun getRange(
        begin: KeySelector,
        end: KeySelector,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<KeyValue> =
        wrap(delegate.getRange(begin.delegate, end.delegate, checkedRangeLimit(limit), reverse, streamingMode.toJava()))

    actual fun getMappedRange(begin: KeySelector, end: KeySelector, mapper: ByteArray): AsyncIterable<MappedKeyValue> =
        getMappedRange(begin, end, mapper, limit = 0, reverse = false)

    actual fun getMappedRange(
        begin: KeySelector,
        end: KeySelector,
        mapper: ByteArray,
        limit: Int,
        reverse: Boolean
    ): AsyncIterable<MappedKeyValue> =
        getMappedRange(begin, end, mapper, limit, reverse, StreamingMode.ITERATOR)

    actual fun getMappedRange(
        begin: KeySelector,
        end: KeySelector,
        mapper: ByteArray,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<MappedKeyValue> =
        wrapMapped(delegate.getMappedRange(begin.delegate, end.delegate, mapper, checkedRangeLimit(limit), reverse, streamingMode.toJava()))

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

    actual open override fun <T> read(block: (ReadTransaction) -> T): T = block(this)
    actual open override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        try { block(this) } catch (error: Throwable) { failedFdbFuture(error) }

    actual fun isSnapshot(): Boolean = delegate.isSnapshot
    actual fun getKey(selector: KeySelector): FdbFuture<ByteArray> = delegate.getKey(selector.delegate).toFdbFuture()
    actual open fun getEstimatedRangeSizeBytes(range: Range): FdbFuture<Long> =
        delegate.getEstimatedRangeSizeBytes(range.delegate).toFdbFuture()
    actual open fun getRangeSplitPoints(range: Range, chunkSize: Long): FdbFuture<List<ByteArray>> {
        require(chunkSize > 0)
        return delegate.getRangeSplitPoints(range.delegate, chunkSize).mapToFdbFuture { result -> result.keys.map { it.copyOf() } }
    }

    @ExperimentalBlobGranules
    actual fun getBlobGranuleRanges(range: Range, rowLimit: Int): FdbFuture<List<Range>> =
        delegate.getBlobGranuleRanges(range.begin, range.end, rowLimit).mapToFdbFuture { result ->
            result.keyRanges.map { Range(it.begin.copyOf(), it.end.copyOf()) }
        }


    actual fun getRange(begin: KeySelector, end: KeySelector, limit: Int): AsyncIterable<KeyValue> = getRange(begin, end, limit, false)
    actual fun getMappedRange(begin: KeySelector, end: KeySelector, mapper: ByteArray, limit: Int): AsyncIterable<MappedKeyValue> = getMappedRange(begin, end, mapper, limit, false)
    actual fun getEstimatedRangeSizeBytes(begin: ByteArray, end: ByteArray): FdbFuture<Long> = getEstimatedRangeSizeBytes(Range(begin, end))
    actual fun getRangeSplitPoints(begin: ByteArray, end: ByteArray, chunkSize: Long): FdbFuture<List<ByteArray>> = getRangeSplitPoints(Range(begin, end), chunkSize)
    @ExperimentalBlobGranules
    actual fun getBlobGranuleRanges(begin: ByteArray, end: ByteArray, rowLimit: Int): FdbFuture<List<Range>> = getBlobGranuleRanges(Range(begin, end), rowLimit)
    actual companion object {
        actual val ROW_LIMIT_UNLIMITED: Int = com.apple.foundationdb.ReadTransaction.ROW_LIMIT_UNLIMITED
    }
}
