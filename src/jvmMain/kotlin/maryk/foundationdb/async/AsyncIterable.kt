package maryk.foundationdb.async

import java.util.concurrent.CompletableFuture
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.toFdbFuture

actual class AsyncIterable<T> internal constructor(
    internal val delegate: com.apple.foundationdb.async.AsyncIterable<*>,
    internal val mapper: (Any?) -> T
) {
    actual fun iterator(): AsyncIterator<T> =
        AsyncIterator(delegate.iterator(), mapper)

    actual fun asList(): FdbFuture<List<T>> =
        (delegate.asList() as CompletableFuture<List<*>>)
            .thenApply { list -> list.map(mapper) }
            .toFdbFuture()
}
