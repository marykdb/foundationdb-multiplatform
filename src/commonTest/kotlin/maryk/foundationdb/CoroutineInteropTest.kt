package maryk.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import maryk.foundationdb.async.CloseableAsyncIterator
import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.async.asFlow
import maryk.foundationdb.async.asyncIterable
import maryk.foundationdb.async.closeableAsyncIterator
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CoroutineInteropTest {
    @Test fun flowIsColdBackpressuredAndClosesOnEarlyCompletion() = runBlocking<Unit> {
        var opened = 0
        var closed = 0
        var consumed = 0
        val source = asyncIterable {
            opened++
            val iterator = closeableAsyncIterator(listOf(1, 2, 3)) { closed++ }
            object : CloseableAsyncIterator<Int> by iterator {
                override suspend fun next(): Int { consumed++; return iterator.next() }
            }
        }
        val flow = source.asFlow()
        assertEquals(0, opened)
        assertEquals(listOf(1), flow.take(1).toList())
        assertEquals(1, consumed)
        assertEquals(1, closed)
        assertEquals(listOf(1, 2, 3), flow.toList())
        assertEquals(2, opened)
        assertEquals(2, closed)
    }

    @Test fun flowCleansUpOnCancellationAndFailure() = runBlocking<Unit> {
        withTimeout(10_000) {
            var cancelled = 0
            val entered = CompletableDeferred<Unit>()
            val flow = asyncIterable { object : AsyncIterator<Int> {
                override fun hasNext() = true
                override fun onHasNext() = completedFdbFuture(true)
                override suspend fun next(): Int { entered.complete(Unit); awaitCancellation() }
                override fun cancel() { cancelled++ }
            } }.asFlow()
            val job = launch { flow.collect() }
            entered.await()
            job.cancel(); job.join()
            assertEquals(1, cancelled)
            var closed = 0
            val failing = asyncIterable { closeableAsyncIterator(listOf(1)) { closed++ } }.asFlow()
            assertFailsWith<IllegalStateException> { failing.collect { error("consumer failed") } }
            assertEquals(1, closed)
        }
    }

    @Test fun flowRetainsPrimaryFailureWhenClosingAlsoFails() = runBlocking {
        for (readFails in listOf(false, true)) {
            val source = asyncIterable { object : CloseableAsyncIterator<Int> {
                override fun hasNext() = true
                override fun onHasNext(): FdbFuture<Boolean> =
                    if (readFails) failedFdbFuture(IllegalStateException("read failed")) else completedFdbFuture(true)
                override suspend fun next() = 1
                override fun cancel() = close()
                override fun close() { throw IllegalArgumentException("cleanup failed") }
            } }.asFlow()
            val failure = assertFailsWith<IllegalStateException> {
                source.collect { error("consumer failed") }
            }
            assertEquals(if (readFails) "read failed" else "consumer failed", failure.message)
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { cause ->
                cause.suppressedExceptions.any { it.message == "cleanup failed" }
            })
        }
    }

    @Test fun mappedCloseableIteratorsReleaseTheSourceOnce() = runBlocking {
        for (cancelEarly in listOf(false, true)) {
            var closes = 0
            val source = object : CloseableAsyncIterator<Int> {
                var consumed = false
                override fun hasNext() = !consumed
                override fun onHasNext() = completedFdbFuture(hasNext())
                override suspend fun next(): Int { consumed = true; return 1 }
                override fun cancel() = error("Close the owned source directly")
                override fun close() { closes++ }
            }
            val mapped = AsyncUtil.mapIterator(source) { it + 1 }
            if (cancelEarly) mapped.cancel() else assertEquals(listOf(2), AsyncUtil.collectRemaining(mapped).await())
            mapped.cancel(); mapped.close()
            assertEquals(1, closes)
        }
    }

    @Test fun futureToDeferredHasExplicitCancellationOwnership() = runBlocking<Unit> {
        supervisorScope {
            val value = CompletableDeferred<Int>()
            val future = value.asFdbFuture()
            val observer = future.asDeferred(this)
            observer.cancel(); observer.join()
            assertFalse(future.isCancelled)
            value.complete(7)
            assertEquals(7, future.await())
            val ownedSource = CompletableDeferred<Int>()
            val ownedFuture = ownedSource.asFdbFuture(FutureCancellation.PROPAGATE)
            val owned = ownedFuture.asDeferred(this, FutureCancellation.PROPAGATE)
            owned.cancel(); owned.join()
            assertTrue(ownedFuture.isCancelled)
            assertTrue(ownedSource.isCancelled)
            var sourceCancels = 0
            val failed = object : FdbFuture<Int> {
                override suspend fun await(): Int = throw IllegalStateException("failed operation")
                override suspend fun awaitObserved(): Int = await()
                override fun cancel() { sourceCancels++ }
                override val isDone = true
                override val isCancelled = false
            }
            assertFailsWith<IllegalStateException> { failed.asDeferred(this, FutureCancellation.PROPAGATE).await() }
            assertEquals(0, sourceCancels, "Failure of a Deferred is not a request to cancel its source")
        }
    }

    @Test fun deferredToFutureHasExplicitCancellationOwnership() = runBlocking<Unit> {
        val source = CompletableDeferred<Int>()
        val observer = source.asFdbFuture()
        observer.cancel()
        assertFalse(source.isCancelled)
        assertFailsWith<CancellationException> { observer.await() }
        source.complete(9)
        assertEquals(9, source.await())
        val owned = CompletableDeferred<Int>()
        owned.asFdbFuture(FutureCancellation.PROPAGATE).cancel()
        assertTrue(owned.isCancelled)
        assertEquals(3, CompletableDeferred(3).asFdbFuture().await())
        val failure = CompletableDeferred<Int>()
        failure.completeExceptionally(IllegalArgumentException("source failure"))
        val failedAdapter = failure.asFdbFuture()
        assertFailsWith<IllegalArgumentException> { failedAdapter.await() }
        assertTrue(failedAdapter.isDone)
        assertFalse(failedAdapter.isCancelled, "An ordinary source failure is not cancellation")
    }

    @Test fun transactionRetriesKeepCallerContextAndDoNotCancelParent() = FoundationDbTestHarness().runAndReset {
        withContext(CoroutineName("caller")) {
            var attempts = 0
            val result = database.runSuspend { transaction ->
                assertEquals("caller", currentCoroutineContext()[CoroutineName]?.name)
                yield()
                assertEquals("caller", currentCoroutineContext()[CoroutineName]?.name)
                if (attempts++ == 0) throw FDBException("injected conflict", 1020)
                transaction.set(key("context"), byteArrayOf(4))
                4
            }
            assertEquals(4, result)
            assertEquals(2, attempts)
            assertTrue(currentCoroutineContext()[Job]!!.isActive)
            val dispatcher = Dispatchers.Default.limitedParallelism(1)
            database.readSuspend(dispatcher + CoroutineName("read-override")) {
                assertEquals(dispatcher, currentCoroutineContext()[ContinuationInterceptor])
                assertEquals("read-override", currentCoroutineContext()[CoroutineName]?.name)
                assertEquals(4, it.get(key("context")).await()!![0].toInt())
            }
            assertFailsWith<IllegalArgumentException> { database.runSuspend(Job()) { } }
        }
    }

    @Test fun rangeFlowCollectsInsideTransactionAndCanStopEarly() = FoundationDbTestHarness().runAndReset {
        database.run { transaction -> repeat(5) { transaction.set(key("flow", it.toString()), byteArrayOf(it.toByte())) } }
        val range = Range.startsWith(prefixBytes("flow"))
        database.readSuspend { transaction ->
            assertEquals(2, transaction.getRange(range).asFlow().take(2).toList().size)
            // Closing the first range iterator must leave its transaction usable.
            assertEquals(5, transaction.getRange(range).asFlow().toList().size)
        }
    }

    @Test fun cancellingCallerCancelsSuspendingTransactionAttempt() = FoundationDbTestHarness().runAndReset {
        withTimeout(10_000) {
            val entered = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            val job = launch {
                database.runSuspend {
                    try { entered.complete(Unit); awaitCancellation() }
                    finally { stopped.complete(Unit) }
                }
            }
            entered.await()
            job.cancel(); job.join()
            stopped.await()
            assertTrue(job.isCancelled)
        }
    }
}
