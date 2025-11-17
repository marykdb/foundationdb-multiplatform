package maryk.foundationdb.options

import kotlin.test.Test
import kotlin.test.assertEquals

class OptionsHelperTest {
    @Test
    fun databaseOptionsSetCollectsAndApplies() {
        val sink = RecordingDatabaseSink()
        val options = databaseOptions {
            locationCacheSize(1024)
            snapshotReadYourWritesDisable()
            transactionTimeout(5000)
        }
        options.applyTo(sink)
        assertEquals(
            listOf(
                "location:1024",
                "snapshotDisable",
                "timeout:5000"
            ),
            sink.events
        )
    }

    @Test
    fun transactionOptionsCollectedAndApplied() {
        val sink = RecordingTransactionSink()
        val options = transactionOptions {
            causalWriteRisky()
            timeout(2000)
            tag("bulk-load")
            readPriorityHigh()
        }
        options.applyTo(sink)
        assertEquals(
            listOf(
                "causalWriteRisky",
                "timeout:2000",
                "tag:bulk-load",
                "priorityHigh"
            ),
            sink.events
        )
    }

    @Test
    fun buildersApplyDirectlyToSink() {
        val sink = RecordingDatabaseSink()
        DatabaseOptionBuilder(sink, null).apply {
            maxWatches(10)
            transactionReportConflictingKeys()
        }
        assertEquals(listOf("maxWatches:10", "reportConflicts"), sink.events)
    }

    @Test
    fun allDatabaseOptionsInvokeSink() {
        val sink = RecordingDatabaseSinkAll()
        DatabaseOptionBuilder(sink, null).apply {
            locationCacheSize(1)
            maxWatches(2)
            machineId("machine")
            datacenterId("dc")
            snapshotReadYourWritesEnable()
            snapshotReadYourWritesDisable()
            transactionLoggingMaxFieldLength(3)
            transactionTimeout(4)
            transactionRetryLimit(5)
            transactionMaxRetryDelay(6)
            transactionSizeLimit(7)
            transactionCausalReadRisky()
            transactionAutomaticIdempotency()
            transactionBypassUnreadable()
            transactionUsedDuringCommitProtectionDisable()
            transactionReportConflictingKeys()
            useConfigDatabase()
            testCausalReadRisky(8)
        }
        assertEquals(RecordingDatabaseSinkAll.expected.sorted(), sink.seen.sorted())
    }

    @Test
    fun allTransactionOptionsInvokeSink() {
        val sink = RecordingTransactionSinkAll()
        TransactionOptionBuilder(sink, null).apply {
            causalWriteRisky()
            causalReadRisky()
            includePortInAddress()
            causalReadDisable()
            readYourWritesDisable()
            nextWriteNoWriteConflictRange()
            snapshotReadYourWritesEnable()
            snapshotReadYourWritesDisable()
            readServerSideCacheEnable()
            readServerSideCacheDisable()
            readPriorityNormal()
            readPriorityLow()
            readPriorityHigh()
            prioritySystemImmediate()
            priorityBatch()
            initializeNewDatabase()
            accessSystemKeys()
            readSystemKeys()
            rawAccess()
            bypassStorageQuota()
            debugTransactionIdentifier("debug")
            logTransaction()
            transactionLoggingMaxFieldLength(9)
            serverRequestTracing()
            timeout(10)
            retryLimit(11)
            maxRetryDelay(12)
            sizeLimit(13)
            automaticIdempotency()
            lockAware()
            readLockAware()
            usedDuringCommitProtectionDisable()
            useProvisionalProxies()
            reportConflictingKeys()
            specialKeySpaceRelaxed()
            specialKeySpaceEnableWrites()
            tag("alpha")
            autoThrottleTag("beta")
            spanParent(byteArrayOf(1))
            expensiveClearCostEstimationEnable()
            bypassUnreadable()
            useGrvCache()
            authorizationToken("token")
            durabilityDatacenter()
            durabilityRisky()
            debugRetryLogging("retry")
        }
        assertEquals(RecordingTransactionSinkAll.expected.sorted(), sink.seen.sorted())
    }
}
