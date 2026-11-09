package maryk.foundationdb

import maryk.foundationdb.directory.DirectoryVersionException
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectoryMoveException
import maryk.foundationdb.directory.MismatchedLayerException
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReviewRegressionTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun childExistenceAndMovesDoNotInheritParentLayerTags() = harness.runAndReset {
        val directories = DirectoryLayer(Subspace(prefixBytes("layer-meta")), Subspace(prefixBytes("layer-data")))
        val parent = directories.create(database, listOf("parent"), "parent-layer".encodeToByteArray()).await()
        parent.create(database, listOf("child"), "child-layer".encodeToByteArray()).await()
        assertTrue(parent.exists(database, listOf("child")).await())
        val moved = parent.move(database, listOf("child"), listOf("moved")).await()
        assertEquals(listOf("parent", "moved"), moved.path)
        assertContentEquals("child-layer".encodeToByteArray(), moved.layer)
    }

    @Test
    fun requestedNonemptyLayerRejectsAnUntaggedDirectory() = harness.runAndReset {
        val directories = DirectoryLayer(Subspace(prefixBytes("empty-meta")), Subspace(prefixBytes("empty-data")))
        directories.create(database, listOf("plain")).await()
        assertFailsWith<MismatchedLayerException> {
            directories.open(database, listOf("plain"), "required-layer".encodeToByteArray()).await()
        }
    }

    @Test
    fun crossingPartitionMoveHasTheCommonDirectoryException() = harness.runAndReset {
        val directories = DirectoryLayer(Subspace(prefixBytes("move-meta")), Subspace(prefixBytes("move-data")))
        directories.create(database, listOf("partition"), DirectoryLayer.PARTITION_LAYER).await()
        val child = directories.create(database, listOf("partition", "child")).await()
        assertFailsWith<DirectoryMoveException> { child.moveTo(database, listOf("outside")).await() }
        assertTrue(directories.exists(database, listOf("partition", "child")).await())
    }

    @Test
    fun directoryPatchVersionsRemainReadableAndWritable() = harness.runAndReset {
        val nodes = Subspace(prefixBytes("patch-meta"))
        val directories = DirectoryLayer(nodes, Subspace(prefixBytes("patch-data")))
        directories.create(database, listOf("existing")).await()
        val version = nodes.get(nodes.getKey()).pack("version".encodeToByteArray())
        database.run { it.set(version, byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0)) }
        assertEquals(listOf("existing"), directories.open(database, listOf("existing")).await().path)
        directories.create(database, listOf("new")).await()
    }

    @Test
    fun newerDirectoryMinorVersionsAreReadableButNotWritable() = harness.runAndReset {
        val nodes = Subspace(prefixBytes("minor-meta"))
        val directories = DirectoryLayer(nodes, Subspace(prefixBytes("minor-data")))
        directories.create(database, listOf("existing")).await()
        val version = nodes.get(nodes.getKey()).pack("version".encodeToByteArray())
        database.run { it.set(version, byteArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0)) }
        assertTrue(directories.exists(database, listOf("existing")).await())
        assertFailsWith<DirectoryVersionException> { directories.create(database, listOf("new")).await() }
    }

    @Test
    fun unsupportedApiVersionsHaveConsistentValidation() {
        assertFailsWith<IllegalArgumentException> { FDB.selectAPIVersion(509) }
        assertFailsWith<IllegalArgumentException> { FDB.selectAPIVersion(ApiVersion.LATEST + 1) }
    }

    @Test
    fun tupleItemListsAndIteratorsCannotMutateTupleStructure() {
        val tuple = Tuple.from(1L, 2L)
        val exposed = tuple.items
        runCatching { (exposed as MutableList<Any?>).clear() }
        assertEquals(2, tuple.size)
        assertEquals(Tuple.from(1L, 2L), tuple)
        val iterator = tuple.iterator()
        iterator.next()
        runCatching { (iterator as MutableIterator<Any?>).remove() }
        assertEquals(Tuple.from(1L, 2L), tuple)
    }

    @Test
    fun truncatedNestedTuplesAreRejected() {
        for (bytes in listOf(byteArrayOf(5), byteArrayOf(5, 0x14), byteArrayOf(5, 0, 0xff.toByte()))) {
            assertFailsWith<IllegalArgumentException> { Tuple.fromBytes(bytes) }
        }
    }

    @Test
    fun malformedUtf8IsRejectedInsteadOfReplaced() {
        for (payload in listOf(byteArrayOf(0x80.toByte()), byteArrayOf(0xc0.toByte(), 0xaf.toByte()), byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()))) {
            assertFailsWith<IllegalArgumentException> { Tuple.fromBytes(byteArrayOf(2) + payload + byteArrayOf(0)) }
        }
    }
}
