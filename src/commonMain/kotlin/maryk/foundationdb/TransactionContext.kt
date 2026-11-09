package maryk.foundationdb

import kotlin.coroutines.EmptyCoroutineContext

/**
 * Shared behaviour of FoundationDB contexts that can execute transactional work.
 *
 * Mirrors the JVM binding's `TransactionContext` contracts while returning coroutine-friendly [FdbFuture]
 * handles for asynchronous paths.
 */
interface TransactionContext : ReadTransactionContext {
    /**
     * Execute [block] in this context with automatic retry semantics for retryable failures.
     *
     * The block runs synchronously on the current thread; any asynchronous work should be awaited
     * through [FdbFuture.await] before returning.
     */
    fun <T> run(block: (Transaction) -> T): T

    /**
     * Execute [block] in this context and return its asynchronous result. The block is responsible
     * for creating an [FdbFuture] that represents its outcome.
     */
    fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T>
}

/**
 * Helper to execute a suspending [block] inside a [TransactionContext], returning its result.
 */
suspend fun <T> TransactionContext.runSuspend(block: suspend (Transaction) -> T): T =
    runSuspend(EmptyCoroutineContext, block)

/**
 * Helper to execute a suspending read-only [block] inside a [TransactionContext].
 */
suspend fun <T> TransactionContext.readSuspend(block: suspend (ReadTransaction) -> T): T =
    (this as ReadTransactionContext).readSuspend(EmptyCoroutineContext, block)

/**
 * Simple implementation helper used by actual platform code to turn a suspending block into an
 * [FdbFuture]. JVM actuals provide the real implementation.
 */
internal expect fun <T> fdbFutureFromSuspend(block: suspend () -> T): FdbFuture<T>

/** Completion hook for internal ownership cleanup, including cancellation before the body starts.
 * The hook must not throw; cleanup failures should be attached to the completion cause.
 */
internal expect fun <T> fdbFutureFromSuspend(onCompletion: (Throwable?) -> Unit, block: suspend () -> T): FdbFuture<T>
