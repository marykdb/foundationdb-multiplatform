package maryk.foundationdb

@Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
actual class Tenant internal constructor(
    internal val delegate: com.apple.foundationdb.Tenant
) : TransactionContext {
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
