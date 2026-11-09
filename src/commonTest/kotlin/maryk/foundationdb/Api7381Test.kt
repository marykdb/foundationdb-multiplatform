package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import maryk.foundationdb.tuple.Tuple
import kotlin.test.assertTrue

class Api7381Test {
    private val harness = FoundationDbTestHarness()

    @Test
    fun keySelectorsAndSnapshotMaintenanceMatch() = harness.runAndReset {
        val a = key("selector", "a")
        val b = key("selector", "b")
        database.run { it.set(a, byteArrayOf(1)); it.set(b, byteArrayOf(2)) }
        database.readSuspend { read ->
            assertFalse(read.isSnapshot())
            val snapshot = read.snapshot()
            assertTrue(snapshot.isSnapshot())
            assertTrue(snapshot.read { it.isSnapshot() })
            assertTrue(snapshot.readAsync { completedFdbFuture(it.isSnapshot()) }.await())
            assertContentEquals(a, snapshot.getKey(KeySelector.firstGreaterOrEqual(a)).await())
            assertContentEquals(b, snapshot.getKey(KeySelector.firstGreaterThan(a)).await())
            assertContentEquals(a, snapshot.getKey(KeySelector.lastLessThan(b)).await())
            assertContentEquals(b, snapshot.getKey(KeySelector.lastLessOrEqual(b)).await())
            val range = Range.startsWith(prefixBytes("selector"))
            assertTrue(snapshot.getEstimatedRangeSizeBytes(range).await() >= 0)
            val splits = snapshot.getRangeSplitPoints(range, 1024).await()
            assertContentEquals(range.begin, splits.first())
            assertContentEquals(range.end, splits.last())
        }
    }

    @Test
    fun committedVersionAndVersionstampAgree() = harness.runAndReset {
        val transaction = database.createTransaction()
        try {
            transaction.set(key("version"), byteArrayOf(1))
            assertTrue(transaction.getDatabase().getMainThreadBusyness() >= 0)
            assertTrue(transaction.getApproximateSize().await() > 0)
            val stampFuture = transaction.getVersionstamp()
            transaction.commit().await()
            val stamp = stampFuture.await()
            assertEquals(10, stamp.size)
            val version = stamp.take(8).fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 255) }
            assertEquals(transaction.getCommittedVersion(), version)
            assertTrue(version > 0)
        } finally { transaction.close() }
    }

    @Test
    fun compareAndClearOnlyRemovesMatchingValues() = harness.runAndReset {
        val target = key("mutation", "value")
        val original = byteArrayOf(1, 2)
        database.run { it.set(target, original) }
        database.run { it.mutate(MutationType.COMPARE_AND_CLEAR, target, byteArrayOf(3)) }
        assertContentEquals(original, database.readSuspend { it.get(target).await() })
        database.run { it.mutate(MutationType.COMPARE_AND_CLEAR, target, original) }
        assertEquals(null, database.readSuspend { it.get(target).await() })
        database.run { it.set(key("clear", "a"), original); it.set(key("clear", "b"), original) }
        database.run { it.clearRangeStartsWith(prefixBytes("clear")) }
        assertTrue(database.readSuspend { it.getRange(Range.startsWith(prefixBytes("clear"))).asList().await() }.isEmpty())
    }

    @Test
    fun clientDiagnosticsAndAdditionalOptionsExecute() = harness.runAndReset {
        val status = database.getClientStatus().await().decodeToString()
        assertTrue(status.trimStart().startsWith("{"), status)
        assertTrue(database.getMainThreadBusyness() >= 0)
        database.options().setTransactionIncludePortInAddress()
        database.readSuspend { read ->
            read.options().setReadAheadDisable()
            read.get(key("options")).await()
        }
    }
    @Test
    @Suppress("DEPRECATION")
    fun tenantNameIdAndDirectTransactionsMatch() = harness.runAndReset {
        val name = Tuple.from(namespace, "api-7381").pack()
        val ownerKey = key("owner-db")
        database.run { it.set(ownerKey, byteArrayOf(3)) }
        TenantManagement.createTenant(database, name).await()
        try {
            val tenant = database.openTenant(name)
            try {
                assertContentEquals(name, tenant.getName())
                val copy = tenant.getName()
                copy[0] = (copy[0].toInt() xor 1).toByte()
                assertContentEquals(name, tenant.getName())
                assertTrue(tenant.getId().await() >= 0)
                val transaction = tenant.createTransaction()
                try {
                    assertContentEquals(byteArrayOf(3), transaction.getDatabase().readSuspend { it.get(ownerKey).await() })
                    transaction.set(byteArrayOf(1), byteArrayOf(2))
                    transaction.commit().await()
                } finally { transaction.close() }
                assertContentEquals(byteArrayOf(2), tenant.readSuspend { it.get(byteArrayOf(1)).await() })
            } finally {
                try { tenant.run { it.clear(byteArrayOf(), byteArrayOf(0xff.toByte())) } }
                finally { tenant.close() }
            }
        } finally { TenantManagement.deleteTenant(database, name).await() }
    }

    @Test
    @OptIn(ExperimentalBlobGranules::class)
    fun emptyBlobRangeQueriesMatchAcrossClients() = harness.runAndReset {
        val range = Range.startsWith(prefixBytes("no-blob-ranges"))
        assertTrue(database.listBlobbifiedRanges(range, 10).await().isEmpty())
        database.readSuspend { transaction ->
            transaction.options().setTimeout(5_000)
            assertTrue(transaction.getBlobGranuleRanges(range, 10).await().isEmpty())
        }
    }

}
