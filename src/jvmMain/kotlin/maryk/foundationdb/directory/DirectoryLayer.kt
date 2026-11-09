package maryk.foundationdb.directory

import com.apple.foundationdb.directory.DirectoryLayer as JavaDirectoryLayer

import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction
import maryk.foundationdb.mapToFdbFuture

actual class DirectoryLayer internal constructor(
    internal val delegate: JavaDirectoryLayer
) : Directory by Directory(delegate) {
    actual constructor() : this(JavaDirectoryLayer())
    actual constructor(allowManualPrefixes: Boolean) : this(JavaDirectoryLayer(allowManualPrefixes))
    actual constructor(nodeSubspace: Subspace, contentSubspace: Subspace) : this(JavaDirectoryLayer(nodeSubspace.delegate, contentSubspace.delegate))
    actual constructor(nodeSubspace: Subspace, contentSubspace: Subspace, allowManualPrefixes: Boolean) : this(JavaDirectoryLayer(nodeSubspace.delegate, contentSubspace.delegate, allowManualPrefixes))

    actual fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        delegate.createOrOpen(transaction.delegate, path)
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)

    actual fun create(
        transaction: Transaction,
        path: List<String>,
        layer: ByteArray,
        prefix: ByteArray?
    ): FdbFuture<DirectorySubspace> =
        delegate.create(transaction.delegate, path, layer, prefix)
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)

    actual fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        delegate.open(transaction.delegate, path)
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)

    actual fun open(readTransaction: ReadTransaction, path: List<String>): FdbFuture<DirectorySubspace> =
        delegate.open(readTransaction.delegate, path)
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)

    actual override val path: List<String> get() = emptyList()
    actual override val layer: ByteArray get() = byteArrayOf()
    actual override fun getDirectoryLayer(): DirectoryLayer = this
    actual fun asDirectory(): Directory = Directory(delegate)

    actual override fun equals(other: Any?): Boolean = other is DirectoryLayer && delegate == other.delegate
    actual override fun hashCode(): Int = delegate.hashCode()

    actual companion object {
        actual fun forLayout(layout: DirectoryLayout, nodeSubspace: Subspace, contentSubspace: Subspace, allowManualPrefixes: Boolean): DirectoryLayer {
            require(layout == DirectoryLayout.JAVA_CANONICAL) { "Legacy Native partition routing is supported on Native only; migrate there before JVM cutover" }
            return from(nodeSubspace, contentSubspace, allowManualPrefixes)
        }
        actual val DEFAULT_NODE_SUBSPACE: Subspace get() = Subspace(byteArrayOf(0xfe.toByte()))
        actual val DEFAULT_CONTENT_SUBSPACE: Subspace get() = Subspace()
        actual val PARTITION_LAYER: ByteArray get() = "partition".encodeToByteArray()
        actual fun createWithNodeSubspace(nodeSubspace: Subspace): Directory = from(nodeSubspace).asDirectory()
        actual fun createWithContentSubspace(contentSubspace: Subspace): Directory =
            from(Subspace(byteArrayOf(0xfe.toByte())), contentSubspace).asDirectory()
        actual fun getDefault(): DirectoryLayer =
            DirectoryLayer(JavaDirectoryLayer.getDefault())

        actual fun from(
            nodeSubspace: Subspace,
            contentSubspace: Subspace,
            allowManualPrefixes: Boolean
        ): DirectoryLayer =
            DirectoryLayer(
                JavaDirectoryLayer(
                    nodeSubspace.delegate,
                    contentSubspace.delegate,
                    allowManualPrefixes
                )
            )
    }
}
