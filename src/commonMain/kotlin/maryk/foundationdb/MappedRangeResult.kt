package maryk.foundationdb

/**
 * Result entry from [ReadTransaction.getMappedRange], capturing the primary key/value plus the
 * secondary range that was fetched for that entry.
 */
data class MappedKeyValue(
    val key: ByteArray,
    val value: ByteArray,
    val rangeBegin: ByteArray,
    val rangeEnd: ByteArray,
    val rangeResult: List<KeyValue>
)

/**
 * Aggregated chunk returned by [collectMappedRange], mirroring the Java client's
 * `MappedRangeResult` contract.
 */
data class MappedRangeResult(
    val values: List<MappedKeyValue>,
    val summary: RangeResultSummary
) {
    val hasMore: Boolean get() = summary.hasMore
}

fun ReadTransaction.collectMappedRange(
    begin: ByteArray,
    end: ByteArray,
    mapper: ByteArray,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): FdbFuture<MappedRangeResult> =
    collectMappedRangeInternal(begin, end, mapper, limit, reverse, streamingMode)

fun ReadTransaction.collectMappedRange(
    range: Range,
    mapper: ByteArray,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): FdbFuture<MappedRangeResult> =
    collectMappedRange(range.begin, range.end, mapper, limit, reverse, streamingMode)

fun ReadTransaction.collectMappedRange(
    begin: KeySelector,
    end: KeySelector,
    mapper: ByteArray,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): FdbFuture<MappedRangeResult> =
    collectMappedRangeInternal(begin, end, mapper, limit, reverse, streamingMode)

internal expect fun ReadTransaction.collectMappedRangeInternal(
    begin: ByteArray,
    end: ByteArray,
    mapper: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<MappedRangeResult>

internal expect fun ReadTransaction.collectMappedRangeInternal(
    begin: KeySelector,
    end: KeySelector,
    mapper: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<MappedRangeResult>
