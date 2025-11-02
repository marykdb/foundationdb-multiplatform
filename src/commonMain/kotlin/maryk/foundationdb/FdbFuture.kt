package maryk.foundationdb

/**
 * Minimal coroutine-friendly abstraction that models the asynchronous results returned by the
 * FoundationDB client.
 *
 * JVM actuals wrap the Java `CompletableFuture` type, while other platforms can plug in their own
 * event sources. Call [await] to suspend until the result is available.
 */
interface FdbFuture<T> {
    /**
     * Suspend until the result is available or rethrow the failure from the underlying operation.
     */
    suspend fun await(): T

    /**
     * Request cancellation of the underlying operation if possible.
     */
    fun cancel()

    /**
     * @return `true` if the operation has completed (successfully or exceptionally).
     */
    val isDone: Boolean

    /**
     * @return `true` if the operation completed due to cancellation.
     */
    val isCancelled: Boolean
}

private class CompletedFdbFuture<T>(private val value: T) : FdbFuture<T> {
    override suspend fun await(): T = value
    override fun cancel() = Unit
    override val isDone: Boolean get() = true
    override val isCancelled: Boolean get() = false
}

private class FailedFdbFuture<T>(private val cause: Throwable) : FdbFuture<T> {
    override suspend fun await(): T = throw cause
    override fun cancel() = Unit
    override val isDone: Boolean get() = true
    override val isCancelled: Boolean get() = false
}

/**
 * Create an already-completed [FdbFuture] with [value].
 */
fun <T> completedFdbFuture(value: T): FdbFuture<T> = CompletedFdbFuture(value)

/**
 * Create an already-failed [FdbFuture] with [error].
 */
fun <T> failedFdbFuture(error: Throwable): FdbFuture<T> = FailedFdbFuture(error)
