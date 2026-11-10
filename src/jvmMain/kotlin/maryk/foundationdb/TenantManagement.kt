package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple
import java.util.concurrent.atomic.AtomicBoolean

@Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
actual object TenantManagement {
    actual fun createTenant(transaction: Transaction, tenantName: ByteArray) {
        com.apple.foundationdb.TenantManagement.createTenant(transaction.delegate, tenantName)
    }

    actual fun createTenant(transaction: Transaction, tenantName: Tuple) {
        com.apple.foundationdb.TenantManagement.createTenant(transaction.delegate, tenantName.delegate)
    }

    actual fun createTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit> {
        return changeTenant(database, tenantName, create = true)
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
        return changeTenant(database, tenantName, create = false)
    }

    actual fun deleteTenant(database: Database, tenantName: Tuple): FdbFuture<Unit> =
        deleteTenant(database, tenantName.pack())

    // Preserve the SDK's one-time precondition check across commit-unknown retries,
    // while keeping transaction ownership in the cancellation-safe wrapper context.
    private fun changeTenant(database: Database, tenantName: ByteArray, create: Boolean): FdbFuture<Unit> {
        val key = ByteArrayUtil.join(TENANT_MANAGEMENT_PREFIX, tenantName)
        val checked = AtomicBoolean(false)
        return database.runAsync { transaction ->
            transaction.options().setSpecialKeySpaceEnableWrites()
            fun change() {
                if (create) transaction.set(key, byteArrayOf()) else transaction.clear(key)
            }
            if (checked.get()) {
                change()
                completedFdbFuture(Unit)
            } else {
                transaction.get(key).asCompletableFuture().mapToFdbFuture { value ->
                    checked.set(true)
                    if (create && value != null) throw FDBException("A tenant with the given name already exists", 2132)
                    if (!create && value == null) throw FDBException("Tenant does not exist", 2131)
                    change()
                }
            }
        }
    }

    actual fun openTenantsIterator(database: Database, begin: ByteArray, end: ByteArray, limit: Int) =
        openTenantsIteratorInternal(database, begin, end, limit)

    actual fun openTenantsIterator(database: Database, begin: Tuple, end: Tuple, limit: Int) =
        openTenantsIterator(database, begin.pack(), end.pack(), limit)

    actual fun listTenants(database: Database, begin: ByteArray, end: ByteArray, limit: Int): FdbFuture<List<KeyValue>> =
        listTenantsInternal(database, begin, end, limit)

    actual fun listTenants(database: Database, begin: Tuple, end: Tuple, limit: Int): FdbFuture<List<KeyValue>> =
        listTenants(database, begin.pack(), end.pack(), limit)
}
