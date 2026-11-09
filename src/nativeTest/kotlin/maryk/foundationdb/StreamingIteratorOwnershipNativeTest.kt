package maryk.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamingIteratorOwnershipNativeTest {
    private class Page : RangePageData<Int> {
        override val values = listOf(1)
        override val lastKey = byteArrayOf(1)
        override val hasMore = false
    }
    @Test fun cancelCoalescedPageReleasesOwnedReadWithoutHoldingIteratorLock() = runBlocking {
        withTimeout(5000) {
            withContext(Dispatchers.Default) {
                val requested = CompletableDeferred<Unit>()
                val page = CompletableDeferred<RangePageData<Int>>()
                val future = page.asFdbFuture(FutureCancellation.PROPAGATE)
                val iterator = StreamingRangeIterator(KeySelector.firstGreaterOrEqual(byteArrayOf(0)),KeySelector.firstGreaterOrEqual(byteArrayOf(2)),0,false) { _,_,_,_ -> requested.complete(Unit);future }
                val reentered = CompletableDeferred<Boolean>()
                page.invokeOnCompletion { reentered.complete(iterator.onHasNext().isDone) }
                val first = iterator.onHasNext()
                assertSame(first,iterator.onHasNext())
                requested.await()
                iterator.cancel()
                page.join()
                assertTrue(page.isCancelled)
                assertTrue(reentered.await())
                assertFalse(iterator.onHasNext().await())
            }
        }
    }

    @Test fun cancellationRacingPageCompletionCannotExposeLateRows() = runBlocking {
        withTimeout(5000) {
            repeat(100) {
                val requested = CompletableDeferred<Unit>()
                val page = CompletableDeferred<RangePageData<Int>>()
                val future = page.asFdbFuture(FutureCancellation.PROPAGATE)
                val iterator = StreamingRangeIterator(KeySelector.firstGreaterOrEqual(byteArrayOf(0)),KeySelector.firstGreaterOrEqual(byteArrayOf(2)),0,false) { _,_,_,_ -> requested.complete(Unit);future }
                val pending = iterator.onHasNext()
                requested.await()
                val completing = launch(Dispatchers.Default) { page.complete(Page()) }
                iterator.cancel()
                completing.join()
                try { pending.await() } catch (_: CancellationException) { }
                assertFalse(iterator.onHasNext().await())
            }
        }
    }
}
