package maryk.foundationdb

/**
 * Read-only transactional context abstraction shared by Database, Transaction, and Tenant.
 */
expect interface ReadTransactionContext {
    fun <T> read(block: (ReadTransaction) -> T): T
    fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T>
}
