package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RangeResultSummaryTest {
    @Test
    fun exposesHasMoreShortcut() {
        val summary = RangeResultSummary(lastKey = "last".encodeToByteArray(), keyCount = 5, hasMore = true)
        val rangeResult = RangeResult(
            values = listOf(KeyValue("k".encodeToByteArray(), "v".encodeToByteArray())),
            summary = summary
        )
        assertTrue(rangeResult.hasMore)
        assertEquals(summary, rangeResult.summary)
        assertEquals("last", summary.lastKey?.decodeToString())
    }
}
