package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple

/**
 * Multiplatform access to tenant administration helpers.
 */
expect object TenantManagement {
    fun createTenant(transaction: Transaction, tenantName: ByteArray)
    fun createTenant(transaction: Transaction, tenantName: Tuple)
    fun createTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit>
    fun createTenant(database: Database, tenantName: Tuple): FdbFuture<Unit>

    fun deleteTenant(transaction: Transaction, tenantName: ByteArray)
    fun deleteTenant(transaction: Transaction, tenantName: Tuple)
    fun deleteTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit>
    fun deleteTenant(database: Database, tenantName: Tuple): FdbFuture<Unit>

    fun listTenants(database: Database, begin: ByteArray = byteArrayOf(), end: ByteArray = byteArrayOf(0xFF.toByte()), limit: Int = 0): FdbFuture<List<KeyValue>>
    fun listTenants(database: Database, begin: Tuple, end: Tuple, limit: Int = 0): FdbFuture<List<KeyValue>>
}
