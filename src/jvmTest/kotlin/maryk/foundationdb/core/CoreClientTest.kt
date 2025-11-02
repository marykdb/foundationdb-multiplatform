package maryk.foundationdb.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import maryk.foundationdb.FDB
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.LocalityUtil
import maryk.foundationdb.Range
import maryk.foundationdb.StreamingMode
import maryk.foundationdb.asFdbFuture
import maryk.foundationdb.completedFdbFuture
import maryk.foundationdb.collectRange
import maryk.foundationdb.decodeToUtf8
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend

class CoreClientTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun roundTripThroughDatabase() = harness.runAndReset {
        val key = key("core", "roundTrip")
        val expected = "maryk-foundationdb".encodeToByteArray()

        database.runSuspend { txn ->
            txn.set(key, expected)
        }

        val actual = database.readSuspend { rt -> rt.get(key).await() }
        assertNotNull(actual)
        assertContentEquals(expected, actual)
    }

    @Test
    fun transactionOptionsToggleReadYourWrites() = harness.runAndReset {
        val key = key("core", "options")
        val value = "opts".encodeToByteArray()

        val seen = database.runSuspend { txn ->
            txn.options().setReadYourWritesDisable()
            txn.set(key, value)
            txn.get(key).await()
        }
        assertEquals(null, seen)

        val committed = database.readSuspend { rt -> rt.get(key).await() }
        assertEquals("opts", committed?.decodeToUtf8())
    }

    @Test
    fun mutationAddAccumulatesValue() = harness.runAndReset {
        val key = key("core", "counter")
        database.runSuspend { txn -> txn.set(key, 0L.toLittleEndianBytes()) }

        repeat(3) {
            database.runSuspend { txn ->
                txn.mutate(maryk.foundationdb.MutationType.ADD, key, 1L.toLittleEndianBytes())
            }
        }

        val stored = database.readSuspend { rt -> rt.get(key).await() }
        assertEquals(3L, stored?.toLittleEndianLong())
    }

    @Test
    fun collectRangeProducesSummary() = harness.runAndReset {
        val prefix = key("core", "range")
        (0 until 5).forEach { idx ->
            database.runSuspend { txn ->
                txn.set(key("core", "range", "k$idx"), "v$idx".encodeToByteArray())
            }
        }

        val result = database.readSuspend { rt ->
            rt.collectRange(
                range = Range.startsWith(prefix),
                limit = 3,
                reverse = false,
                streamingMode = StreamingMode.WANT_ALL
            ).await()
        }

        assertEquals(3, result.values.size)
        assertEquals(result.values.size, result.summary.keyCount)
        val expectedLastKey = result.values.last().key
        assertTrue(result.summary.lastKey?.contentEquals(expectedLastKey) == true)
    }

    @Test
    fun runAsyncBridgeReturnsResult() = harness.runAndReset {
        val key = key("core", "async")
        val expected = "async-result"

        val future = database.runAsync { txn ->
            txn.set(key, expected.encodeToByteArray())
            completedFdbFuture(expected.length)
        }

        assertEquals(expected.length, future.await())

        val stored = database.readSuspend { rt -> rt.get(key).await() }
        assertEquals(expected, stored?.decodeToUtf8())
    }

    @Test
    fun localityHelpersListBoundaryKeysAndAddresses() = harness.runAndReset {
        val begin = key("core", "locality", "A")
        val end = key("core", "locality", "Z")

        database.runSuspend { txn ->
            (0 until 5).forEach { idx ->
                txn.set(key("core", "locality", "k$idx"), idx.toString().encodeToByteArray())
            }
        }

        val dbKeys = LocalityUtil.getBoundaryKeys(database, begin, end).await()
        assertNotNull(dbKeys)

        val addresses = database.runSuspend { txn ->
            LocalityUtil.getAddressesForKey(txn, key("core", "locality", "k0")).await()
        }
        assertNotNull(addresses)
    }

    @Test
    fun versionstampHelpersRoundTrip() = harness.runAndReset {
        val incomplete = maryk.foundationdb.tuple.Versionstamp.incomplete(42)
        assertTrue(!incomplete.isComplete)
        assertEquals(42, incomplete.userVersion)

        val transactionVersion = ByteArray(10) { it.toByte() }
        val complete = maryk.foundationdb.tuple.Versionstamp.complete(transactionVersion, 7)
        assertTrue(complete.isComplete)
        assertEquals(7, complete.userVersion)
        assertTrue(complete.transactionVersion().contentEquals(transactionVersion))

        val fromBytes = maryk.foundationdb.tuple.Versionstamp.fromBytes(complete.bytes)
        assertTrue(fromBytes.isComplete)
        assertEquals(complete.userVersion, fromBytes.userVersion)
    }

    @Test
    fun networkAndDatabaseOptionsApplyWithoutError() = harness.runAndReset {
        FDB.instance().options().setTraceLogGroup("maryk-tests")

        database.options().apply {
            setSnapshotRywEnable()
            setTransactionRetryLimit(5)
        }

        database.runSuspend { txn ->
            txn.options().apply {
                setReadPriorityHigh()
                setTimeout(5_000)
            }
            txn.set(key("core", "options"), "configured".encodeToByteArray())
        }
    }

    @Test
    fun futureBridgingCoversVoidAndResult() = harness.runAndReset {
        val key = key("core", "future")
        val value = "bridge".encodeToByteArray()

        val txWrite = database.createTransaction()
        txWrite.set(key, value)
        val futureVoid = txWrite.delegate.commit()
        futureVoid.asFdbFuture().await()
        txWrite.close()

        val txRead = database.createTransaction()
        val futureResult = txRead.delegate.get(key)
        val result = futureResult.asFdbFuture().await()
        assertEquals("bridge", result?.decodeToUtf8())
        txRead.close()
    }
}

private fun Long.toLittleEndianBytes(): ByteArray =
    java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(this).array()

private fun ByteArray.toLittleEndianLong(): Long =
    java.nio.ByteBuffer.wrap(this).order(java.nio.ByteOrder.LITTLE_ENDIAN).long
