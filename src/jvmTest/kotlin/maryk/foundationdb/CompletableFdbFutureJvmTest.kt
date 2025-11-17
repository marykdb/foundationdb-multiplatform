package maryk.foundationdb

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CompletableFdbFutureJvmTest {
    @Test
    fun cancellationCancelsDelegate() = runBlocking {
        val delegate = TrackingCompletableFuture<String>()
        val fdb = delegate.asFdbFuture()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            fdb.await()
        }
        job.cancelAndJoin()

        assertTrue(delegate.cancelled, "delegate future should cancel when coroutine is cancelled")
        assertTrue(fdb.isCancelled)
    }

    @Test
    fun exceptionUnwrapsCompletionException() = runBlocking {
        val delegate = CompletableFuture<String>()
        delegate.completeExceptionally(RuntimeException("boom"))

        val fdb = delegate.asFdbFuture()
        val thrown = assertFailsWith<RuntimeException> { fdb.await() }
        assertEquals("boom", thrown.message)
        assertTrue(fdb.isDone)
    }

    @Test
    fun asCompletableFuturePropagatesResult() {
        val fdb = fdbFutureFromSuspend { "bridge-value" }
        val completable = fdb.asCompletableFuture()
        assertEquals("bridge-value", completable.get(1, TimeUnit.SECONDS))
        assertTrue(fdb.isDone)
    }

    @Test
    fun cancelPropagatesToDelegate() {
        val delegate = TrackingCompletableFuture<String>()
        val fdb = delegate.asFdbFuture()
        fdb.cancel()
        assertTrue(delegate.cancelled)
        assertTrue(fdb.isCancelled)
    }
}

private class TrackingCompletableFuture<T> : CompletableFuture<T>() {
    var cancelled: Boolean = false
        private set

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        cancelled = true
        return super.cancel(mayInterruptIfRunning)
    }
}
