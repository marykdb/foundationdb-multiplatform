package maryk.foundationdb.async

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.fdbFuture

internal fun <T> observeAll(tasks: List<FdbFuture<T>>): FdbFuture<List<T>> {
    val inputs = tasks.toList()
    return fdbFuture {
        if (inputs.all { it.isDone }) {
            // Already-completed inputs need no per-input observer coroutine. Still observe
            // every result, preserving cleanup and the original first-input failure order.
            val values = ArrayList<T>(inputs.size)
            var failure: Throwable? = null
            for (input in inputs) {
                currentCoroutineContext().ensureActive()
                try { values += input.awaitObserved() }
                catch (error: Throwable) {
                    currentCoroutineContext().ensureActive()
                    if (failure == null) failure = error
                }
            }
            failure?.let { throw it }
            values
        } else coroutineScope {
            inputs.map { async { runCatching { it.awaitObserved() } } }.awaitAll().map { it.getOrThrow() }
        }
    }
}

internal fun observeAny(tasks: Collection<FdbFuture<*>>): FdbFuture<Unit> {
    val inputs = tasks.toList()
    return fdbFuture {
        coroutineScope {
            val observers = inputs.map { async { runCatching { it.awaitObserved(); Unit } } }
            try { select { observers.forEach { observer -> observer.onAwait { it.getOrThrow() } } } }
            finally { observers.forEach { it.cancel() } }
        }
    }
}
