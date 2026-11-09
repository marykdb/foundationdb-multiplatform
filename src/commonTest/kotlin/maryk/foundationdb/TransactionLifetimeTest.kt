package maryk.foundationdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionLifetimeTest {
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
    fun transactionCloseHandlesOutstandingFuture() {
        runBlocking {
            FDB.selectAPIVersion(ApiVersion.LATEST)
            val database = FDB.instance().open()
            val transaction = database.createTransaction()
            val future = transaction.get("lifetime-close-outstanding".encodeToByteArray())

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
    fun databaseCloseAfterAwaitedFuturesIsStable() {
        runBlocking {
            FDB.selectAPIVersion(ApiVersion.LATEST)
            repeat(100) { index ->
                val database = FDB.instance().open()
                val transaction = database.createTransaction()
                try {
                    assertEquals(null, transaction.get("lifetime-awaited-close-$index".encodeToByteArray()).await())
                } finally {
                    transaction.close()
                    database.close()
                }
            }
        }
    }

    @Test
    fun databaseCloseAfterCancelledWatchIsStable() {
        FDB.selectAPIVersion(ApiVersion.LATEST)
        repeat(100) { index ->
            val database = FDB.instance().open()
            val transaction = database.createTransaction()
            try {
                transaction.watch("lifetime-watch-close-$index".encodeToByteArray()).cancel()
            } finally {
                transaction.close()
                database.close()
            }
        }
    }

    @Test
    fun rangeExtractionHandlesEmptyValues() {
        runBlocking {
            FDB.selectAPIVersion(ApiVersion.LATEST)
            val database = FDB.instance().open()
            val key = "lifetime-empty-range-value".encodeToByteArray()
            val range = Range.startsWith(key)

            try {
                database.run { txn ->
                    txn.clear(range)
                    txn.set(key, ByteArray(0))
                }

                val values = database.readSuspend { rt ->
                    rt.getRange(range).asList().await()
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

}
