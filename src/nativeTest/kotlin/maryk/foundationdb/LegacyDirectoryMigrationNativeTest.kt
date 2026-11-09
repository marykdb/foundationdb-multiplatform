package maryk.foundationdb

import maryk.foundationdb.directory.DirectoryVersionException
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectoryLayout
import maryk.foundationdb.directory.DirectoryTreeMigration
import maryk.foundationdb.subspace.Subspace
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegacyDirectoryMigrationNativeTest {
    private val harness = FoundationDbTestHarness()
    @Test fun flatLegacyPartitionCannotSilentlyForkIntoCanonicalMetadata() = harness.runAndReset {
        val nodes = Subspace(prefixBytes("flat-meta"))
        val content = Subspace(prefixBytes("flat-data"))
        val source = DirectoryLayer(nodes, content)
        val parent = source.create(database, listOf("partition")).await()
        val child = source.create(database, listOf("partition", "child")).await()
        database.run {
            it.set(child.pack("key"), byteArrayOf(4, 5, 6))
            // Reproduce the old full-path Native API's partition metadata layout.
            it.set(nodes.get(parent.getKey()).pack("layer".encodeToByteArray()), DirectoryLayer.PARTITION_LAYER)
        }
        assertFailsWith<DirectoryVersionException> {
            source.createOrOpen(database, listOf("partition", "child")).await()
        }
        assertContentEquals(byteArrayOf(4, 5, 6), database.readSuspend { it.get(child.pack("key")).await() })
        val targetNodes = Subspace(prefixBytes("flat-target-meta"))
        val targetContent = Subspace(prefixBytes("flat-target-data"))
        val migration = DirectoryTreeMigration(
            DirectoryTreeMigration.Configuration(nodes, content, DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS),
            DirectoryTreeMigration.Configuration(targetNodes, targetContent),
        )
        val parentEntry = database.readSuspend { migration.inspect(it, listOf("partition")) }
        val childEntry = database.readSuspend { migration.inspect(it, listOf("partition", "child")) }
        database.runSuspend { migration.prepare(it, listOf(parentEntry, childEntry)) }
        database.runSuspend { migration.copyBatch(it, parentEntry) }
        database.runSuspend { migration.copyBatch(it, childEntry) }
        assertTrue(database.readSuspend { migration.verifyBatch(it, childEntry).complete })
        database.readSuspend { migration.verifyRoots(it) }
        val target = DirectoryLayer(targetNodes, targetContent)
        val copied = target.open(database, listOf("partition", "child")).await()
        assertContentEquals(byteArrayOf(4, 5, 6), database.readSuspend { it.get(copied.pack("key")).await() })
        val readOnly = DirectoryLayer.forLayout(DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS, nodes, content)
        assertFailsWith<DirectoryVersionException> { readOnly.create(database, listOf("new")).await() }
        assertContentEquals(byteArrayOf(4, 5, 6), database.readSuspend { it.get(child.pack("key")).await() })
    }

    @Test fun legacyPartitionTreeCanBeCopiedToCanonicalWithoutDeletingSource() = harness.runAndReset {
        val oldContent = Subspace()
        val newContent = Subspace(prefixBytes("new-content"))
        val legacy = DirectoryLayer.forLayout(DirectoryLayout.LEGACY_NATIVE, Subspace(prefixBytes("old-meta")), oldContent, allowManualPrefixes = true)
        val canonical = DirectoryLayer.forLayout(DirectoryLayout.JAVA_CANONICAL, Subspace(prefixBytes("new-meta")), newContent)
        val partition = legacy.create(database, listOf("partition"), DirectoryLayer.PARTITION_LAYER, prefixBytes("legacy-partition")).await()
        assertFailsWith<UnsupportedOperationException> { partition.pack() }
        val oldChild = legacy.create(database, listOf("partition", "child")).await()
        database.run { it.set(oldChild.pack("key"), byteArrayOf(1, 2, 3)) }
        val wrongMode = DirectoryLayer.forLayout(DirectoryLayout.JAVA_CANONICAL, Subspace(prefixBytes("old-meta")), oldContent)
        assertFailsWith<DirectoryVersionException> { wrongMode.open(database, listOf("partition")).await() }
        assertFailsWith<DirectoryVersionException> { wrongMode.remove(database, listOf("partition")).await() }
        assertContentEquals(byteArrayOf(1, 2, 3), database.readSuspend { it.get(oldChild.pack("key")).await() })
        val migration = DirectoryTreeMigration(
            DirectoryTreeMigration.Configuration(Subspace(prefixBytes("old-meta")), oldContent, DirectoryLayout.LEGACY_NATIVE),
            DirectoryTreeMigration.Configuration(Subspace(prefixBytes("new-meta")), newContent),
        )
        val partitionEntry = database.readSuspend { migration.inspect(it, listOf("partition")) }
        val childEntry = database.readSuspend { migration.inspect(it, listOf("partition", "child")) }
        database.runSuspend { migration.prepare(it, listOf(partitionEntry, childEntry)) }
        database.runSuspend { migration.copyBatch(it, partitionEntry) }
        assertTrue(database.runSuspend { migration.copyBatch(it, childEntry).complete })
        assertTrue(database.readSuspend { migration.verifyBatch(it, childEntry).complete })
        val newChild = canonical.open(database, listOf("partition", "child")).await()
        assertContentEquals(byteArrayOf(1, 2, 3), database.readSuspend { it.get(newChild.pack("key")).await() })
        assertContentEquals(byteArrayOf(1, 2, 3), database.readSuspend { it.get(oldChild.pack("key")).await() })
        assertEquals(listOf("partition"), partition.getDirectoryLayer().path)

    }
}
