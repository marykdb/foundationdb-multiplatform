package maryk.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutureObservationContractTest {
    @Test fun concurrentObservationAndCancellationPreserveRemainingWaiters() = runBlocking {
        withTimeout(10_000) {
            supervisorScope {
                repeat(10) {
                    val release = CompletableDeferred<Int>()
                    val future = fdbFuture { release.await() }
                    val waiters = List(100) { async(Dispatchers.Default) { future.awaitObserved() } }
                    waiters.filterIndexed { index, _ -> index % 2 == 0 }.forEach { it.cancel() }
                    assertFalse(future.isCancelled)
                    release.complete(9)
                    waiters.forEachIndexed { index, waiter ->
                        if (index % 2 == 0) try { waiter.await() } catch (_: CancellationException) { }
                        else assertEquals(9, waiter.await())
                    }
                }
            }
        }
    }

    @Test fun cancellingAnObserverLeavesThePendingOperationAvailable() = runBlocking {
        withTimeout(10_000) {
            supervisorScope {
                val release = CompletableDeferred<Int>()
                val future = fdbFuture { release.await() }
                val observer = async(start = CoroutineStart.UNDISPATCHED) { future.awaitObserved() }
                observer.cancelAndJoin()
                assertFalse(future.isCancelled)
                assertFalse(future.isDone)
                release.complete(7)
                assertEquals(7, future.await())
                assertTrue(future.isDone)
            }
        }
    }
}
