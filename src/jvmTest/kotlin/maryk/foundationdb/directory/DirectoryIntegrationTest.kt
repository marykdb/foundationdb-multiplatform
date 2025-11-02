package maryk.foundationdb.directory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.Range
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.decodeToUtf8
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend
import maryk.foundationdb.tuple.Tuple

class DirectoryIntegrationTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun directoryLayerAndDirectoryOperations() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val rootPath = listOf(namespace, "dirs")

        val root = database.runSuspend { txn ->
            layer.createOrOpen(txn, rootPath).await()
        }
        assertTrue(root.pack().isNotEmpty())

        val directory = Directory(layer.delegate)
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
        val partition = database.runSuspend { txn ->
            layer.createOrOpen(txn, listOf(namespace, "partition")).await()
        } as DirectoryPartition

        val packed = partition.pack(Tuple.from("item"))
        database.runSuspend { txn ->
            txn.set(packed, "value".encodeToByteArray())
        }

        val values = database.readSuspend { rt ->
            AsyncUtil.collect(rt.getRange(Range.startsWith(partition.pack()))).await()
        }
        assertEquals(1, values.size)
        assertEquals("value", values.single().value.decodeToUtf8())
    }
}
