package maryk.foundationdb.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.decodeToUtf8
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend

class OptionsDslIntegrationTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun databaseOptionsConfigureApplies() = harness.runAndReset {
        val key = key("options", "db", "configure")
        database.options().configure {
            snapshotReadYourWritesEnable()
            transactionRetryLimit(3)
            transactionTimeout(2_000)
        }

        database.runSuspend { txn ->
            txn.set(key, "configured".encodeToByteArray())
        }

        val stored = database.readSuspend { rt -> rt.get(key).await() }
        assertEquals("configured", stored?.decodeToUtf8())
    }

    @Test
    fun databaseOptionsApplyIterableRunsAllOptions() = harness.runAndReset {
        val key = key("options", "db", "apply")
        val options = databaseOptions {
            locationCacheSize(4096)
            transactionSizeLimit(20_000)
        }
        database.options().apply(options)

        database.runSuspend { txn -> txn.set(key, "applied".encodeToByteArray()) }
    }

    @Test
    fun transactionOptionsConfigureDisablesReadYourWrites() = harness.runAndReset {
        val key = key("options", "txn", "configure")
        val seen = database.runSuspend { txn ->
            txn.options().configure {
                readYourWritesDisable()
                timeout(5_000)
            }
            txn.set(key, "value".encodeToByteArray())
            txn.get(key).await()
        }
        assertNull(seen)
    }

    @Test
    fun transactionOptionsApplyIterableSetsPriority() = harness.runAndReset {
        val key = key("options", "txn", "apply")
        val options = transactionOptions {
            readPriorityHigh()
            tag("coverage")
        }
        database.runSuspend { txn ->
            txn.options().apply(options)
            txn.set(key, "high".encodeToByteArray())
        }

        val value = database.readSuspend { rt -> rt.get(key).await() }
        assertEquals("high", value?.decodeToUtf8())
    }
}
