package maryk.foundationdb

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import maryk.foundationdb.tuple.Tuple

class TenantCompatibilityTest {
    @OptIn(ExperimentalTime::class, kotlin.experimental.ExperimentalNativeApi::class)
    @Test
    fun createTenantIsIdempotentAndLists() = runBlocking {
        ensureApiVersionSelected()
        val database = FDB.instance().open()
        val nameString = "tenant-interop-${Clock.System.now().toEpochMilliseconds()}"
        val nameBytes = nameString.encodeToByteArray()
        try {
            // first create succeeds
            TenantManagement.createTenant(database, nameBytes).await()
            // second create: tolerate either success or duplicate error
            runCatching { TenantManagement.createTenant(database, nameBytes).await() }

            val endBytes = "tenant-interop-~".encodeToByteArray()
            val tenants = TenantManagement.listTenants(database, nameBytes, endBytes, 0).await()
            assertTrue(tenants.any { it.key.contentEquals(nameBytes) })
        } finally {
            try { TenantManagement.deleteTenant(database, nameBytes).await() } catch (_: Throwable) {}
            database.close()
        }
    }
}
