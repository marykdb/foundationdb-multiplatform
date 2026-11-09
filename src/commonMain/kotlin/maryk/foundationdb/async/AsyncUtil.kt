package maryk.foundationdb.async

import kotlinx.coroutines.CancellationException
import maryk.foundationdb.completedFdbFuture
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.fdbFutureFromSuspend

/** Portable async helpers. Collection owns iterator cleanup; completion combinators only observe inputs. */
object AsyncUtil {
    fun <T> getAll(tasks: Collection<FdbFuture<T>>): FdbFuture<List<T>> = observeAll(tasks.toList())
    fun <T> getAll(tasks: List<FdbFuture<T>>): FdbFuture<List<T>> = observeAll(tasks)
    fun whenAll(tasks: Collection<FdbFuture<*>>): FdbFuture<Unit> = observeAll(tasks.toList()).let { combined ->
        // Own the internal observer even if this wrapper is cancelled before it starts.
        // observeAll observes inputs non-owningly, so input operations remain unaffected.
        fdbFutureFromSuspend(onCompletion = { error -> if (error is CancellationException) combined.cancel() }) {
            combined.await()
            Unit
        }
    }
    fun whenAny(tasks: Collection<FdbFuture<*>>): FdbFuture<Unit> = observeAny(tasks)
    val DONE: FdbFuture<Unit> = completedFdbFuture(Unit)
    val READY_TRUE: FdbFuture<Boolean> = completedFdbFuture(true)
    val READY_FALSE: FdbFuture<Boolean> = completedFdbFuture(false)
    fun <T> collect(iterable: AsyncIterable<T>): FdbFuture<List<T>> = collectIterator(iterable.iterator())
    fun <T> collectRemaining(iterator: AsyncIterator<T>): FdbFuture<List<T>> = collectIterator(iterator)
    fun <T> forEach(iterable: AsyncIterable<T>, consumer: (T) -> Unit): FdbFuture<Unit> = consumeIterator(iterable.iterator(), consumer)
    fun <T> forEachRemaining(iterator: AsyncIterator<T>, consumer: (T) -> Unit): FdbFuture<Unit> = consumeIterator(iterator, consumer)
    fun <I, O> applySafely(function: (I) -> FdbFuture<O>, value: I): FdbFuture<O> = applyFutureSafely(function, value)
    fun whileTrue(body: () -> FdbFuture<Boolean>): FdbFuture<Unit> = whileFutureTrue(body)
    fun <V> success(task: FdbFuture<V>): FdbFuture<Unit> = fdbFutureFromSuspend { task.awaitObserved(); Unit }
    fun <V> whenReady(task: FdbFuture<V>): FdbFuture<Unit> = handleFuture(task) { _, _ -> completedFdbFuture(Unit) }
    fun <V> composeExceptionally(task: FdbFuture<V>, handler: (Throwable) -> FdbFuture<V>): FdbFuture<V> =
        handleFuture(task) { value, error -> if (error == null) completedFdbFuture(value as V) else handler(error) }
    fun <V, T> composeHandle(task: FdbFuture<V>, handler: (V?, Throwable?) -> FdbFuture<T>): FdbFuture<T> = handleFuture(task, handler)
    fun <V, T> composeHandleAsync(task: FdbFuture<V>, handler: (V?, Throwable?) -> FdbFuture<T>): FdbFuture<T> = handleFuture(task, handler)
    fun <V, T> tag(task: FdbFuture<T>, value: V): FdbFuture<V> = fdbFutureFromSuspend { task.awaitObserved(); value }
    fun <V, T> mapIterator(source: AsyncIterator<V>, mapper: (V) -> T): AsyncIterator<T> =
        if (source is CloseableAsyncIterator<V>) mapIterator(source, mapper) else mappedIterator(source, mapper)
    private fun <V, T> mappedIterator(source: AsyncIterator<V>, mapper: (V) -> T, cancelSource: () -> Unit = source::cancel): AsyncIterator<T> = object : AsyncIterator<T> {
        override fun hasNext() = source.hasNext()
        override suspend fun next(): T = mapper(source.next())
        override fun onHasNext() = source.onHasNext()
        override fun cancel() = cancelSource()
    }
    fun <V, T> mapIterator(source: CloseableAsyncIterator<V>, mapper: (V) -> T): CloseableAsyncIterator<T> =
        ownedCloseableIterator(mappedIterator(source, mapper, source::close)) {}
    fun <V, T> mapIterable(source: AsyncIterable<V>, mapper: (V) -> T): AsyncIterable<T> =
        asyncIterable { mapIterator(source.iterator(), mapper) }
}
