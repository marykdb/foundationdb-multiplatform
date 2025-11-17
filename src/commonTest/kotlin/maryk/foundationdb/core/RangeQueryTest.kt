package maryk.foundationdb.core

import kotlin.test.Test
import kotlin.test.assertEquals
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.Range
import maryk.foundationdb.StreamingMode
import maryk.foundationdb.KeySelector
import maryk.foundationdb.async.AsyncUtil
import maryk.foundationdb.decodeToUtf8
import maryk.foundationdb.rangeQuery
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend

class RangeQueryTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun rangeQueryAsListReturnsAllItems() = harness.runAndReset {
        val prefix = key("range", "list")
        database.runSuspend { txn ->
            repeat(3) { idx ->
                txn.set(key("range", "list", "k$idx"), "value$idx".encodeToByteArray())
            }
        }

        val result = database.readSuspend { rt ->
            rt.rangeQuery(Range.startsWith(prefix)).asList().await()
        }

        assertEquals(listOf("value0", "value1", "value2"), result.map { it.value.decodeToUtf8() })
    }

    @Test
    fun rangeQueryIteratorRespectsLimit() = harness.runAndReset {
        val prefix = key("range", "iterator")
        database.runSuspend { txn ->
            repeat(5) { idx ->
                txn.set(key("range", "iterator", "k$idx"), "iter$idx".encodeToByteArray())
            }
        }

        val limited = database.readSuspend { rt ->
            val iterator = rt.rangeQuery(Range.startsWith(prefix), limit = 2).iterator()
            AsyncUtil.collectRemaining(iterator).await()
        }

        assertEquals(listOf("iter0", "iter1"), limited.map { it.value.decodeToUtf8() })
    }

    @Test
    fun transactionRangeQuerySupportsByteBounds() = harness.runAndReset {
        val prefix = key("range", "tx", "bytes")
        database.runSuspend { txn ->
            repeat(3) { idx -> txn.set(key("range", "tx", "bytes", "k$idx"), "tx$idx".encodeToByteArray()) }
        }

        val bounds = Range.startsWith(prefix)
        val values = database.runSuspend { txn ->
            txn.rangeQuery(bounds.begin, bounds.end, limit = 2, reverse = false, streamingMode = StreamingMode.ITERATOR)
                .asList()
                .await()
        }

        assertEquals(listOf("tx0", "tx1"), values.map { it.value.decodeToUtf8() })
    }

    @Test
    fun readTransactionRangeQuerySupportsSelectors() = harness.runAndReset {
        val prefix = key("range", "selector")
        database.runSuspend { txn ->
            repeat(2) { idx -> txn.set(key("range", "selector", "k$idx"), "sel$idx".encodeToByteArray()) }
        }

        val bounds = Range.startsWith(prefix)
        val beginSelector = KeySelector.firstGreaterOrEqual(bounds.begin)
        val endSelector = KeySelector.firstGreaterThan(bounds.end)
        val values = database.readSuspend { rt ->
            rt.rangeQuery(beginSelector, endSelector, limit = 0, reverse = false, streamingMode = StreamingMode.WANT_ALL)
                .asList()
                .await()
        }

        assertEquals(listOf("sel0", "sel1"), values.map { it.value.decodeToUtf8() })
    }

    @Test
    fun transactionRangeQueryHandlesRangeOverload() = harness.runAndReset {
        val prefix = key("range", "range-overload")
        database.runSuspend { txn ->
            repeat(4) { idx -> txn.set(key("range", "range-overload", "k$idx"), "ro$idx".encodeToByteArray()) }
        }

        val range = Range.startsWith(prefix)
        val values = database.runSuspend { txn ->
            txn.rangeQuery(range, limit = 3, reverse = true, streamingMode = StreamingMode.WANT_ALL)
                .asList()
                .await()
        }

        assertEquals(listOf("ro3", "ro2", "ro1"), values.map { it.value.decodeToUtf8() })
    }
}
