package maryk.foundationdb

import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectoryLayout
import maryk.foundationdb.directory.DirectoryTreeMigration
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartitionRootMigrationNativeTest {
    private class Fixture(val harness: FoundationDbTestHarness, val layout: DirectoryLayout) {
        val sourceNodes = Subspace(harness.prefixBytes("${layout.name}-source-meta"))
        val sourceData = Subspace(harness.prefixBytes("${layout.name}-source-data"))
        val targetNodes = Subspace(harness.prefixBytes("${layout.name}-target-meta"))
        val targetData = Subspace(harness.prefixBytes("${layout.name}-target-data"))
        val prefix = sourceData.get("allocated-partition").getKey()
        val suffix = Tuple.from("zz-direct-root-data").pack()
        val payload = byteArrayOf(7, 8, 9)
        val source = DirectoryLayer.forLayout(layout, sourceNodes, sourceData, true)
        val target = DirectoryLayer(targetNodes, targetData)
        val migration = DirectoryTreeMigration(
            DirectoryTreeMigration.Configuration(sourceNodes, sourceData, layout),
            DirectoryTreeMigration.Configuration(targetNodes, targetData),
        )
        suspend fun create() {
            if (layout == DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) {
                DirectoryLayer(sourceNodes, sourceData, true).create(harness.database, listOf("p"), prefix = prefix).await()
                harness.database.run {
                    it.set(sourceNodes.get(prefix).pack("layer".encodeToByteArray()), DirectoryLayer.PARTITION_LAYER)
                }
            } else source.create(harness.database, listOf("p"), DirectoryLayer.PARTITION_LAYER, prefix).await()
        }
        fun writeRoot() = harness.database.run { it.set(prefix + suffix, payload) }
        suspend fun entry() = harness.database.readSuspend { migration.inspect(it, listOf("p")) }
        suspend fun assertSourceIntact() {
            assertContentEquals(payload, harness.database.readSuspend { it.get(prefix + suffix).await() })
        }
    }

    @Test
    fun preparationRejectsUnaccountedRootDataForEveryLayout() = FoundationDbTestHarness().runAndReset {
        for (layout in DirectoryLayout.entries) {
            val fixture = Fixture(this, layout)
            fixture.create()
            fixture.writeRoot()
            val entry = fixture.entry()
            val failure = assertFailsWith<IllegalStateException> {
                database.runSuspend { fixture.migration.prepare(it, listOf(entry)) }
            }
            assertTrue(failure.message.orEmpty().contains("/p"))
            assertFalse(fixture.target.exists(database, listOf("p")).await())
            fixture.assertSourceIntact()
        }
    }

    @Test
    fun copyingAndVerificationRecheckRootDataAfterPreparation() = FoundationDbTestHarness().runAndReset {
        val fixture = Fixture(this, DirectoryLayout.LEGACY_NATIVE)
        fixture.create()
        val entry = fixture.entry()
        database.runSuspend { fixture.migration.prepare(it, listOf(entry)) }
        fixture.writeRoot()
        assertFailsWith<IllegalStateException> { database.runSuspend { fixture.migration.copyBatch(it, entry) } }
        assertFalse(fixture.target.exists(database, listOf("p")).await())
        fixture.assertSourceIntact()
        database.run { it.clear(fixture.prefix + fixture.suffix) }
        assertTrue(database.runSuspend { fixture.migration.copyBatch(it, entry).complete })
        assertTrue(database.readSuspend { fixture.migration.verifyBatch(it, entry).complete })
        fixture.writeRoot()
        assertFailsWith<IllegalStateException> { database.readSuspend { fixture.migration.verifyBatch(it, entry) } }
        fixture.assertSourceIntact()
    }

    @Test
    fun knownChildDataUnderAManualPrefixIsCopiedRatherThanRejected() = FoundationDbTestHarness().runAndReset {
        val fixture = Fixture(this, DirectoryLayout.LEGACY_NATIVE)
        fixture.create()
        val child = fixture.source.create(database, listOf("p", "child"),
            prefix = fixture.prefix + Tuple.from("owned-child").pack()).await()
        database.run { transaction -> repeat(256) { transaction.set(child.pack(it), byteArrayOf(it.toByte())) } }
        val parentEntry = fixture.entry()
        val childEntry = database.readSuspend { fixture.migration.inspect(it, listOf("p", "child")) }
        database.runSuspend { fixture.migration.prepare(it, listOf(parentEntry, childEntry)) }
        assertTrue(database.runSuspend { fixture.migration.copyBatch(it, parentEntry).complete })
        assertTrue(database.runSuspend { fixture.migration.copyBatch(it, childEntry, keyLimit = 1000).complete })
        assertTrue(database.readSuspend { fixture.migration.verifyBatch(it, parentEntry).complete })
        assertTrue(database.readSuspend { fixture.migration.verifyBatch(it, childEntry, keyLimit = 1000).complete })
        database.readSuspend { fixture.migration.verifyRoots(it) }
        val copied = fixture.target.open(database, listOf("p", "child")).await()
        repeat(256) { index ->
            assertContentEquals(byteArrayOf(index.toByte()), database.readSuspend { it.get(copied.pack(index)).await() })
        }
        // A child prefix cannot hide an unrelated root key later in the range.
        fixture.writeRoot()
        assertFailsWith<IllegalStateException> { database.readSuspend { fixture.migration.verifyBatch(it, parentEntry) } }
        fixture.assertSourceIntact()
    }
}
