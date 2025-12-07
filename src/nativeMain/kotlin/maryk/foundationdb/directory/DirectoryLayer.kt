package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.fdbFutureFromSuspend

actual class DirectoryLayer internal constructor(
    private val context: DirectoryContext = DirectoryContext.default()
) {
    constructor(
        nodeSubspace: maryk.foundationdb.subspace.Subspace,
        contentSubspace: maryk.foundationdb.subspace.Subspace = maryk.foundationdb.subspace.Subspace(),
        allowManualPrefixes: Boolean = false
    ) : this(DirectoryContext(nodeSubspace, contentSubspace, allowManualPrefixes))

    actual fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend { DirectoryStore.ensureDirectory(transaction, path, ByteArray(0), context) }

    actual fun create(
        transaction: Transaction,
        path: List<String>,
        layer: ByteArray,
        prefix: ByteArray?
    ): FdbFuture<DirectorySubspace> = fdbFutureFromSuspend {
        DirectoryStore.createDirectory(transaction, path, layer, prefix, context)
    }

    actual fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend {
            DirectoryStore.openDirectory(transaction, path, ByteArray(0), context)
                ?: throw NoSuchDirectoryException(path)
        }

    actual fun open(readTransaction: ReadTransaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend {
            DirectoryStore.openDirectory(readTransaction, path, ByteArray(0), context)
                ?: throw NoSuchDirectoryException(path)
        }

    actual companion object {
        actual fun getDefault(): DirectoryLayer = DirectoryLayer()

        actual fun from(
            nodeSubspace: maryk.foundationdb.subspace.Subspace,
            contentSubspace: maryk.foundationdb.subspace.Subspace,
            allowManualPrefixes: Boolean
        ): DirectoryLayer = DirectoryLayer(nodeSubspace, contentSubspace, allowManualPrefixes)
    }
}
