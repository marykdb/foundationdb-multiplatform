package maryk.foundationdb.async

import maryk.foundationdb.FdbFuture

expect object AsyncUtil {
    fun <T> collect(iterable: AsyncIterable<T>): FdbFuture<List<T>>
    fun <T> collectRemaining(iterator: AsyncIterator<T>): FdbFuture<List<T>>
    fun <T> forEach(iterable: AsyncIterable<T>, consumer: (T) -> Unit): FdbFuture<Unit>
    fun <T> forEachRemaining(iterator: AsyncIterator<T>, consumer: (T) -> Unit): FdbFuture<Unit>
}
