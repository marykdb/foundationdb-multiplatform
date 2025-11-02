package maryk.foundationdb.management

import com.apple.foundationdb.FDBException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import maryk.foundationdb.Cluster
import maryk.foundationdb.FDB
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.TenantManagement
import maryk.foundationdb.decodeToUtf8
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend
import maryk.foundationdb.tuple.Tuple
import org.junit.jupiter.api.Assumptions.assumeTrue

class ClusterTenantTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun clusterOpensDatabaseAndOptions() = harness.runAndReset {
        val cluster = FDB.instance().createCluster(null)
        try {
            val options = cluster.options()
            assertNotNull(options)

            val clusterDatabase = cluster.openDatabase()
            val key = key("cluster", "ping")
            val value = "pong".encodeToByteArray()

            clusterDatabase.runSuspend { txn ->
                txn.set(key, value)
            }

            val fetched = clusterDatabase.readSuspend { rt -> rt.get(key).await() }
            assertEquals("pong", fetched?.decodeToUtf8())

            clusterDatabase.close()
        } finally {
            cluster.close()
        }
    }

    @Test
    fun tenantLifecycleViaManagement() = harness.runAndReset {
        val beginRange = byteArrayOf()
        val endRange = byteArrayOf(0xFF.toByte())
        try {
            TenantManagement.listTenants(harness.database, beginRange, endRange, 1).await()
        } catch (e: FDBException) {
            assumeTrue(false, "Tenants not enabled in local FoundationDB: code=${e.code}")
        }

        val tenantTuple = Tuple.from(harness.namespace, "tenant")
        try {
            TenantManagement.createTenant(harness.database, tenantTuple).await()
        } catch (e: FDBException) {
            assumeTrue(false, "Tenants not enabled in local FoundationDB: code=${'$'}{e.code}")
        }

        val tenant = harness.database.openTenant(tenantTuple)
        try {
            val tenantKey = "tenant/data".encodeToByteArray()
            val tenantValue = "inside-tenant".encodeToByteArray()

            tenant.runSuspend { txn ->
                txn.set(tenantKey, tenantValue)
            }

            val stored = tenant.readSuspend { rt -> rt.get(tenantKey).await() }
            assertEquals("inside-tenant", stored?.decodeToUtf8())
        } finally {
            tenant.close()
        }

        val listed = TenantManagement.listTenants(harness.database, beginRange, endRange, 10).await()
        assertTrue(listed.any { it.key.contentEquals(tenantTuple.pack()) })

        TenantManagement.deleteTenant(harness.database, tenantTuple).await()
        val afterDelete = TenantManagement.listTenants(harness.database, beginRange, endRange, 10).await()
        assertFalse(afterDelete.any { it.key.contentEquals(tenantTuple.pack()) })
    }
}
