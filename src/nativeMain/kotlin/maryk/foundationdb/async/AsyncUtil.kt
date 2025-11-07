package maryk.foundationdb.async

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.completedFdbFuture
import maryk.foundationdb.fdbFutureFromSuspend

actual object AsyncUtil {
    actual fun <T> collect(iterable: AsyncIterable<T>): FdbFuture<List<T>> = iterable.asList()

    actual fun <T> collectRemaining(iterator: AsyncIterator<T>): FdbFuture<List<T>> =
        iterator.collectAllAsync()

    actual fun <T> forEach(iterable: AsyncIterable<T>, consumer: (T) -> Unit): FdbFuture<Unit> =
        iterable.iterator().consumeAsync(consumer)

    actual fun <T> forEachRemaining(iterator: AsyncIterator<T>, consumer: (T) -> Unit): FdbFuture<Unit> =
        iterator.consumeAsync(consumer)
}

private fun <T> AsyncIterator<T>.consumeAsync(consumer: (T) -> Unit): FdbFuture<Unit> = fdbFutureFromSuspend {
    while (true) {
        val more = onHasNext().await()
        if (!more) break
        consumer(next())
    }
}
