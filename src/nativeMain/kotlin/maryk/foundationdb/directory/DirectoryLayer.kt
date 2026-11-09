package maryk.foundationdb.directory

import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.fdbFutureFromSuspend

actual class DirectoryLayer internal constructor(
    private val context: DirectoryContext
) : Directory by Directory(context.pathPrefix, byteArrayOf(), context) {
    actual constructor() : this(DirectoryContext.default())
    actual constructor(allowManualPrefixes: Boolean) : this(DirectoryContext.default().copy(allowManualPrefixes = allowManualPrefixes))
    actual constructor(nodeSubspace: Subspace, contentSubspace: Subspace) : this(DirectoryContext(nodeSubspace, contentSubspace))

    actual constructor(
        nodeSubspace: Subspace,
        contentSubspace: Subspace,
        allowManualPrefixes: Boolean
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

    actual override val path: List<String> get() = context.pathPrefix
    actual override val layer: ByteArray get() = byteArrayOf()
    actual override fun getDirectoryLayer(): DirectoryLayer = this
    actual fun asDirectory(): Directory = Directory(context.pathPrefix, byteArrayOf(), context)

    actual override fun equals(other: Any?): Boolean = other is DirectoryLayer && context.pathPrefix == other.context.pathPrefix && context.nodeSubspace == other.context.nodeSubspace && context.contentSubspace == other.context.contentSubspace && context.layout == other.context.layout
    actual override fun hashCode(): Int = context.pathPrefix.hashCode() xor (context.nodeSubspace.hashCode() * 179) xor (context.contentSubspace.hashCode() * 937) xor context.layout.ordinal

    actual companion object {
        actual fun forLayout(layout: DirectoryLayout, nodeSubspace: Subspace, contentSubspace: Subspace, allowManualPrefixes: Boolean): DirectoryLayer =
            DirectoryLayer(DirectoryContext(nodeSubspace, contentSubspace, allowManualPrefixes, layout))
        actual val DEFAULT_NODE_SUBSPACE: Subspace get() = Subspace(byteArrayOf(0xfe.toByte()))
        actual val DEFAULT_CONTENT_SUBSPACE: Subspace get() = Subspace()
        actual val PARTITION_LAYER: ByteArray get() = "partition".encodeToByteArray()
        actual fun createWithNodeSubspace(nodeSubspace: Subspace): Directory = from(nodeSubspace).asDirectory()
        actual fun createWithContentSubspace(contentSubspace: Subspace): Directory =
            from(Subspace(byteArrayOf(0xfe.toByte())), contentSubspace).asDirectory()
        actual fun getDefault(): DirectoryLayer = DirectoryLayer()

        actual fun from(
            nodeSubspace: Subspace,
            contentSubspace: Subspace,
            allowManualPrefixes: Boolean
        ): DirectoryLayer = DirectoryLayer(nodeSubspace, contentSubspace, allowManualPrefixes)
    }
}
