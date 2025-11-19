package maryk.foundationdb.async

import maryk.foundationdb.fdbFutureFromSuspend

actual class CloseableAsyncIterator<T> internal constructor(
    private val delegate: AsyncIterator<T>,
    private val closeAction: () -> Unit
) : AsyncIterator<T>() {
    actual constructor(items: List<T>, closeAction: () -> Unit) : this(
        ListBackedIterator(items),
        closeAction
    )

    private var closed = false

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
        delegate.cancel()
    }

    actual fun close() {
        if (!closed) {
            closed = true
            try {
                closeAction()
            } finally {
                delegate.cancel()
            }
        }
    }
}

private class ListBackedIterator<T>(items: List<T>) : AsyncIterator<T>() {
    init {
        initialise(items)
    }
}
