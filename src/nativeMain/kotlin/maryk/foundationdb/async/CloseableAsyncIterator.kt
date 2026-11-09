package maryk.foundationdb.async

import maryk.foundationdb.completedFdbFuture
import maryk.foundationdb.fdbFutureFromSuspend
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class NativeCloseableAsyncIterator<T>(
    private val delegate: AsyncIterator<T>,
    private val closeAction: () -> Unit
) : ListAsyncIterator<T>(), CloseableAsyncIterator<T> {
    constructor(items: List<T>, closeAction: () -> Unit) : this(
        ListBackedIterator(items),
        closeAction
    )

    private val closed = AtomicInt(0)

    override fun hasNext(): Boolean {
        if (closed.load() != 0) return false
        return try {
            delegate.hasNext().also { if (!it) close() }
        } catch (error: Throwable) { closePreservingFailure(error); throw error }
    }

    override suspend fun next(): T {
        if (closed.load() != 0) throw NoSuchElementException("Iterator is closed")
        return try { delegate.next() }
        catch (error: Throwable) { closePreservingFailure(error); throw error }
    }

    override fun onHasNext() = if (closed.load() != 0) completedFdbFuture(false) else
        fdbFutureFromSuspend(onCompletion = { error ->
            if (error != null) closePreservingFailure(error)
        }) {
            try {
                if (closed.load() != 0) false else delegate.onHasNext().await().let { more ->
                    if (!more) close()
                    more && closed.load() == 0
                }
            } catch (error: Throwable) { closePreservingFailure(error); throw error }
        }

    private fun closePreservingFailure(error: Throwable) {
        try { close() } catch (cleanup: Throwable) {
            if (cleanup !== error) error.addSuppressed(cleanup)
        }
    }

    override fun cancel() {
        close()
    }

    override fun close() {
        if (closed.compareAndSet(0, 1)) {
            try {
                delegate.cancel()
            } finally {
                closeAction()
            }
        }
    }
}

private class ListBackedIterator<T>(items: List<T>) : ListAsyncIterator<T>() {
    init {
        initialise(items)
    }
}

internal fun <T> CloseableAsyncIterator(delegate: AsyncIterator<T>, closeAction: () -> Unit): CloseableAsyncIterator<T> = NativeCloseableAsyncIterator(delegate, closeAction)
internal actual fun <T> ownedCloseableIterator(source: AsyncIterator<T>, closeAction: () -> Unit): CloseableAsyncIterator<T> = NativeCloseableAsyncIterator(source, closeAction)
