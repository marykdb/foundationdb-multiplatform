package maryk.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.async.closeableAsyncIterator
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.async.asyncIterable
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.subspace.SubspaceLayout
import maryk.foundationdb.subspace.subspace
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.TupleInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DecisionApiTest {
    @Test fun integersRoundTripAcrossTheLongBoundary() {
        val values = listOf("0", "-1", "9223372036854775807", "-9223372036854775808", "9223372036854775808", "-9223372036854775809", "18446744073709551616", "-18446744073709551616")
        for (text in values) {
            val number = TupleInteger.parse(text)
            assertEquals(text, number.toString())
            val tuple = Tuple.from(number)
            val namespace = Subspace(byteArrayOf(1))
            assertContentEquals(namespace.pack(tuple), namespace.pack(number))
            assertContentEquals(namespace.get(tuple).getKey(), namespace.get(number).getKey())
            val decoded = Tuple.fromBytes(tuple.pack())
            assertEquals(number, decoded.getBigInteger(0))
            assertEquals(tuple, decoded)
            assertEquals(tuple.pack().size, tuple.getPackedSize())
        }
        assertEquals(Long.MIN_VALUE, TupleInteger.fromLong(Long.MIN_VALUE).toLongExact())
        assertFailsWith<ArithmeticException> { TupleInteger.parse("9223372036854775808").toLongExact() }
        val bytes = byteArrayOf(1, 2)
        val integer = TupleInteger.fromMagnitude(1, bytes)
        bytes[0] = 7
        integer.magnitudeBytes()[0] = 9
        assertEquals("258", integer.toString())
        assertFailsWith<IllegalArgumentException> { TupleInteger.parse("-") }
        assertFailsWith<IllegalArgumentException> { Tuple.from(TupleInteger.fromMagnitude(1, ByteArray(256) { 1 })).pack() }
    }
    @Test fun extendedIntegerWireBoundariesMatchJava() {
        for (size in listOf(8, 9, 255)) {
            val bytes = ByteArray(size) { 0xff.toByte() }
            for (sign in listOf(-1, 1)) {
                val integer = TupleInteger.fromMagnitude(sign, bytes)
                val header = if (size == 8) byteArrayOf((0x14 + sign * size).toByte())
                    else byteArrayOf((if (sign > 0) 0x1d else 0x0b).toByte(), (if (sign > 0) size else size xor 255).toByte())
                val expected = header + ByteArray(size) { if (sign > 0) 0xff.toByte() else 0 }
                assertContentEquals(expected, Tuple.from(integer).pack())
                assertEquals(integer, Tuple.fromBytes(expected).getBigInteger(0))
            }
        }
    }
    @Test fun layoutFactoriesAreExplicitAndPlatformIndependent() {
        val tuple = Tuple.from("part")
        val raw = byteArrayOf(10, 20)
        assertContentEquals(raw + tuple.pack(), subspace(tuple, raw).getKey())
        assertContentEquals(raw + tuple.pack(), Subspace(tuple, raw).getKey())
        assertContentEquals(tuple.pack() + raw, subspace(tuple, raw, SubspaceLayout.LEGACY_NATIVE).getKey())
    }
    @Test fun nonOwningCombinatorsLeaveInputsRunning() = runBlocking {
        withTimeout(10_000) {
            val gate = CompletableDeferred<Int>()
            val slow = fdbFuture { gate.await() }
            AsyncUtil.whenAny(listOf(completedFdbFuture(1), slow)).await()
            assertFalse(slow.isCancelled)
            val all = AsyncUtil.getAll(listOf(slow))
            assertEquals(null, withTimeoutOrNull(30) { all.awaitObserved() })
            assertFalse(all.isCancelled)
            all.cancel()
            assertFalse(slow.isCancelled)
            gate.complete(8)
            assertEquals(8, slow.await())
            assertEquals(emptyList(), AsyncUtil.getAll<Int>(emptyList()).await())
            assertEquals(listOf(3, 4), AsyncUtil.getAll(linkedSetOf(completedFdbFuture(3), completedFdbFuture(4))).await())
            assertEquals(Unit, AsyncUtil.whenAll(emptyList()).await())
            val empty = AsyncUtil.whenAny(emptyList())
            assertEquals(null, withTimeoutOrNull(30) { empty.awaitObserved() })
            empty.cancel()
        }
    }
    @Test fun whenAllWaitsForFailuresAndRemainingInputs() = runBlocking {
        withTimeout(10_000) {
            val gate = CompletableDeferred<Int>()
            val slow = fdbFuture { gate.await() }
            val combined = AsyncUtil.whenAll(listOf(failedFdbFuture<Int>(IllegalStateException("failure")), slow))
            assertEquals(null, withTimeoutOrNull(30) { combined.awaitObserved() })
            gate.complete(2)
            assertFailsWith<IllegalStateException> { combined.await() }
            assertFalse(slow.isCancelled)
        }
    }
    @Test fun mappingAnIterableRetainsCloseableCleanup() = runBlocking {
        var closed = 0
        val source = asyncIterable<Int> { closeableAsyncIterator(listOf(1, 2)) { closed++ } }
        assertEquals(listOf(2, 3), AsyncUtil.mapIterable(source) { it + 1 }.asList().await())
        assertEquals(1, closed)
    }
    @Test fun customIteratorsAndEventsWorkInCommonCode() = runBlocking {
        val iterable = asyncIterable { object : AsyncIterator<Int> {
            var index = 0
            override fun hasNext() = index < 3
            override suspend fun next() = index++
            override fun onHasNext() = completedFdbFuture(hasNext())
            override fun cancel() { index = 3 }
        } }
        assertEquals(listOf(2, 3, 4), AsyncUtil.mapIterable(iterable) { it + 2 }.asList().await())
        val event = object : Event { override val name = "application.read" }
        val keeper = MapEventKeeper()
        coroutineScope { List(8) { async(Dispatchers.Default) { repeat(1000) { keeper.increment(event) } } }.awaitAll() }
        assertEquals(8000, keeper.getCount(event))
        assertEquals(0, keeper.getCount(FdbEvent.JNI_CALL))
    }
}
