package maryk.foundationdb.options

import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionOptionSealedTest {
    @Test
    fun everyTransactionOptionAppliesToSink() {
        val sink = RecordingTransactionSinkAll()
        listOf(
            TransactionOption.CausalWriteRisky,
            TransactionOption.CausalReadRisky,
            TransactionOption.IncludePortInAddress,
            TransactionOption.CausalReadDisable,
            TransactionOption.ReadYourWritesDisable,
            TransactionOption.NextWriteNoWriteConflictRange,
            TransactionOption.SnapshotRywEnable,
            TransactionOption.SnapshotRywDisable,
            TransactionOption.ReadServerSideCacheEnable,
            TransactionOption.ReadServerSideCacheDisable,
            TransactionOption.ReadPriorityNormal,
            TransactionOption.ReadPriorityLow,
            TransactionOption.ReadPriorityHigh,
            TransactionOption.PrioritySystemImmediate,
            TransactionOption.PriorityBatch,
            TransactionOption.InitializeNewDatabase,
            TransactionOption.AccessSystemKeys,
            TransactionOption.ReadSystemKeys,
            TransactionOption.RawAccess,
            TransactionOption.BypassStorageQuota,
            TransactionOption.DebugTransactionIdentifier("debug"),
            TransactionOption.LogTransaction,
            TransactionOption.TransactionLoggingMaxFieldLength(128),
            TransactionOption.ServerRequestTracing,
            TransactionOption.Timeout(500),
            TransactionOption.RetryLimit(7),
            TransactionOption.MaxRetryDelay(250),
            TransactionOption.SizeLimit(2048),
            TransactionOption.AutomaticIdempotency,
            TransactionOption.LockAware,
            TransactionOption.ReadLockAware,
            TransactionOption.UsedDuringCommitProtectionDisable,
            TransactionOption.UseProvisionalProxies,
            TransactionOption.ReportConflictingKeys,
            TransactionOption.SpecialKeySpaceRelaxed,
            TransactionOption.SpecialKeySpaceEnableWrites,
            TransactionOption.Tag("tag"),
            TransactionOption.AutoThrottleTag("auto"),
            TransactionOption.SpanParent(byteArrayOf(1, 2, 3)),
            TransactionOption.ExpensiveClearCostEstimationEnable,
            TransactionOption.BypassUnreadable,
            TransactionOption.UseGrvCache,
            TransactionOption.AuthorizationToken("token"),
            TransactionOption.DurabilityDatacenter,
            TransactionOption.DurabilityRisky,
            TransactionOption.DebugRetryLogging("retry")
        ).forEach { it.applyTo(sink) }

        assertEquals(RecordingTransactionSinkAll.expected.sorted(), sink.seen.sorted())
    }
}
