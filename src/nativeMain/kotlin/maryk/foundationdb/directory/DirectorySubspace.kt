package maryk.foundationdb.directory

import maryk.foundationdb.ByteArrayUtil
import maryk.foundationdb.FDBException
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.fdbFutureFromSuspend
import maryk.foundationdb.tuple.Tuple

actual open class DirectorySubspace internal constructor(
    protected open val prefix: ByteArray,
    actual val path: List<String>,
    actual val layer: ByteArray,
    private val context: DirectoryContext = DirectoryContext.default()
) {
    actual fun pack(): ByteArray = prefix

    actual fun pack(tuple: Tuple): ByteArray = ByteArrayUtil.join(prefix, tuple.pack())

    open actual fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend {
            val fullPath = this.path + path
            DirectoryStore.ensureDirectory(transaction, fullPath, layer, context)
        }

    open actual fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend {
            val fullPath = this.path + path
            DirectoryStore.openDirectory(transaction, fullPath, layer, context)
                ?: throw FDBException("Directory does not exist", 2131)
        }

}

actual fun DirectorySubspace.asDirectory(): Directory =
    Directory(path = this.path, layer = this.layer)
