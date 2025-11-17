package maryk.foundationdb.directory

import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend
import maryk.foundationdb.testDirectoryOrNull
import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertTrue

class DirectoryLayerJvmTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun layerAndSubspaceOverloads() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val directory = layer.testDirectoryOrNull() ?: return@runAndReset
        val basePath = listOf(namespace, "jvm-layer", "root")

        val created = database.runSuspend { txn ->
            layer.createOrOpen(txn, basePath).await()
        }
        val txnOpened = database.runSuspend { txn ->
            layer.open(txn, basePath).await()
        }
        val readOpened = database.readSuspend { rt ->
            layer.open(rt, basePath).await()
        }
        assertTrue(created.pack().contentEquals(txnOpened.pack()))
        assertTrue(txnOpened.pack().contentEquals(readOpened.pack()))

        val child = database.runSuspend { txn ->
            created.createOrOpen(txn, listOf("child")).await()
        }
        val reopenedChild = database.runSuspend { txn ->
            created.open(txn, listOf("child")).await()
        }
        assertTrue(child.pack().contentEquals(reopenedChild.pack()))

        val tupleKey = child.pack(Tuple.from("item"))
        assertTrue(child.pack().isNotEmpty())
        assertTrue(tupleKey.startsWith(child.pack()))

        database.runSuspend { txn ->
            directory.removeIfExists(txn, basePath).await()
        }
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    this.size >= prefix.size && this.sliceArray(0 until prefix.size).contentEquals(prefix)
