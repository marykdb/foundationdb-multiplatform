package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple

expect class Database : TransactionContext {
    override fun <T> run(block: (Transaction) -> T): T
    override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T>
    override fun <T> read(block: (ReadTransaction) -> T): T
    override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T>
    fun options(): DatabaseOptions
    fun createTransaction(): Transaction
    @Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
    fun openTenant(tenantName: Tuple): Tenant
    fun close()
}
