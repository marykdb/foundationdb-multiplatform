package maryk.foundationdb.directory

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.runSuspend
import maryk.foundationdb.testDirectoryOrNull
import maryk.foundationdb.readSuspend
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.subspace.Subspace

class DirectoryPartitionInteropTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun partitionCreatesNestedDirectoryLayerMetadata() = harness.runAndReset {
        val directoryRoot = DirectoryLayer.getDefault().testDirectoryOrNull() ?: return@runAndReset
        val partition = directoryRoot.createOrOpen(database, listOf(namespace, "partition"), "partition".encodeToByteArray()).await()

        // create a child directory inside the partition
        val child = database.runSuspend { txn -> partition.createOrOpen(txn, listOf("child")).await() }
        assertTrue(child.pack().isNotEmpty())

        // Metadata for the child should live under the partition's node subspace (0xFE || partitionPrefix)
        val partitionPrefix = kotlin.runCatching { partition.testRawPrefix() }.getOrNull() ?: return@runAndReset
        val nodeSubspace = Subspace(byteArrayOf(0xFE.toByte())).get(partitionPrefix)
        val rootNode = nodeSubspace.get(nodeSubspace.getKey())
        val subdirMap = rootNode.get(0)
        val childEntry = database.readSuspend { rt -> rt.get(subdirMap.pack("child")).await() }
        assertNotNull(childEntry)
        assertFalse(childEntry.contentEquals(Tuple.from(namespace, "partition", "child").pack()))
    }

    @Test
    fun partitionChildPointerLivesInsidePartitionNodeSubspace() = harness.runAndReset {
        val directoryRoot = DirectoryLayer.getDefault().testDirectoryOrNull() ?: return@runAndReset
        val partition = directoryRoot.createOrOpen(database, listOf(namespace, "partition"), "partition".encodeToByteArray()).await()
        val child = database.runSuspend { txn -> partition.createOrOpen(txn, listOf("child")).await() }
        assertTrue(child.pack().isNotEmpty())

        val partitionPrefix = kotlin.runCatching { partition.testRawPrefix() }.getOrNull() ?: return@runAndReset
        val nodeSubspace = Subspace(byteArrayOf(0xFE.toByte())).get(partitionPrefix)
        val rootNode = nodeSubspace.get(nodeSubspace.getKey())
        val subdirMap = rootNode.get(0)

        val childPointer = database.readSuspend { rt -> rt.get(subdirMap.pack("child")).await() }
        assertNotNull(childPointer, "child pointer must be stored inside partition's node subspace")
    }
}
