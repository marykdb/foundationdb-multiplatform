@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package maryk.foundationdb

import foundationdb.c.fdb_database_wait_purge_granules_complete

import foundationdb.c.fdb_database_flush_blob_range

import foundationdb.c.fdb_database_verify_blob_range

import foundationdb.c.fdb_database_list_blobbified_ranges

import foundationdb.c.fdb_database_unblobbify_range

import foundationdb.c.fdb_database_blobbify_range_blocking

import foundationdb.c.fdb_database_blobbify_range

import foundationdb.c.fdb_database_purge_blob_granules

import foundationdb.c.fdb_database_get_client_status
import foundationdb.c.fdb_database_get_main_thread_busyness
import foundationdb.c.fdb_future_get_key
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.readBytes

import foundationdb.c.FDBDatabase
import foundationdb.c.FDBTenant
import foundationdb.c.FDBTransaction
import foundationdb.c.fdb_database_create_transaction
import foundationdb.c.fdb_database_destroy
import foundationdb.c.fdb_database_open_tenant
import foundationdb.c.fdb_tenant_destroy
import foundationdb.c.fdb_transaction_destroy
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import maryk.foundationdb.tuple.Tuple
import platform.posix.sched_yield
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

actual class Database internal constructor(internal val handle: NativeDatabaseHandle) : TransactionContext {
    internal constructor(pointer: CPointer<FDBDatabase>) : this(NativeDatabaseHandle(pointer))

    actual fun options(): DatabaseOptions = DatabaseOptions(handle)

    actual fun createTransaction(): Transaction = memScoped {
        NativeEnvironment.ensureNetwork()
        val out = allocPointerTo<FDBTransaction>()
        handle.usePointer { pointer ->
            checkError(fdb_database_create_transaction(pointer, out.ptr))
        }
        val txnPointer = out.value ?: error("fdb_database_create_transaction returned null pointer")
        try {
            Transaction(txnPointer, this@Database)
        } catch (throwable: Throwable) {
            fdb_transaction_destroy(txnPointer)
            throw throwable
        }
    }

    @Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
    actual fun openTenant(tenantName: Tuple): Tenant = openTenant(tenantName.pack())

    actual fun openTenant(tenantName: ByteArray): Tenant = memScoped {
        NativeEnvironment.ensureNetwork()
        val out = allocPointerTo<FDBTenant>()
        val packed = tenantName
        packed.withPointer { ptr, length ->
            handle.usePointer { pointer ->
                checkError(fdb_database_open_tenant(pointer, ptr, length, out.ptr))
            }
        }
        val tenantPointer = out.value ?: error("fdb_database_open_tenant returned null pointer")
        try {
            Tenant(tenantPointer, packed, this@Database)
        } catch (throwable: Throwable) {
            fdb_tenant_destroy(tenantPointer)
            throw throwable
        }
    }

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

    actual fun getClientStatus(): FdbFuture<ByteArray> {
        val future = handle.usePointer { fdb_database_get_client_status(it) } ?: error("Missing client-status future")
        return NativeFuture(future) { native ->
            val key = allocPointerTo<UByteVar>()
            val size = alloc<IntVar>()
            checkError(fdb_future_get_key(native, key.ptr, size.ptr))
            if (size.value == 0) byteArrayOf() else key.value!!.readBytes(size.value)
        }
    }
    actual fun getMainThreadBusyness(): Double = handle.usePointer { fdb_database_get_main_thread_busyness(it) }

    @ExperimentalBlobGranules
    actual fun purgeBlobGranules(range: Range, purgeVersion: Long, force: Boolean): FdbFuture<ByteArray> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_database_purge_blob_granules(owner, begin, beginSize, end, endSize, purgeVersion, force.toFdbBool()) }
        } }
        return nativeKeyFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun blobbifyRange(range: Range): FdbFuture<Boolean> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_database_blobbify_range(owner, begin, beginSize, end, endSize) }
        } }
        return nativeBooleanFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun blobbifyRangeBlocking(range: Range): FdbFuture<Boolean> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_database_blobbify_range_blocking(owner, begin, beginSize, end, endSize) }
        } }
        return nativeBooleanFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun unblobbifyRange(range: Range): FdbFuture<Boolean> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_database_unblobbify_range(owner, begin, beginSize, end, endSize) }
        } }
        return nativeBooleanFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun listBlobbifiedRanges(range: Range, rangeLimit: Int): FdbFuture<List<Range>> {
        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_database_list_blobbified_ranges(owner, begin, beginSize, end, endSize, rangeLimit) }
        } }
        return nativeRangesFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun verifyBlobRange(range: Range, version: Long): FdbFuture<Long> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_database_verify_blob_range(owner, begin, beginSize, end, endSize, version) }
        } }
        return nativeLongFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun flushBlobRange(range: Range, compact: Boolean, version: Long): FdbFuture<Boolean> {

        val future = handle.usePointer { owner -> range.begin.withPointer { begin, beginSize ->
            range.end.withPointer { end, endSize -> fdb_database_flush_blob_range(owner, begin, beginSize, end, endSize, compact.toFdbBool(), version) }
        } }
        return nativeBooleanFuture(future)
    }
    @ExperimentalBlobGranules
    actual fun waitPurgeGranulesComplete(purgeKey: ByteArray): FdbFuture<Unit> {
        val future = handle.usePointer { owner -> purgeKey.withPointer { key, size -> fdb_database_wait_purge_granules_complete(owner, key, size) } }
        return NativeFuture(requireNotNull(future)) { }
    }

    actual override fun <T> run(block: (Transaction) -> T): T =
        runBlockingWithRetry(::createTransaction, needsCommit = true, block = block)

    actual override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> =
        runAsyncWithRetry(::createTransaction, needsCommit = true, block = block)

    actual override fun <T> read(block: (ReadTransaction) -> T): T =
        runBlockingWithRetry(::createTransaction, needsCommit = false, block = block)

    actual override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        runAsyncWithRetry(::createTransaction, needsCommit = false, block = block)
}

internal class NativeDatabaseHandle(private val pointer: CPointer<FDBDatabase>) {
    private val closed = AtomicInt(0)
    private val activeUses = AtomicInt(0)

    fun checkOpen() {
        check(closed.load() == 0) { "Database is closed." }
    }

    fun <T> usePointer(block: (CPointer<FDBDatabase>) -> T): T {
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
            fdb_database_destroy(pointer)
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
            if (activeUses.compareAndSet(current, current - 1)) return
        }
    }
}
