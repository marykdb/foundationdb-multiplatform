package maryk.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.tuple.Tuple
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PerformanceSafetyTest {
    @Test fun cancellingDeferredObservationDetachesSourceRegistration() = runBlocking<Unit> {
        val source = CompletableDeferred<Int>()
        var disposed = 0
        val tracked = object : Deferred<Int> by source {
            override fun invokeOnCompletion(handler: CompletionHandler): DisposableHandle {
                val registration = source.invokeOnCompletion(handler)
                return object : DisposableHandle {
                    override fun dispose() { disposed++; registration.dispose() }
                }
            }
        }
        val observer = tracked.asFdbFuture()
        observer.cancel()
        assertEquals(1, disposed)
        assertFalse(source.isCancelled)
        source.complete(7)
    }

    @Test fun deferredBridgeStartsLazyOperationsAndReflectsCompletedState() = runBlocking {
        withTimeout(5000) {
            val source = async(start = CoroutineStart.LAZY) { 9 }
            assertEquals(9, source.asFdbFuture().await())
            val ready = CompletableDeferred(2).asFdbFuture()
            assertTrue(ready.isDone)
            assertEquals(2, ready.await())
        }
    }

    @Test fun completedGetAllObservesEveryInputDespiteFailure() = runBlocking<Unit> {
        var observed = 0
        val input = object : FdbFuture<Int> {
            override suspend fun await(): Int = awaitObserved()
            override suspend fun awaitObserved(): Int { observed++; return 2 }
            override fun cancel() = error("Observation must not cancel an input")
            override val isDone = true
            override val isCancelled = false
        }
        assertFailsWith<IllegalStateException> { AsyncUtil.getAll(listOf(failedFdbFuture(IllegalStateException("failed")),input)).await() }
        assertEquals(1, observed)
        assertEquals(listOf(2,2), AsyncUtil.getAll(listOf(input,input)).await())
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test fun tupleSlicesRoundTripAndCannotReadBeyondTheirBounds() {
        val random = Random(730)
        repeat(100) {
            val payload = ByteArray(256).also(random::nextBytes)
            val tuple = Tuple.from(random.nextLong(), random.nextDouble(), Uuid.fromLongs(random.nextLong(),random.nextLong()), payload, listOf("nested",null))
            val packed = tuple.pack()
            val wrapped = ByteArray(31) + packed + ByteArray(31)
            val decoded = Tuple.fromBytes(wrapped,31,packed.size)
            assertContentEquals(packed, decoded.pack())
            wrapped.fill(0)
            assertContentEquals(packed,decoded.pack())
        }
        for (value in listOf(1.25f, 3.5, Uuid.fromLongs(1,2))) {
            val bytes = Tuple.from(value).pack()
            // The omitted byte exists physically after the selected slice and must not be read.
            assertFailsWith<IllegalArgumentException> { Tuple.fromBytes(bytes,0,bytes.size-1) }
        }
    }

    @Test fun reverseRangesCrossPagesWithoutSkipping() = FoundationDbTestHarness().runAndReset {
        val prefix = prefixBytes("reverse-pages")
        database.run { tr -> repeat(256) { tr.set(key("reverse-pages",it.toString().padStart(4,'0')),ByteArray(1024)) } }
        val expected = (0 until 256).reversed().map { key("reverse-pages",it.toString().padStart(4,'0')).decodeToString() }
        database.readSuspend { tr ->
            val iterator = tr.getRange(Range.startsWith(prefix),0,true,StreamingMode.SMALL).iterator()
            val actual = mutableListOf<String>()
            try { while(iterator.onHasNext().await()) actual += iterator.next().key.decodeToString() }
            finally { iterator.cancel() }
            assertEquals(expected,actual)
            assertEquals(expected,tr.getRange(Range.startsWith(prefix),0,true,StreamingMode.SMALL).asList().await().map { it.key.decodeToString() })
        }
    }

    @Test fun rangeRowsOwnTheirBuffersAfterFutureCleanupAndKeyMutation() = FoundationDbTestHarness().runAndReset {
        val prefix = prefixBytes("owned-rows")
        database.run { tr -> repeat(128) { tr.set(key("owned-rows",it.toString().padStart(4,'0')),ByteArray(1024){it.toByte()}) } }
        val rows = database.readSuspend { it.getRange(Range.startsWith(prefix)).asList().await() }
        database.run { tr -> tr.clear(Range.startsWith(prefix)) }
        assertEquals(128,rows.size)
        rows.forEach { row -> assertContentEquals(ByteArray(1024){it.toByte()},row.value) }
    }
}
