package maryk.foundationdb

import maryk.foundationdb.asCompletableFuture
import maryk.foundationdb.toFdbFuture
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

    override fun <T> run(block: (Transaction) -> T): T =
        delegate.run { txn -> block(Transaction(txn)) }

    override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> =
        delegate.runAsync { txn -> block(Transaction(txn)).asCompletableFuture() }.toFdbFuture()

    override fun <T> read(block: (ReadTransaction) -> T): T =
        delegate.read { rt -> block(ReadTransaction(rt)) }

    override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        delegate.readAsync { rt -> block(ReadTransaction(rt)).asCompletableFuture() }.toFdbFuture()
}
