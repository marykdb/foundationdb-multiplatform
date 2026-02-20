@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDBTenant
import foundationdb.c.FDBTransaction
import foundationdb.c.fdb_tenant_create_transaction
import foundationdb.c.fdb_tenant_destroy
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

@Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
actual class Tenant internal constructor(internal val pointer: CPointer<FDBTenant>) : TransactionContext {
    private fun createTransaction(): Transaction = memScoped {
        val out = allocPointerTo<FDBTransaction>()
        checkError(fdb_tenant_create_transaction(pointer, out.ptr))
        val txnPointer = out.value ?: error("fdb_tenant_create_transaction returned null pointer")
        Transaction(txnPointer)
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
        fdb_tenant_destroy(pointer)
    }
}
