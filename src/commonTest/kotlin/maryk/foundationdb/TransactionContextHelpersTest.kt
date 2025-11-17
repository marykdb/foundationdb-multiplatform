package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionContextHelpersTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun runSuspendAndReadSuspendBridge() = harness.runAndReset {
        val key = key("context", "helper")
        harness.database.runSuspend { txn ->
            txn.set(key, "value".encodeToByteArray())
        }

        val stored = harness.database.readSuspend { rt ->
            rt.get(key).await()
        }
        assertEquals("value", stored?.decodeToUtf8())
    }
}
