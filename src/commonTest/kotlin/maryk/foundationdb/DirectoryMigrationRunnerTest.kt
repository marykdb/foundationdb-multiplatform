package maryk.foundationdb

import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectoryTreeMigration
import maryk.foundationdb.subspace.Subspace
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DirectoryMigrationRunnerTest {
    @Test
    fun runnerOrdersReviewedEntriesAndReplaysAfterAnAbandonedBatch() = FoundationDbTestHarness().runAndReset {
        val sourceNodes = Subspace(prefixBytes("runner-source-nodes"))
        val sourceData = Subspace(prefixBytes("runner-source-data"))
        val targetNodes = Subspace(prefixBytes("runner-target-nodes"))
        val targetData = Subspace(prefixBytes("runner-target-data"))
        val source = DirectoryLayer(sourceNodes, sourceData)
        val target = DirectoryLayer(targetNodes, targetData)
        source.create(database, listOf("p"), DirectoryLayer.PARTITION_LAYER).await()
        val original = source.create(database, listOf("p", "reports")).await()
        database.run { transaction -> repeat(7) { transaction.set(original.pack(it), byteArrayOf(it.toByte())) } }
        fun migration() = DirectoryTreeMigration(
            DirectoryTreeMigration.Configuration(sourceNodes, sourceData),
            DirectoryTreeMigration.Configuration(targetNodes, targetData),
        )
        suspend fun inventory(migration: DirectoryTreeMigration) = listOf(
            database.readSuspend { migration.inspect(it, listOf("p", "reports")) },
            database.readSuspend { migration.inspect(it, listOf("p")) },
        )
        val migration = migration()
        val reviewed = inventory(migration)
        database.runSuspend { migration.prepare(it, reviewed) }
        database.runSuspend { migration.copyBatch(it, reviewed.last()) }
        database.runSuspend { migration.copyBatch(it, reviewed.first(), keyLimit = 2) }
        val abandoned = database.createTransaction()
        try { migration.copyBatch(abandoned, reviewed.first(), keyLimit = 3) }
        finally { abandoned.close() }
        // A new runner knows neither whether the previous attempt completed nor its cursor.
        val restarted = migration()
        restarted.copyAndVerify(database, inventory(restarted), keyLimit = 2)
        val copied = target.open(database, listOf("p", "reports")).await()
        repeat(7) { index ->
            assertContentEquals(byteArrayOf(index.toByte()), database.readSuspend { it.get(copied.pack(index)).await() })
            assertContentEquals(byteArrayOf(index.toByte()), database.readSuspend { it.get(original.pack(index)).await() })
        }
        restarted.copyAndVerify(database, inventory(restarted), keyLimit = 2)
        // A collision is an error, not an instruction to overwrite destination data.
        database.run { it.set(copied.pack(3), byteArrayOf(99)) }
        assertFailsWith<IllegalStateException> { restarted.copyAndVerify(database, inventory(restarted), keyLimit = 2) }
        assertContentEquals(byteArrayOf(99), database.readSuspend { it.get(copied.pack(3)).await() })
        assertContentEquals(byteArrayOf(3), database.readSuspend { it.get(original.pack(3)).await() })
    }

    @Test
    fun runnerRejectsIncompleteInventoryAndInvalidLimitsBeforeCopying() = FoundationDbTestHarness().runAndReset {
        val sourceNodes = Subspace(prefixBytes("invalid-source-nodes"))
        val sourceData = Subspace(prefixBytes("invalid-source-data"))
        val targetNodes = Subspace(prefixBytes("invalid-target-nodes"))
        val targetData = Subspace(prefixBytes("invalid-target-data"))
        val source = DirectoryLayer(sourceNodes, sourceData)
        val target = DirectoryLayer(targetNodes, targetData)
        source.create(database, listOf("records")).await()
        val migration = DirectoryTreeMigration(
            DirectoryTreeMigration.Configuration(sourceNodes, sourceData),
            DirectoryTreeMigration.Configuration(targetNodes, targetData),
        )
        val entry = database.readSuspend { migration.inspect(it, listOf("records")) }
        assertFailsWith<IllegalArgumentException> { migration.copyAndVerify(database, listOf(entry), keyLimit = 0) }
        assertFailsWith<IllegalStateException> { migration.copyAndVerify(database, emptyList()) }
        assertFalse(target.exists(database, listOf("records")).await())
        assertEquals(0, database.readSuspend { it.getRange(Range.startsWith(targetNodes.getKey())).asList().await().size })
    }
}
