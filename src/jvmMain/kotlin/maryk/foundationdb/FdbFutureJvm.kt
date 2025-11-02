package maryk.foundationdb

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val futureScope = CoroutineScope(SupervisorJobWrapper.job + Dispatchers.Default)

private object SupervisorJobWrapper {
    val job = SupervisorJob()
}

internal class CompletableFdbFuture<T>(internal val delegate: CompletableFuture<T>) : FdbFuture<T> {
    override suspend fun await(): T = suspendCancellableCoroutine { cont ->
        delegate.whenComplete { value, throwable ->
            if (throwable != null) {
                cont.resumeWithException(throwable.unwrapCompletionException())
            } else {
                cont.resume(value)
            }
        }
        cont.invokeOnCancellation {
            delegate.cancel(true)
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

internal fun <T> CompletableFuture<T>.toFdbFuture(): FdbFuture<T> = CompletableFdbFuture(this)

internal actual fun <T> fdbFutureFromSuspend(block: suspend () -> T): FdbFuture<T> {
    val future = CompletableFuture<T>()
    val job = futureScope.launch {
        try {
            future.complete(block())
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }
    future.whenComplete { _, _ ->
        if (future.isCancelled) {
            job.cancel()
        }
    }
    return CompletableFdbFuture(future)
}

internal fun <T> FdbFuture<T>.asCompletableFuture(): CompletableFuture<T> = when (this) {
    is CompletableFdbFuture<T> -> this.delegate
    else -> {
        val future = CompletableFuture<T>()
        futureScope.launch {
            try {
                future.complete(this@asCompletableFuture.await())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        future
    }
}

private fun Throwable.unwrapCompletionException(): Throwable = when (this) {
    is CompletionException -> this.cause ?: this
    else -> this
}
