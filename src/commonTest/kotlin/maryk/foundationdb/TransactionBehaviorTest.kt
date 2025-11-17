package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionBehaviorTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun `retry loop handles conflicts`() = harness.runAndReset {
        val key = key("retry", "conflict")
        database.run { txn ->
            txn.set(key, "seed".encodeToByteArray())
        }

        // Start a transaction that will conflict: we read value, schedule a write,
        // but another transaction will mutate between read and commit.
        var conflictInjected = false
        var attempts = 0
        database.runSuspend { txn ->
            attempts += 1
            txn.get(key).await()?.decodeToString()
            if (!conflictInjected) {
                conflictInjected = true
                // Bump value in a concurrent transaction to force a single onError retry.
                database.run { other ->
                    other.set(key, "updated".encodeToByteArray())
                }
            }
            txn.set(key, "after-conflict".encodeToByteArray())
        }

        // We should have observed at least one retry.
        assertTrue(attempts >= 2)
        val final = database.readSuspend { txn ->
            txn.get(key).await()?.decodeToString()
        }
        assertEquals("after-conflict", final)
    }
}
