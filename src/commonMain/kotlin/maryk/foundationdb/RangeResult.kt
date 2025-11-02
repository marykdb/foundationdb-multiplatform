package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple

data class RangeResultSummary(
    val lastKey: ByteArray?,
    val keyCount: Int,
    val hasMore: Boolean
)

data class RangeResult(
    val values: List<KeyValue>,
    val summary: RangeResultSummary
) {
    val hasMore: Boolean get() = summary.hasMore
}

fun ReadTransaction.collectRange(
    begin: ByteArray,
    end: ByteArray,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): FdbFuture<RangeResult> = collectRangeInternal(begin, end, limit, reverse, streamingMode)

fun ReadTransaction.collectRange(
    range: Range,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): FdbFuture<RangeResult> = collectRange(range.begin, range.end, limit, reverse, streamingMode)

fun ReadTransaction.collectRange(
    begin: KeySelector,
    end: KeySelector,
    limit: Int = 0,
    reverse: Boolean = false,
    streamingMode: StreamingMode = StreamingMode.ITERATOR
): FdbFuture<RangeResult> = collectRangeInternal(begin, end, limit, reverse, streamingMode)

internal expect fun ReadTransaction.collectRangeInternal(
    begin: ByteArray,
    end: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<RangeResult>

internal expect fun ReadTransaction.collectRangeInternal(
    begin: KeySelector,
    end: KeySelector,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<RangeResult>
