package maryk.foundationdb

import com.apple.foundationdb.async.AsyncUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

/** Owns the transaction independently of the cancellable result and SDK cleanup stages. */
internal fun <T> runAsyncWithRetry(
    transaction: Transaction,
    executor: Executor,
    block: (Transaction) -> FdbFuture<T>,
): FdbFuture<T> {
    val result = CompletableFuture<T>()
    val active = AtomicReference<CompletableFuture<*>?>()
    val returnValue = AtomicReference<T>()

    fun <R> track(operation: CompletableFuture<R>): CompletableFuture<R> {
        active.set(operation)
        // Cancellation can race with creating a body, commit, or retry future.
        if (result.isCancelled) operation.cancel(true)
        return operation
    }

    result.whenComplete { _, _ ->
        if (result.isCancelled) {
            try {
                transaction.cancel()
            } finally {
                try { transaction.close() } finally { active.get()?.cancel(true) }
            }
        }
    }

    // Keep the SDK's eager first attempt, configured executor, and onError policy.
    // Never cancel this loop or its cleanup observer: onError may still deliver a
    // replacement native handle, which Transaction owns even after cancellation.
    val loop = AsyncUtil.whileTrue(Supplier {
        if (result.isDone) {
            CompletableFuture.completedFuture(false)
        } else {
            val process = track(AsyncUtil.applySafely({ txn -> block(Transaction(txn.delegate)).asCompletableFuture() }, transaction))
            AsyncUtil.composeHandleAsync(
                process.thenComposeAsync({ value ->
                    if (result.isDone) CompletableFuture.completedFuture(false)
                    else track(transaction.commit().asCompletableFuture()).thenApply {
                        returnValue.set(value)
                        false
                    }
                }, executor),
                { value, failure ->
                    when {
                        result.isDone -> CompletableFuture.completedFuture(false)
                        failure == null -> CompletableFuture.completedFuture(value)
                        failure !is RuntimeException -> throw CompletionException(failure)
                        else -> track(transaction.onError(failure).asCompletableFuture()).thenApply { !result.isDone }
                    }
                }, executor,
            )
        }
    }, executor)
    loop.whenComplete { _, failure ->
        val closeFailure = runCatching { transaction.close() }.exceptionOrNull()
        if (failure != null) {
            if (closeFailure != null) failure.addSuppressed(closeFailure)
            result.completeExceptionally(failure)
        } else if (closeFailure != null) result.completeExceptionally(closeFailure)
        else result.complete(returnValue.get())
    }
    return result.toFdbFuture()
}
