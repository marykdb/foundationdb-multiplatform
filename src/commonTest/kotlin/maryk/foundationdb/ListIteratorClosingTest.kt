package maryk.foundationdb

import kotlinx.coroutines.runBlocking
import maryk.foundationdb.async.CloseableAsyncIterator
import maryk.foundationdb.async.closeableAsyncIterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListIteratorClosingTest {
    @Test fun exhaustionClosesOnceThroughBothReadinessMethodsAndFactories() = runBlocking {
        for (asyncReadiness in listOf(false, true)) for (compatibilityFactory in listOf(false, true)) {
            var closes = 0
            val iterator = if (compatibilityFactory) CloseableAsyncIterator(listOf("alpha", "beta")) { closes++ }
                else closeableAsyncIterator(listOf("alpha", "beta")) { closes++ }
            val observed = mutableListOf<String>()
            while (if (asyncReadiness) iterator.onHasNext().await() else iterator.hasNext()) {
                observed += iterator.next()
            }
            assertEquals(listOf("alpha", "beta"), observed)
            assertEquals(1, closes)
            assertFalse(iterator.hasNext())
            assertFalse(iterator.onHasNext().await())
            assertFailsWith<NoSuchElementException> { iterator.next() }
            iterator.cancel(); iterator.close()
            assertEquals(1, closes)
        }
    }

    @Test fun emptyListClosesWhenExhaustionIsObserved() = runBlocking {
        var closes = 0
        val iterator = closeableAsyncIterator(emptyList<Int>()) { closes++ }
        assertEquals(0, closes)
        assertFalse(iterator.onHasNext().await())
        assertEquals(1, closes)
        iterator.close()
        assertEquals(1, closes)
    }

    @Test fun explicitCloseAndCancellationHideBufferedRows() = runBlocking {
        for (cancel in listOf(false, true)) {
            var closes = 0
            val iterator = closeableAsyncIterator(listOf(1, 2)) { closes++ }
            assertEquals(1, iterator.next())
            if (cancel) iterator.cancel() else iterator.close()
            assertFalse(iterator.hasNext())
            assertFalse(iterator.onHasNext().await())
            assertFailsWith<NoSuchElementException> { iterator.next() }
            iterator.cancel(); iterator.close()
            assertEquals(1, closes)
        }
    }

    @Test fun failedCleanupIsReportedOnceAndStillClosesTheCursor() = runBlocking {
        for (asyncReadiness in listOf(false, true)) {
            var closes = 0
            val iterator = closeableAsyncIterator(emptyList<Int>()) { closes++; error("cleanup failed") }
            val failure = assertFailsWith<IllegalStateException> {
                if (asyncReadiness) iterator.onHasNext().await() else iterator.hasNext()
            }
            assertEquals("cleanup failed", failure.message)
            assertFalse(iterator.hasNext())
            assertFalse(iterator.onHasNext().await())
            iterator.close()
            assertEquals(1, closes)
        }
    }

    @Test fun cancellingCompletedReadinessDoesNotCloseAnAvailableList() = runBlocking {
        var closes = 0
        val iterator = closeableAsyncIterator(listOf(1)) { closes++ }
        val ready = iterator.onHasNext()
        assertTrue(ready.isDone)
        assertTrue(ready.await())
        ready.cancel()
        assertEquals(0, closes)
        assertEquals(1, iterator.next())
        iterator.close()
        assertEquals(1, closes)
    }
}
