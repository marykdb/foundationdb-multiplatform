package maryk.foundationdb

import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectoryMoveException
import maryk.foundationdb.directory.create
import maryk.foundationdb.directory.move
import maryk.foundationdb.directory.open
import maryk.foundationdb.directory.exists
import maryk.foundationdb.subspace.Subspace
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DirectoryMoveSafetyTest {
    @Test fun cannotMoveDirectoryIntoItsDescendant() = FoundationDbTestHarness().runAndReset {
        val layer = DirectoryLayer(Subspace(prefixBytes("nodes")), Subspace(prefixBytes("data")))
        val parent = layer.create(database, listOf("parent")).await()
        layer.create(database, listOf("parent", "child")).await()
        database.run { it.set(parent.pack("value"), byteArrayOf(7)) }
        assertFailsWith<DirectoryMoveException> { layer.move(database, listOf("parent"), listOf("parent", "child", "moved")).await() }
        val original = layer.open(database, listOf("parent")).await()
        assertContentEquals(byteArrayOf(7), database.readSuspend { it.get(original.pack("value")).await() })
    }
    @Test fun rootMoveRejectsWithoutStagingMetadata() = FoundationDbTestHarness().runAndReset {
        val layer = DirectoryLayer(Subspace(prefixBytes("root-nodes")), Subspace(prefixBytes("root-data")))
        layer.create(database, listOf("existing")).await()
        val transaction = database.createTransaction()
        try {
            assertFailsWith<DirectoryMoveException> { layer.move(transaction, emptyList(), listOf("moved-root")).await() }
            transaction.commit().await()
        } finally { transaction.close() }
        assertFalse(layer.exists(database, listOf("moved-root")).await())
        layer.open(database, listOf("existing")).await()
    }

}
