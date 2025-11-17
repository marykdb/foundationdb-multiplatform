package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertEquals

class RangeQueryTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun rangeQueryCoversOverloads() = harness.runAndReset {
        val prefix = Tuple.from(namespace, "range-query").pack()
        val keys = (0 until 4).map { Tuple.from(namespace, "range-query", it).pack() }
        database.runSuspend { txn ->
            txn.clear(Range.startsWith(prefix))
            keys.forEachIndexed { index, key ->
                txn.set(key, "value-$index".encodeToByteArray())
            }
        }

        val begin = keys.first()
        val end = keys.last() + 1

        val txnResult = database.runSuspend { txn ->
            txn.rangeQuery(begin, end).asList().await()
        }
        assertEquals(4, txnResult.size)

        val selectorResult = database.runSuspend { txn ->
            val iterator = txn.rangeQuery(
                KeySelector.firstGreaterOrEqual(begin),
                KeySelector.firstGreaterThan(end)
            ).iterator()
            val items = mutableListOf<KeyValue>()
            while (iterator.hasNext()) {
                items += iterator.next()
            }
            items
        }
        assertEquals(4, selectorResult.size)

        val range = Range.startsWith(prefix)
        val readResult = database.readSuspend { rt ->
            rt.rangeQuery(range).asList().await()
        }
        assertEquals(4, readResult.size)
    }

    private operator fun ByteArray.plus(increment: Int): ByteArray {
        val copy = this.copyOf()
        copy[copy.lastIndex] = (copy.last().toInt() + increment).toByte()
        return copy
    }
}
