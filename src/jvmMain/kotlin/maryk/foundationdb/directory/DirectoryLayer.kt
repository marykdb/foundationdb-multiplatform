package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction
import maryk.foundationdb.toFdbFuture

actual class DirectoryLayer internal constructor(
    internal val delegate: com.apple.foundationdb.directory.DirectoryLayer
) {
    actual fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        delegate.createOrOpen(transaction.delegate, path)
            .thenApply(::DirectorySubspace)
            .toFdbFuture()

    actual fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        delegate.open(transaction.delegate, path)
            .thenApply(::DirectorySubspace)
            .toFdbFuture()

    actual fun open(readTransaction: ReadTransaction, path: List<String>): FdbFuture<DirectorySubspace> =
        delegate.open(readTransaction.delegate, path)
            .thenApply(::DirectorySubspace)
            .toFdbFuture()

    actual companion object {
        actual fun getDefault(): DirectoryLayer =
            DirectoryLayer(com.apple.foundationdb.directory.DirectoryLayer.getDefault())
    }
}
