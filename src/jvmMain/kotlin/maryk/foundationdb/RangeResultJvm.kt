package maryk.foundationdb

import com.apple.foundationdb.KeyValue as JavaKeyValue
import com.apple.foundationdb.MappedKeyValue as JavaMappedKeyValue
import com.apple.foundationdb.async.AsyncIterable as JavaAsyncIterable
import maryk.foundationdb.async.AsyncIterable
import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.async.CloseableAsyncIterator
import java.util.concurrent.atomic.AtomicBoolean
import maryk.foundationdb.async.AsyncUtil

private fun requestedRowLimit(limit: Int): Int {
    require(limit >= 0) { "Range limit must be nonnegative" }
    return if (limit in 1 until Int.MAX_VALUE) limit + 1 else limit
}

internal actual fun ReadTransaction.collectRangeInternal(
    begin: ByteArray, end: ByteArray, limit: Int, reverse: Boolean, streamingMode: StreamingMode
): FdbFuture<RangeResult> = collectRows(
    delegate.getRange(begin, end, requestedRowLimit(limit), reverse, streamingMode.toJava()), limit,
    { KeyValue.fromDelegate(it as JavaKeyValue) }, ::RangeResult,
)

internal actual fun ReadTransaction.collectRangeInternal(
    begin: KeySelector, end: KeySelector, limit: Int, reverse: Boolean, streamingMode: StreamingMode
): FdbFuture<RangeResult> = collectRows(
    delegate.getRange(begin.delegate, end.delegate, requestedRowLimit(limit), reverse, streamingMode.toJava()), limit,
    { KeyValue.fromDelegate(it as JavaKeyValue) }, ::RangeResult,
)

internal actual fun ReadTransaction.collectMappedRangeInternal(
    begin: ByteArray, end: ByteArray, mapper: ByteArray, limit: Int, reverse: Boolean, streamingMode: StreamingMode
): FdbFuture<MappedRangeResult> = collectMappedRangeInternal(
    KeySelector.firstGreaterOrEqual(begin), KeySelector.firstGreaterOrEqual(end), mapper, limit, reverse, streamingMode,
)

internal actual fun ReadTransaction.collectMappedRangeInternal(
    begin: KeySelector, end: KeySelector, mapper: ByteArray, limit: Int, reverse: Boolean, streamingMode: StreamingMode
): FdbFuture<MappedRangeResult> = collectRows(
    delegate.getMappedRange(begin.delegate, end.delegate, mapper, requestedRowLimit(limit), reverse, streamingMode.toJava()), limit,
    { (it as JavaMappedKeyValue).toKotlinMappedKeyValue() }, ::MappedRangeResult,
)

/** Keep the requested mode and stop at the limit without mapping the lookahead row. */
private fun <T : KeyValue, R> collectRows(
    iterable: JavaAsyncIterable<*>, limit: Int, mapper: (Any?) -> T, result: (List<T>, RangeResultSummary) -> R
): FdbFuture<R> {
    val cursor = LimitedRangeIterator(AsyncIterable(iterable, mapper).iterator(), limit)
    return AsyncUtil.collectRemaining(cursor).asCompletableFuture().mapToFdbFuture { values ->
        result(values, RangeResultSummary(values.lastOrNull()?.key, values.size, cursor.hasMore))
    }
}

private class LimitedRangeIterator<T>(private val source: AsyncIterator<T>, private val limit: Int) : CloseableAsyncIterator<T> {
    private val closed = AtomicBoolean(false)
    private var count = 0
    var hasMore: Boolean = false
        private set
    private val atLimit: Boolean get() = limit > 0 && count >= limit

    override fun hasNext(): Boolean = if (atLimit) {
        hasMore = source.hasNext()
        false
    } else source.hasNext()

    override fun onHasNext(): FdbFuture<Boolean> = if (atLimit) {
        source.onHasNext().asCompletableFuture().mapToFdbFuture { more -> hasMore = more; false }
    } else source.onHasNext()

    override suspend fun next(): T {
        if (atLimit) throw NoSuchElementException()
        return source.next().also { count++ }
    }
    override fun cancel() = close()
    override fun close() { if (closed.compareAndSet(false, true)) source.cancel() }
}

internal fun JavaMappedKeyValue.toKotlinMappedKeyValue(): MappedKeyValue = MappedKeyValue(
    key = key,
    value = value,
    rangeBegin = rangeBegin,
    rangeEnd = rangeEnd,
    rangeResult = rangeResult.map { KeyValue.fromDelegate(it) },
)
