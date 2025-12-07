package maryk.foundationdb.management

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.TenantManagement
import maryk.foundationdb.decodeToUtf8
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend
import maryk.foundationdb.tuple.Tuple
import kotlin.experimental.ExperimentalNativeApi

class ClusterTenantTest {
    private val harness = FoundationDbTestHarness()

    @Test
    @OptIn(ExperimentalNativeApi::class)
    fun tenantLifecycleViaManagement() = harness.runAndReset {
        val beginRange = byteArrayOf()
        val endRange = byteArrayOf(0xFF.toByte())
        val tenantsSupported = try {
            TenantManagement.listTenants(harness.database, beginRange, endRange, 1).await()
            true
        } catch (_: Throwable) {
            false
        }
        if (!tenantsSupported) return@runAndReset

        val tenantTuple = Tuple.from(harness.namespace, "tenant")
        try {
            TenantManagement.createTenant(harness.database, tenantTuple).await()
        } catch (_: Throwable) {
            return@runAndReset
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

            tenant.runSuspend { txn ->
                txn.clear(byteArrayOf(), byteArrayOf(0xFF.toByte()))
            }
        } finally {
            tenant.close()
        }

        val listed = TenantManagement.listTenants(harness.database, beginRange, endRange, 10).await()
        assertTrue(listed.any { it.key.contentEquals(tenantTuple.pack()) })

        runCatching { TenantManagement.deleteTenant(harness.database, tenantTuple).await() }
        val afterDelete = TenantManagement.listTenants(harness.database, beginRange, endRange, 10).await()
        assertFalse(afterDelete.any { it.key.contentEquals(tenantTuple.pack()) })
    }
}
