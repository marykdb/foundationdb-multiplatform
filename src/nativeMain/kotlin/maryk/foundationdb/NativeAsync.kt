@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDBFuture
import foundationdb.c.fdb_future_cancel
import foundationdb.c.fdb_future_destroy
import foundationdb.c.fdb_future_get_error
import foundationdb.c.fdb_future_set_callback
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private var supervisorJob = SupervisorJob()
private var futureScope = CoroutineScope(supervisorJob + Dispatchers.Default)

internal class NativeFuture<T>(
    private val pointer: CPointer<FDBFuture>,
    private val extractor: MemScope.(CPointer<FDBFuture>) -> T
) : FdbFuture<T> {
    private val deferred = CompletableDeferred<T>()
    private val ref = StableRef.create(this)

    init {
        checkError(fdb_future_set_callback(pointer, callback, ref.asCPointer()))
    }

    private fun completeFromCallback(future: CPointer<FDBFuture>) {
        futureScope.launch {
            try {
                val result = memScoped {
                    val error = fdb_future_get_error(future)
                    if (error != 0) throw FDBException(error)
                    extractor(future)
                }
                deferred.complete(result)
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            } finally {
                fdb_future_destroy(future)
                ref.dispose()
            }
        }
    }

    override suspend fun await(): T = deferred.await()

    override fun cancel() {
        fdb_future_cancel(pointer)
    }

    override val isDone: Boolean get() = deferred.isCompleted
    override val isCancelled: Boolean get() = deferred.isCancelled

    companion object {
        private val callback = staticCFunction { future: CPointer<FDBFuture>?, userData: COpaquePointer? ->
            if (future != null && userData != null) {
                val owner = userData.asStableRef<NativeFuture<*>>()
                owner.get().completeFromCallback(future)
            }
        }
    }
}

internal actual fun <T> fdbFutureFromSuspend(block: suspend () -> T): FdbFuture<T> {
    val deferred = CompletableDeferred<T>()
    val job = futureScope.launch {
        try {
            deferred.complete(block())
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
        }
    }
    deferred.invokeOnCompletion {
        if (deferred.isCancelled) job.cancel()
    }
    return object : FdbFuture<T> {
        override suspend fun await(): T = deferred.await()
        override fun cancel() { deferred.cancel() }
        override val isDone: Boolean get() = deferred.isCompleted
        override val isCancelled: Boolean get() = deferred.isCancelled
    }
}

internal fun <T> FdbFuture<T>.awaitBlocking(): T = runBlocking { await() }

internal fun resetNativeFutureScope() {
    supervisorJob.cancel()
    supervisorJob = SupervisorJob()
    futureScope = CoroutineScope(supervisorJob + Dispatchers.Default)
}
