package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.toFdbFuture
import maryk.foundationdb.tuple.Tuple

actual open class DirectorySubspace internal constructor(
    internal val delegate: com.apple.foundationdb.directory.DirectorySubspace
) {
    actual val path: List<String>
        get() = delegate.path

    actual val layer: ByteArray
        get() = delegate.layer

    actual fun pack(): ByteArray = delegate.pack()

    actual fun pack(tuple: Tuple): ByteArray = delegate.pack(tuple.delegate)

    actual fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        delegate.createOrOpen(transaction.delegate, path)
            .thenApply(::DirectorySubspace)
            .toFdbFuture()

    actual fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        delegate.open(transaction.delegate, path)
            .thenApply(::DirectorySubspace)
            .toFdbFuture()
}

actual fun DirectorySubspace.asDirectory(): Directory = Directory(delegate)
