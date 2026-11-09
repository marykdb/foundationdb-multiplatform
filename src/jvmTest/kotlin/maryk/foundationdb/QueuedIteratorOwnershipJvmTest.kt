package maryk.foundationdb

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.async.asyncIterable
import maryk.foundationdb.async.closeableAsyncIterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueuedIteratorOwnershipJvmTest {
    /** Occupy each CPU worker without marking the tasks as blocking, so factory launches stay queued. */
    private fun withDefaultWorkersBlocked(block: () -> Unit) = runBlocking {
        withTimeout(10_000) {
            val workers = System.getProperty("kotlinx.coroutines.scheduler.core.pool.size")?.toIntOrNull()
                ?: Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            val entered = CountDownLatch(workers)
            val release = CountDownLatch(1)
            val jobs = List(workers) { launch(Dispatchers.Default) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            } }
            try { assertTrue(entered.await(5, TimeUnit.SECONDS)); block() }
            finally { release.countDown(); jobs.joinAll() }
        }
    }

    @Test fun allQueuedIteratorConsumersReleaseTheirSuppliedIterator() {
        withDefaultWorkersBlocked {
            for (mode in 0..3) {
                val closes = AtomicInteger()
                val source = closeableAsyncIterator(listOf(1, 2)) { closes.incrementAndGet() }
                val result = when (mode) {
                    0 -> AsyncUtil.collect(asyncIterable { source })
                    1 -> AsyncUtil.collectRemaining(source)
                    2 -> AsyncUtil.forEach(asyncIterable { source }) {}
                    else -> AsyncUtil.forEachRemaining(source) {}
                }
                result.cancel()
                assertTrue(result.isCancelled)
                assertEquals(1, closes.get(), "Consumer $mode leaked a queued iterator")
            }
        }
    }

    @Test fun cancelledQueuedWhenAllCancelsOnlyItsInternalObserver() {
        val gate = CompletableDeferred<Int>()
        val reads = AtomicInteger()
        val cancels = AtomicInteger()
        val input = object : FdbFuture<Int> by gate.asFdbFuture() {
            override val isDone: Boolean get() { reads.incrementAndGet(); return false }
            override fun cancel() { cancels.incrementAndGet() }
        }
        try {
            withDefaultWorkersBlocked {
                val result = AsyncUtil.whenAll(listOf(input))
                result.cancel()
                assertTrue(result.isCancelled)
            }
            // All earlier CPU tasks must have entered before these workers can all block again.
            withDefaultWorkersBlocked {
                assertEquals(0, reads.get())
                assertEquals(0, cancels.get())
            }
        } finally { gate.complete(1) }
    }
}
