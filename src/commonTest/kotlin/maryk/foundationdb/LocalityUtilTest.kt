package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import maryk.foundationdb.async.CloseableAsyncIterator
import maryk.foundationdb.tuple.Tuple

class LocalityUtilTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun boundaryKeysAndAddresses() = harness.runAndReset {
        val beginPrefix = Tuple.from(namespace, "locality-util").pack()
        val endKey = Tuple.from(namespace, "locality-util", Int.MAX_VALUE).pack()
        val sampleKeys = (0 until 5).map { index -> Tuple.from(namespace, "locality-util", index).pack() }

        database.runSuspend { txn ->
            txn.clear(Range.startsWith(beginPrefix))
            sampleKeys.forEachIndexed { idx, key ->
                txn.set(key, "value-$idx".encodeToByteArray())
            }
        }

        val dbBoundaryKeys = LocalityUtil.getBoundaryKeys(database, beginPrefix, endKey).await()
        val iteratorKeys = LocalityUtil.openBoundaryKeysIterator(database, beginPrefix, endKey).collectAll()
        assertEquals(dbBoundaryKeys, iteratorKeys)

        database.runSuspend { txn ->
            val txnBoundaries = LocalityUtil.getBoundaryKeys(txn, beginPrefix, endKey).await()
            assertEquals(dbBoundaryKeys, txnBoundaries)

            val txnIteratorKeys = LocalityUtil.openBoundaryKeysIterator(txn, beginPrefix, endKey).collectAll()
            assertEquals(dbBoundaryKeys, txnIteratorKeys)

            val addresses = LocalityUtil.getAddressesForKey(txn, sampleKeys.first()).await()
            assertTrue(addresses.isNotEmpty(), "expected storage addresses for populated keyspace")
        }
    }

    private suspend fun CloseableAsyncIterator<ByteArray>.collectAll(): List<ByteArray> {
        val collected = mutableListOf<ByteArray>()
        try {
            while (hasNext()) {
                collected += next()
            }
        } finally {
            close()
        }
        return collected
    }
}
