package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple

@Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
actual object TenantManagement {
    actual fun createTenant(transaction: Transaction, tenantName: ByteArray) {
        com.apple.foundationdb.TenantManagement.createTenant(transaction.delegate, tenantName)
    }

    actual fun createTenant(transaction: Transaction, tenantName: Tuple) {
        com.apple.foundationdb.TenantManagement.createTenant(transaction.delegate, tenantName.delegate)
    }

    actual fun createTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit> {
        return com.apple.foundationdb.TenantManagement.createTenant(database.delegate, tenantName)
            .mapToFdbFuture { }
    }

    actual fun createTenant(database: Database, tenantName: Tuple): FdbFuture<Unit> =
        createTenant(database, tenantName.pack())

    actual fun deleteTenant(transaction: Transaction, tenantName: ByteArray) {
        com.apple.foundationdb.TenantManagement.deleteTenant(transaction.delegate, tenantName)
    }

    actual fun deleteTenant(transaction: Transaction, tenantName: Tuple) {
        com.apple.foundationdb.TenantManagement.deleteTenant(transaction.delegate, tenantName.delegate)
    }

    actual fun deleteTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit> {
        return com.apple.foundationdb.TenantManagement.deleteTenant(database.delegate, tenantName)
            .mapToFdbFuture { }
    }

    actual fun deleteTenant(database: Database, tenantName: Tuple): FdbFuture<Unit> =
        deleteTenant(database, tenantName.pack())

    actual fun openTenantsIterator(database: Database, begin: ByteArray, end: ByteArray, limit: Int) =
        openTenantsIteratorInternal(database, begin, end, limit)

    actual fun openTenantsIterator(database: Database, begin: Tuple, end: Tuple, limit: Int) =
        openTenantsIterator(database, begin.pack(), end.pack(), limit)

    actual fun listTenants(database: Database, begin: ByteArray, end: ByteArray, limit: Int): FdbFuture<List<KeyValue>> =
        listTenantsInternal(database, begin, end, limit)

    actual fun listTenants(database: Database, begin: Tuple, end: Tuple, limit: Int): FdbFuture<List<KeyValue>> =
        listTenants(database, begin.pack(), end.pack(), limit)
}
