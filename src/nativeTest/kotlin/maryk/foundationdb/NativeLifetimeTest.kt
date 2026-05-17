package maryk.foundationdb

import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.async.CloseableAsyncIterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeLifetimeTest {
    @Test
    fun databaseRejectsUseAfterClose() {
        FDB.selectAPIVersion(ApiVersion.LATEST)
        val database = FDB.instance().open()
        val options = database.options()

        database.close()

        assertFailsWith<IllegalStateException> {
            database.createTransaction()
        }
        assertFailsWith<IllegalStateException> {
            options.setSnapshotRywEnable()
        }
    }

    @Test
    fun transactionSnapshotOptionsAndLazyRangeRejectUseAfterClose() {
        runBlocking {
            FDB.selectAPIVersion(ApiVersion.LATEST)
            val database = FDB.instance().open()
            val transaction = database.createTransaction()
            val snapshot = transaction.snapshot()
            val options = transaction.options()
            val range = transaction.getRange(byteArrayOf(), byteArrayOf(0xFF.toByte()), limit = 1)

            transaction.close()
            try {
                assertFailsWith<IllegalStateException> {
                    transaction.getReadVersion()
                }
                assertFailsWith<IllegalStateException> {
                    snapshot.getReadVersion()
                }
                assertFailsWith<IllegalStateException> {
                    options.setReadYourWritesDisable()
                }
                assertFailsWith<IllegalStateException> {
                    range.asList().await()
                }
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun closeableIteratorCancelRunsCloseActionOnce() {
        var closeCalls = 0
        var delegateCancelCalls = 0
        val delegate = object : AsyncIterator<String>() {
            override fun cancel() {
                delegateCancelCalls++
            }
        }
        val iterator = CloseableAsyncIterator(delegate) {
            closeCalls++
        }

        iterator.cancel()
        iterator.cancel()
        iterator.close()

        assertEquals(1, closeCalls)
        assertEquals(1, delegateCancelCalls)
    }

    @Test
    fun transactionCloseHandlesOutstandingNativeFuture() {
        runBlocking {
            FDB.selectAPIVersion(ApiVersion.LATEST)
            val database = FDB.instance().open()
            val transaction = database.createTransaction()
            val future = transaction.get("native-close-outstanding".encodeToByteArray())

            try {
                val close = async(Dispatchers.Default) {
                    transaction.close()
                }
                withTimeout(5_000) {
                    close.await()
                }
                runCatching {
                    future.await()
                }
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun rangeExtractionHandlesEmptyValues() {
        runBlocking {
            FDB.selectAPIVersion(ApiVersion.LATEST)
            val database = FDB.instance().open()
            val key = "native-empty-range-value".encodeToByteArray()
            val range = Range.startsWith(key)

            try {
                database.run { txn ->
                    txn.clear(range)
                    txn.set(key, ByteArray(0))
                }

                val values = database.read { rt ->
                    rt.getRange(range).asList().awaitBlocking()
                }

                assertEquals(1, values.size)
                assertContentEquals(key, values.single().key)
                assertContentEquals(ByteArray(0), values.single().value)
            } finally {
                database.run { txn ->
                    txn.clear(range)
                }
                database.close()
            }
        }
    }

    @Test
    fun asyncCollectCancelsIteratorOnFailure() {
        runBlocking {
            var cancelCalls = 0
            val iterator = object : AsyncIterator<String>() {
                override fun onHasNext(): FdbFuture<Boolean> =
                    failedFdbFuture(IllegalStateException("hasNext failed"))

                override fun cancel() {
                    cancelCalls++
                }
            }

            assertFailsWith<IllegalStateException> {
                AsyncUtil.collectRemaining(iterator).await()
            }

            assertEquals(1, cancelCalls)
        }
    }

    @Test
    fun asyncForEachCancelsIteratorOnConsumerFailure() {
        runBlocking {
            var cancelCalls = 0
            var emitted = false
            val iterator = object : AsyncIterator<String>() {
                override fun onHasNext(): FdbFuture<Boolean> =
                    completedFdbFuture(!emitted)

                override suspend fun next(): String {
                    emitted = true
                    return "value"
                }

                override fun cancel() {
                    cancelCalls++
                }
            }

            assertFailsWith<IllegalStateException> {
                AsyncUtil.forEachRemaining(iterator) {
                    throw IllegalStateException("consumer failed")
                }.await()
            }

            assertEquals(1, cancelCalls)
        }
    }
}
