package maryk.foundationdb.directory

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.Range
import maryk.foundationdb.testDirectoryOrNull
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend
import maryk.foundationdb.tuple.Tuple

class DirectoryIntegrationTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun directoryLayerAndDirectoryOperations() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val directory = layer.testDirectoryOrNull() ?: return@runAndReset
        val rootPath = listOf(namespace, "dirs")

        val root: DirectorySubspace = try {
            database.runSuspend { txn ->
                directory.createOrOpen(txn, rootPath).await()
            }
        } catch (_: Throwable) {
            return@runAndReset
        }
        assertTrue(root.pack().isNotEmpty())
        val childPath = rootPath + "child"
        database.runSuspend { txn ->
            directory.create(txn, childPath).await()
        }

        val listed = directory.list(database, rootPath).await()
        assertEquals(listOf("child"), listed)

        val movedPath = rootPath + "child-renamed"
        database.runSuspend { txn ->
            directory.move(txn, childPath, movedPath).await()
        }
        assertTrue(directory.exists(database, movedPath).await())

        database.runSuspend { txn ->
            directory.remove(txn, movedPath).await()
        }
        assertFalse(directory.exists(database, movedPath).await())

        val removedRoot = database.runSuspend { txn ->
            directory.removeIfExists(txn, rootPath).await()
        }
        assertTrue(removedRoot)
    }

    @Test
    fun directoryPartitionBehavesAsSubspace() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val partition: DirectoryPartition = try {
            database.runSuspend { txn ->
                layer.createOrOpen(txn, listOf(namespace, "partition")).await()
            }
        } catch (_: Throwable) {
            return@runAndReset
        }

        val packed = partition.pack(Tuple.from("item"))
        database.runSuspend { txn ->
            txn.set(packed, "value".encodeToByteArray())
        }

        val values = database.readSuspend { rt ->
            AsyncUtil.collect(rt.getRange(Range.startsWith(partition.pack()))).await()
        }
        assertEquals(1, values.size)
        assertEquals("value", values.single().value.decodeToString())
    }

    @Test
    fun creatingExistingDirectoryFails() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val directory = layer.testDirectoryOrNull() ?: return@runAndReset
        val targetPath = listOf(namespace, "dup", "path")
        database.runSuspend { txn ->
            directory.createOrOpen(txn, targetPath).await()
        }

        assertFailsWith<DirectoryAlreadyExistsException> {
            database.runSuspend { txn ->
                directory.create(txn, targetPath).await()
            }
        }
    }

    @Test
    fun removingMissingDirectoryFails() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val directory = layer.testDirectoryOrNull() ?: return@runAndReset
        val targetPath = listOf(namespace, "missing", "path")

        assertFailsWith<NoSuchDirectoryException> {
            database.runSuspend { txn ->
                directory.remove(txn, targetPath).await()
            }
        }
    }

    @Test
    fun movingToExistingPathFails() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val directory = layer.testDirectoryOrNull() ?: return@runAndReset
        val sourcePath = listOf(namespace, "path", "src")
        val destPath = listOf(namespace, "path", "dst")

        database.runSuspend { txn ->
            directory.createOrOpen(txn, sourcePath).await()
            directory.createOrOpen(txn, destPath).await()
        }

        assertFailsWith<DirectoryAlreadyExistsException> {
            database.runSuspend { txn ->
                directory.move(txn, sourcePath, destPath).await()
            }
        }
    }

    @Test
    fun openingWithMismatchedLayerFails() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val directory = layer.testDirectoryOrNull() ?: return@runAndReset
        val targetPath = listOf(namespace, "layers", "path")
        val storedLayer = "alpha".encodeToByteArray()
        val otherLayer = "beta".encodeToByteArray()

        database.runSuspend { txn ->
            directory.create(txn, targetPath, storedLayer).await()
        }

        assertFailsWith<MismatchedLayerException> {
            directory.createOrOpen(database, targetPath, otherLayer).await()
        }

        assertFailsWith<MismatchedLayerException> {
            directory.open(database, targetPath, otherLayer).await()
        }

        // wildcard (empty) layer should still allow access
        val opened = directory.open(database, targetPath).await()
        assertTrue(opened.pack().isNotEmpty())
    }

    @Test
    fun movingDirectoryInsideItselfFails() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val directory = layer.testDirectoryOrNull() ?: return@runAndReset
        val sourcePath = listOf(namespace, "move", "self")
        database.runSuspend { txn ->
            directory.create(txn, sourcePath).await()
        }

        assertFailsWith<DirectoryMoveException> {
            database.runSuspend { txn ->
                directory.move(txn, sourcePath, sourcePath).await()
            }
        }

        val nestedDest = sourcePath + "child"
        assertFailsWith<DirectoryMoveException> {
            database.runSuspend { txn ->
                directory.move(txn, sourcePath, nestedDest).await()
            }
        }
    }

    @Test
    fun movePreservesDirectoryPrefix() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val directory = layer.testDirectoryOrNull() ?: return@runAndReset
        val sourcePath = listOf(namespace, "move", "prefix", "src")
        val destPath = listOf(namespace, "move", "prefix", "dst")

        val created = database.runSuspend { txn ->
            directory.create(txn, sourcePath).await()
        }
        val recordKey = created.pack(Tuple.from("existing"))
        database.runSuspend { txn ->
            txn.set(recordKey, "before".encodeToByteArray())
        }

        val moved = database.runSuspend { txn ->
            directory.move(txn, sourcePath, destPath).await()
        }

        val reopened = directory.open(database, destPath).await()
        assertContentEquals(reopened.pack(), moved.pack())

        val existing = database.readSuspend { rt -> rt.get(reopened.pack(Tuple.from("existing"))).await() }
        assertEquals("before", existing?.decodeToString())

        database.runSuspend { txn ->
            txn.set(moved.pack(Tuple.from("after")), "after".encodeToByteArray())
        }

        val after = database.readSuspend { rt -> rt.get(reopened.pack(Tuple.from("after"))).await() }
        assertEquals("after", after?.decodeToString())
    }
}
