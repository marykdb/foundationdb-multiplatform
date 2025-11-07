package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.fdbFutureFromSuspend

actual class DirectoryLayer {
    actual fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend { DirectoryStore.ensureDirectory(transaction, path, ByteArray(0)) }

    actual fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend {
            DirectoryStore.openDirectory(transaction, path, ByteArray(0))
                ?: throw NoSuchDirectoryException(path)
        }

    actual fun open(readTransaction: ReadTransaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend {
            DirectoryStore.openDirectory(readTransaction, path, ByteArray(0))
                ?: throw NoSuchDirectoryException(path)
        }

    actual companion object {
        actual fun getDefault(): DirectoryLayer = DirectoryLayer()
    }
}
