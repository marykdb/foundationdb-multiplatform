package maryk.foundationdb.async

import com.apple.foundationdb.async.AsyncIterator as SdkAsyncIterator

import java.util.concurrent.CompletableFuture
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.toFdbFuture

internal open class JavaAsyncIterator<T>(
    internal val delegate: SdkAsyncIterator<*>,
    internal val mapper: (Any?) -> T
) : AsyncIterator<T> {
    @Suppress("UNCHECKED_CAST")
    override fun hasNext(): Boolean = delegate.hasNext()

    @Suppress("UNCHECKED_CAST")
    override suspend fun next(): T = mapper(delegate.next())

    override fun onHasNext(): FdbFuture<Boolean> =
        (delegate.onHasNext() as CompletableFuture<Boolean>).toFdbFuture()

    override open fun cancel() {
        delegate.cancel()
    }
}

internal fun <T> AsyncIterator(delegate: SdkAsyncIterator<*>, mapper: (Any?) -> T): AsyncIterator<T> = JavaAsyncIterator(delegate, mapper)
