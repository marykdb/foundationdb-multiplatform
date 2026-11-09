package maryk.foundationdb.async

import com.apple.foundationdb.async.CloseableAsyncIterator as SdkCloseableAsyncIterator

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

internal class JavaCloseableAsyncIterator<T>(
    internal val closeableDelegate: SdkCloseableAsyncIterator<*>,
    mapper: (Any?) -> T
) : JavaAsyncIterator<T>(closeableDelegate, mapper), CloseableAsyncIterator<T> {
    private val closed = AtomicBoolean(false)

    constructor(items: List<T>, closeAction: () -> Unit) : this(
        object : SdkCloseableAsyncIterator<Any?> {
            private val iterator = items.iterator()
            private val closed = AtomicBoolean(false)

            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): Any? = iterator.next()

            override fun onHasNext(): CompletableFuture<Boolean> =
                CompletableFuture.completedFuture(iterator.hasNext())

            override fun cancel() = close()
            override fun remove() {}

            override fun close() {
                if (closed.compareAndSet(false, true)) closeAction()
            }
        },
        {
            @Suppress("UNCHECKED_CAST")
            it as T
        }
    )

    override fun cancel() = close()

    override fun close() {
        if (closed.compareAndSet(false, true)) closeableDelegate.close()
    }
}

internal fun <T> CloseableAsyncIterator(delegate: SdkCloseableAsyncIterator<*>, mapper: (Any?) -> T): CloseableAsyncIterator<T> = JavaCloseableAsyncIterator(delegate, mapper)

internal actual fun <T> ownedCloseableIterator(source: AsyncIterator<T>, closeAction: () -> Unit): CloseableAsyncIterator<T> = object : CloseableAsyncIterator<T> {
    private val closed = AtomicBoolean(false)
    override fun hasNext(): Boolean = source.hasNext()
    override suspend fun next(): T = source.next()
    override fun onHasNext() = source.onHasNext()
    override fun cancel() = close()
    override fun close() { if (closed.compareAndSet(false, true)) try { source.cancel() } finally { closeAction() } }
}
