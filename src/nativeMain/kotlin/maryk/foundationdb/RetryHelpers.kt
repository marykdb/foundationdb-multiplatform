package maryk.foundationdb

import kotlinx.coroutines.runBlocking

internal fun <T> runBlockingWithRetry(
    createTxn: () -> Transaction,
    needsCommit: Boolean,
    block: (Transaction) -> T
): T = runBlocking {
    executeWithRetry(createTxn, needsCommit) { txn -> block(txn) }
}

internal fun <T> runAsyncWithRetry(
    createTxn: () -> Transaction,
    needsCommit: Boolean,
    block: (Transaction) -> FdbFuture<T>
): FdbFuture<T> = fdbFutureFromSuspend {
    executeWithRetry(createTxn, needsCommit) { txn -> block(txn).await() }
}

internal suspend fun <T> executeWithRetry(
    createTxn: () -> Transaction,
    needsCommit: Boolean,
    block: suspend (Transaction) -> T
): T {
    while (true) {
        val txn = createTxn()
        try {
            val result = block(txn)
            if (needsCommit) {
                txn.commit().await()
            }
            return result
        } catch (error: Throwable) {
            val handled = txn.handleOnError(error)
            if (!handled) throw error
        } finally {
            txn.close()
        }
    }
}

private suspend fun Transaction.handleOnError(error: Throwable): Boolean {
    if (error !is FDBException) return false
    onError(error).await()
    return true
}
