package maryk.foundationdb.async

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.completedFdbFuture

internal open class ListAsyncIterator<T> : AsyncIterator<T> {
    protected open val backing: MutableList<T> = mutableListOf()
    protected var position = 0
    internal fun initialise(items: List<T>) { backing.clear(); backing.addAll(items); position = 0 }
    override fun hasNext(): Boolean = position < backing.size
    override suspend fun next(): T { if (!hasNext()) throw NoSuchElementException(); return backing[position++] }
    override fun onHasNext(): FdbFuture<Boolean> = completedFdbFuture(hasNext())
    override fun cancel() { position = backing.size }
}

/** Construct a custom, lazy iterable without a platform delegate. */
fun <T> asyncIterable(iteratorFactory: () -> AsyncIterator<T>): AsyncIterable<T> = object : AsyncIterable<T> {
    override fun iterator(): AsyncIterator<T> = iteratorFactory()
    override fun asList(): FdbFuture<List<T>> = AsyncUtil.collect(this)
}

fun <T> closeableAsyncIterator(items: List<T>, closeAction: () -> Unit = {}): CloseableAsyncIterator<T> =
    CloseableAsyncIterator(items, closeAction)

/** Source-compatible list factory; custom implementations can implement the interface directly. */
fun <T> CloseableAsyncIterator(items: List<T>, closeAction: () -> Unit): CloseableAsyncIterator<T> =
    ownedCloseableIterator(object : ListAsyncIterator<T>() { init { initialise(items) } }, closeAction)

internal expect fun <T> ownedCloseableIterator(source: AsyncIterator<T>, closeAction: () -> Unit): CloseableAsyncIterator<T>
