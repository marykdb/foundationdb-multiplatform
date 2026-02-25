package maryk.foundationdb.directory

import kotlin.test.Test
import kotlin.test.assertFailsWith
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.runSuspend
import maryk.foundationdb.testDirectoryOrNull

class DirectorySubspaceNativeTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun subspaceOpenMissingChildThrowsNoSuchDirectory() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val directory = layer.testDirectoryOrNull() ?: return@runAndReset
        val basePath = listOf(namespace, "missing-child", "root")
        val parent = database.runSuspend { txn ->
            directory.createOrOpen(txn, basePath).await()
        }

        assertFailsWith<NoSuchDirectoryException> {
            database.runSuspend { txn ->
                parent.open(txn, listOf("does-not-exist")).await()
            }
        }
    }
}
