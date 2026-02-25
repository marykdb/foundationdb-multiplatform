package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// Special key prefix for tenant management (see FDB special key docs)
private val TENANT_MANAGEMENT_PREFIX = ByteArrayUtil.join(
    byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
    "/management/tenant/map/".encodeToByteArray()
)

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
        database.runAsync { tr ->
            tr.enableTenantOptions()
            fdbFutureFromSuspend {
                tr.set(managementKey(tenantName), tenantJsonValue(tenantName))
            }
        }

    actual fun createTenant(database: Database, tenantName: Tuple): FdbFuture<Unit> =
        createTenant(database, tenantName.pack())

    actual fun deleteTenant(transaction: Transaction, tenantName: ByteArray) {
        transaction.enableTenantOptions()
        transaction.clear(managementKey(tenantName))
    }

    actual fun deleteTenant(transaction: Transaction, tenantName: Tuple) =
        deleteTenant(transaction, tenantName.pack())

    actual fun deleteTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit> =
        database.runAsync { tr ->
            tr.enableTenantOptions()
            fdbFutureFromSuspend {
                tr.clear(managementKey(tenantName))
            }
        }

    actual fun deleteTenant(database: Database, tenantName: Tuple): FdbFuture<Unit> =
        deleteTenant(database, tenantName.pack())

    actual fun listTenants(database: Database, begin: ByteArray, end: ByteArray, limit: Int): FdbFuture<List<KeyValue>> =
        database.runAsync { tr ->
            tr.options().setRawAccess()
            tr.options().setLockAware()
            tr.options().setSpecialKeySpaceRelaxed()
            fdbFutureFromSuspend {
                val beginKey = managementKey(begin)
                val endKey = managementKey(end)
                val slice = tr.collectRange(
                    beginKey,
                    endKey,
                    limit,
                    reverse = false,
                    streamingMode = StreamingMode.WANT_ALL
                ).await()
                slice.values.map { kv ->
                    val name = kv.key.copyOfRange(TENANT_MANAGEMENT_PREFIX.size, kv.key.size)
                    KeyValue(name, kv.value)
                }
            }
        }

    actual fun listTenants(database: Database, begin: Tuple, end: Tuple, limit: Int): FdbFuture<List<KeyValue>> =
        listTenants(database, begin.pack(), end.pack(), limit)

    private fun tenantJsonValue(@Suppress("UNUSED_PARAMETER") name: ByteArray): ByteArray {
        // The name is encoded in the special-key path itself; keep payload encoding independent
        // from tenant-name byte content so tuple-packed (binary) names work unchanged.
        return "{}".encodeToByteArray()
    }
}
