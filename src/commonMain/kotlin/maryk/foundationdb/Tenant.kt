package maryk.foundationdb

expect class Tenant : TransactionContext {
    override fun <T> run(block: (Transaction) -> T): T
    override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T>
    override fun <T> read(block: (ReadTransaction) -> T): T
    override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T>
    fun close()
}
