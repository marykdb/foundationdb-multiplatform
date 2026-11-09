package maryk.foundationdb

import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectoryTreeMigration
import maryk.foundationdb.subspace.Subspace
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationRestartTest {
    @Test fun abandonedAndConflictedBatchesCanRestartWithLostOrSavedCursor() = FoundationDbTestHarness().runAndReset {
        val sourceNodes = Subspace(prefixBytes("restart-source-meta"))
        val sourceData = Subspace(prefixBytes("restart-source-data"))
        val targetNodes = Subspace(prefixBytes("restart-target-meta"))
        val targetData = Subspace(prefixBytes("restart-target-data"))
        val source = DirectoryLayer(sourceNodes, sourceData)
        val target = DirectoryLayer(targetNodes, targetData)
        val path = listOf("records")
        val original = source.create(database, path).await()
        database.run { transaction -> repeat(7) { transaction.set(original.pack(it), byteArrayOf(it.toByte())) } }
        fun migration() = DirectoryTreeMigration(
            DirectoryTreeMigration.Configuration(sourceNodes, sourceData),
            DirectoryTreeMigration.Configuration(targetNodes, targetData),
        )
        val first = migration()
        val entry = database.readSuspend { first.inspect(it, path) }
        database.runSuspend { first.prepare(it, listOf(entry)) }
        // A process can disappear after writes were staged but before commit.
        val abandoned = database.createTransaction()
        try { assertEquals(2, first.copyBatch(abandoned, entry, keyLimit = 2).copiedKeys) }
        finally { abandoned.close() }
        assertFalse(target.exists(database, path).await())
        // A real FDB conflict must not publish partial metadata/data or advance a cursor.
        val fence = key("restart-fence")
        database.run { it.set(fence, byteArrayOf(0)) }
        val conflicted = database.createTransaction()
        try {
            conflicted.get(fence).await()
            first.copyBatch(conflicted, entry, keyLimit = 2)
            database.run { it.set(fence, byteArrayOf(1)) }
            assertEquals(1020, assertFailsWith<FDBException> { conflicted.commit().await() }.getCode())
        } finally { conflicted.close() }
        assertFalse(target.exists(database, path).await())
        val committed = database.runSuspend { first.copyBatch(it, entry, keyLimit = 2) }
        assertFalse(committed.complete)
        // Recreate both the connection and migration object, as a new process would.
        val restartedDatabase = FDB.instance().open()
        try {
            val restarted = migration()
            val reviewed = restartedDatabase.readSuspend { restarted.inspect(it, path) }
            restartedDatabase.runSuspend { restarted.prepare(it, listOf(reviewed)) }
            // Commit succeeded but cursor persistence was lost: replay is idempotent.
            val replay = restartedDatabase.runSuspend { restarted.copyBatch(it, reviewed, keyLimit = 2) }
            assertContentEquals(committed.nextSuffix, replay.nextSuffix)
            var cursor = replay.nextSuffix
            do {
                val progress = restartedDatabase.runSuspend { restarted.copyBatch(it, reviewed, cursor, keyLimit = 2) }
                cursor = progress.nextSuffix
            } while (!progress.complete)
            var checked = 0
            cursor = null
            do {
                val progress = restartedDatabase.readSuspend { restarted.verifyBatch(it, reviewed, cursor, keyLimit = 2) }
                checked += progress.copiedKeys
                cursor = progress.nextSuffix
            } while (!progress.complete)
            assertEquals(7, checked)
            restartedDatabase.readSuspend { restarted.verifyRoots(it) }
            val copied = target.open(restartedDatabase, path).await()
            repeat(7) { index ->
                assertContentEquals(byteArrayOf(index.toByte()), restartedDatabase.readSuspend { it.get(copied.pack(index)).await() })
                assertContentEquals(byteArrayOf(index.toByte()), restartedDatabase.readSuspend { it.get(original.pack(index)).await() })
            }
            assertTrue(restartedDatabase.readSuspend { restarted.verifyBatch(it, reviewed).complete })
        } finally { restartedDatabase.close() }
    }
}
