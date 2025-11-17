package maryk.foundationdb.async

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.asCompletableFuture
import maryk.foundationdb.toFdbFuture
import java.util.concurrent.CompletableFuture

actual object AsyncUtil {
    actual fun <T> collect(iterable: AsyncIterable<T>): FdbFuture<List<T>> =
        collectRemaining(iterable.iterator())

    actual fun <T> collectRemaining(iterator: AsyncIterator<T>): FdbFuture<List<T>> {
        val future = CompletableFuture<List<T>>()
        val collected = ArrayList<T>()

        fun step() {
            iterator.onHasNext().asCompletableFuture().whenComplete { hasNext, error ->
                if (error != null) {
                    future.completeExceptionally(error)
                    return@whenComplete
                }
                if (hasNext == true) {
                    try {
                        collected.add(iterator.mapper(iterator.delegate.next()))
                        step()
                    } catch (t: Throwable) {
                        future.completeExceptionally(t)
                    }
                } else {
                    future.complete(collected)
                }
            }
        }

        step()
        return future.toFdbFuture()
    }

    actual fun <T> forEach(iterable: AsyncIterable<T>, consumer: (T) -> Unit): FdbFuture<Unit> =
        forEachRemaining(iterable.iterator(), consumer)

    actual fun <T> forEachRemaining(iterator: AsyncIterator<T>, consumer: (T) -> Unit): FdbFuture<Unit> {
        val future = CompletableFuture<Unit>()

        fun step() {
            iterator.onHasNext().asCompletableFuture().whenComplete { hasNext, error ->
                if (error != null) {
                    future.completeExceptionally(error)
                    return@whenComplete
                }
                if (hasNext == true) {
                    try {
                        consumer(iterator.mapper(iterator.delegate.next()))
                        step()
                    } catch (t: Throwable) {
                        future.completeExceptionally(t)
                    }
                } else {
                    future.complete(Unit)
                }
            }
        }

        step()
        return future.toFdbFuture()
    }
}
