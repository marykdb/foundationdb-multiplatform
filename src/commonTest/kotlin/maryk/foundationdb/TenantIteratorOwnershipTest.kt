package maryk.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.async.asFlow
import maryk.foundationdb.async.asyncIterable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TenantIteratorOwnershipTest {
    private class Source : AsyncIterator<KeyValue> {
        var rows = 0
        var cancels = 0
        var checks = 0
        var available: () -> FdbFuture<Boolean> = { completedFdbFuture(rows < 1000) }
        override fun hasNext() = rows < 1000
        override suspend fun next(): KeyValue { rows++; return KeyValue(TENANT_MANAGEMENT_PREFIX + byteArrayOf(rows.toByte()), byteArrayOf(7)) }
        override fun onHasNext(): FdbFuture<Boolean> { checks++; return available() }
        override fun cancel() { cancels++ }
    }

    @Test fun earlyFlowCompletionConsumesOnlyOneRowAndClosesExactlyOnce() = runBlocking {
        val source = Source()
        var closed = 0
        var opened = 0
        val flow = asyncIterable { opened++; TenantListingIterator(source) { closed++ } }.asFlow()
        assertEquals(0, opened)
        val first = flow.take(1).toList().single()
        assertContentEquals(byteArrayOf(1), first.key)
        assertEquals(1, source.rows)
        assertEquals(1, closed)
        assertEquals(1, source.cancels)
    }

    @Test fun closeExhaustionAndFailuresReleaseOwnership() = runBlocking {
        val source = Source()
        var closed = 0
        val iterator = TenantListingIterator(source) { closed++ }
        iterator.close(); iterator.cancel(); iterator.close()
        assertFalse(iterator.hasNext())
        assertFalse(iterator.onHasNext().await())
        assertEquals(0, source.checks)
        assertEquals(1, closed)
        assertEquals(1, source.cancels)
        val empty = Source().apply { available = { completedFdbFuture(false) } }
        var emptyClosed = 0
        assertFalse(TenantListingIterator(empty) { emptyClosed++ }.onHasNext().await())
        assertEquals(1, emptyClosed)
        val broken = Source().apply { available = { failedFdbFuture(IllegalStateException("read failed")) } }
        var failedClosed = 0
        assertFailsWith<IllegalStateException> { TenantListingIterator(broken) { failedClosed++ }.onHasNext().await() }
        assertEquals(1, failedClosed)
    }

    @Test fun cleanupFailureDoesNotReplaceReadFailure() = runBlocking {
        val readFailure = IllegalStateException("read failed")
        val cleanupFailure = IllegalArgumentException("close failed")
        val source = Source().apply { available = { failedFdbFuture(readFailure) } }
        val iterator = TenantListingIterator(source) { throw cleanupFailure }
        val thrown = assertFailsWith<IllegalStateException> { iterator.onHasNext().await() }
        assertEquals(readFailure.message, thrown.message)
        // JVM coroutine stack recovery may copy the exception and retain its original as cause.
        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { error ->
            error.suppressedExceptions.any { it === cleanupFailure }
        })
        assertEquals(1, source.cancels)
    }

    @Test fun cancellingCompletedReadinessDoesNotCloseTheCursor() = runBlocking {
        val source = Source()
        var closed = 0
        val iterator = TenantListingIterator(source) { closed++ }
        try {
            val ready = iterator.onHasNext()
            assertTrue(ready.await())
            ready.cancel()
            assertFalse(ready.isCancelled)
            assertEquals(0, closed)
            assertEquals(0, source.cancels)
            assertContentEquals(byteArrayOf(1), iterator.next().key)
        } finally { iterator.close() }
        assertEquals(1, closed)
    }

    @Test fun pendingReadinessCancellationEventuallyClosesOwnership() = runBlocking {
        withTimeout(10_000) {
            repeat(100) {
                for (waitUntilStarted in listOf(false, true)) {
                    val entered = CompletableDeferred<Unit>()
                    val source = Source().apply {
                        available = { fdbFuture { entered.complete(Unit); awaitCancellation() } }
                    }
                    var closed = 0
                    val ownerClosed = CompletableDeferred<Unit>()
                    val iterator = TenantListingIterator(source) { closed++; ownerClosed.complete(Unit) }
                    val pending = iterator.onHasNext()
                    if (waitUntilStarted) entered.await()
                    // Keep readiness pending: cancellation of a completed successful read
                    // intentionally leaves ownership available, tested separately above.
                    pending.cancel()
                    // Cancellation requests cleanup; a concurrent callback may own it.
                    ownerClosed.await()
                    assertEquals(1, closed)
                    assertEquals(1, source.cancels)
                    assertTrue(pending.isCancelled)
                    pending.cancel(); iterator.close()
                    assertEquals(1, closed)
                    assertEquals(1, source.cancels)
                }
            }
        }
    }
}
