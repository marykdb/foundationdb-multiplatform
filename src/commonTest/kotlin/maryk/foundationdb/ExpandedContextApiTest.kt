package maryk.foundationdb

import maryk.foundationdb.directory.asDirectory
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.create
import maryk.foundationdb.directory.exists
import maryk.foundationdb.directory.list
import maryk.foundationdb.directory.move
import maryk.foundationdb.directory.remove
import maryk.foundationdb.subspace.Subspace
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExpandedContextApiTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun directTransactionsRetainTheirReplacementAfterRetry() = harness.runAndReset {
        val key = key("direct-retry")
        val transaction = database.createTransaction()
        try {
            transaction.options().getOptionConsumer().setOption(500, ByteArrayUtil.encodeInt(5_000))
            var attempts = 0
            transaction.run {
                if (++attempts == 1) throw FDBException("injected conflict", 1020)
                it.set(key, byteArrayOf(1))
            }
            assertEquals(2, attempts)
            assertContentEquals(byteArrayOf(1), database.readSuspend { it.get(key).await() })
            val retryable: Throwable = FDBException("retry again", 1020)
            assertSame(transaction, transaction.onError(retryable).await())
            transaction.set(key, byteArrayOf(2))
            transaction.commit().await()
            assertContentEquals(byteArrayOf(2), database.readSuspend { it.get(key).await() })
        } finally { transaction.close() }
    }

    @Test
    fun directoryViewsKeepCustomMetadataAndContentContexts() = harness.runAndReset {
        val layer = DirectoryLayer(Subspace(prefixBytes("meta")), Subspace(prefixBytes("content")))
        val path = listOf(namespace, "root")
        val root = layer.create(database, path).await()
        root.asDirectory().create(database, listOf("child")).await()
        assertEquals(listOf("child"), layer.list(database, path).await())
        assertTrue(root.asDirectory().getDirectoryLayer().exists(database, path).await())
        layer.move(database, path + "child", path + "renamed").await()
        assertEquals(listOf("renamed"), layer.list(database, path).await())
        layer.remove(database, path).await()
        assertFalse(layer.exists(database, path).await())
    }
    @Test
    fun customContextsAreAcceptedByDirectoryOperations() = harness.runAndReset {
        var writes = 0
        var reads = 0
        val context = object : TransactionContext {
            override fun <T> run(block: (Transaction) -> T): T = database.run(block)
            override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> { writes++; return database.runAsync(block) }
            override fun <T> read(block: (ReadTransaction) -> T): T = database.read(block)
            override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> { reads++; return database.readAsync(block) }
        }
        val path = listOf(namespace, "custom-context")
        val layer = DirectoryLayer.getDefault()
        layer.create(context, path).await()
        assertTrue(layer.exists(context, path).await())
        assertTrue(writes > 0)
        assertTrue(reads > 0)
        layer.remove(context, listOf(namespace)).await()
    }

}
