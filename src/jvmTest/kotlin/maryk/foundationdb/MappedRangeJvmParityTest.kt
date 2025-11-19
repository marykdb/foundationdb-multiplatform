package maryk.foundationdb

import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MappedRangeJvmParityTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun mappedRangeHasMoreWhenLimited() = harness.runAndReset {
        val records = Subspace(keyTuple("jvm", "records"))
        val index = Subspace(keyTuple("jvm", "index"))

        database.runSuspend { txn ->
            listOf("a", "b").forEach { item ->
                txn.set(records.pack(Tuple.from(item)), item.encodeToByteArray())
                txn.set(index.pack(Tuple.from(item)), byteArrayOf())
            }
        }

        val mapper = Tuple.from(namespace, "jvm", "records", "{K[2]}", "{...}").pack()

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
        assertTrue(limited.summary.hasMore)
        assertEquals(index.pack(Tuple.from("a")).toList(), limited.values.first().key.toList())
    }
}

