package maryk.foundationdb.directory

import maryk.foundationdb.ByteArrayUtil
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.fdbFutureFromSuspend
import maryk.foundationdb.tuple.Tuple

actual open class DirectorySubspace internal constructor(
    protected open val prefix: ByteArray,
    actual override val path: List<String>,
    actual override val layer: ByteArray,
    internal val context: DirectoryContext = DirectoryContext.default()
) : Subspace(prefix), Directory by Directory(path, layer, context) {
    override fun equals(other: Any?): Boolean = this === other || (other is DirectorySubspace && this::class == other::class && path == other.path && layer.contentEquals(other.layer) && prefix.contentEquals(other.prefix) && getDirectoryLayer() == other.getDirectoryLayer())
    override fun hashCode(): Int = path.hashCode() xor (layer.contentHashCode() * 1153) xor (getDirectoryLayer().hashCode() * 929) xor (prefix.contentHashCode() * 419)

    actual override fun pack(): ByteArray { checkUsable(); return prefix.copyOf() }

    actual override fun pack(tuple: Tuple): ByteArray { checkUsable(); return ByteArrayUtil.join(prefix, tuple.pack()) }

    open actual fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend {
            val fullPath = this.path.drop(context.pathPrefix.size) + path
            DirectoryStore.ensureDirectory(transaction, fullPath, byteArrayOf(), context)
        }

    open actual fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend {
            val fullPath = this.path.drop(context.pathPrefix.size) + path
            DirectoryStore.openDirectory(transaction, fullPath, byteArrayOf(), context)
                ?: throw NoSuchDirectoryException(fullPath)
        }

}

actual fun DirectorySubspace.asDirectory(): Directory = this
