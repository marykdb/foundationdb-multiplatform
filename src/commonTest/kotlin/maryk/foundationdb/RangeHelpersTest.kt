package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RangeHelpersTest {
    @Test
    fun rangeAndKeySelectors() {
        val begin = byteArrayOf(0x01, 0x02)
        val end = byteArrayOf(0x01, 0x05)
        val range = Range(begin, end)
        assertTrue(range.begin.contentEquals(begin))
        assertTrue(range.end.contentEquals(end))

        val startsWith = Range.startsWith(byteArrayOf(0x10))
        assertTrue(startsWith.begin.first() == 0x10.toByte())

        val selector = KeySelector.firstGreaterThan(begin).add(2)
        val selector2 = KeySelector.lastLessOrEqual(end).add(-1)
        assertTrue(selector.offset > 0)
        assertTrue(selector2.offset < selector.offset)
    }

    @Test
    fun stringUtilValidationAndComparison() {
        StringUtil.validate("simple")
        assertFailsWith<IllegalArgumentException> {
            StringUtil.validate("\uD800")
        }

        val cmp = StringUtil.compareUtf8("alpha", "alphabet")
        assertTrue(cmp < 0)

        val packed = StringUtil.packedSize("abc\u0000def")
        assertEquals(8, packed)
    }

    @Test
    fun iterableComparatorOrdersSequences() {
        val comparator = IterableComparator<Int>()
        assertTrue(comparator.compare(listOf(1, 2), listOf(1, 2, 3)) < 0)
        assertTrue(comparator.compare(listOf(1, 3), listOf(1, 2, 3)) > 0)
        assertEquals(0, comparator.compare(listOf(1, 2, 3), listOf(1, 2, 3)))
    }
}
