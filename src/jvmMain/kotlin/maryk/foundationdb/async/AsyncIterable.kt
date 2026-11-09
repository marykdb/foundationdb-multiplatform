package maryk.foundationdb.async

import com.apple.foundationdb.async.AsyncIterable as SdkAsyncIterable

import java.util.concurrent.CompletableFuture
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.mapToFdbFuture
import maryk.foundationdb.toFdbFuture

internal class JavaAsyncIterable<T>(
    internal val delegate: SdkAsyncIterable<*>,
    internal val mapper: (Any?) -> T
) : AsyncIterable<T> {
    override fun iterator(): AsyncIterator<T> =
        AsyncIterator(delegate.iterator(), mapper)

    override fun asList(): FdbFuture<List<T>> =
        (delegate.asList() as CompletableFuture<List<*>>)
            .mapToFdbFuture { list -> list.map(mapper) }
}

internal fun <T> AsyncIterable(delegate: SdkAsyncIterable<*>, mapper: (Any?) -> T): AsyncIterable<T> = JavaAsyncIterable(delegate, mapper)
