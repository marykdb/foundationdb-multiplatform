package maryk.foundationdb.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.StreamingMode
import maryk.foundationdb.collectMappedRange
import maryk.foundationdb.decodeToUtf8
import maryk.foundationdb.runSuspend
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.tuple.Tuple

class MappedRangeTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun collectMappedRangeReturnsSecondaryResults() = harness.runAndReset {
        val records = Subspace(keyTuple("records"))
        val index = Subspace(keyTuple("index"))

        database.runSuspend { txn ->
            txn.set(records.pack(Tuple.from("alpha")), "A".encodeToByteArray())
            txn.set(records.pack(Tuple.from("beta")), "B".encodeToByteArray())
            txn.set(index.pack(Tuple.from("alpha")), byteArrayOf())
            txn.set(index.pack(Tuple.from("beta")), byteArrayOf())
        }

        val mapper = Tuple.from(namespace, "records", "{K[2]}", "{...}").pack()

        val result = database.runSuspend { txn ->
            txn.collectMappedRange(
                range = index.range(),
                mapper = mapper,
                streamingMode = StreamingMode.WANT_ALL
            ).await()
        }

        assertEquals(2, result.values.size)
        val first = result.values.first()
        assertContentEquals(index.pack(Tuple.from("alpha")), first.key)
        assertEquals(1, first.rangeResult.size)
        assertContentEquals(records.pack(Tuple.from("alpha")), first.rangeResult.first().key)
        assertEquals("A", first.rangeResult.first().value.decodeToUtf8())
        assertEquals(false, result.summary.hasMore)
    }

    @Test
    fun collectMappedRangeHonorsLimit() = harness.runAndReset {
        val records = Subspace(keyTuple("records-limit"))
        val index = Subspace(keyTuple("index-limit"))

        database.runSuspend { txn ->
            repeat(3) { idx ->
                txn.set(records.pack(Tuple.from("item-$idx")), "value-$idx".encodeToByteArray())
                txn.set(index.pack(Tuple.from("item-$idx")), byteArrayOf())
            }
        }

        val mapper = Tuple.from(namespace, "records-limit", "{K[2]}", "{...}").pack()

        val limited = database.runSuspend { txn ->
            txn.collectMappedRange(
                begin = index.range().begin,
                end = index.range().end,
                mapper = mapper,
                limit = 1,
                streamingMode = StreamingMode.WANT_ALL
            ).await()
        }

        assertEquals(1, limited.values.size)
        assertContentEquals(index.pack(Tuple.from("item-0")), limited.values.first().key)
        assertEquals(false, limited.summary.hasMore)
    }
}
