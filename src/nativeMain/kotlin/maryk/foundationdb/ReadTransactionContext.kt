package maryk.foundationdb

actual interface ReadTransactionContext {
    actual fun <T> read(block: (ReadTransaction) -> T): T
    actual fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T>
}
