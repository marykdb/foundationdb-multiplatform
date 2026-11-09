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
    // onError resets the same native transaction while retaining its retry count,
    // timeout and backoff state. Recreating it here would reset those limits.
    val txn = createTxn()
    try {
        while (true) {
            try {
                val result = block(txn)
                if (needsCommit) txn.commit().await()
                return result
            } catch (error: Throwable) {
                if (!txn.handleOnError(error)) throw error
            }
        }
    } finally {
        txn.close()
    }
}

private suspend fun Transaction.handleOnError(error: Throwable): Boolean {
    if (error !is FDBException) return false
    onError(error).await()
    return true
}
