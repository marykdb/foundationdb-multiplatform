package maryk.foundationdb

import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectoryLayout
import maryk.foundationdb.directory.DirectoryTreeMigration
import maryk.foundationdb.directory.DirectoryMigrationRequiredException
import maryk.foundationdb.subspace.Subspace
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanonicalNativeMigrationTest {
    private class Fixture(val harness: FoundationDbTestHarness, val layout: DirectoryLayout) {
        val nodes = Subspace(harness.prefixBytes("${layout.name}-nodes"))
        val content = Subspace(harness.prefixBytes("${layout.name}-content"))
        val targetNodes = Subspace(harness.prefixBytes("${layout.name}-target-nodes"))
        val targetContent = Subspace(harness.prefixBytes("${layout.name}-target-content"))
        val canonical = DirectoryLayer(nodes, content)
        val target = DirectoryLayer(targetNodes, targetContent)
        val partitionPath = listOf("partition")
        val childPath = partitionPath + "child"
        lateinit var oldKey: ByteArray

        suspend fun create() {
            val writer = if (layout == DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) canonical
                else DirectoryLayer.forLayout(layout, nodes, content)
            val parent = writer.create(harness.database, partitionPath,
                if (layout == DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) byteArrayOf() else DirectoryLayer.PARTITION_LAYER).await()
            val child = writer.create(harness.database, childPath).await()
            oldKey = child.pack("record")
            harness.database.run { transaction ->
                transaction.set(oldKey, byteArrayOf(1, 2, 3))
                if (layout == DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) {
                    transaction.set(nodes.get(parent.getKey()).pack("layer".encodeToByteArray()), DirectoryLayer.PARTITION_LAYER)
                }
            }
        }

        fun migration() = DirectoryTreeMigration(
            DirectoryTreeMigration.Configuration(nodes, content, layout),
            DirectoryTreeMigration.Configuration(targetNodes, targetContent),
        )
    }

    @Test
    fun canonicalAccessRejectsLegacyPartitionsIncludingExistenceChecks() = FoundationDbTestHarness().runAndReset {
        for (layout in listOf(DirectoryLayout.LEGACY_NATIVE, DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS)) {
            val fixture = Fixture(this, layout)
            fixture.create()
            val error = assertFailsWith<DirectoryMigrationRequiredException> { fixture.canonical.exists(database, fixture.partitionPath).await() }
            assertEquals(fixture.partitionPath, error.path)
            assertEquals(layout, error.detectedLayout)
            assertFailsWith<DirectoryMigrationRequiredException> { fixture.canonical.open(database, fixture.partitionPath).await() }
            assertFailsWith<DirectoryMigrationRequiredException> { fixture.canonical.createOrOpen(database, fixture.childPath).await() }
            assertFailsWith<DirectoryMigrationRequiredException> { fixture.canonical.list(database, fixture.partitionPath).await() }
            assertFailsWith<DirectoryMigrationRequiredException> { fixture.canonical.create(database, fixture.partitionPath + "new").await() }
            assertFailsWith<DirectoryMigrationRequiredException> { fixture.canonical.remove(database, fixture.partitionPath).await() }
            assertFailsWith<DirectoryMigrationRequiredException> { fixture.canonical.move(database, fixture.partitionPath, listOf("moved")).await() }
            assertContentEquals(byteArrayOf(1, 2, 3), database.readSuspend { it.get(fixture.oldKey).await() })
        }
    }

    @Test
    fun offlineRunnerMigratesBothLegacyLayoutsAndReplaysCommittedCopies() = FoundationDbTestHarness().runAndReset {
        for (layout in listOf(DirectoryLayout.LEGACY_NATIVE, DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS)) {
            val fixture = Fixture(this, layout)
            fixture.create()
            suspend fun inventory(migration: DirectoryTreeMigration): List<DirectoryTreeMigration.Entry> {
                val parent = database.readSuspend { migration.inspect(it, fixture.partitionPath) }
                return listOf(parent) + parent.children.map { path -> database.readSuspend { migration.inspect(it, path) } }
            }
            val migration = fixture.migration()
            val reviewed = inventory(migration)
            database.runSuspend { migration.prepare(it, reviewed) }
            database.runSuspend { migration.copyBatch(it, reviewed.first(), keyLimit = 1) }
            migration.copyAndVerify(database, reviewed, keyLimit = 1)
            // Recreate the runner/inventory after an uncertain completion; replay is safe.
            val restarted = fixture.migration()
            restarted.copyAndVerify(database, inventory(restarted), keyLimit = 1)
            val copied = fixture.target.open(database, fixture.childPath).await()
            assertContentEquals(byteArrayOf(1, 2, 3), database.readSuspend { it.get(copied.pack("record")).await() })
            assertContentEquals(byteArrayOf(1, 2, 3), database.readSuspend { it.get(fixture.oldKey).await() })
            assertEquals(listOf("child"), fixture.target.list(database, fixture.partitionPath).await())
            // Cutover uses ordinary canonical APIs, including writes.
            fixture.target.create(database, fixture.partitionPath + "new").await()
            // The old source cannot silently become the application's canonical tree.
            assertFailsWith<DirectoryMigrationRequiredException> { fixture.canonical.open(database, fixture.childPath).await() }
        }
    }
}
