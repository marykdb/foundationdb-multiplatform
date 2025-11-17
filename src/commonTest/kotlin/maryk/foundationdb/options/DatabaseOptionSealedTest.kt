package maryk.foundationdb.options

import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseOptionSealedTest {
    @Test
    fun allDatabaseOptionShapesApplyDirectly() {
        val sink = RecordingDatabaseSinkAll()
        listOf(
            DatabaseOption.LocationCacheSize(1),
            DatabaseOption.MaxWatches(2),
            DatabaseOption.MachineId("machine"),
            DatabaseOption.DatacenterId("dc"),
            DatabaseOption.SnapshotReadYourWritesEnable,
            DatabaseOption.SnapshotReadYourWritesDisable,
            DatabaseOption.TransactionLoggingMaxFieldLength(3),
            DatabaseOption.TransactionTimeout(4),
            DatabaseOption.TransactionRetryLimit(5),
            DatabaseOption.TransactionMaxRetryDelay(6),
            DatabaseOption.TransactionSizeLimit(7),
            DatabaseOption.TransactionCausalReadRisky,
            DatabaseOption.TransactionAutomaticIdempotency,
            DatabaseOption.TransactionBypassUnreadable,
            DatabaseOption.TransactionUsedDuringCommitProtectionDisable,
            DatabaseOption.TransactionReportConflictingKeys,
            DatabaseOption.UseConfigDatabase,
            DatabaseOption.TestCausalReadRisky(9)
        ).forEach { it.applyTo(sink) }

        assertEquals(RecordingDatabaseSinkAll.expected.sorted(), sink.seen.sorted())
    }
}
