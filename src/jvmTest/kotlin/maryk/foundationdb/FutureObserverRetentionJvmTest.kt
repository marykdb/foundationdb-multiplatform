package maryk.foundationdb

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import maryk.foundationdb.jvm.toFoundationDbFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutureObserverRetentionJvmTest {
    @Test fun cancelledObserversDoNotAccumulateAcrossRepeatedAdapters() = runBlocking {
        supervisorScope {
            val source = CompletableFuture<Int>()
            repeat(1000) {
                val future = source.toFoundationDbFuture()
                val observer = async(start = CoroutineStart.UNDISPATCHED) { future.awaitObserved() }
                observer.cancelAndJoin()
            }
            assertFalse(source.isDone)
            assertTrue(source.numberOfDependents <= 1, "Retained ${source.numberOfDependents} callbacks")
            source.complete(7)
            assertEquals(7, source.toFoundationDbFuture().awaitObserved())
        }
    }

    @Test fun owningAwaitDetachesEvenWhenSourceCancellationIsIgnored() = runBlocking {
        supervisorScope {
            val cancellations = AtomicInteger()
            val source = object : CompletableFuture<Int>() {
                override fun cancel(mayInterruptIfRunning: Boolean): Boolean { cancellations.incrementAndGet(); return false }
            }
            val future = source.toOperationFdbFuture()
            repeat(1000) {
                val waiter = async(start = CoroutineStart.UNDISPATCHED) { future.await() }
                waiter.cancelAndJoin()
            }
            assertEquals(1000, cancellations.get())
            assertFalse(source.isDone)
            assertTrue(source.numberOfDependents <= 1)
            source.complete(8)
            assertEquals(8, future.awaitObserved())
        }
    }

    @Test fun equalFutureObjectsRemainIndependent() = runBlocking {
        class EqualFuture : CompletableFuture<Int>() {
            override fun equals(other: Any?) = other is EqualFuture
            override fun hashCode() = 1
        }
        supervisorScope {
            val first = EqualFuture()
            val second = EqualFuture()
            val one = async(start = CoroutineStart.UNDISPATCHED) { first.toFoundationDbFuture().awaitObserved() }
            val two = async(start = CoroutineStart.UNDISPATCHED) { second.toFoundationDbFuture().awaitObserved() }
            first.complete(1); second.complete(2)
            assertEquals(1, one.await()); assertEquals(2, two.await())
        }
    }

    @Test fun concurrentObservationAndCancellationPreserveRemainingWaiters() = runBlocking {
        withTimeout(10_000) {
            supervisorScope {
                repeat(10) {
                    val source = CompletableFuture<Int>()
                    val future = source.toFoundationDbFuture()
                    val waiters = List(100) { async(Dispatchers.Default) { future.awaitObserved() } }
                    waiters.filterIndexed { index, _ -> index % 2 == 0 }.forEach { it.cancel() }
                    assertTrue(source.numberOfDependents <= 1)
                    assertFalse(source.isCancelled)
                    source.complete(9)
                    waiters.forEachIndexed { index, waiter ->
                        if (index % 2 == 0) try { waiter.await() } catch (_: CancellationException) { }
                        else assertEquals(9, waiter.await())
                    }
                }
            }
        }
    }
}
