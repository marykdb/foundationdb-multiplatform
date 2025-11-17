package maryk.foundationdb

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FdbFutureFactoriesTest {
    @Test
    fun completedFutureExposesState() = runBlocking {
        val future = completedFdbFuture("done")

        assertEquals("done", future.await())
        assertTrue(future.isDone)
        assertFalse(future.isCancelled)

        future.cancel()
        assertTrue(future.isDone, "completed futures remain done after cancel()")
        assertFalse(future.isCancelled, "cancel() on completed future is a no-op")
    }

    @Test
    fun failedFuturePropagatesError() = runBlocking {
        val expected = IllegalStateException("boom")
        val future = failedFdbFuture<String>(expected)

        val thrown = assertFailsWith<IllegalStateException> { future.await() }
        assertEquals(expected, thrown)
        assertTrue(future.isDone)
        assertFalse(future.isCancelled)

        future.cancel()
        assertFalse(future.isCancelled, "cancel() should not flip cancelled flag on failed future")
    }
}
