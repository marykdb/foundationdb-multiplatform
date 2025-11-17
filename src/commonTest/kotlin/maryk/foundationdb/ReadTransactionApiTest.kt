package maryk.foundationdb

import maryk.foundationdb.async.AsyncIterable
import maryk.foundationdb.async.AsyncUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadTransactionApiTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun byteArrayAndSelectorOverloadsReturnData() = harness.runAndReset {
        val prefix = key("read", "overloads")
        database.runSuspend { txn ->
            repeat(5) { idx ->
                txn.set(key("read", "overloads", "k$idx"), "v$idx".encodeToByteArray())
            }
        }

        val range = Range.startsWith(prefix)
        val begin = range.begin
        val end = range.end

        val byteRange = database.readSuspend { rt ->
            rt.getRange(begin, end, limit = 3).collectValues()
        }
        assertEquals(listOf("v0", "v1", "v2"), byteRange.map { it.value.decodeToUtf8() })

        val reversed = database.readSuspend { rt ->
            rt.getRange(begin, end, limit = 2, reverse = true, streamingMode = StreamingMode.WANT_ALL).collectValues()
        }
        assertEquals(listOf("v4", "v3"), reversed.map { it.value.decodeToUtf8() })

        val selectorRange = database.readSuspend { rt ->
            val beginSelector = KeySelector.firstGreaterOrEqual(begin)
            val endSelector = KeySelector.firstGreaterThan(end)
            rt.getRange(beginSelector, endSelector, limit = 2, reverse = false, streamingMode = StreamingMode.ITERATOR).collectValues()
        }
        assertEquals(listOf("v0", "v1"), selectorRange.map { it.value.decodeToUtf8() })

        val readVersion = database.readSuspend { rt -> rt.getReadVersion().await() }
        assertTrue(readVersion > 0)
    }

    @Test
    fun conflictHelpersRespectSnapshotState() = harness.runAndReset {
        val targetKey = key("read", "conflicts", "k1")
        val normalResult = database.readSuspend { rt ->
            val added = rt.addReadConflictKeyIfNotSnapshot(targetKey)
            val snapshotAdded = rt.snapshot().addReadConflictKeyIfNotSnapshot(targetKey)
            added to snapshotAdded
        }
        assertTrue(normalResult.first)
        assertFalse(normalResult.second)

        val rangeAdded = database.readSuspend { rt ->
            rt.addReadConflictRangeIfNotSnapshot(targetKey, key("read", "conflicts", "k2"))
        }
        assertTrue(rangeAdded)
    }

    @Test
    fun rowLimitUnboundedConstantExposed() {
        assertTrue(ReadTransaction.ROW_LIMIT_UNLIMITED <= 0)
    }

    @Test
    fun keySelectorHelpersAdjustOffsets() = harness.runAndReset {
        val target = key("read", "selectors")
        val selector = KeySelector.firstGreaterOrEqual(target)
        val advanced = selector.add(2)
        assertEquals(selector.key.decodeToUtf8(), advanced.key.decodeToUtf8())
        assertEquals(selector.orEqual, advanced.orEqual)
        assertEquals(selector.offset + 2, advanced.offset)
        val last = KeySelector.lastLessOrEqual(target)
        val adjusted = last.add(-1)
        assertEquals(last.offset - 1, adjusted.offset)
    }
}

private suspend fun <T> AsyncIterable<T>.collectValues(): List<T> =
    AsyncUtil.collectRemaining(iterator()).await()
