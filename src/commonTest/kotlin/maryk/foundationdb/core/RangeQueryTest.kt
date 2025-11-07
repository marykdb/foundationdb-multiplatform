package maryk.foundationdb.core

import kotlin.test.Test
import kotlin.test.assertEquals
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.Range
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
}
