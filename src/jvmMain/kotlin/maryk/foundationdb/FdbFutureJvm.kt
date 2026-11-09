package maryk.foundationdb

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val futureScope = CoroutineScope(SupervisorJobWrapper.job + Dispatchers.Default)

private object SupervisorJobWrapper {
    val job = SupervisorJob()
}

internal class CompletableFdbFuture<T>(
    internal val delegate: CompletableFuture<T>,
    private val shareAcrossAdapters: Boolean
) : FdbFuture<T> {
    constructor(delegate: CompletableFuture<T>) : this(delegate, true)
    private val observation = if (shareAcrossAdapters) null else AtomicReference<FutureObservation<T>?>(null)

    override suspend fun awaitObserved(): T = awaitResult(propagateCancellation = false)
    override suspend fun await(): T = awaitResult(propagateCancellation = true)

    private suspend fun awaitResult(propagateCancellation: Boolean): T {
        try { currentCoroutineContext().ensureActive() }
        catch (error: CancellationException) {
            if (propagateCancellation) cancel()
            throw error
        }
        if (delegate.isDone) return try { delegate.join() }
        catch (error: Throwable) { throw error.unwrapCompletionException() }
        val shared = if (shareAcrossAdapters) sharedObservation(delegate) else operationObservation()
        return shared.await(propagateCancellation)
    }

    /** Fresh operations need no global identity index on the hot read path. */
    private fun operationObservation(): FutureObservation<T> {
        val slot = checkNotNull(observation)
        while (true) {
            slot.get()?.let { return it }
            val created = FutureObservation(delegate) { finished -> slot.compareAndSet(finished, null) }
            if (slot.compareAndSet(null, created)) return created
        }
    }

    override fun cancel() {
        delegate.cancel(true)
    }

    override val isDone: Boolean
        get() = delegate.isDone

    override val isCancelled: Boolean
        get() = delegate.isCancelled
}

/** A single callback fans out to removable waiters; cancellation never leaves a waiter behind. */
private class FutureObservation<T>(
    private val source: CompletableFuture<T>,
    private val onComplete: (FutureObservation<T>) -> Unit
) {
    private var first: CancellableContinuation<T>? = null
    private var others: MutableSet<CancellableContinuation<T>>? = null
    private var registered = false
    private var finished = false
    private var value: T? = null
    private var failure: Throwable? = null

    suspend fun await(propagateCancellation: Boolean): T = suspendCancellableCoroutine { continuation ->
        var register = false
        var immediate = false
        var completedValue: T? = null
        var completedFailure: Throwable? = null
        synchronized(this) {
            if (finished) {
                immediate = true
                completedValue = value
                completedFailure = failure
            } else {
                if (first == null) first = continuation
                else (others ?: HashSet<CancellableContinuation<T>>().also { others = it }).add(continuation)
                if (!registered) { registered = true; register = true }
            }
        }
        continuation.invokeOnCancellation {
            synchronized(this) {
                if (first === continuation) first = null else others?.remove(continuation)
                if (others?.isEmpty() == true) others = null
            }
            if (propagateCancellation) source.cancel(true)
        }
        if (immediate) resume(continuation, completedValue, completedFailure)
        else if (register) {
            // A completed source may invoke this callback synchronously: no monitor is held.
            try { source.whenComplete { result, error -> complete(result, error?.unwrapCompletionException()) } }
            catch (error: Throwable) { complete(null, error) }
        }
    }

    private fun complete(result: T?, error: Throwable?) {
        val single: CancellableContinuation<T>?
        val remaining: Set<CancellableContinuation<T>>?
        synchronized(this) {
            if (finished) return
            finished = true
            value = result
            failure = error
            single = first
            remaining = others
            first = null
            others = null
        }
        // Cache cleanup and all continuation callbacks run outside the observation monitor.
        var callbackFailure: Throwable? = null
        try { onComplete(this) } catch (failure: Throwable) { callbackFailure = failure }
        fun deliver(waiter: CancellableContinuation<T>) {
            try { resume(waiter, result, error) }
            catch (failure: Throwable) {
                val previous = callbackFailure
                if (previous == null) callbackFailure = failure
                else if (previous !== failure) previous.addSuppressed(failure)
            }
        }
        if (single != null) deliver(single)
        remaining?.forEach(::deliver)
        callbackFailure?.let { throw it }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resume(waiter: CancellableContinuation<T>, result: T?, error: Throwable?) {
        if (error == null) waiter.resume(result as T) else waiter.resumeWithException(error)
    }
}

/** Identity weak keys/values do not retain abandoned operations, even with custom source equality. */
private class ObservationSource(source: CompletableFuture<*>, queue: ReferenceQueue<CompletableFuture<*>>? = null) :
    WeakReference<CompletableFuture<*>>(source, queue) {
    private val identityHash = System.identityHashCode(source)
    override fun hashCode(): Int = identityHash
    override fun equals(other: Any?): Boolean = this === other ||
        (other is ObservationSource && get()?.let { it === other.get() } == true)
}

private val observationLock = Any()
private val collectedSources = ReferenceQueue<CompletableFuture<*>>()
private val pendingObservations = HashMap<ObservationSource, WeakReference<FutureObservation<*>>>()

private fun <T> sharedObservation(source: CompletableFuture<T>): FutureObservation<T> = synchronized(observationLock) {
    var expired = collectedSources.poll()
    while (expired != null) {
        pendingObservations.remove(expired as ObservationSource)
        expired = collectedSources.poll()
    }
    val key = ObservationSource(source, collectedSources)
    @Suppress("UNCHECKED_CAST")
    val existing = pendingObservations[key]?.get() as FutureObservation<T>?
    existing ?: FutureObservation(source) { completed ->
        synchronized(observationLock) {
            if (pendingObservations[key]?.get() === completed) pendingObservations.remove(key)
        }
    }.also { pendingObservations[key] = WeakReference(it) }
}

internal fun <T> CompletableFuture<T>.toFdbFuture(): FdbFuture<T> = CompletableFdbFuture(this)

/** Only for fresh result stages/SDK operation futures, rather than arbitrary shared Java futures. */
internal fun <T> CompletableFuture<T>.toOperationFdbFuture(): FdbFuture<T> = CompletableFdbFuture(this, false)

internal actual fun <T> fdbFutureFromSuspend(block: suspend () -> T): FdbFuture<T> = coroutineFuture(block, null)

internal actual fun <T> fdbFutureFromSuspend(onCompletion: (Throwable?) -> Unit, block: suspend () -> T): FdbFuture<T> =
    coroutineFuture(block, onCompletion)

private fun <T> coroutineFuture(block: suspend () -> T, onCompletion: ((Throwable?) -> Unit)?): FdbFuture<T> {
    val future = CompletableFuture<T>()
    val job = futureScope.launch {
        try {
            future.complete(block())
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }
    future.whenComplete { _, error ->
        if (future.isCancelled) job.cancel()
        onCompletion?.invoke(error?.unwrapCompletionException())
    }
    return future.toOperationFdbFuture()
}

internal fun <T> FdbFuture<T>.asCompletableFuture(): CompletableFuture<T> = when (this) {
    is CompletableFdbFuture<T> -> this.delegate
    else -> {
        val future = CompletableFuture<T>()
        val job = futureScope.launch {
            try {
                future.complete(this@asCompletableFuture.await())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        future.whenComplete { _, _ ->
            if (future.isCancelled) {
                this@asCompletableFuture.cancel()
                job.cancel()
            }
        }
        future
    }
}

private fun Throwable.unwrapCompletionException(): Throwable = when (this) {
    is CompletionException -> this.cause ?: this
    else -> this
}

/** Preserve native cancellation when adapting a Java future's result type. */
internal fun <T, R> CompletableFuture<T>.mapToFdbFuture(transform: (T) -> R): FdbFuture<R> {
    val mapped = thenApply(transform)
    mapped.whenComplete { _, _ -> if (mapped.isCancelled) cancel(true) }
    return mapped.toOperationFdbFuture()
}
