@file:OptIn(ExperimentalAtomicApi::class)

package maryk.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.async.CloseableAsyncIterator
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IteratorFutureLifecycleTest {
    private class Source : CloseableAsyncIterator<Int> {
        val closes = AtomicInt(0)
        val closed = CompletableDeferred<Unit>()
        var closeError: Throwable? = null
        var available: () -> FdbFuture<Boolean> = { completedFdbFuture(false) }
        override fun hasNext() = false
        override suspend fun next() = 1
        override fun onHasNext() = available()
        override fun cancel() = close()
        override fun close() {
            closes.fetchAndAdd(1)
            closed.complete(Unit)
            closeError?.let { throw it }
        }
    }

    @Test fun observationCancellationLeavesOwnershipButOwningCancellationClosesOnce() = runBlocking {
        withTimeout(10_000) {
            val entered = CompletableDeferred<Unit>()
            val source = Source().apply { available = { fdbFuture { entered.complete(Unit); awaitCancellation() } } }
            val result = AsyncUtil.collectRemaining(source)
            entered.await()
            val observer = launch(start = CoroutineStart.UNDISPATCHED) { result.awaitObserved() }
            observer.cancelAndJoin()
            assertFalse(result.isCancelled)
            assertEquals(0, source.closes.load())
            result.cancel()
            source.closed.await()
            result.cancel()
            assertEquals(1, source.closes.load())
        }
    }

    @Test fun readAndConsumerFailuresKeepTheirCauseWhenCleanupAlsoFails() = runBlocking {
        for (consumerFails in listOf(false, true)) {
            val source = Source().apply {
                closeError = IllegalArgumentException("close failed")
                available = { if (consumerFails) completedFdbFuture(true) else failedFdbFuture(IllegalStateException("read failed")) }
            }
            val result = if (consumerFails) AsyncUtil.forEachRemaining(source) { throw IllegalStateException("consumer failed") }
                else AsyncUtil.forEachRemaining(source) {}
            val failure = assertFailsWith<IllegalStateException> { result.await() }
            assertEquals(if (consumerFails) "consumer failed" else "read failed", failure.message)
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { error ->
                error.suppressedExceptions.any { it.message == "close failed" }
            })
            assertEquals(1, source.closes.load())
        }
    }

    @Test fun successfulCleanupFailureFailsTheFuture() = runBlocking {
        val source = Source().apply { closeError = IllegalArgumentException("close failed") }
        val result = AsyncUtil.collectRemaining(source)
        assertEquals("close failed", assertFailsWith<IllegalArgumentException> { result.await() }.message)
        assertFalse(result.isCancelled)
        assertEquals(1, source.closes.load())
    }

    @Test fun successfulPlainIteratorIsNotCancelledAfterCompletion() = runBlocking {
        var position = 0
        var cancels = 0
        val source = object : AsyncIterator<Int> {
            override fun hasNext() = position < 2
            override suspend fun next() = position++
            override fun onHasNext() = completedFdbFuture(hasNext())
            override fun cancel() { cancels++ }
        }
        val result = AsyncUtil.collectRemaining(source)
        assertEquals(listOf(0, 1), result.await())
        result.cancel()
        assertEquals(0, cancels)
    }
}
