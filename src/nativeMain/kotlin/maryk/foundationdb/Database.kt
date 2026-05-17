@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package maryk.foundationdb

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
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
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
            Transaction(txnPointer)
        } catch (throwable: Throwable) {
            fdb_transaction_destroy(txnPointer)
            throw throwable
        }
    }

    @Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
    actual fun openTenant(tenantName: Tuple): Tenant = memScoped {
        NativeEnvironment.ensureNetwork()
        val out = allocPointerTo<FDBTenant>()
        val packed = tenantName.pack()
        packed.usePinned { pinned ->
            val ptr = pinned.addressOf(0).reinterpret<UByteVar>()
            handle.usePointer { pointer ->
                checkError(fdb_database_open_tenant(pointer, ptr, packed.size, out.ptr))
            }
        }
        val tenantPointer = out.value ?: error("fdb_database_open_tenant returned null pointer")
        try {
            Tenant(tenantPointer)
        } catch (throwable: Throwable) {
            fdb_tenant_destroy(tenantPointer)
            throw throwable
        }
    }

    actual fun close() {
        handle.close()
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
