package maryk.foundationdb.async

import maryk.foundationdb.fdbFutureFromSuspend
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
actual class CloseableAsyncIterator<T> internal constructor(
    private val delegate: AsyncIterator<T>,
    private val closeAction: () -> Unit
) : AsyncIterator<T>() {
    actual constructor(items: List<T>, closeAction: () -> Unit) : this(
        ListBackedIterator(items),
        closeAction
    )

    private val closed = AtomicInt(0)

    override fun hasNext(): Boolean {
        val more = delegate.hasNext()
        if (!more) close()
        return more
    }

    override suspend fun next(): T = delegate.next()

    override fun onHasNext() = fdbFutureFromSuspend {
        val more = delegate.onHasNext().await()
        if (!more) close()
        more
    }

    override fun cancel() {
        close()
    }

    actual fun close() {
        if (closed.compareAndSet(0, 1)) {
            try {
                delegate.cancel()
            } finally {
                closeAction()
            }
        }
    }
}

private class ListBackedIterator<T>(items: List<T>) : AsyncIterator<T>() {
    init {
        initialise(items)
    }
}
