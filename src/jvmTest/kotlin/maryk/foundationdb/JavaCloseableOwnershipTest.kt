package maryk.foundationdb

import com.apple.foundationdb.async.CloseableAsyncIterator as JavaCloseableAsyncIterator
import java.util.concurrent.CompletableFuture
import maryk.foundationdb.async.CloseableAsyncIterator
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaCloseableOwnershipTest {
    @Test fun cancellingAndClosingTheAdapterClosesTheJavaOwnerOnce() {
        var closes = 0
        val source = object : JavaCloseableAsyncIterator<Int> {
            override fun hasNext() = false
            override fun next() = 1
            override fun onHasNext() = CompletableFuture.completedFuture(false)
            override fun remove() = error("Unsupported")
            override fun close() { closes++ }
        }
        val adapter = CloseableAsyncIterator(source) { it as Int }
        adapter.cancel(); adapter.cancel(); adapter.close()
        assertEquals(1, closes)
    }
}
