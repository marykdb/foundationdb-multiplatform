package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FastByteComparisonsTest {
    @Test
    fun compareToOrdersUnsignedBytes() {
        val left = byteArrayOf(0x01, 0x7F)
        val right = byteArrayOf(0x01, 0x80.toByte())
        assertTrue(FastByteComparisons.compareTo(left, 0, left.size, right, 0, right.size) < 0)
        assertEquals(0, FastByteComparisons.compareTo(left, 0, 1, right, 0, 1))
    }

    @Test
    fun comparatorSortsLexicographically() {
        val values = listOf(
            byteArrayOf(0x02),
            byteArrayOf(0x01, 0xFF.toByte()),
            byteArrayOf(0x01, 0x7F)
        )
        val sorted = values.sortedWith(FastByteComparisons.comparator())
        assertEquals(listOf(values[2], values[1], values[0]), sorted)
    }

    @Test
    fun iterableComparatorOrdersSequences() {
        val comparator = IterableComparator<Int>()
        val ordered = listOf(
            listOf(1, 2),
            listOf(1, 2, 3),
            listOf(2)
        ).sortedWith(comparator)

        assertEquals(listOf(listOf(1, 2), listOf(1, 2, 3), listOf(2)), ordered)
        assertTrue(comparator.compare(listOf(1, 3), listOf(1, 2)) > 0)
    }
}
