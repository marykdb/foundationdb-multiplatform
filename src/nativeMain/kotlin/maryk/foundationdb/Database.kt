@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDBDatabase
import foundationdb.c.FDBTenant
import foundationdb.c.FDBTransaction
import foundationdb.c.fdb_database_create_transaction
import foundationdb.c.fdb_database_destroy
import foundationdb.c.fdb_database_open_tenant
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

actual class Database internal constructor(internal val pointer: CPointer<FDBDatabase>) : TransactionContext {
    actual fun options(): DatabaseOptions = DatabaseOptions(pointer)

    actual fun createTransaction(): Transaction = memScoped {
        NativeEnvironment.ensureNetwork()
        val out = allocPointerTo<FDBTransaction>()
        checkError(fdb_database_create_transaction(pointer, out.ptr))
        val txnPointer = out.value ?: error("fdb_database_create_transaction returned null pointer")
        Transaction(txnPointer)
    }

    actual fun openTenant(tenantName: Tuple): Tenant = memScoped {
        NativeEnvironment.ensureNetwork()
        val out = allocPointerTo<FDBTenant>()
        val packed = tenantName.pack()
        packed.usePinned { pinned ->
            val ptr = pinned.addressOf(0).reinterpret<UByteVar>()
            checkError(fdb_database_open_tenant(pointer, ptr, packed.size, out.ptr))
        }
        val tenantPointer = out.value ?: error("fdb_database_open_tenant returned null pointer")
        Tenant(tenantPointer)
    }

    actual fun close() {
        fdb_database_destroy(pointer)
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
