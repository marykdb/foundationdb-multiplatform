package maryk.foundationdb

import kotlin.coroutines.EmptyCoroutineContext

/**
 * Read-only transactional context abstraction shared by Database, Transaction, and Tenant.
 */
expect interface ReadTransactionContext {
    fun <T> read(block: (ReadTransaction) -> T): T
    fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T>
}

/** Execute a suspending read against a database, transaction, snapshot, tenant, or custom read context. */
suspend fun <T> ReadTransactionContext.readSuspend(block: suspend (ReadTransaction) -> T): T =
    readSuspend(EmptyCoroutineContext, block)
