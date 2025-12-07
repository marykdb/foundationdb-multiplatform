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
    private val nestedContext = DirectoryContext(
        nodeSubspace = parentContext.nodeSubspace.get(prefix),
        contentSubspace = parentContext.contentSubspace.get(prefix),
        allowManualPrefixes = parentContext.allowManualPrefixes
    )

    private val nestedLayer = DirectoryLayer(nestedContext)

    override fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        nestedLayer.createOrOpen(transaction, path)

    override fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace> =
        fdbFutureFromSuspend {
            nestedLayer.open(transaction, path).await()
        }
}
