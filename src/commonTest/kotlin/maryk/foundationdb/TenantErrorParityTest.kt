package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TenantErrorParityTest {
    @Test fun duplicateDatabaseCreateReportsTenantAlreadyExists() = FoundationDbTestHarness().runAndReset {
        for (raw in listOf(false, true)) {
            val name = Tuple.from(namespace, "duplicate", raw)
            if (raw) TenantManagement.createTenant(database, name.pack()).await()
            else TenantManagement.createTenant(database, name).await()
            try {
                val error = assertFailsWith<FDBException> {
                    if (raw) TenantManagement.createTenant(database, name.pack()).await()
                    else TenantManagement.createTenant(database, name).await()
                }
                assertEquals(2132, error.getCode())
            } finally { TenantManagement.deleteTenant(database, name).await() }
        }
    }

    @Test fun missingDatabaseDeleteReportsTenantNotFound() = FoundationDbTestHarness().runAndReset {
        for (raw in listOf(false, true)) {
            val name = Tuple.from(namespace, "absent", raw)
            val error = assertFailsWith<FDBException> {
                if (raw) TenantManagement.deleteTenant(database, name.pack()).await()
                else TenantManagement.deleteTenant(database, name).await()
            }
            assertEquals(2131, error.getCode())
        }
    }

    @Test fun transactionMutationsRemainIdempotent() = FoundationDbTestHarness().runAndReset {
        val name = Tuple.from(namespace, "raw-mutations")
        try {
            repeat(2) { database.run { TenantManagement.createTenant(it, name) } }
            repeat(2) { database.run { TenantManagement.deleteTenant(it, name) } }
        } finally { database.run { TenantManagement.deleteTenant(it, name) } }
    }
}
