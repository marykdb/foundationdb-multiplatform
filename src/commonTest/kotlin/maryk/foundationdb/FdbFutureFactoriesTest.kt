package maryk.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FdbFutureFactoriesTest {
    @Test
    fun cancellingAnAwaitCancelsTheFactoryOperation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val future = fdbFuture<Unit> {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                stopped.complete(Unit)
            }
        }
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) { future.await() }
        started.await()
        waiter.cancelAndJoin()
        withTimeout(2_000) { stopped.await() }
        assertTrue(future.isCancelled)
    }

    @Test
    fun asynchronousFailureDoesNotReportCancellation() = runBlocking<Unit> {
        val future = fdbFuture<Int> { throw IllegalArgumentException("ordinary failure") }
        assertFailsWith<IllegalArgumentException> { future.await() }
        assertTrue(future.isDone)
        assertFalse(future.isCancelled)
        future.cancel()
        assertFalse(future.isCancelled)
    }

    @Test
    fun transactionFailureIsNotFutureCancellation() = FoundationDbTestHarness().runAndReset {
        val transaction = database.createTransaction()
        try {
            transaction.cancel()
            val future = transaction.getReadVersion()
            assertFailsWith<FDBException> { future.await() }
            assertTrue(future.isDone)
            assertFalse(future.isCancelled)
        } finally { transaction.close() }
    }

    @Test
    fun completedFutureExposesState() = runBlocking {
        val future = completedFdbFuture("done")

        assertEquals("done", future.await())
        assertTrue(future.isDone)
        assertFalse(future.isCancelled)

        future.cancel()
        assertTrue(future.isDone, "completed futures remain done after cancel()")
        assertFalse(future.isCancelled, "cancel() on completed future is a no-op")
    }

    @Test
    fun failedFuturePropagatesError() = runBlocking {
        val expected = IllegalStateException("boom")
        val future = failedFdbFuture<String>(expected)

        val thrown = assertFailsWith<IllegalStateException> { future.await() }
        assertEquals(expected, thrown)
        assertTrue(future.isDone)
        assertFalse(future.isCancelled)

        future.cancel()
        assertFalse(future.isCancelled, "cancel() should not flip cancelled flag on failed future")
    }
}
