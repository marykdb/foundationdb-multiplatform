package maryk.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlin.coroutines.CoroutineContext

/** Whether cancelling an adapter also cancels the operation it observes. */
enum class FutureCancellation { OBSERVE, PROPAGATE }

/** A child of [scope]. Observation is non-owning unless cancellation propagation is requested. */
fun <T> FdbFuture<T>.asDeferred(
    scope: CoroutineScope,
    cancellation: FutureCancellation = FutureCancellation.OBSERVE,
): Deferred<T> {
    val result = scope.async { awaitObserved() }
    if (cancellation == FutureCancellation.PROPAGATE) {
        result.invokeOnCompletion { cause -> if (cause is CancellationException) cancel() }
    }
    return result
}

/** Observe a Deferred; cancellation of the adapter owns the source only when explicitly requested. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Deferred<T>.asFdbFuture(cancellation: FutureCancellation = FutureCancellation.OBSERVE): FdbFuture<T> {
    val source = this
    val observation = CompletableDeferred<T>()
    val registration = source.invokeOnCompletion { error ->
        if (error != null) observation.completeExceptionally(error)
        else try { observation.complete(source.getCompleted()) }
        catch (failure: Throwable) { observation.completeExceptionally(failure) }
    }
    observation.invokeOnCompletion { cause ->
        registration.dispose()
        if (cancellation == FutureCancellation.PROPAGATE && cause is CancellationException) source.cancel()
    }
    // The former awaiting observer also started lazy Deferred operations.
    source.start()
    return object : FdbFuture<T> {
        override suspend fun awaitObserved(): T = observation.await()
        override suspend fun await(): T = try { observation.await() }
        catch (error: CancellationException) { cancel(); throw error }
        override fun cancel() = observation.cancel()
        override val isDone: Boolean get() = observation.isCompleted
        override val isCancelled: Boolean get() = observation.isCompleted && observation.getCompletionExceptionOrNull() is CancellationException
    }
}

/**
 * Run every retry attempt in the caller's structured scope with [context] overrides.
 * SDK/network callbacks retain their platform execution policy; this controls the suspending block.
 * A Job override is rejected to keep cancellation attached to the caller.
 */
suspend fun <T> TransactionContext.runSuspend(context: CoroutineContext, block: suspend (Transaction) -> T): T {
    require(context[Job] == null) { "Pass context elements without a Job" }
    return supervisorScope {
        runAsync { transaction -> async(context) { block(transaction) }.asFdbFuture(FutureCancellation.PROPAGATE) }.await()
    }
}

/** Read-only equivalent of context-aware [runSuspend], including retry isolation. */
suspend fun <T> ReadTransactionContext.readSuspend(context: CoroutineContext, block: suspend (ReadTransaction) -> T): T {
    require(context[Job] == null) { "Pass context elements without a Job" }
    return supervisorScope {
        readAsync { transaction -> async(context) { block(transaction) }.asFdbFuture(FutureCancellation.PROPAGATE) }.await()
    }
}
