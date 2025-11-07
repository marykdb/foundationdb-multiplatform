package maryk.foundationdb

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import maryk.foundationdb.tuple.Tuple

private val TENANT_MAP_PREFIX = ByteArrayUtil.join(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), "/management/tenant/map/".encodeToByteArray())

@OptIn(ExperimentalAtomicApi::class)
actual object TenantManagement {
    private fun tenantKey(name: ByteArray): ByteArray = ByteArrayUtil.join(TENANT_MAP_PREFIX, name)

    actual fun createTenant(transaction: Transaction, tenantName: ByteArray) {
        transaction.options().setSpecialKeySpaceEnableWrites()
        transaction.set(tenantKey(tenantName), ByteArray(0))
    }

    actual fun createTenant(transaction: Transaction, tenantName: Tuple) {
        createTenant(transaction, tenantName.pack())
    }

    actual fun createTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit> {
        val checkedExistence = AtomicInt(0)
        val key = tenantKey(tenantName)
        return database.runAsync { tr ->
            tr.options().setSpecialKeySpaceEnableWrites()
            if (checkedExistence.load() != 0) {
                tr.set(key, ByteArray(0))
                completedFdbFuture(Unit)
            } else {
                fdbFutureFromSuspend {
                    val existing = tr.get(key).await()
                    checkedExistence.store(1)
                    if (existing != null) {
                        throw FDBException(2132, "A tenant with the given name already exists")
                    }
                    tr.set(key, ByteArray(0))
                }
            }
        }
    }

    actual fun createTenant(database: Database, tenantName: Tuple): FdbFuture<Unit> =
        createTenant(database, tenantName.pack())

    actual fun deleteTenant(transaction: Transaction, tenantName: ByteArray) {
        transaction.options().setSpecialKeySpaceEnableWrites()
        transaction.clear(tenantKey(tenantName))
    }

    actual fun deleteTenant(transaction: Transaction, tenantName: Tuple) {
        deleteTenant(transaction, tenantName.pack())
    }

    actual fun deleteTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit> {
        val checkedExistence = AtomicInt(0)
        val key = tenantKey(tenantName)
        return database.runAsync { tr ->
            tr.options().setSpecialKeySpaceEnableWrites()
            if (checkedExistence.load() == 0) {
                fdbFutureFromSuspend {
                    val existing = tr.get(key).await()
                    checkedExistence.store(1)
                    if (existing == null) {
                        throw FDBException(2131, "Tenant does not exist")
                    }
                    tr.clear(key)
                }
            } else {
                tr.clear(key)
                completedFdbFuture(Unit)
            }
        }
    }

    actual fun deleteTenant(database: Database, tenantName: Tuple): FdbFuture<Unit> =
        deleteTenant(database, tenantName.pack())

    actual fun listTenants(database: Database, begin: ByteArray, end: ByteArray, limit: Int): FdbFuture<List<KeyValue>> {
        val beginKey = tenantKey(begin)
        val endKey = tenantKey(end)
        return fdbFutureFromSuspend {
            database.read { tr ->
                tr.options().setRawAccess()
                tr.options().setLockAware()
                val result = tr.collectRange(beginKey, endKey, limit, reverse = false, streamingMode = StreamingMode.WANT_ALL).awaitBlocking()
                result.values.map { kv ->
                    val tenantName = kv.key.copyOfRange(TENANT_MAP_PREFIX.size, kv.key.size)
                    KeyValue(tenantName, kv.value)
                }
            }
        }
    }

    actual fun listTenants(database: Database, begin: Tuple, end: Tuple, limit: Int): FdbFuture<List<KeyValue>> =
        listTenants(database, begin.pack(), end.pack(), limit)
}
