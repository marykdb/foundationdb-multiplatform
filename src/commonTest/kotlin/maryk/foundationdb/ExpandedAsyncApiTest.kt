package maryk.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.async.closeableAsyncIterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ExpandedAsyncApiTest {
    @Test
    fun collectingMappedReadyResultsDoesNotOverflowAndClosesOnce() = runBlocking {
        var closed = 0
        val original = closeableAsyncIterator((0 until 30_000).toList()) { closed++ }
        val mapped = AsyncUtil.mapIterator(original) { it * 2 }
        val values = AsyncUtil.collectRemaining(mapped).await()
        assertEquals(30_000, values.size)
        assertEquals(59_998, values.last())
        mapped.close()
        original.close()
        assertEquals(1, closed)
    }

    @Test
    fun futureHandlersPreserveResultsErrorsAndDoNotRepeatHandlers() = runBlocking {
        val failure = IllegalStateException("input")
        assertEquals(4, AsyncUtil.applySafely<Int, Int>({ completedFdbFuture(it + 1) }, 3).await())
        assertSame(failure, assertFailsWith<IllegalStateException> { AsyncUtil.applySafely<Int, Int>({ throw failure }, 1).await() })
        assertEquals(Unit, AsyncUtil.whenReady(failedFdbFuture<Int>(failure)).await())
        assertEquals(failure.message, assertFailsWith<IllegalStateException> { AsyncUtil.success(failedFdbFuture<Int>(failure)).await() }.message)
        assertEquals("tag", AsyncUtil.tag(completedFdbFuture(1), "tag").await())
        assertEquals(7, AsyncUtil.composeExceptionally(failedFdbFuture<Int>(failure)) { completedFdbFuture(7) }.await())
        var calls = 0
        val handlerFailure = IllegalArgumentException("handler")
        val result = AsyncUtil.composeHandle<Int, Int>(failedFdbFuture(failure)) { value, error ->
            assertEquals(null, value)
            assertSame(failure, error)
            calls++
            failedFdbFuture(handlerFailure)
        }
        assertEquals(handlerFailure.message, assertFailsWith<IllegalArgumentException> { result.await() }.message)
        assertEquals(1, calls)
    }

    @Test
    fun immediatelyReadyLoopsRemainStackSafeAndCancellable() = runBlocking {
        var count = 0
        AsyncUtil.whileTrue { completedFdbFuture(++count < 30_000) }.await()
        assertEquals(30_000, count)
        val started = CompletableDeferred<Unit>()
        val loop = AsyncUtil.whileTrue { started.complete(Unit); completedFdbFuture(true) }
        started.await()
        loop.cancel()
        withTimeout(2_000) { assertFailsWith<CancellationException> { loop.await() } }
        Unit
    }
}
