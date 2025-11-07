package maryk.foundationdb.async

import java.util.concurrent.CompletableFuture

actual class CloseableAsyncIterator<T> internal constructor(
    private val closeableDelegate: com.apple.foundationdb.async.CloseableAsyncIterator<*>,
    mapper: (Any?) -> T
) : AsyncIterator<T>(closeableDelegate, mapper) {
    actual constructor(items: List<T>, closeAction: () -> Unit) : this(
        object : com.apple.foundationdb.async.CloseableAsyncIterator<Any?> {
            private val iterator = items.iterator()

            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): Any? = iterator.next()

            override fun onHasNext(): CompletableFuture<Boolean> =
                CompletableFuture.completedFuture(iterator.hasNext())

            override fun cancel() {}
            override fun remove() {}

            override fun close() {
                closeAction()
            }
        },
        {
            @Suppress("UNCHECKED_CAST")
            it as T
        }
    )

    actual fun close() {
        closeableDelegate.close()
    }
}
