package maryk.foundationdb

import com.apple.foundationdb.Database as JavaDatabase
import com.apple.foundationdb.KeyRangeArrayResult
import com.apple.foundationdb.Range as JavaRange
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalBlobGranules::class)
class ApiAdaptersTest {
    @Test
    fun experimentalBlobControlsPreserveArgumentsAndResults() = runBlocking {
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        val range = Range(byteArrayOf(1), byteArrayOf(4))
        val delegate = Proxy.newProxyInstance(JavaDatabase::class.java.classLoader, arrayOf(JavaDatabase::class.java)) { _, method, args ->
            calls += method.name to args.orEmpty().toList()
            val value: Any? = when (method.name) {
                "purgeBlobGranules" -> byteArrayOf(7)
                "waitPurgeGranulesComplete" -> null
                "verifyBlobRange" -> 123L
                "listBlobbifiedRanges" -> KeyRangeArrayResult(arrayOf(JavaRange(range.begin, range.end)))
                else -> true
            }
            CompletableFuture.completedFuture(value)
        } as JavaDatabase
        val database = Database(delegate)
        assertContentEquals(byteArrayOf(7), database.purgeBlobGranules(range, 42, true).await())
        assertEquals(listOf("purgeBlobGranules"), calls.map { it.first })
        assertEquals(42L, calls.last().second[2])
        assertEquals(true, calls.last().second[3])
        assertTrue(database.blobbifyRange(range).await())
        assertTrue(database.blobbifyRangeBlocking(range).await())
        assertTrue(database.unblobbifyRange(range).await())
        val ranges = database.listBlobbifiedRanges(range, 8).await()
        assertEquals(8, calls.last().second[2])
        assertContentEquals(range.begin, ranges.single().begin)
        assertContentEquals(range.end, ranges.single().end)
        assertEquals(123L, database.verifyBlobRange(range, 99).await())
        assertEquals(99L, calls.last().second[2])
        assertTrue(database.flushBlobRange(range, true, 100).await())
        assertEquals(listOf(true, 100L), calls.last().second.drop(2))
        database.waitPurgeGranulesComplete(byteArrayOf(7)).await()
        assertContentEquals(byteArrayOf(7), calls.last().second.single() as ByteArray)
    }
}
