package maryk.foundationdb.directory

import maryk.foundationdb.FDBException
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.fdbFutureFromSuspend
import maryk.foundationdb.tuple.Tuple

/**
 * Partition-aware DirectorySubspace that delegates further directory operations
 * to a nested DirectoryLayer rooted at this partition's prefix, matching the
 * official DirectoryLayer partition semantics.
 */
internal class PartitionDirectorySubspace(
    override val prefix: ByteArray,
    path: List<String>,
    layer: ByteArray,
    parentContext: DirectoryContext
) : DirectorySubspace(prefix, path, layer, parentContext) {
    private val nestedContext = parentContext.partition(prefix, path.drop(parentContext.pathPrefix.size))

    private val nestedLayer = DirectoryLayer(nestedContext)

    override fun getDirectoryLayer(): DirectoryLayer = nestedLayer
    override fun checkUsable(): Unit = throw UnsupportedOperationException("Cannot use the root of a directory partition as a Subspace")

    override fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        if (context.layout == DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) super.createOrOpen(transaction, path) else nestedLayer.createOrOpen(transaction, path)

    override fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend {
            if (context.layout == DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) super.open(transaction, path).await() else nestedLayer.open(transaction, path).await()
        }
}
