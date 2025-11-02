package maryk.foundationdb.async

import java.util.concurrent.CompletableFuture
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.toFdbFuture

actual open class AsyncIterator<T> internal constructor(
    internal val delegate: com.apple.foundationdb.async.AsyncIterator<*>,
    internal val mapper: (Any?) -> T
) {
    @Suppress("UNCHECKED_CAST")
    actual fun hasNext(): Boolean = delegate.hasNext()

    @Suppress("UNCHECKED_CAST")
    actual suspend fun next(): T = mapper(delegate.next())

    actual fun onHasNext(): FdbFuture<Boolean> =
        (delegate.onHasNext() as CompletableFuture<Boolean>).toFdbFuture()

    actual fun cancel() {
        delegate.cancel()
    }
}
