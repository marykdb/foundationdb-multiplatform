@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package maryk.foundationdb

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
actual class Tenant internal constructor(internal val handle: NativeTenantHandle) : TransactionContext {
    internal constructor(pointer: CPointer<FDBTenant>) : this(NativeTenantHandle(pointer))

    private fun createTransaction(): Transaction = memScoped {
        val out = allocPointerTo<FDBTransaction>()
        handle.usePointer { pointer ->
            checkError(fdb_tenant_create_transaction(pointer, out.ptr))
        }
        val txnPointer = out.value ?: error("fdb_tenant_create_transaction returned null pointer")
        try {
            Transaction(txnPointer)
        } catch (throwable: Throwable) {
            fdb_transaction_destroy(txnPointer)
            throw throwable
        }
    }

    actual override fun <T> run(block: (Transaction) -> T): T =
        runBlockingWithRetry(::createTransaction, needsCommit = true, block = block)

    actual override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> =
        runAsyncWithRetry(::createTransaction, needsCommit = true, block = block)

    actual override fun <T> read(block: (ReadTransaction) -> T): T =
        runBlockingWithRetry(::createTransaction, needsCommit = false, block = block)

    actual override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        runAsyncWithRetry(::createTransaction, needsCommit = false, block = block)

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
