package maryk.foundationdb.async

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Cold, backpressured iteration with a fresh iterator per collection. The originating transaction
 * must remain open until collection finishes. This does not extend a transaction or retry a stream.
 * Completion, failure and cancellation all release the iterator.
 */
fun <T> AsyncIterable<T>.asFlow(): Flow<T> = flow {
    val iterator = iterator()
    var failure: Throwable? = null
    try {
        while (iterator.onHasNext().await()) emit(iterator.next())
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try {
            if (iterator is CloseableAsyncIterator<T>) iterator.close() else iterator.cancel()
        } catch (cleanup: Throwable) {
            val primary = failure
            if (primary == null) throw cleanup
            if (cleanup !== primary) primary.addSuppressed(cleanup)
        }
    }
}
