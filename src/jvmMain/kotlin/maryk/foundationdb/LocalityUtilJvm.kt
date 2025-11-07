package maryk.foundationdb

import java.util.ArrayList
import java.util.concurrent.CompletableFuture
import maryk.foundationdb.async.CloseableAsyncIterator

internal actual fun getBoundaryKeysInternal(database: Database, begin: ByteArray, end: ByteArray): FdbFuture<List<ByteArray>> {
    val iterator = com.apple.foundationdb.LocalityUtil.getBoundaryKeys(database.delegate, begin, end)
    return collectIterator(iterator) { it }.toFdbFuture()
}

internal actual fun getBoundaryKeysInternal(transaction: Transaction, begin: ByteArray, end: ByteArray): FdbFuture<List<ByteArray>> {
    val iterator = com.apple.foundationdb.LocalityUtil.getBoundaryKeys(transaction.delegate, begin, end)
    return collectIterator(iterator) { it }.toFdbFuture()
}

internal actual fun getAddressesForKeyInternal(transaction: Transaction, key: ByteArray): FdbFuture<List<String>> =
    com.apple.foundationdb.LocalityUtil.getAddressesForKey(transaction.delegate, key)
        .thenApply { list -> list.toList() }
        .toFdbFuture()

private fun <T, R> collectIterator(
    iterator: com.apple.foundationdb.async.CloseableAsyncIterator<T>,
    transform: (T) -> R
): CompletableFuture<List<R>> {
    val collected = ArrayList<R>()

    fun step(): CompletableFuture<List<R>> {
        return iterator.onHasNext().thenCompose { hasNext ->
            if (!hasNext) {
                iterator.close()
                CompletableFuture.completedFuture(collected)
            } else {
                collected.add(transform(iterator.next()))
                step()
            }
        }
    }

    return step()
}

internal actual fun openBoundaryKeysIteratorInternal(
    database: Database,
    begin: ByteArray,
    end: ByteArray
): CloseableAsyncIterator<ByteArray> =
    CloseableAsyncIterator(
        com.apple.foundationdb.LocalityUtil.getBoundaryKeys(database.delegate, begin, end),
        { it as ByteArray }
    )

internal actual fun openBoundaryKeysIteratorInternal(
    transaction: Transaction,
    begin: ByteArray,
    end: ByteArray
): CloseableAsyncIterator<ByteArray> =
    CloseableAsyncIterator(
        com.apple.foundationdb.LocalityUtil.getBoundaryKeys(transaction.delegate, begin, end),
        { it as ByteArray }
    )
