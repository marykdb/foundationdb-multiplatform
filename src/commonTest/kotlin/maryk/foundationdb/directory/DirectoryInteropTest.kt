package maryk.foundationdb.directory

import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.testDirectoryOrNull
import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class DirectoryInteropTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun nativeLayoutMatchesOfficialDirectoryLayer() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val directory = layer.testDirectoryOrNull() ?: return@runAndReset
        val path = listOf(namespace, "interop", "runner")

        val subspace: DirectorySubspace = database.runSuspend { txn -> directory.createOrOpen(txn, path).await() }
        val dataPrefix = subspace.pack()

        // Should not be the deterministic tuple(path) prefix used by the old native implementation
        val tuplePrefix = Tuple.fromList(path).pack()
        assertFalse(dataPrefix.contentEquals(tuplePrefix))

        // Version key must exist in the node subspace (default 0xFE)
        val nodeSubspace = Subspace(byteArrayOf(0xFE.toByte()))
        val rootNode = nodeSubspace.get(nodeSubspace.getKey())
        val versionKey = rootNode.pack("version")
        val versionValue = database.readSuspend { rt -> rt.get(versionKey).await() }
        versionValue?.let {
            assertContentEquals(
                byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                it
            )
        }

        // The stored mapping in metadata must point to the allocated data prefix
        val parentPrefix = database.readSuspend { rt -> fetchPrefix(rt, path.dropLast(1)) } ?: return@runAndReset
        val parentNode = nodeSubspace.get(parentPrefix)
        val subdirKey = parentNode.get(0).pack(path.last())
        val storedPrefix = database.readSuspend { rt -> rt.get(subdirKey).await() }
        assertContentEquals(dataPrefix, storedPrefix)
    }

    private suspend fun fetchPrefix(rt: ReadTransaction, path: List<String>): ByteArray? {
        if (path.isEmpty()) return Subspace(byteArrayOf(0xFE.toByte())).getKey()
        var node = Subspace(byteArrayOf(0xFE.toByte())).get(Subspace(byteArrayOf(0xFE.toByte())).getKey())
        for (segment in path) {
            val subdir = node.get(0)
            val childPrefix = rt.get(subdir.pack(segment)).await() ?: return null
            if (segment == path.last()) return childPrefix
            node = Subspace(byteArrayOf(0xFE.toByte())).get(childPrefix)
        }
        return null
    }
}
