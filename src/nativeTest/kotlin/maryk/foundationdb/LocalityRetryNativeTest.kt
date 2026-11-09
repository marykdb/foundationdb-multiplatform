package maryk.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalityRetryNativeTest {
    @Test fun retryResumesAfterLastReturnedBoundaryAndRestoresOptions() = FoundationDbTestHarness().runAndReset {
        val transaction = database.createTransaction()
        val prefix = byteArrayOf(-1) + "/keyServers/".encodeToByteArray()
        val boundaries = listOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(0, 0), byteArrayOf(1))
        var fetched = 0
        var closed = 0
        val iterator = boundaryKeyIterator(transaction, byteArrayOf(), byteArrayOf(-1), { closed++; transaction.close() }) { begin, _, _ ->
            fdbFuture {
                fetched++
                // Exercise a real system read before and after onError resets options.
                transaction.get(prefix).await()
                if (fetched == 2) throw FDBException("injected conflict", 1020)
                val selected = boundaries.filter { ByteArrayUtil.compareUnsigned(prefix + it, begin.key) > 0 || (!begin.orEqual && (prefix + it).contentEquals(begin.key)) }
                val value = selected.firstOrNull()
                if (value == null) RangePage(emptyList(), null, false)
                else RangePage(listOf(KeyValue(prefix + value, byteArrayOf())), prefix + value, selected.size > 1)
            }
        }
        try {
            val result = mutableListOf<List<Byte>>()
            while (iterator.onHasNext().await()) result += iterator.next().toList()
            assertEquals(boundaries.map { it.toList() }, result)
            assertEquals(5, fetched)
        } finally { iterator.close() }
        assertEquals(1, closed)
    }

    @Test fun nonRetryableFailuresCloseTheOwnedTransaction() = FoundationDbTestHarness().runAndReset {
        val transaction = database.createTransaction()
        var closed = 0
        val iterator = boundaryKeyIterator(transaction, byteArrayOf(), byteArrayOf(-1), { closed++; transaction.close() }) { _, _, _ ->
            failedFdbFuture(FDBException("injected invalid operation", 2000))
        }
        try { assertEquals(2000, assertFailsWith<FDBException> { iterator.onHasNext().await() }.getCode()) }
        finally { iterator.close() }
        assertEquals(1, closed)
    }

    @Test fun readinessObserversShareOnePageRequest() = FoundationDbTestHarness().runAndReset {
        val transaction = database.createTransaction()
        val requested = CompletableDeferred<Unit>()
        val page = CompletableDeferred<RangePage>()
        var fetched = 0
        val iterator = boundaryKeyIterator(transaction, byteArrayOf(), byteArrayOf(-1), transaction::close) { _, _, _ ->
            fetched++; requested.complete(Unit); page.asFdbFuture(FutureCancellation.PROPAGATE)
        }
        try {
            val first = iterator.onHasNext()
            val second = iterator.onHasNext()
            requested.await()
            val key = byteArrayOf(-1) + "/keyServers/".encodeToByteArray()
            page.complete(RangePage(listOf(KeyValue(key, byteArrayOf())), key, false))
            assertTrue(first.await())
            assertTrue(second.await())
            assertEquals(1, fetched)
        } finally { iterator.close() }
    }
}
