package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.TupleInteger
import maryk.foundationdb.tuple.Versionstamp
import kotlin.math.sign
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ValueParityRegressionTest {
    @OptIn(ExperimentalUuidApi::class)
    @Test fun iterableComparatorUsesTupleOrderForUnicodeBytesNullsAndNestedValues() {
        val comparator = IterableComparator<Any?>()
        assertTrue(comparator.compare(listOf("\uD800\uDC00"), listOf("\uE000")) > 0)
        assertTrue(comparator.compare(listOf(byteArrayOf(0x7f)), listOf(byteArrayOf(0x80.toByte()))) < 0)
        assertTrue(comparator.compare(listOf(null), listOf(byteArrayOf())) < 0)
        assertEquals(0, comparator.compare(listOf(listOf(null, byteArrayOf(1), 2)), listOf(listOf(null, byteArrayOf(1), 2L))))
        assertTrue(comparator.compare(listOf(1L), listOf(1L, null)) < 0)
        assertTrue(comparator.compare(listOf(1L), listOf(1.0)) < 0)
        assertTrue(comparator.compare(emptyList(), listOf(null)) < 0)
        assertEquals(0, IterableComparator<Int>().compare(listOf(1, 2), listOf(1, 2)))
        val ordered = listOf<Any?>(null, byteArrayOf(), byteArrayOf(0x80.toByte()), "a", "\uE000", "\uD800\uDC00",
            listOf(null, byteArrayOf(1), 2L), Tuple.from("nested", 1L), -1L, 0, 1L,
            TupleInteger.parse("18446744073709551616"), -0.0f, 0.0f, Float.NaN, -0.0, Double.NaN,
            false, true, Uuid.parse("ffffffff-ffff-ffff-ffff-ffffffffffff"),
            Versionstamp.complete(ByteArray(10), 1), Versionstamp.incomplete(2))
        // This complete order is independently checked against Java in JavaValueParityTest.
        for (left in ordered.indices) for (right in ordered.indices) {
            assertEquals(left.compareTo(right).sign, comparator.compare(listOf(ordered[left]), listOf(ordered[right])).sign)
        }
    }

    @Test fun comparisonStopsBeforeUnvisitedItems() {
        fun input(first: Long) = Iterable<Any?> {
            var consumed = false
            object : Iterator<Any?> {
                override fun hasNext() = true
                override fun next(): Any? {
                    check(!consumed) { "Comparator read beyond the first differing item" }
                    consumed = true
                    return first
                }
            }
        }
        assertTrue(IterableComparator<Any?>().compare(input(1), input(2)) < 0)
    }

    @Test fun nestedListsVisitOnlyTheComparedPrefix() {
        var visits = 0
        fun nested(first: Long): List<Any?> = object : AbstractList<Any?>() {
            override val size = 1000
            override fun get(index: Int): Any? {
                visits++
                check(index == 0) { "Read beyond the ordering decision" }
                return first
            }
        }
        assertTrue(IterableComparator<Any?>().compare(listOf(nested(1)), listOf(nested(2))) < 0)
        assertEquals(2, visits)
        val a = listOf(listOf(listOf(1L), byteArrayOf(7)))
        val b = listOf(listOf(listOf(2L), byteArrayOf(7)))
        assertTrue(IterableComparator<Any?>().compare(a, b) < 0)
    }

    @Test fun mappedRowsHaveContentEqualityAndKeepDataClassAndKeyValueContracts() {
        fun row() = MappedKeyValue(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), byteArrayOf(4), listOf(KeyValue(byteArrayOf(5), byteArrayOf(6))))
        val first = row()
        val second = row()
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(1, setOf(first, second).size)
        assertEquals("found", mapOf(first to "found")[second])
        val plain: KeyValue = first
        assertContentEquals(byteArrayOf(1), plain.key)
        val (key, value, begin, end, result) = first
        assertContentEquals(first.key, key)
        assertContentEquals(first.value, value)
        assertContentEquals(first.rangeBegin, begin)
        assertContentEquals(first.rangeEnd, end)
        assertEquals(first.rangeResult, result)
        assertEquals(first, first.copy(key = first.key.copyOf(), value = first.value.copyOf()))
        assertNotEquals(first, first.copy(rangeBegin = byteArrayOf(9)))
        assertNotEquals(first, first.copy(rangeEnd = byteArrayOf(9)))
        assertNotEquals(first, first.copy(rangeResult = emptyList()))
        // Do not inherit Java's asymmetric equality between plain and mapped rows.
        val simple = KeyValue(byteArrayOf(1), byteArrayOf(2))
        assertFalse(simple.equals(first))
        assertFalse(first.equals(simple))
    }
}
