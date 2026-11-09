@file:OptIn(ExperimentalAtomicApi::class)

package maryk.foundationdb.async

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.failedFdbFuture
import maryk.foundationdb.fdbFutureFromSuspend

internal fun <I, O> applyFutureSafely(function: (I) -> FdbFuture<O>, value: I): FdbFuture<O> =
    try { function(value) } catch (error: Throwable) { failedFdbFuture(error) }

internal fun whileFutureTrue(body: () -> FdbFuture<Boolean>): FdbFuture<Unit> = fdbFutureFromSuspend {
    var count = 0
    do {
        currentCoroutineContext().ensureActive()
        if (++count % 128 == 0) yield()
    } while (body().await())
}

internal fun <V, T> handleFuture(task: FdbFuture<V>, handler: (V?, Throwable?) -> FdbFuture<T>): FdbFuture<T> =
    fdbFutureFromSuspend {
        val outcome = runCatching { task.awaitObserved() }
        currentCoroutineContext().ensureActive()
        handler(outcome.getOrNull(), outcome.exceptionOrNull()).awaitObserved()
    }

/** Own the iterator from call time, independently of whether the factory body is dispatched. */
private fun <T, R> iteratorFuture(source: AsyncIterator<T>, block: suspend () -> R): FdbFuture<R> {
    val released = AtomicInt(0)
    fun release(failure: Throwable?, complete: Boolean = false) {
        if (!released.compareAndSet(0, 1)) return
        try {
            if (source is CloseableAsyncIterator<*>) source.close() else if (!complete) source.cancel()
        } catch (cleanup: Throwable) {
            if (failure == null) throw cleanup
            if (cleanup !== failure) failure.addSuppressed(cleanup)
        }
    }
    return fdbFutureFromSuspend(onCompletion = { error -> if (error != null) release(error) }) {
        var failure: Throwable? = null
        var complete = false
        try {
            currentCoroutineContext().ensureActive()
            val result = block()
            complete = true
            result
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally { release(failure, complete) }
    }
}

private suspend fun <T> consumeIteratorBody(source: AsyncIterator<T>, consumer: (T) -> Unit) {
    var count = 0
    while (source.onHasNext().await()) {
        currentCoroutineContext().ensureActive()
        consumer(source.next())
        if (++count % 128 == 0) yield()
    }
}

internal fun <T> consumeIterator(source: AsyncIterator<T>, consumer: (T) -> Unit): FdbFuture<Unit> =
    iteratorFuture(source) { consumeIteratorBody(source, consumer) }

internal fun <T> collectIterator(source: AsyncIterator<T>): FdbFuture<List<T>> = iteratorFuture(source) {
    val result = mutableListOf<T>()
    consumeIteratorBody(source) { result += it }
    result
}
