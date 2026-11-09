package maryk.foundationdb

import kotlinx.coroutines.runBlocking
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.async.ListAsyncIterator
import maryk.foundationdb.async.ownedCloseableIterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IteratorCleanupTest {
    @Test
    fun closeableIteratorCancelRunsCloseActionOnce() {
        var closeCalls = 0
        var delegateCancelCalls = 0
        val delegate = object : ListAsyncIterator<String>() {
            override fun cancel() {
                delegateCancelCalls++
            }
        }
        val iterator = ownedCloseableIterator(delegate) {
            closeCalls++
        }

        iterator.cancel()
        iterator.cancel()
        iterator.close()

        assertEquals(1, closeCalls)
        assertEquals(1, delegateCancelCalls)
    }

    @Test
    fun asyncCollectCancelsIteratorOnFailure() {
        runBlocking {
            var cancelCalls = 0
            val iterator = object : ListAsyncIterator<String>() {
                override fun onHasNext(): FdbFuture<Boolean> =
                    failedFdbFuture(IllegalStateException("hasNext failed"))

                override fun cancel() {
                    cancelCalls++
                }
            }

            assertFailsWith<IllegalStateException> {
                AsyncUtil.collectRemaining(iterator).await()
            }

            assertEquals(1, cancelCalls)
        }
    }

    @Test
    fun asyncForEachCancelsIteratorOnConsumerFailure() {
        runBlocking {
            var cancelCalls = 0
            var emitted = false
            val iterator = object : ListAsyncIterator<String>() {
                override fun onHasNext(): FdbFuture<Boolean> =
                    completedFdbFuture(!emitted)

                override suspend fun next(): String {
                    emitted = true
                    return "value"
                }

                override fun cancel() {
                    cancelCalls++
                }
            }

            assertFailsWith<IllegalStateException> {
                AsyncUtil.forEachRemaining(iterator) {
                    throw IllegalStateException("consumer failed")
                }.await()
            }

            assertEquals(1, cancelCalls)
        }
    }
}
