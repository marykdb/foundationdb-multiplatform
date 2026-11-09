@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package maryk.foundationdb

import foundationdb.c.fdb_transaction_get_blob_granule_ranges

import foundationdb.c.FDBKey
import foundationdb.c.fdb_future_get_key_array
import foundationdb.c.fdb_future_get_key
import foundationdb.c.fdb_transaction_get_key
import foundationdb.c.fdb_transaction_get_estimated_range_size_bytes
import foundationdb.c.fdb_transaction_get_range_split_points

import foundationdb.c.FDBKeySelector
import foundationdb.c.FDBKeyValue
import foundationdb.c.FDBFuture
import foundationdb.c.FDBMappedKeyValue
import foundationdb.c.FDBTransaction
import foundationdb.c.fdb_future_get_int64
import foundationdb.c.fdb_future_get_keyvalue_array
import foundationdb.c.fdb_future_get_mappedkeyvalue_array
import foundationdb.c.fdb_future_get_value
import foundationdb.c.fdb_transaction_add_conflict_range
import foundationdb.c.fdb_transaction_get
import foundationdb.c.fdb_transaction_get_mapped_range
import foundationdb.c.fdb_transaction_get_range
import foundationdb.c.fdb_transaction_get_read_version
import foundationdb.c.fdb_transaction_cancel
import foundationdb.c.fdb_transaction_destroy
import foundationdb.c.fdb_transaction_set_read_version
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.async.AsyncIterable
import maryk.foundationdb.async.AsyncIterator
import kotlin.collections.ArrayDeque
import kotlin.collections.copyOf
import platform.posix.sched_yield

internal interface RangePageData<out T : Any> {
    val values: List<T>
    val lastKey: ByteArray?
    val hasMore: Boolean
}

internal data class RangePage(
    override val values: List<KeyValue>,
    override val lastKey: ByteArray?,
    override val hasMore: Boolean
) : RangePageData<KeyValue>

internal data class MappedRangePage(
    override val values: List<MappedKeyValue>,
    override val lastKey: ByteArray?,
    override val hasMore: Boolean
) : RangePageData<MappedKeyValue>

private fun KeySelector.copySelector(): KeySelector = KeySelector(key.copyOf(), orEqual, offset)

/** One consumer, with thread-safe cancellation and coalesced pending page requests. */
internal class StreamingRangeIterator<T : Any>(
    initialBegin: KeySelector,
    initialEnd: KeySelector,
    private val limit: Int,
    private val reverse: Boolean,
    private val fetchPage: (KeySelector, KeySelector, Int, Int) -> FdbFuture<RangePageData<T>>
) : AsyncIterator<T> {
    private val stateLock = AtomicInt(0)
    private val buffer = ArrayDeque<T>()
    private var remaining = if (limit > 0) limit else Int.MAX_VALUE
    private var iteration = 1
    private var begin = initialBegin
    private var end = initialEnd
    private var exhausted = false
    private var cancelled = false
    private var inFlight: FdbFuture<Boolean>? = null

    private suspend fun requestMore(): Boolean {
        if (withStateLock { cancelled || exhausted || (limit > 0 && remaining <= 0) }) return false
        // Only the coalesced in-flight operation changes selectors and remaining limits.
        val page = fetchPage(begin, end, if (limit == 0) 0 else remaining, iteration).await()
        return withStateLock {
            if (cancelled) return@withStateLock false
            iteration++
            val count = if (limit == 0) page.values.size else minOf(page.values.size, remaining)
            for (index in 0 until count) buffer.addLast(page.values[index])
            if (limit > 0) remaining -= count
            val boundary = page.lastKey
            if (page.hasMore && (limit == 0 || remaining > 0) && boundary != null) {
                if (reverse) end = KeySelector.firstGreaterOrEqual(boundary)
                else begin = KeySelector.firstGreaterThan(boundary)
            } else exhausted = true
            buffer.isNotEmpty()
        }
    }

    override fun hasNext(): Boolean = when (val ready = onHasNext()) {
        AsyncUtil.READY_TRUE -> true
        AsyncUtil.READY_FALSE -> false
        else -> runBlocking { ready.await() }
    }

    override suspend fun next(): T {
        while (true) {
            val value = withStateLock { if (cancelled || buffer.isEmpty()) null else buffer.removeFirst() }
            if (value != null) return value
            if (!onHasNext().await()) throw NoSuchElementException()
        }
    }

    override fun onHasNext(): FdbFuture<Boolean> = withStateLock {
        when {
            cancelled -> AsyncUtil.READY_FALSE
            buffer.isNotEmpty() -> AsyncUtil.READY_TRUE
            exhausted -> AsyncUtil.READY_FALSE
            else -> inFlight ?: fdbFutureFromSuspend {
                try { requestMore() }
                finally { withStateLock { inFlight = null } }
            }.also { inFlight = it }
        }
    }

    override fun cancel() {
        val pending = withStateLock {
            cancelled = true
            exhausted = true
            buffer.clear()
            inFlight.also { inFlight = null }
        }
        // Cancellation can resume code which re-enters this iterator; never hold its lock.
        pending?.cancel()
    }

    private inline fun <R> withStateLock(block: () -> R): R {
        while (!stateLock.compareAndSet(0, 1)) sched_yield()
        try { return block() } finally { stateLock.store(0) }
    }
}

actual open class ReadTransaction internal constructor(
    internal val handle: NativeTransactionHandle,
    internal val snapshot: Boolean
) : ReadTransactionContext {
    internal constructor(pointer: CPointer<FDBTransaction>, snapshot: Boolean) : this(
        NativeTransactionHandle(pointer),
        snapshot
    )

    actual fun get(key: ByteArray): FdbFuture<ByteArray?> {
        val future = handle.usePointerForFuture { pointer ->
            key.withPointer { ptr, len ->
                fdb_transaction_get(pointer, ptr, len, snapshot.toFdbBool())
            }
        } ?: error("fdb_transaction_get returned null future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { fut ->
            val present = alloc<IntVar>()
            val outValue = allocPointerTo<UByteVar>()
            val outLength = alloc<IntVar>()
            checkError(fdb_future_get_value(fut, present.ptr, outValue.ptr, outLength.ptr))
            if (present.value != 0 && outValue.value != null) outValue.value!!.readBytes(outLength.value) else null
        }
    }

    actual fun getRange(begin: ByteArray, end: ByteArray): AsyncIterable<KeyValue> =
        getRange(KeySelector.firstGreaterOrEqual(begin), KeySelector.firstGreaterOrEqual(end))

    actual fun getRange(begin: ByteArray, end: ByteArray, limit: Int): AsyncIterable<KeyValue> =
        getRange(KeySelector.firstGreaterOrEqual(begin), KeySelector.firstGreaterOrEqual(end), limit, false)

    actual fun getRange(begin: ByteArray, end: ByteArray, limit: Int, reverse: Boolean): AsyncIterable<KeyValue> =
        getRange(KeySelector.firstGreaterOrEqual(begin), KeySelector.firstGreaterOrEqual(end), limit, reverse)

    actual fun getRange(
        begin: ByteArray,
        end: ByteArray,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<KeyValue> =
        getRange(KeySelector.firstGreaterOrEqual(begin), KeySelector.firstGreaterOrEqual(end), limit, reverse, streamingMode)

    actual fun getRange(range: Range): AsyncIterable<KeyValue> = getRange(range.begin, range.end)

    actual fun getRange(range: Range, limit: Int): AsyncIterable<KeyValue> = getRange(range.begin, range.end, limit)

    actual fun getRange(range: Range, limit: Int, reverse: Boolean): AsyncIterable<KeyValue> =
        getRange(range.begin, range.end, limit, reverse)

    actual fun getRange(
        range: Range,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<KeyValue> = getRange(range.begin, range.end, limit, reverse, streamingMode)

    actual fun getRange(begin: KeySelector, end: KeySelector): AsyncIterable<KeyValue> =
        rangeAsyncIterable(begin, end, 0, false, StreamingMode.ITERATOR)

    actual fun getRange(begin: KeySelector, end: KeySelector, limit: Int, reverse: Boolean): AsyncIterable<KeyValue> =
        rangeAsyncIterable(begin, end, limit, reverse, StreamingMode.ITERATOR)

    actual fun getRange(
        begin: KeySelector,
        end: KeySelector,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<KeyValue> =
        rangeAsyncIterable(begin, end, limit, reverse, streamingMode)

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

    actual fun getMappedRange(begin: KeySelector, end: KeySelector, mapper: ByteArray): AsyncIterable<MappedKeyValue> =
        mappedRangeAsyncIterable(begin, end, mapper, limit = 0, reverse = false, streamingMode = StreamingMode.ITERATOR)

    actual fun getMappedRange(
        begin: KeySelector,
        end: KeySelector,
        mapper: ByteArray,
        limit: Int,
        reverse: Boolean
    ): AsyncIterable<MappedKeyValue> =
        mappedRangeAsyncIterable(begin, end, mapper, limit, reverse, StreamingMode.ITERATOR)

    actual fun getMappedRange(
        begin: KeySelector,
        end: KeySelector,
        mapper: ByteArray,
        limit: Int,
        reverse: Boolean,
        streamingMode: StreamingMode
    ): AsyncIterable<MappedKeyValue> =
        mappedRangeAsyncIterable(begin, end, mapper, limit, reverse, streamingMode)

    actual fun getReadVersion(): FdbFuture<Long> {
        val future = handle.usePointerForFuture { pointer ->
            fdb_transaction_get_read_version(pointer)
        } ?: error("fdb_transaction_get_read_version returned null future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { fut ->
            val out = alloc<LongVar>()
            checkError(fdb_future_get_int64(fut, out.ptr))
            out.value
        }
    }

    actual fun setReadVersion(version: Long) {
        handle.usePointer { pointer ->
            fdb_transaction_set_read_version(pointer, version)
        }
    }

    actual fun addReadConflictKeyIfNotSnapshot(key: ByteArray): Boolean {
        if (snapshot) return false
        addConflictRange(key, ByteArrayUtil.keyAfter(key), ConflictRangeType.READ)
        return true
    }

    actual fun addReadConflictRangeIfNotSnapshot(beginKey: ByteArray, endKey: ByteArray): Boolean {
        if (snapshot) return false
        addConflictRange(beginKey, endKey, ConflictRangeType.READ)
        return true
    }

    private fun addConflictRange(beginKey: ByteArray, endKey: ByteArray, type: ConflictRangeType) {
        handle.usePointer { pointer ->
            beginKey.withPointer { beginPtr, beginLen ->
                endKey.withPointer { endPtr, endLen ->
                    checkError(
                        fdb_transaction_add_conflict_range(
                            pointer,
                            beginPtr,
                            beginLen,
                            endPtr,
                            endLen,
                            type.toNative()
                        )
                    )
                }
            }
        }
    }

    actual fun snapshot(): ReadTransaction {
        handle.checkOpen()
        return ReadTransaction(handle, snapshot = true)
    }

    actual open fun options(): TransactionOptions = TransactionOptions(handle)

    actual open override fun <T> read(block: (ReadTransaction) -> T): T = block(this)
    actual open override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        try { block(this) } catch (error: Throwable) { failedFdbFuture(error) }

    actual fun isSnapshot(): Boolean = snapshot
    actual fun getKey(selector: KeySelector): FdbFuture<ByteArray> {
        val future = handle.usePointerForFuture { transaction -> selector.key.withPointer { key, size ->
            fdb_transaction_get_key(transaction, key, size, selector.orEqual.toFdbBool(), selector.offset, snapshot.toFdbBool())
        } } ?: error("Missing get-key future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { native ->
            val key = allocPointerTo<UByteVar>()
            val size = alloc<IntVar>()
            checkError(fdb_future_get_key(native, key.ptr, size.ptr))
            if (size.value == 0) byteArrayOf() else key.value!!.readBytes(size.value)
        }
    }
    actual open fun getEstimatedRangeSizeBytes(range: Range): FdbFuture<Long> {
        val future = handle.usePointerForFuture { pointer ->
            range.begin.withPointer { beginPtr, beginLen ->
                range.end.withPointer { endPtr, endLen ->
                    fdb_transaction_get_estimated_range_size_bytes(pointer, beginPtr, beginLen, endPtr, endLen)
                }
            }
        } ?: error("fdb_transaction_get_estimated_range_size_bytes returned null future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { fut ->
            val out = alloc<LongVar>()
            checkError(fdb_future_get_int64(fut, out.ptr))
            out.value
        }
    }

    actual open fun getRangeSplitPoints(range: Range, chunkSize: Long): FdbFuture<List<ByteArray>> {
        require(chunkSize > 0)
        val future = handle.usePointerForFuture { transaction ->
            range.begin.withPointer { begin, beginSize ->
                range.end.withPointer { end, endSize ->
                    fdb_transaction_get_range_split_points(transaction, begin, beginSize, end, endSize, chunkSize)
                }
            }
        } ?: error("Missing split-point future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { native ->
            val keys = allocPointerTo<FDBKey>()
            val count = alloc<IntVar>()
            checkError(fdb_future_get_key_array(native, keys.ptr, count.ptr))
            List(count.value) { index ->
                val key = keys.value!![index]
                key.key!!.readBytes(key.key_length)
            }
        }
    }


    @ExperimentalBlobGranules
    actual fun getBlobGranuleRanges(range: Range, rowLimit: Int): FdbFuture<List<Range>> {
        val future = handle.usePointerForFuture { transaction -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_transaction_get_blob_granule_ranges(transaction, begin, beginSize, end, endSize, rowLimit) }
        } }
        return nativeRangesFuture(future, handle::releaseFutureUse)
    }


    actual fun getRange(begin: KeySelector, end: KeySelector, limit: Int): AsyncIterable<KeyValue> = getRange(begin, end, limit, false)
    actual fun getMappedRange(begin: KeySelector, end: KeySelector, mapper: ByteArray, limit: Int): AsyncIterable<MappedKeyValue> = getMappedRange(begin, end, mapper, limit, false)
    actual fun getEstimatedRangeSizeBytes(begin: ByteArray, end: ByteArray): FdbFuture<Long> = getEstimatedRangeSizeBytes(Range(begin, end))
    actual fun getRangeSplitPoints(begin: ByteArray, end: ByteArray, chunkSize: Long): FdbFuture<List<ByteArray>> = getRangeSplitPoints(Range(begin, end), chunkSize)
    @ExperimentalBlobGranules
    actual fun getBlobGranuleRanges(begin: ByteArray, end: ByteArray, rowLimit: Int): FdbFuture<List<Range>> = getBlobGranuleRanges(Range(begin, end), rowLimit)
    actual companion object {
        actual val ROW_LIMIT_UNLIMITED: Int = 0
    }
}

internal class NativeTransactionHandle(private val pointer: CPointer<FDBTransaction>) {
    private val closed = AtomicInt(0)
    private val destroyed = AtomicInt(0)
    private val activeUses = AtomicInt(0)
    private val nonCancellableUses = AtomicInt(0)

    fun checkOpen() {
        check(closed.load() == 0) { "Transaction is closed." }
    }

    fun <T> usePointer(block: (CPointer<FDBTransaction>) -> T): T {
        enterUse()
        try {
            return block(pointer)
        } finally {
            exitUse()
        }
    }

    fun usePointerForFuture(
        cancelOnClose: Boolean = true,
        block: (CPointer<FDBTransaction>) -> CPointer<FDBFuture>?
    ): CPointer<FDBFuture>? {
        enterUse()
        var release = true
        val nonCancellable = !cancelOnClose
        if (nonCancellable) {
            enterNonCancellableUse()
        }
        try {
            val future = block(pointer)
            if (future != null) {
                release = false
            }
            return future
        } finally {
            if (release) {
                if (nonCancellable) exitNonCancellableUse()
                exitUse()
            }
        }
    }

    fun releaseFutureUse(cancelOnClose: Boolean = true) {
        if (!cancelOnClose) {
            exitNonCancellableUse()
        }
        exitUse()
    }

    fun close() {
        // Hold a pointer use BEFORE publishing closed: the last read completion may
        // otherwise destroy the handle between that publication and native cancel.
        while (true) {
            if (closed.load() != 0) return
            val current = activeUses.load()
            if (!activeUses.compareAndSet(current, current + 1)) continue
            try {
                if (closed.compareAndSet(0, 1) && nonCancellableUses.load() == 0) {
                    fdb_transaction_cancel(pointer)
                }
            } finally { exitUse() }
            return
        }
    }

    private fun enterUse() {
        while (true) {
            checkOpen()
            val current = activeUses.load()
            if (activeUses.compareAndSet(current, current + 1)) {
                if (closed.load() == 0) return
                exitUse()
                checkOpen()
            }
        }
    }

    private fun exitUse() {
        while (true) {
            val current = activeUses.load()
            if (activeUses.compareAndSet(current, current - 1)) {
                tryDestroy()
                return
            }
        }
    }

    private fun enterNonCancellableUse() {
        while (true) {
            val current = nonCancellableUses.load()
            if (nonCancellableUses.compareAndSet(current, current + 1)) return
        }
    }

    private fun exitNonCancellableUse() {
        while (true) {
            val current = nonCancellableUses.load()
            if (current == 0) return
            if (nonCancellableUses.compareAndSet(current, current - 1)) return
        }
    }

    private fun tryDestroy() {
        if (
            closed.load() != 0 &&
            activeUses.load() == 0 &&
            destroyed.compareAndSet(0, 1)
        ) {
            fdb_transaction_destroy(pointer)
        }
    }
}

private fun ReadTransaction.rangeAsyncIterable(
    begin: KeySelector,
    end: KeySelector,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): AsyncIterable<KeyValue> {
    checkedRangeLimit(limit)
    val iteratorFactory: () -> AsyncIterator<KeyValue> = {
        StreamingRangeIterator(
            begin.copySelector(),
            end.copySelector(),
            limit,
            reverse
        ) { currentBegin, currentEnd, pageLimit, iteration ->
            fetchRange(currentBegin, currentEnd, pageLimit, reverse, streamingMode, iteration)
        }
    }
    val listFutureFactory = {
        val capturedBegin = begin.copySelector()
        val capturedEnd = end.copySelector()
        fdbFutureFromSuspend { collectRangeResult(capturedBegin, capturedEnd, limit, reverse, listStreamingMode(streamingMode, limit), exactSummary = false).values }
    }
    return AsyncIterable(iteratorFactory = iteratorFactory, listFutureFactory = listFutureFactory)
}

private fun ReadTransaction.mappedRangeAsyncIterable(
    begin: KeySelector,
    end: KeySelector,
    mapper: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): AsyncIterable<MappedKeyValue> {
    checkedRangeLimit(limit)
    val mapperCopy = mapper.copyOf()
    val iteratorFactory: () -> AsyncIterator<MappedKeyValue> = {
        val capturedMapper = mapperCopy.copyOf()
        StreamingRangeIterator(
            begin.copySelector(), end.copySelector(), limit, reverse
        ) { currentBegin, currentEnd, pageLimit, iteration ->
            fetchMappedRange(currentBegin, currentEnd, capturedMapper, pageLimit, reverse, streamingMode, iteration)
        }
    }
    val listFutureFactory = {
        val capturedBegin = begin.copySelector()
        val capturedEnd = end.copySelector()
        val capturedMapper = mapperCopy.copyOf()
        fdbFutureFromSuspend { collectMappedRangeResult(capturedBegin, capturedEnd, capturedMapper, limit, reverse, listStreamingMode(streamingMode, limit), exactSummary = false).values }
    }
    return AsyncIterable(iteratorFactory = iteratorFactory, listFutureFactory = listFutureFactory)
}

internal actual fun ReadTransaction.collectRangeInternal(
    begin: ByteArray,
    end: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<RangeResult> = fdbFutureFromSuspend {
    collectRangeResult(
        KeySelector.firstGreaterOrEqual(begin),
        KeySelector.firstGreaterOrEqual(end),
        limit,
        reverse,
        streamingMode
    )
}

internal actual fun ReadTransaction.collectRangeInternal(
    begin: KeySelector,
    end: KeySelector,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<RangeResult> = fdbFutureFromSuspend {
    collectRangeResult(begin, end, limit, reverse, streamingMode)
}

private suspend fun ReadTransaction.collectRangeResult(
    begin: KeySelector,
    end: KeySelector,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode,
    exactSummary: Boolean = true
): RangeResult {
    var remaining = if (limit > 0) limit else Int.MAX_VALUE
    var lastKey: ByteArray? = null
    val items = mutableListOf<KeyValue>()
    var lastPageHasMore = false
    var iteration = 1
    var currentBegin = begin
    var currentEnd = end
    var shouldFetchMore = true

    while (shouldFetchMore && remaining > 0) {
        val pageLimit = if (limit == 0) 0 else remaining
        val page = fetchRange(currentBegin, currentEnd, pageLimit, reverse, streamingMode, iteration).await()
        items.addAll(page.values)
        remaining -= page.values.size
        page.lastKey?.let { lastKey = it }
        lastPageHasMore = page.hasMore
        shouldFetchMore = page.hasMore && remaining > 0
        if (shouldFetchMore) {
            val boundary = page.lastKey
            if (boundary == null) {
                shouldFetchMore = false
            } else if (reverse) {
                currentEnd = KeySelector.firstGreaterOrEqual(boundary)
            } else {
                currentBegin = KeySelector.firstGreaterThan(boundary)
            }
        }
        iteration++
    }

    // The C page flag means the read stopped before proving exhaustion, even if
    // the row limit happens to equal the range size. Only summaries need lookahead.
    if (exactSummary && limit > 0 && remaining <= 0 && lastPageHasMore) {
        lastPageHasMore = hasPrimaryRowAfter(begin, end, checkNotNull(lastKey), reverse, streamingMode, iteration)
    }

    return RangeResult(
        values = items,
        summary = RangeResultSummary(lastKey, items.size, lastPageHasMore)
    )
}

internal actual fun ReadTransaction.collectMappedRangeInternal(
    begin: ByteArray,
    end: ByteArray,
    mapper: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<MappedRangeResult> = fdbFutureFromSuspend {
    collectMappedRangeResult(
        KeySelector.firstGreaterOrEqual(begin),
        KeySelector.firstGreaterOrEqual(end),
        mapper,
        limit,
        reverse,
        streamingMode
    )
}

internal actual fun ReadTransaction.collectMappedRangeInternal(
    begin: KeySelector,
    end: KeySelector,
    mapper: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): FdbFuture<MappedRangeResult> = fdbFutureFromSuspend {
    collectMappedRangeResult(begin, end, mapper, limit, reverse, streamingMode)
}

private suspend fun ReadTransaction.collectMappedRangeResult(
    begin: KeySelector,
    end: KeySelector,
    mapper: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode,
    exactSummary: Boolean = true
): MappedRangeResult {
    var remaining = if (limit > 0) limit else Int.MAX_VALUE
    var lastKey: ByteArray? = null
    val items = mutableListOf<MappedKeyValue>()
    var lastPageHasMore = false
    var iteration = 1
    var currentBegin = begin
    var currentEnd = end
    var shouldFetchMore = true

    while (shouldFetchMore && remaining > 0) {
        val pageLimit = if (limit == 0) 0 else remaining
        val page = fetchMappedRange(currentBegin, currentEnd, mapper, pageLimit, reverse, streamingMode, iteration).await()
        items.addAll(page.values)
        remaining -= page.values.size
        page.lastKey?.let { lastKey = it }
        lastPageHasMore = page.hasMore
        shouldFetchMore = page.hasMore && remaining > 0
        if (shouldFetchMore) {
            val boundary = page.lastKey
            if (boundary == null) {
                shouldFetchMore = false
            } else if (reverse) {
                currentEnd = KeySelector.firstGreaterOrEqual(boundary)
            } else {
                currentBegin = KeySelector.firstGreaterThan(boundary)
            }
        }
        iteration++
    }

    // The C page flag means the read stopped before proving exhaustion, even if
    // the row limit happens to equal the range size. Only summaries need lookahead.
    if (exactSummary && limit > 0 && remaining <= 0 && lastPageHasMore) {
        lastPageHasMore = hasPrimaryRowAfter(begin, end, checkNotNull(lastKey), reverse, streamingMode, iteration)
    }

    return MappedRangeResult(
        values = items,
        summary = RangeResultSummary(lastKey, items.size, lastPageHasMore)
    )
}

/** One primary-row probe; mapped summaries do not fetch another secondary range. */
private suspend fun ReadTransaction.hasPrimaryRowAfter(
    begin: KeySelector,
    end: KeySelector,
    lastKey: ByteArray,
    reverse: Boolean,
    streamingMode: StreamingMode,
    iteration: Int
): Boolean = fetchRange(
    if (reverse) begin else KeySelector.firstGreaterThan(lastKey),
    if (reverse) KeySelector.firstGreaterOrEqual(lastKey) else end,
    1, reverse, streamingMode, iteration
).await().values.isNotEmpty()

internal fun ReadTransaction.fetchRange(
    begin: KeySelector,
    end: KeySelector,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode,
    iteration: Int
): FdbFuture<RangePage> {
    val future = handle.usePointerForFuture { pointer ->
        begin.key.withPointer { beginPtr, beginLen ->
            end.key.withPointer { endPtr, endLen ->
                fdb_transaction_get_range(
                    pointer,
                    beginPtr,
                    beginLen,
                    begin.orEqual.toFdbBool(),
                    begin.offset,
                    endPtr,
                    endLen,
                    end.orEqual.toFdbBool(),
                    end.offset,
                    limit,
                    0,
                    streamingMode.toNative(),
                    iteration,
                    snapshot.toFdbBool(),
                    reverse.toFdbBool()
                )
            }
        }
    } ?: error("fdb_transaction_get_range returned null future")

    return NativeFuture(future, onCleanup = handle::releaseFutureUse) { fut ->
        memScoped {
            val outKv = allocPointerTo<FDBKeyValue>()
            val outCount = alloc<IntVar>()
            val outMore = alloc<IntVar>()
            checkError(fdb_future_get_keyvalue_array(fut, outKv.ptr, outCount.ptr, outMore.ptr))
            val pointer = outKv.value
            val values = ArrayList<KeyValue>(outCount.value)
            var lastKey: ByteArray? = null
            if (pointer != null) {
                for (idx in 0 until outCount.value) {
                    val kv = pointer[idx]
                    val keyBytes = kv.key.readFdbBytes(kv.key_length, "key")
                    val valueBytes = kv.value.readFdbBytes(kv.value_length, "value")
                    values += KeyValue.takeOwnership(keyBytes, valueBytes)
                    lastKey = keyBytes
                }
            }
            RangePage(values, lastKey, outMore.value != 0)
        }
    }
}

private fun ReadTransaction.fetchMappedRange(
    begin: KeySelector,
    end: KeySelector,
    mapper: ByteArray,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode,
    iteration: Int
): FdbFuture<MappedRangePage> {
    val future = handle.usePointerForFuture { pointer ->
        begin.key.withPointer { beginPtr, beginLen ->
            end.key.withPointer { endPtr, endLen ->
                mapper.withPointer { mapperPtr, mapperLen ->
                    fdb_transaction_get_mapped_range(
                        pointer,
                        beginPtr,
                        beginLen,
                        begin.orEqual.toFdbBool(),
                        begin.offset,
                        endPtr,
                        endLen,
                        end.orEqual.toFdbBool(),
                        end.offset,
                        mapperPtr,
                        mapperLen,
                        limit,
                        0,
                        streamingMode.toNative(),
                        iteration,
                        snapshot.toFdbBool(),
                        reverse.toFdbBool()
                    )
                }
            }
        }
    } ?: error("fdb_transaction_get_mapped_range returned null future")

    return NativeFuture(future, onCleanup = handle::releaseFutureUse) { fut ->
        memScoped {
            val outKv = allocPointerTo<FDBMappedKeyValue>()
            val outCount = alloc<IntVar>()
            val outMore = alloc<IntVar>()
            checkError(fdb_future_get_mappedkeyvalue_array(fut, outKv.ptr, outCount.ptr, outMore.ptr))
            val pointer = outKv.value
            val values = mutableListOf<MappedKeyValue>()
            var lastKey: ByteArray? = null
            if (pointer != null) {
                for (idx in 0 until outCount.value) {
                    val mapped = pointer[idx]
                    val entry = mapped.toMappedKeyValue()
                    values += entry
                    lastKey = entry.key
                }
            }
            MappedRangePage(values, lastKey, outMore.value != 0)
        }
    }
}

private fun FDBMappedKeyValue.toMappedKeyValue(): MappedKeyValue {
    val keyBytes = this.key.toByteArray()
    val valueBytes = this.value.toByteArray()
    val rangeBeginBytes = this.getRange.begin.toByteArray()
    val rangeEndBytes = this.getRange.end.toByteArray()
    val rangeValues = mutableListOf<KeyValue>()
    val dataPointer = this.getRange.data
    val count = this.getRange.m_size
    if (dataPointer != null && count > 0) {
        for (idx in 0 until count) {
            val kv = dataPointer[idx]
            val key = kv.key.readFdbBytes(kv.key_length, "key")
            val value = kv.value.readFdbBytes(kv.value_length, "value")
            rangeValues += KeyValue.takeOwnership(key, value)
        }
    }
    return MappedKeyValue(
        key = keyBytes,
        value = valueBytes,
        rangeBegin = rangeBeginBytes,
        rangeEnd = rangeEndBytes,
        rangeResult = rangeValues
    )
}

private fun FDBKeySelector.toByteArray(): ByteArray = this.key.toByteArray()

private fun foundationdb.c.FDBKey.toByteArray(): ByteArray =
    this.key?.readBytes(this.key_length) ?: ByteArray(0)

private fun CPointer<UByteVar>?.readFdbBytes(length: Int, fieldName: String): ByteArray {
    if (length == 0) return ByteArray(0)
    return checkNotNull(this) { "FoundationDB returned null $fieldName pointer with length $length" }
        .readBytes(length)

}

private fun listStreamingMode(mode: StreamingMode, limit: Int): StreamingMode =
    if (mode != StreamingMode.ITERATOR) mode else if (limit == 0) StreamingMode.WANT_ALL else StreamingMode.EXACT
