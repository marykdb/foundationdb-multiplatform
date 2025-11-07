package maryk.foundationdb.async

import kotlinx.coroutines.runBlocking
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.completedFdbFuture
import maryk.foundationdb.fdbFutureFromSuspend

private class ListAsyncIterator<T>(items: List<T>) : AsyncIterator<T>() {
    init {
        initialise(items)
    }
}

actual class AsyncIterable<T> internal constructor(
    private val iteratorFactory: () -> AsyncIterator<T>,
    private val listFutureFactory: () -> FdbFuture<List<T>>
) {
    actual fun iterator(): AsyncIterator<T> = iteratorFactory()
    actual fun asList(): FdbFuture<List<T>> = listFutureFactory()
}

internal fun <T> List<T>.toAsyncIterable(): AsyncIterable<T> = asyncIterableOfList(this)

internal fun <T> asyncIterableOfList(items: List<T>): AsyncIterable<T> = AsyncIterable(
    iteratorFactory = { ListAsyncIterator(items) },
    listFutureFactory = { completedFdbFuture(items) }
)

internal fun <T> AsyncIterator<T>.collectAllAsync(): FdbFuture<List<T>> = fdbFutureFromSuspend {
    val collected = mutableListOf<T>()
    while (true) {
        val more = onHasNext().await()
        if (!more) break
        collected += next()
    }
    collected
}

internal fun <T> runBlockingNext(iterator: AsyncIterator<T>): T = runCatching {
    runBlocking { iterator.next() }
}.getOrElse { throw it }
