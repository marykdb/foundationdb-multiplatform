package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.async.CloseableAsyncIterator

/**
 * Multiplatform access to tenant administration helpers.
 */
@Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
expect object TenantManagement {
    fun createTenant(transaction: Transaction, tenantName: ByteArray)
    fun createTenant(transaction: Transaction, tenantName: Tuple)
    fun createTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit>
    fun createTenant(database: Database, tenantName: Tuple): FdbFuture<Unit>

    fun deleteTenant(transaction: Transaction, tenantName: ByteArray)
    fun deleteTenant(transaction: Transaction, tenantName: Tuple)
    fun deleteTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit>
    fun deleteTenant(database: Database, tenantName: Tuple): FdbFuture<Unit>

    /** Owns a transaction until closed/exhausted. Close after early termination; no partial-stream retries. */
    fun openTenantsIterator(database: Database, begin: ByteArray = byteArrayOf(), end: ByteArray = byteArrayOf(0xFF.toByte()), limit: Int = 0): CloseableAsyncIterator<KeyValue>
    fun openTenantsIterator(database: Database, begin: Tuple, end: Tuple, limit: Int = 0): CloseableAsyncIterator<KeyValue>

    fun listTenants(database: Database, begin: ByteArray = byteArrayOf(), end: ByteArray = byteArrayOf(0xFF.toByte()), limit: Int = 0): FdbFuture<List<KeyValue>>
    fun listTenants(database: Database, begin: Tuple, end: Tuple, limit: Int = 0): FdbFuture<List<KeyValue>>
}
