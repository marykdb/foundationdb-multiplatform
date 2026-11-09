package maryk.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maryk.foundationdb.async.AsyncUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AsyncHandlerOwnershipTest {
    private class Input : FdbFuture<Int> {
        val gate = CompletableDeferred<Int>()
        val entered = CompletableDeferred<Unit>()
        val exited = CompletableDeferred<Unit>()
        var cancels = 0
        override suspend fun awaitObserved(): Int {
            entered.complete(Unit)
            try { return gate.await() } finally { exited.complete(Unit) }
        }
        override suspend fun await(): Int = try { awaitObserved() }
            catch (error: CancellationException) { cancel(); throw error }
        override fun cancel() { cancels++; gate.cancel() }
        override val isDone get() = gate.isCompleted
        override val isCancelled get() = gate.isCancelled
    }

    @Test fun cancellingHandlersDoesNotCancelBorrowedInputs() = runBlocking {
        withTimeout(10_000) {
            val operations: List<(FdbFuture<Int>) -> FdbFuture<*>> = listOf(
                { AsyncUtil.success(it) }, { AsyncUtil.whenReady(it) }, { AsyncUtil.tag(it, "tag") },
                { AsyncUtil.composeExceptionally(it) { completedFdbFuture(0) } },
                { AsyncUtil.composeHandle(it) { value, _ -> completedFdbFuture(value ?: 0) } },
                { AsyncUtil.composeHandleAsync(it) { value, _ -> completedFdbFuture(value ?: 0) } },
            )
            for ((index, operation) in operations.withIndex()) {
                val source = Input()
                try {
                    val result = operation(source)
                    source.entered.await()
                    result.cancel()
                    source.exited.await()
                    assertFalse(source.isCancelled, "Operation $index cancelled its input")
                    assertEquals(0, source.cancels)
                    source.gate.complete(7)
                    assertEquals(7, source.awaitObserved())
                } finally { source.gate.complete(7) }
            }
        }
    }

    @Test fun cancellingCompositionDoesNotCancelTheBorrowedHandlerFuture() = runBlocking {
        withTimeout(10_000) {
            for (mode in 0..2) {
                val handler = Input()
                try {
                    val result = when (mode) {
                        0 -> AsyncUtil.composeHandle(completedFdbFuture(1)) { _, _ -> handler }
                        1 -> AsyncUtil.composeHandleAsync(completedFdbFuture(1)) { _, _ -> handler }
                        else -> AsyncUtil.composeExceptionally(failedFdbFuture<Int>(IllegalStateException("input"))) { handler }
                    }
                    handler.entered.await()
                    result.cancel()
                    handler.exited.await()
                    assertFalse(handler.isCancelled)
                    assertEquals(0, handler.cancels)
                    handler.gate.complete(9)
                    assertEquals(9, handler.awaitObserved())
                } finally { handler.gate.complete(9) }
            }
        }
    }
}
