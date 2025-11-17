package maryk.foundationdb.async

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking

class CloseableAsyncIteratorJvmTest {
    @Test
    fun closeableIteratorWrapsListAndClosesCallback() = runBlocking {
        var closeCalls = 0
        val iterator = closeableAsyncIterator(listOf("alpha", "beta")) {
            closeCalls++
        }

        val observed = mutableListOf<String>()
        while (iterator.hasNext()) {
            observed += iterator.next()
        }

        assertEquals(listOf("alpha", "beta"), observed)
        assertFalse(iterator.onHasNext().await())
        assertEquals(0, closeCalls, "close callback should not run before explicit close")

        iterator.close()
        assertEquals(1, closeCalls)
    }

    @Test
    fun closeDelegatesToUnderlyingIterator() = runBlocking {
        val delegate = RecordingCloseableIterator(listOf("x", "y"))
        val iterator = CloseableAsyncIterator(delegate) { (it as String).uppercase() }

        assertEquals("X", iterator.next())
        assertEquals("Y", iterator.next())
        iterator.cancel()
        assertEquals(1, delegate.cancelCount)

        iterator.close()
        assertEquals(1, delegate.closeCount)
    }
}

private class RecordingCloseableIterator<T>(
    items: List<T>
) : com.apple.foundationdb.async.CloseableAsyncIterator<T> {
    private val iterator = items.iterator()
    var closeCount = 0
    var cancelCount = 0

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): T = iterator.next()

    override fun onHasNext(): CompletableFuture<Boolean> =
        CompletableFuture.completedFuture(iterator.hasNext())

    override fun close() {
        closeCount++
    }

    override fun cancel() {
        cancelCount++
    }

    override fun remove() {
        // No-op for tests
    }
}
