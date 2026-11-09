package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class CollectionLimitTest {
    @Test fun negativeLimitsAreRejectedBeforeReading() = FoundationDbTestHarness().runAndReset {
        val transaction = database.createTransaction()
        try {
            val begin = byteArrayOf(1)
            val end = byteArrayOf(2)
            val selectorBegin = KeySelector.firstGreaterOrEqual(begin)
            val selectorEnd = KeySelector.firstGreaterOrEqual(end)
            assertFailsWith<IllegalArgumentException> { transaction.collectRange(begin, end, limit = -1) }
            assertFailsWith<IllegalArgumentException> { transaction.collectRange(selectorBegin, selectorEnd, limit = -1) }
            assertFailsWith<IllegalArgumentException> { transaction.collectMappedRange(begin, end, byteArrayOf(), limit = -1) }
            assertFailsWith<IllegalArgumentException> { transaction.collectMappedRange(selectorBegin, selectorEnd, byteArrayOf(), limit = -1) }
        } finally { transaction.close() }
    }

    @Test fun negativeStreamingLimitsAreRejectedBeforeReading() = FoundationDbTestHarness().runAndReset {
        val transaction = database.createTransaction()
        try {
            val begin = byteArrayOf(1)
            val end = byteArrayOf(2)
            val first = KeySelector.firstGreaterOrEqual(begin)
            val last = KeySelector.firstGreaterOrEqual(end)
            val range = Range(begin, end)
            assertFailsWith<IllegalArgumentException> { transaction.getRange(begin, end, -1) }
            assertFailsWith<IllegalArgumentException> { transaction.getRange(range, -1, false, StreamingMode.ITERATOR) }
            assertFailsWith<IllegalArgumentException> { transaction.getRange(first, last, -1, true) }
            assertFailsWith<IllegalArgumentException> { transaction.getMappedRange(begin, end, byteArrayOf(), -1) }
            assertFailsWith<IllegalArgumentException> { transaction.getMappedRange(first, last, byteArrayOf(), -1, false, StreamingMode.ITERATOR) }
        } finally { transaction.close() }
    }

    @Test fun maximumCollectionLimitCanReadSmallRanges() = FoundationDbTestHarness().runAndReset {
        database.run { it.set(key("row"), byteArrayOf(1)) }
        val range = Range.startsWith(prefixBytes())
        val rows = database.readSuspend { it.collectRange(range, limit = Int.MAX_VALUE).await() }
        assertEquals(1, rows.values.size)
        assertFalse(rows.hasMore)
    }

}
