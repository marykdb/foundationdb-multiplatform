@file:OptIn(ExperimentalAtomicApi::class)

package maryk.foundationdb.async

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.failedFdbFuture
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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

/**
 * An owned list cursor. Exhaustion observed through hasNext/onHasNext, explicit close,
 * and cancellation invoke closeAction once. A closed cursor exposes no further rows.
 */
fun <T> closeableAsyncIterator(items: List<T>, closeAction: () -> Unit = {}): CloseableAsyncIterator<T> =
    CloseableAsyncIterator(items, closeAction)

/** Source-compatible list factory; custom implementations can implement the interface directly. */
fun <T> CloseableAsyncIterator(items: List<T>, closeAction: () -> Unit): CloseableAsyncIterator<T> =
    ListCloseableAsyncIterator(items, closeAction)

internal expect fun <T> ownedCloseableIterator(source: AsyncIterator<T>, closeAction: () -> Unit): CloseableAsyncIterator<T>

private class ListCloseableAsyncIterator<T>(items: List<T>, private val closeAction: () -> Unit) :
    ListAsyncIterator<T>(), CloseableAsyncIterator<T> {
    private val closed = AtomicInt(0)
    init { initialise(items) }

    override fun hasNext(): Boolean {
        if (closed.load() != 0) return false
        return super.hasNext().also { if (!it) close() }
    }

    override suspend fun next(): T {
        if (!hasNext()) throw NoSuchElementException("Iterator is closed or exhausted")
        return super.next()
    }

    override fun onHasNext(): FdbFuture<Boolean> =
        try { completedFdbFuture(hasNext()) } catch (error: Throwable) { failedFdbFuture(error) }

    override fun cancel() = close()

    override fun close() {
        if (closed.compareAndSet(0, 1)) {
            super.cancel()
            closeAction()
        }
    }
}
