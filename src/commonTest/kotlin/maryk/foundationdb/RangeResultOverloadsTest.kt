package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RangeResultOverloadsTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun collectRangeSupportsByteArraysAndSelectors() = harness.runAndReset {
        val prefix = key("range", "collect")
        database.runSuspend { txn ->
            repeat(4) { idx ->
                txn.set(key("range", "collect", "k$idx"), "value$idx".encodeToByteArray())
            }
        }

        val bounds = Range.startsWith(prefix)
        val byteResult = database.readSuspend { rt ->
            rt.collectRange(bounds.begin, bounds.end, limit = 2, reverse = false, streamingMode = StreamingMode.WANT_ALL).await()
        }
        assertEquals(2, byteResult.values.size)
        assertEquals(byteResult.values.size, byteResult.summary.keyCount)

        val selectorResult = database.readSuspend { rt ->
            val beginSelector = KeySelector.firstGreaterOrEqual(bounds.begin)
            val endSelector = KeySelector.firstGreaterThan(bounds.end)
            rt.collectRange(beginSelector, endSelector, limit = 0, reverse = false, streamingMode = StreamingMode.ITERATOR).await()
        }
        assertEquals(4, selectorResult.summary.keyCount)
        assertEquals("value3", selectorResult.values.last().value.decodeToUtf8())
        assertTrue(!selectorResult.hasMore)
    }

    @Test
    fun collectRangeWithRangeOverload() = harness.runAndReset {
        val prefix = key("range", "collect-range")
        database.runSuspend { txn ->
            repeat(3) { idx ->
                txn.set(key("range", "collect-range", "k$idx"), "rr$idx".encodeToByteArray())
            }
        }

        val range = Range.startsWith(prefix)
        val result = database.readSuspend { rt ->
            rt.collectRange(range, limit = 0, reverse = true, streamingMode = StreamingMode.WANT_ALL).await()
        }
        assertEquals(3, result.values.size)
        assertEquals("rr2", result.values.first().value.decodeToUtf8())
    }
}
