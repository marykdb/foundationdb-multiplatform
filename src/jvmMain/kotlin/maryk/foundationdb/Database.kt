package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple

actual class Database internal constructor(
    internal val delegate: com.apple.foundationdb.Database
) : TransactionContext {
    actual fun options(): DatabaseOptions = DatabaseOptions(delegate.options())

    actual fun createTransaction(): Transaction =
        Transaction(delegate.createTransaction())

    actual fun openTenant(tenantName: Tuple): Tenant =
        Tenant(delegate.openTenant(tenantName.delegate))

    actual fun close() {
        delegate.close()
    }

    actual override fun <T> run(block: (Transaction) -> T): T =
        delegate.run { txn -> block(Transaction(txn)) }

    actual override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> =
        delegate.runAsync { txn -> block(Transaction(txn)).asCompletableFuture() }.toFdbFuture()

    actual override fun <T> read(block: (ReadTransaction) -> T): T =
        delegate.read { rt -> block(ReadTransaction(rt)) }

    actual override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        delegate.readAsync { rt -> block(ReadTransaction(rt)).asCompletableFuture() }.toFdbFuture()
}
