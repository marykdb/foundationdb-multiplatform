@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package maryk.foundationdb

import foundationdb.c.fdb_tenant_wait_purge_granules_complete

import foundationdb.c.fdb_tenant_flush_blob_range

import foundationdb.c.fdb_tenant_verify_blob_range

import foundationdb.c.fdb_tenant_list_blobbified_ranges

import foundationdb.c.fdb_tenant_unblobbify_range

import foundationdb.c.fdb_tenant_blobbify_range_blocking

import foundationdb.c.fdb_tenant_blobbify_range

import foundationdb.c.fdb_tenant_purge_blob_granules

import foundationdb.c.fdb_tenant_get_id
import foundationdb.c.fdb_future_get_int64
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.alloc

import foundationdb.c.FDBTenant
import foundationdb.c.FDBTransaction
import foundationdb.c.fdb_tenant_create_transaction
import foundationdb.c.fdb_tenant_destroy
import foundationdb.c.fdb_transaction_destroy
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.sched_yield
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
actual class Tenant internal constructor(internal val handle: NativeTenantHandle, private val name: ByteArray, private val ownerDatabase: Database) : TransactionContext {
    internal constructor(pointer: CPointer<FDBTenant>, name: ByteArray, database: Database) : this(NativeTenantHandle(pointer), name.copyOf(), database)

    actual fun createTransaction(): Transaction = memScoped {
        val out = allocPointerTo<FDBTransaction>()
        handle.usePointer { pointer ->
            checkError(fdb_tenant_create_transaction(pointer, out.ptr))
        }
        val txnPointer = out.value ?: error("fdb_tenant_create_transaction returned null pointer")
        try {
            Transaction(txnPointer, ownerDatabase)
        } catch (throwable: Throwable) {
            fdb_transaction_destroy(txnPointer)
            throw throwable
        }
    }

    actual fun getId(): FdbFuture<Long> {
        val future = handle.usePointer { fdb_tenant_get_id(it) } ?: error("Missing tenant-id future")
        return NativeFuture(future) { native ->
            val id = alloc<LongVar>()
            checkError(fdb_future_get_int64(native, id.ptr))
            id.value
        }
    }

    @ExperimentalBlobGranules
    actual fun purgeBlobGranules(range: Range, purgeVersion: Long, force: Boolean): FdbFuture<ByteArray> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_tenant_purge_blob_granules(owner, begin, beginSize, end, endSize, purgeVersion, force.toFdbBool()) }
        } }
        return nativeKeyFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun blobbifyRange(range: Range): FdbFuture<Boolean> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_tenant_blobbify_range(owner, begin, beginSize, end, endSize) }
        } }
        return nativeBooleanFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun blobbifyRangeBlocking(range: Range): FdbFuture<Boolean> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_tenant_blobbify_range_blocking(owner, begin, beginSize, end, endSize) }
        } }
        return nativeBooleanFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun unblobbifyRange(range: Range): FdbFuture<Boolean> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_tenant_unblobbify_range(owner, begin, beginSize, end, endSize) }
        } }
        return nativeBooleanFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun listBlobbifiedRanges(range: Range, rangeLimit: Int): FdbFuture<List<Range>> {
        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_tenant_list_blobbified_ranges(owner, begin, beginSize, end, endSize, rangeLimit) }
        } }
        return nativeRangesFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun verifyBlobRange(range: Range, version: Long): FdbFuture<Long> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_tenant_verify_blob_range(owner, begin, beginSize, end, endSize, version) }
        } }
        return nativeLongFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun flushBlobRange(range: Range, compact: Boolean, version: Long): FdbFuture<Boolean> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_tenant_flush_blob_range(owner, begin, beginSize, end, endSize, compact.toFdbBool(), version) }
        } }
        return nativeBooleanFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun waitPurgeGranulesComplete(purgeKey: ByteArray): FdbFuture<Unit> {
        val future = handle.usePointer { owner -> purgeKey.withPointer { key, size -> fdb_tenant_wait_purge_granules_complete(owner, key, size) } }
        return NativeFuture(requireNotNull(future)) { }
    }

    actual fun getName(): ByteArray = name.copyOf()

    actual override fun <T> run(block: (Transaction) -> T): T =
        runBlockingWithRetry(::createTransaction, needsCommit = true, block = block)

    actual override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> =
        runAsyncWithRetry(::createTransaction, needsCommit = true, block = block)

    actual override fun <T> read(block: (ReadTransaction) -> T): T =
        runBlockingWithRetry(::createTransaction, needsCommit = false, block = block)

    actual override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        runAsyncWithRetry(::createTransaction, needsCommit = false, block = block)

    @ExperimentalBlobGranules
    actual fun purgeBlobGranules(begin: ByteArray, end: ByteArray, force: Boolean): FdbFuture<ByteArray> = purgeBlobGranules(Range(begin, end), force = force)
    @ExperimentalBlobGranules
    actual fun purgeBlobGranules(begin: ByteArray, end: ByteArray, purgeVersion: Long, force: Boolean): FdbFuture<ByteArray> = purgeBlobGranules(Range(begin, end), purgeVersion, force)
    @ExperimentalBlobGranules
    actual fun blobbifyRange(begin: ByteArray, end: ByteArray): FdbFuture<Boolean> = blobbifyRange(Range(begin, end))
    @ExperimentalBlobGranules
    actual fun blobbifyRangeBlocking(begin: ByteArray, end: ByteArray): FdbFuture<Boolean> = blobbifyRangeBlocking(Range(begin, end))
    @ExperimentalBlobGranules
    actual fun unblobbifyRange(begin: ByteArray, end: ByteArray): FdbFuture<Boolean> = unblobbifyRange(Range(begin, end))
    @ExperimentalBlobGranules
    actual fun listBlobbifiedRanges(begin: ByteArray, end: ByteArray, rangeLimit: Int): FdbFuture<List<Range>> = listBlobbifiedRanges(Range(begin, end), rangeLimit)
    @ExperimentalBlobGranules
    actual fun verifyBlobRange(begin: ByteArray, end: ByteArray): FdbFuture<Long> = verifyBlobRange(Range(begin, end))
    @ExperimentalBlobGranules
    actual fun verifyBlobRange(begin: ByteArray, end: ByteArray, version: Long): FdbFuture<Long> = verifyBlobRange(Range(begin, end), version)
    @ExperimentalBlobGranules
    actual fun flushBlobRange(begin: ByteArray, end: ByteArray, compact: Boolean): FdbFuture<Boolean> = flushBlobRange(Range(begin, end), compact)
    @ExperimentalBlobGranules
    actual fun flushBlobRange(begin: ByteArray, end: ByteArray, compact: Boolean, version: Long): FdbFuture<Boolean> = flushBlobRange(Range(begin, end), compact, version)

    actual fun close() {
        handle.close()
    }
}

internal class NativeTenantHandle(private val pointer: CPointer<FDBTenant>) {
    private val closed = AtomicInt(0)
    private val activeUses = AtomicInt(0)

    fun <T> usePointer(block: (CPointer<FDBTenant>) -> T): T {
        enterUse()
        try {
            return block(pointer)
        } finally {
            exitUse()
        }
    }

    fun close() {
        if (closed.compareAndSet(0, 1)) {
            while (activeUses.load() != 0) {
                sched_yield()
            }
            fdb_tenant_destroy(pointer)
        }
    }

    private fun checkOpen() {
        check(closed.load() == 0) { "Tenant is closed." }
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
            if (activeUses.compareAndSet(current, current - 1)) return
        }
    }
}
