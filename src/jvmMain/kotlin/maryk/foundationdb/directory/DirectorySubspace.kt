package maryk.foundationdb.directory

import com.apple.foundationdb.directory.DirectorySubspace as JavaDirectorySubspace

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.mapToFdbFuture
import maryk.foundationdb.tuple.Tuple

actual open class DirectorySubspace internal constructor(
    internal val directoryDelegate: JavaDirectorySubspace
) : Subspace(directoryDelegate), Directory by Directory(directoryDelegate) {
    actual override val path: List<String>
        get() = directoryDelegate.path

    actual override val layer: ByteArray
        get() = directoryDelegate.layer

    actual override fun pack(): ByteArray = directoryDelegate.pack()

    actual override fun pack(tuple: Tuple): ByteArray = directoryDelegate.pack(tuple.delegate)

    actual fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        directoryDelegate.createOrOpen(transaction.delegate, path)
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)

    actual fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        directoryDelegate.open(transaction.delegate, path)
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)
}

actual fun DirectorySubspace.asDirectory(): Directory = Directory(directoryDelegate)
