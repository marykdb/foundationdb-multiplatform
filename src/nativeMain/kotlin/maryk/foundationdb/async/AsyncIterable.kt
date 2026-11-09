package maryk.foundationdb.async

import kotlinx.coroutines.runBlocking
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.completedFdbFuture
import maryk.foundationdb.fdbFutureFromSuspend

private class PopulatedListIterator<T>(items: List<T>) : ListAsyncIterator<T>() {
    init {
        initialise(items)
    }
}

internal class NativeAsyncIterable<T>(
    private val iteratorFactory: () -> AsyncIterator<T>,
    private val listFutureFactory: () -> FdbFuture<List<T>>
) : AsyncIterable<T> {
    override fun iterator(): AsyncIterator<T> = iteratorFactory()
    override fun asList(): FdbFuture<List<T>> = listFutureFactory()
}

internal fun <T> List<T>.toAsyncIterable(): AsyncIterable<T> = asyncIterableOfList(this)

internal fun <T> asyncIterableOfList(items: List<T>): AsyncIterable<T> = AsyncIterable(
    iteratorFactory = { PopulatedListIterator(items) },
    listFutureFactory = { completedFdbFuture(items) }
)

internal fun <T> AsyncIterator<T>.collectAllAsync(): FdbFuture<List<T>> = fdbFutureFromSuspend {
    val collected = mutableListOf<T>()
    var completed = false
    try {
        while (true) {
            val more = onHasNext().await()
            if (!more) break
            collected += next()
        }
        completed = true
        collected
    } finally {
        if (!completed) cancel()
    }
}

internal fun <T> runBlockingNext(iterator: AsyncIterator<T>): T = runCatching {
    runBlocking { iterator.next() }
}.getOrElse { throw it }

internal fun <T> AsyncIterable(iteratorFactory: () -> AsyncIterator<T>, listFutureFactory: () -> FdbFuture<List<T>>): AsyncIterable<T> = NativeAsyncIterable(iteratorFactory, listFutureFactory)
