package maryk.foundationdb

import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.Versionstamp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.DurationUnit

class ExpandedValueApiTest {
    @Test
    fun tupleConstructionSlicesOrderingAndIteration() {
        val tuple = Tuple().add("a").add(10L).add(byteArrayOf(9, 8, 7), 1, 2).addAll(Tuple.from(true))
        assertEquals(4, tuple.size)
        assertEquals(10L, tuple.getLong(1))
        assertContentEquals(byteArrayOf(8, 7), tuple.getBytes(2))
        assertEquals(4, tuple.toList().size)
        assertEquals(Tuple.from(10L, byteArrayOf(8, 7), true), tuple.popFront())
        assertEquals(Tuple.from("a", 10L, byteArrayOf(8, 7)), tuple.popBack())
        assertTrue(Tuple().isEmpty())
        assertFailsWith<IllegalStateException> { Tuple().popFront() }
        val encoded = tuple.pack()
        assertEquals(encoded.size, tuple.getPackedSize())
        val buffer = byteArrayOf(99) + encoded + byteArrayOf(98)
        assertEquals(tuple, Tuple.fromBytes(buffer, 1, encoded.size))
        assertEquals(tuple, Tuple.fromItems(tuple))
        assertEquals(tuple.hashCode(), Tuple.fromList(tuple.items).hashCode())
        assertTrue(Tuple.from(-1L) < Tuple.from(0L))
        assertTrue(Tuple.from(byteArrayOf(0x7f)) < Tuple.from(byteArrayOf(0x80.toByte())))
        val nested = Tuple.from(listOf("inside", 12L), null)
        assertEquals(listOf("inside", 12L), nested.getNestedList(0))
        assertEquals(Tuple.from("inside", 12L), nested.getNestedTuple(0))
        assertEquals(null, nested.getNestedTuple(1))
    }

    @Test
    fun tupleRangesAndPrefixedVersionstampsUseJavaWireContract() {
        ensureApiVersionSelected()
        val tuple = Tuple.from("a")
        val payload = byteArrayOf(2, 97, 0)
        assertContentEquals(payload, tuple.pack())
        assertContentEquals(payload + byteArrayOf(0), tuple.range().begin)
        assertContentEquals(payload + byteArrayOf(0xff.toByte()), tuple.range().end)
        val prefix = byteArrayOf(70, 71)
        assertEquals(tuple.range(prefix), Subspace(prefix).range(tuple))
        assertEquals(Tuple().range(prefix), Subspace(prefix).range())
        val stamp = Versionstamp.incomplete(7)
        val stamped = Tuple.from("a", stamp)
        val bytes = Subspace(prefix).packWithVersionstamp(stamped)
        assertEquals(6L, ByteArrayUtil.decodeInt(bytes.copyOfRange(bytes.size - 4, bytes.size) + ByteArray(4)))
        assertEquals(stamped.getPackedSize() + prefix.size, bytes.size)
        assertFailsWith<IllegalArgumentException> { stamped.pack() }
    }

    @Test
    fun incompleteStampEdgesAndValueObjectsMatch() {
        ensureApiVersionSelected()
        val incomplete = Versionstamp.incomplete(0)
        val complete = Versionstamp.complete(ByteArray(10) { 0xff.toByte() }, 65535)
        assertTrue(complete < incomplete)
        assertTrue(Tuple.from(complete) < Tuple.from(incomplete))
        val multiple = Tuple.from(incomplete, Tuple.from(incomplete))
        assertTrue(multiple.hasIncompleteVersionstamp())
        assertEquals(0, multiple.compareTo(multiple))
        assertFailsWith<IllegalArgumentException> { multiple.packWithVersionstamp() }
        assertEquals(incomplete, Versionstamp.fromBytes(incomplete.bytes.copyOf()))
        assertEquals(incomplete.hashCode(), Versionstamp.fromBytes(incomplete.bytes.copyOf()).hashCode())
        val a = Range(byteArrayOf(1), byteArrayOf(2))
        val b = Range(byteArrayOf(1), byteArrayOf(2))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(Subspace(byteArrayOf(1)), Subspace(byteArrayOf(1)))
    }

    @Test
    fun byteUtilitiesUseUnsignedOrderAndLittleEndianIntegers() {
        val source = byteArrayOf(0, 1, 0, 2, 0)
        val parts = ByteArrayUtil.split(source, byteArrayOf(0))
        assertEquals(listOf(emptyList(), listOf<Byte>(1), listOf<Byte>(2), emptyList()), parts.map { it.toList() })
        assertContentEquals(source, ByteArrayUtil.interludeJoin(byteArrayOf(0), parts.toTypedArray()))
        assertTrue(ByteArrayUtil.startsWith(byteArrayOf(), byteArrayOf()))
        assertTrue(ByteArrayUtil.compareUnsigned(byteArrayOf(0x80.toByte()), byteArrayOf(0x7f)) > 0)
        assertContentEquals(byteArrayOf(2), ByteArrayUtil.strinc(byteArrayOf(1, 0xff.toByte())))
        assertContentEquals(byteArrayOf(1, 0), ByteArrayUtil.keyAfter(byteArrayOf(1)))
        assertFailsWith<IllegalArgumentException> { ByteArrayUtil.strinc(byteArrayOf(0xff.toByte())) }
        assertFailsWith<IllegalArgumentException> { ByteArrayUtil.split(source, byteArrayOf()) }
        for (value in listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)) assertEquals(value, ByteArrayUtil.decodeInt(ByteArrayUtil.encodeInt(value)))
        assertEquals(listOf(8, 7, 6, 5, 4, 3, 2, 1), ByteArrayUtil.encodeInt(0x0102030405060708L).map { it.toInt() })
    }

    @Test
    fun metricUnitsAndEnumCodesArePortable() {
        val keeper = MapEventKeeper()
        keeper.increment(FdbEvent.JNI_CALL)
        keeper.time(FdbEvent.RANGE_QUERY_FETCH_TIME_NANOS, 5, DurationUnit.MILLISECONDS)
        assertEquals(1L, keeper.getCount(FdbEvent.JNI_CALL))
        assertEquals(5_000L, keeper.getTime(FdbEvent.RANGE_QUERY_FETCH_TIME_NANOS, DurationUnit.MICROSECONDS))
        assertEquals(6, MutationType.BIT_AND.code())
        assertEquals(MutationType.AND.code(), MutationType.BIT_AND.code())
        assertEquals(-2, StreamingMode.WANT_ALL.code())
        assertEquals(4, StreamingMode.SERIAL.code())
    }
    @Test
    fun standaloneOptionConsumersWorkWithoutNativeHandles() {
        val calls = mutableListOf<Pair<Int, ByteArray?>>()
        val consumer = OptionConsumer { code, bytes -> calls += code to bytes?.copyOf() }
        NetworkOptions(consumer).setClientThreadsPerVersion(3)
        DatabaseOptions(consumer).setTransactionTimeout(4_000)
        TransactionOptions(consumer).setTag("test")
        assertEquals(listOf(65, 500, 800), calls.map { it.first })
        assertEquals(3L, ByteArrayUtil.decodeInt(calls[0].second!!))
        assertEquals(4_000L, ByteArrayUtil.decodeInt(calls[1].second!!))
        assertContentEquals("test".encodeToByteArray(), calls[2].second)
    }

}
