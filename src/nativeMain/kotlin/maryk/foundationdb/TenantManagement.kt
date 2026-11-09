package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
actual object TenantManagement {
    private fun managementKey(name: ByteArray): ByteArray = ByteArrayUtil.join(TENANT_MANAGEMENT_PREFIX, name)

    private fun Transaction.enableTenantOptions() {
        options().setSpecialKeySpaceEnableWrites()
        options().setRawAccess()
        options().setLockAware()
        options().setSpecialKeySpaceRelaxed()
    }

    actual fun createTenant(transaction: Transaction, tenantName: ByteArray) {
        transaction.enableTenantOptions()
        val key = managementKey(tenantName)
        val value = tenantJsonValue(tenantName)
        transaction.set(key, value)
    }

    actual fun createTenant(transaction: Transaction, tenantName: Tuple) =
        createTenant(transaction, tenantName.pack())

    actual fun createTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit> =
        changeTenant(database, tenantName, create = true)

    actual fun createTenant(database: Database, tenantName: Tuple): FdbFuture<Unit> =
        createTenant(database, tenantName.pack())

    actual fun deleteTenant(transaction: Transaction, tenantName: ByteArray) {
        transaction.enableTenantOptions()
        transaction.clear(managementKey(tenantName))
    }

    actual fun deleteTenant(transaction: Transaction, tenantName: Tuple) =
        deleteTenant(transaction, tenantName.pack())

    actual fun deleteTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit> =
        changeTenant(database, tenantName, create = false)

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

    /** Check the initial precondition once, preserving idempotent commit-unknown replay. */
    internal fun changeTenant(context: TransactionContext, tenantName: ByteArray, create: Boolean): FdbFuture<Unit> {
        val key = managementKey(tenantName)
        val value = if (create) tenantJsonValue(tenantName) else byteArrayOf()
        val checked = AtomicInt(0)
        return context.runAsync { transaction ->
            transaction.enableTenantOptions()
            fdbFutureFromSuspend {
                if (checked.load() == 0) {
                    val exists = transaction.get(key).await() != null
                    checked.store(1)
                    if (create && exists) throw FDBException("A tenant with the given name already exists", 2132)
                    if (!create && !exists) throw FDBException("Tenant does not exist", 2131)
                }
                if (create) transaction.set(key, value) else transaction.clear(key)
            }
        }
    }

    private fun tenantJsonValue(@Suppress("UNUSED_PARAMETER") name: ByteArray): ByteArray {
        // The name is encoded in the special-key path itself; keep payload encoding independent
        // from tenant-name byte content so tuple-packed (binary) names work unchanged.
        return "{}".encodeToByteArray()
    }
}
