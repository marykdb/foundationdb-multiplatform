package maryk.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.async.CloseableAsyncIterator
import maryk.foundationdb.async.asyncIterable
import maryk.foundationdb.async.closeableAsyncIterator
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueuedIteratorOwnershipNativeTest {
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }

    private fun exercise(reset: Boolean) = runBlocking {
        val dispatcher = QueuedDispatcher()
        resetNativeFutureScope(dispatcher)
        var closes = 0
        try {
            val futures = List(6) { mode ->
                if (mode == 5) {
                    val source = object : AsyncIterator<Int> {
                        override fun hasNext() = true
                        override fun onHasNext() = completedFdbFuture(true)
                        override suspend fun next() = 1
                        override fun cancel() {}
                    }
                    return@List CloseableAsyncIterator(source) { closes++ }.onHasNext()
                }
                if (mode == 4) {
                    val tenantSource = object : AsyncIterator<KeyValue> {
                        override fun hasNext() = true
                        override fun onHasNext() = completedFdbFuture(true)
                        override suspend fun next(): KeyValue = error("Unused")
                        override fun cancel() {}
                    }
                    return@List TenantListingIterator(tenantSource) { closes++ }.onHasNext()
                }
                val source = closeableAsyncIterator(listOf(1, 2)) { closes++ }
                when (mode) {
                    0 -> AsyncUtil.collect(asyncIterable { source })
                    1 -> AsyncUtil.collectRemaining(source)
                    2 -> AsyncUtil.forEach(asyncIterable { source }) {}
                    else -> AsyncUtil.forEachRemaining(source) {}
                }
            }
            if (reset) resetNativeFutureScope() else futures.forEach { it.cancel() }
            dispatcher.drain()
            for (future in futures) {
                assertTrue(future.isDone)
                try { future.awaitObserved() } catch (_: CancellationException) { }
            }
            assertEquals(6, closes)
        } finally {
            resetNativeFutureScope()
            dispatcher.drain()
        }
    }
    @Test fun queuedCancellationClosesOwnedIterators() = exercise(false)
    @Test fun scopeResetClosesOwnedIteratorsEvenIfBodiesNeverStart() = exercise(true)
}
