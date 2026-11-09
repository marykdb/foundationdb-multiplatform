package maryk.foundationdb

import maryk.foundationdb.directory.Directory
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectoryLayout
import maryk.foundationdb.directory.DirectoryTreeMigration
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.subspace.PrefixMigration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DecisionMigrationTest {
    private val harness = FoundationDbTestHarness()
    @Test fun reviewedPrefixBatchesRejectCollisionsAndResume() = harness.runAndReset {
        val from = Subspace(prefixBytes("from"))
        val to = Subspace(prefixBytes("to"))
        val migration = PrefixMigration(from, to)
        database.run { tr -> repeat(5) { tr.set(from.pack(it), byteArrayOf(it.toByte())) } }
        val batch = database.readSuspend { migration.inspect(it, keyLimit = 2) }
        batch.entries()[0].value[0] = 99
        database.run { it.set(to.pack(0), byteArrayOf(77)) }
        assertFailsWith<IllegalStateException> { database.runSuspend { migration.apply(it, batch) } }
        assertContentEquals(byteArrayOf(0), database.readSuspend { it.get(from.pack(0)).await() })
        database.run { it.clear(to.pack(0)) }
        assertEquals(2, database.runSuspend { migration.apply(it, batch) })
        assertEquals(0, database.runSuspend { migration.apply(it, batch) })
        while (true) {
            val next = database.readSuspend { migration.inspect(it, keyLimit = 2) }
            if (next.keyCount == 0) break
            database.runSuspend { migration.apply(it, next) }
        }
        assertTrue(database.readSuspend { it.getRange(Range.startsWith(from.getKey()).begin, Range.startsWith(from.getKey()).end).asList().await().isEmpty() })
        assertContentEquals(byteArrayOf(4), database.readSuspend { it.get(to.pack(4)).await() })
    }
    @Test fun canonicalPartitionTreesMigrateAndVerifyInBatches() = harness.runAndReset {
        val sourceContent = Subspace(prefixBytes("source-content"))
        val targetContent = Subspace(prefixBytes("target-content"))
        val source = DirectoryLayer.forLayout(DirectoryLayout.JAVA_CANONICAL, Subspace(prefixBytes("source-meta")), sourceContent)
        val target = DirectoryLayer.forLayout(DirectoryLayout.JAVA_CANONICAL, Subspace(prefixBytes("target-meta")), targetContent)
        val partitionPath = listOf("partition")
        source.create(database, partitionPath, DirectoryLayer.PARTITION_LAYER).await()
        val child = source.create(database, partitionPath + "child").await()
        assertEquals(child, source.open(database, partitionPath + "child").await())
        assertEquals(child.hashCode(), source.open(database, partitionPath + "child").await().hashCode())
        assertTrue(child != Subspace(child.getKey()))
        assertTrue(Subspace(child.getKey()) != child)
        val namespace: Subspace = child
        val directory: Directory = child
        assertEquals(partitionPath + "child", directory.path)
        database.run { tr -> repeat(5) { tr.set(namespace.pack(it), byteArrayOf(it.toByte())) } }
        assertEquals(listOf("child"), source.list(database, partitionPath).await())
        val migration = DirectoryTreeMigration(
            DirectoryTreeMigration.Configuration(Subspace(prefixBytes("source-meta")), sourceContent),
            DirectoryTreeMigration.Configuration(Subspace(prefixBytes("target-meta")), targetContent),
        )
        val parent = database.readSuspend { migration.inspect(it, partitionPath) }
        val inventory = listOf(parent) + parent.children.map { path -> database.readSuspend { migration.inspect(it, path) } }
        assertFailsWith<IllegalStateException> { database.runSuspend { migration.copyBatch(it, parent) } }
        assertFailsWith<IllegalStateException> { database.runSuspend { migration.prepare(it, listOf(parent)) } }
        database.run { it.set(targetContent.pack("unrelated"), byteArrayOf(9)) }
        assertFailsWith<IllegalStateException> { database.runSuspend { migration.prepare(it, inventory) } }
        database.run { it.clear(targetContent.pack("unrelated")) }
        database.runSuspend { migration.prepare(it, inventory) }
        database.runSuspend { migration.copyBatch(it, parent) }
        for (path in parent.children) {
            val entry = database.readSuspend { migration.inspect(it, path) }
            var cursor: ByteArray? = null
            do {
                val progress = database.runSuspend { migration.copyBatch(it, entry, cursor, keyLimit = 2) }
                cursor = progress.nextSuffix
            } while (!progress.complete)
            cursor = null
            do {
                val progress = database.readSuspend { migration.verifyBatch(it, entry, cursor, keyLimit = 2) }
                cursor = progress.nextSuffix
            } while (!progress.complete)
        }
        assertTrue(database.readSuspend { migration.verifyBatch(it, parent).complete })
        database.readSuspend { migration.verifyRoots(it) }
        database.runSuspend { migration.prepare(it, inventory) }
        val copied = target.open(database, partitionPath + "child").await()
        assertContentEquals(byteArrayOf(4), database.readSuspend { it.get(copied.pack(4)).await() })
        assertContentEquals(byteArrayOf(4), database.readSuspend { it.get(child.pack(4)).await() })
        source.move(database, partitionPath + "child", partitionPath + "renamed").await()
        assertTrue(source.exists(database, partitionPath + "renamed").await())
        source.remove(database, partitionPath).await()
        assertTrue(!source.exists(database, partitionPath).await())
    }
}
