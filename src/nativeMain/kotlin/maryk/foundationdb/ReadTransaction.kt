@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDBKeySelector
import foundationdb.c.FDBKeyValue
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
import maryk.foundationdb.async.AsyncIterable
import maryk.foundationdb.async.AsyncIterator
import kotlin.collections.ArrayDeque
import kotlin.collections.copyOf

internal data class RangePage(
    val values: List<KeyValue>,
    val lastKey: ByteArray?,
    val hasMore: Boolean
)

internal data class MappedRangePage(
    val values: List<MappedKeyValue>,
    val lastKey: ByteArray?,
    val hasMore: Boolean
)

private fun KeySelector.copySelector(): KeySelector = KeySelector(key.copyOf(), orEqual, offset)

private class StreamingRangeIterator(
    initialBegin: KeySelector,
    initialEnd: KeySelector,
    private val limit: Int,
    private val reverse: Boolean,
    private val fetchPage: (KeySelector, KeySelector, Int, Int) -> FdbFuture<RangePage>
) : AsyncIterator<KeyValue>() {
    private val buffer = ArrayDeque<KeyValue>()
    private var remaining = if (limit > 0) limit else Int.MAX_VALUE
    private var iteration = 1
    private var begin = initialBegin
    private var end = initialEnd
    private var exhausted = false

    private suspend fun requestMore(): Boolean {
        if (exhausted || (limit > 0 && remaining <= 0)) return false
        val pageLimit = if (limit == 0) 0 else remaining
        val page = fetchPage(begin, end, pageLimit, iteration).await()
        iteration++
        if (page.values.isNotEmpty()) {
            val emitted = if (limit == 0) page.values else page.values.take(remaining)
            emitted.forEach { buffer.addLast(it) }
            if (limit > 0) {
                remaining -= emitted.size
            }
        }
        val canContinue = page.hasMore && (limit == 0 || remaining > 0)
        val boundary = page.lastKey
        if (canContinue && boundary != null) {
            if (reverse) {
                end = KeySelector.lastLessThan(boundary)
            } else {
                begin = KeySelector.firstGreaterThan(boundary)
            }
        } else {
            exhausted = true
        }
        return buffer.isNotEmpty()
    }

    private fun ensureBufferBlocking(): Boolean {
        if (buffer.isNotEmpty()) return true
        if (exhausted) return false
        return runBlocking { requestMore() } && buffer.isNotEmpty()
    }

    override fun hasNext(): Boolean = ensureBufferBlocking()

    override suspend fun next(): KeyValue {
        if (buffer.isEmpty()) {
            if (!requestMore()) throw NoSuchElementException()
        }
        return buffer.removeFirst()
    }

    override fun onHasNext(): FdbFuture<Boolean> = fdbFutureFromSuspend {
        if (buffer.isNotEmpty()) return@fdbFutureFromSuspend true
        if (exhausted) return@fdbFutureFromSuspend false
        requestMore()
        buffer.isNotEmpty()
    }

    override fun cancel() {
        buffer.clear()
        exhausted = true
    }
}

private class StreamingMappedRangeIterator(
    initialBegin: KeySelector,
    initialEnd: KeySelector,
    private val mapper: ByteArray,
    private val limit: Int,
    private val reverse: Boolean,
    private val fetchPage: (KeySelector, KeySelector, ByteArray, Int, Int) -> FdbFuture<MappedRangePage>
) : AsyncIterator<MappedKeyValue>() {
    private val buffer = ArrayDeque<MappedKeyValue>()
    private var remaining = if (limit > 0) limit else Int.MAX_VALUE
    private var iteration = 1
    private var begin = initialBegin
    private var end = initialEnd
    private var exhausted = false

    private suspend fun requestMore(): Boolean {
        if (exhausted || (limit > 0 && remaining <= 0)) return false
        val pageLimit = if (limit == 0) 0 else remaining
        val page = fetchPage(begin, end, mapper, pageLimit, iteration).await()
        iteration++
        if (page.values.isNotEmpty()) {
            val emitted = if (limit == 0) page.values else page.values.take(remaining)
            emitted.forEach { buffer.addLast(it) }
            if (limit > 0) {
                remaining -= emitted.size
            }
        }
        val canContinue = page.hasMore && (limit == 0 || remaining > 0)
        val boundary = page.lastKey
        if (canContinue && boundary != null) {
            if (reverse) {
                end = KeySelector.lastLessThan(boundary)
            } else {
                begin = KeySelector.firstGreaterThan(boundary)
            }
        } else {
            exhausted = true
        }
        return buffer.isNotEmpty()
    }

    private fun ensureBufferBlocking(): Boolean {
        if (buffer.isNotEmpty()) return true
        if (exhausted) return false
        return runBlocking { requestMore() } && buffer.isNotEmpty()
    }

    override fun hasNext(): Boolean = ensureBufferBlocking()

    override suspend fun next(): MappedKeyValue {
        if (buffer.isEmpty()) {
            if (!requestMore()) throw NoSuchElementException()
        }
        return buffer.removeFirst()
    }

    override fun onHasNext(): FdbFuture<Boolean> = fdbFutureFromSuspend {
        if (buffer.isNotEmpty()) return@fdbFutureFromSuspend true
        if (exhausted) return@fdbFutureFromSuspend false
        requestMore()
        buffer.isNotEmpty()
    }

    override fun cancel() {
        buffer.clear()
        exhausted = true
    }
}

actual open class ReadTransaction internal constructor(
    internal val pointer: CPointer<FDBTransaction>,
    internal val snapshot: Boolean
) {
    actual fun get(key: ByteArray): FdbFuture<ByteArray?> {
        val future = key.withPointer { ptr, len ->
            fdb_transaction_get(pointer, ptr, len, snapshot.toFdbBool())
        } ?: error("fdb_transaction_get returned null future")
        return NativeFuture(future) { fut ->
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
        val future = fdb_transaction_get_read_version(pointer) ?: error("fdb_transaction_get_read_version returned null future")
        return NativeFuture(future) { fut ->
            val out = alloc<LongVar>()
            checkError(fdb_future_get_int64(fut, out.ptr))
            out.value
        }
    }

    actual fun setReadVersion(version: Long) {
        fdb_transaction_set_read_version(pointer, version)
    }

    actual fun addReadConflictKeyIfNotSnapshot(key: ByteArray): Boolean {
        if (snapshot) return false
        addConflictRange(key, key.nextKey(), ConflictRangeType.READ)
        return true
    }

    actual fun addReadConflictRangeIfNotSnapshot(beginKey: ByteArray, endKey: ByteArray): Boolean {
        if (snapshot) return false
        addConflictRange(beginKey, endKey, ConflictRangeType.READ)
        return true
    }

    private fun addConflictRange(beginKey: ByteArray, endKey: ByteArray, type: ConflictRangeType) {
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

    actual fun snapshot(): ReadTransaction = ReadTransaction(pointer, snapshot = true)

    actual open fun options(): TransactionOptions = TransactionOptions(pointer)

actual companion object {
        actual val ROW_LIMIT_UNLIMITED: Int = 0
    }
}

private fun ReadTransaction.rangeAsyncIterable(
    begin: KeySelector,
    end: KeySelector,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode
): AsyncIterable<KeyValue> {
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
        val future = collectRangeInternal(begin.copySelector(), end.copySelector(), limit, reverse, streamingMode)
        fdbFutureFromSuspend { future.await().values }
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
    val mapperCopy = mapper.copyOf()
    val iteratorFactory: () -> AsyncIterator<MappedKeyValue> = {
        StreamingMappedRangeIterator(
            begin.copySelector(),
            end.copySelector(),
            mapperCopy.copyOf(),
            limit,
            reverse
        ) { currentBegin, currentEnd, mapperBytes, pageLimit, iteration ->
            fetchMappedRange(currentBegin, currentEnd, mapperBytes, pageLimit, reverse, streamingMode, iteration)
        }
    }
    val listFutureFactory = {
        val future = collectMappedRangeInternal(
            begin.copySelector(),
            end.copySelector(),
            mapperCopy.copyOf(),
            limit,
            reverse,
            streamingMode
        )
        fdbFutureFromSuspend { future.await().values }
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
    streamingMode: StreamingMode
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
        lastKey = page.lastKey
        lastPageHasMore = page.hasMore
        shouldFetchMore = page.hasMore && remaining > 0
        if (shouldFetchMore) {
            val boundary = page.lastKey
            if (boundary == null) {
                shouldFetchMore = false
            } else if (reverse) {
                currentEnd = KeySelector.lastLessThan(boundary)
            } else {
                currentBegin = KeySelector.firstGreaterThan(boundary)
            }
        }
        iteration++
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
    streamingMode: StreamingMode
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
        lastKey = page.lastKey
        lastPageHasMore = page.hasMore
        shouldFetchMore = page.hasMore && remaining > 0
        if (shouldFetchMore) {
            val boundary = page.lastKey
            if (boundary == null) {
                shouldFetchMore = false
            } else if (reverse) {
                currentEnd = KeySelector.lastLessThan(boundary)
            } else {
                currentBegin = KeySelector.firstGreaterThan(boundary)
            }
        }
        iteration++
    }

    return MappedRangeResult(
        values = items,
        summary = RangeResultSummary(lastKey, items.size, lastPageHasMore)
    )
}

private fun ReadTransaction.fetchRange(
    begin: KeySelector,
    end: KeySelector,
    limit: Int,
    reverse: Boolean,
    streamingMode: StreamingMode,
    iteration: Int
): FdbFuture<RangePage> {
    val future = begin.key.withPointer { beginPtr, beginLen ->
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
    } ?: error("fdb_transaction_get_range returned null future")

    return NativeFuture(future) { fut ->
        memScoped {
            val outKv = allocPointerTo<FDBKeyValue>()
            val outCount = alloc<IntVar>()
            val outMore = alloc<IntVar>()
            checkError(fdb_future_get_keyvalue_array(fut, outKv.ptr, outCount.ptr, outMore.ptr))
            val pointer = outKv.value
            val values = mutableListOf<KeyValue>()
            var lastKey: ByteArray? = null
            if (pointer != null) {
                for (idx in 0 until outCount.value) {
                    val kv = pointer[idx]
                    val keyBytes = kv.key!!.readBytes(kv.key_length)
                    val valueBytes = kv.value!!.readBytes(kv.value_length)
                    values += KeyValue(keyBytes, valueBytes)
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
    val future = begin.key.withPointer { beginPtr, beginLen ->
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
    } ?: error("fdb_transaction_get_mapped_range returned null future")

    return NativeFuture(future) { fut ->
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
            val key = kv.key!!.readBytes(kv.key_length)
            val value = kv.value!!.readBytes(kv.value_length)
            rangeValues += KeyValue(key, value)
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
