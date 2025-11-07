package maryk.foundationdb

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FdbFutureCommonTest {
    @Test
    fun completedFutureYieldsValue() = runBlocking {
        val future = completedFdbFuture(42)
        assertEquals(42, future.await())
    }

    @Test
    fun failedFutureThrowsOriginalCause() = runBlocking {
        val failure = IllegalStateException("boom")
        val future = failedFdbFuture<Int>(failure)
        val error = assertFailsWith<IllegalStateException> {
            future.await()
        }
        assertEquals("boom", error.message)
    }
}
