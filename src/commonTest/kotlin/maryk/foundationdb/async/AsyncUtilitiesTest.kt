package maryk.foundationdb.async

import kotlin.test.Test
import kotlin.test.assertEquals
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.LocalityUtil
import maryk.foundationdb.Range
import maryk.foundationdb.decodeToUtf8
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend

class AsyncUtilitiesTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun collectAndForEachOverAsyncIterable() = harness.runAndReset {
        val prefix = key("async", "collect")
        (1..4).forEach { idx ->
            database.runSuspend { txn ->
                txn.set(key("async", "collect", "k$idx"), "v$idx".encodeToByteArray())
            }
        }

        val collected = database.readSuspend { rt ->
            AsyncUtil.collect(rt.getRange(Range.startsWith(prefix))).await()
        }
        assertEquals(4, collected.size)

        val visited = mutableListOf<String>()
        database.readSuspend { rt ->
            AsyncUtil.forEach(rt.getRange(Range.startsWith(prefix))) { kv ->
                visited += kv.value.decodeToUtf8()
            }.await()
        }
        assertEquals(listOf("v1", "v2", "v3", "v4"), visited)
    }

    @Test
    fun closeableBoundaryIteratorWorksAcrossTargets() = harness.runAndReset {
        val iterator = LocalityUtil.openBoundaryKeysIterator(
            database,
            byteArrayOf(),
            byteArrayOf(0xFF.toByte())
        )
        val hasNext = iterator.onHasNext().await()
        if (hasNext) iterator.next()
        iterator.cancel()
        iterator.close()
    }

}
