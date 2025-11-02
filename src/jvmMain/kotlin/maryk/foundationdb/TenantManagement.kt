package maryk.foundationdb

import java.util.ArrayList
import java.util.concurrent.CompletableFuture
import maryk.foundationdb.toFdbFuture
import maryk.foundationdb.tuple.Tuple

actual object TenantManagement {
    actual fun createTenant(transaction: Transaction, tenantName: ByteArray) {
        com.apple.foundationdb.TenantManagement.createTenant(transaction.delegate, tenantName)
    }

    actual fun createTenant(transaction: Transaction, tenantName: Tuple) {
        com.apple.foundationdb.TenantManagement.createTenant(transaction.delegate, tenantName.delegate)
    }

    actual fun createTenant(database: Database, tenantName: ByteArray): FdbFuture<Unit> {
        return com.apple.foundationdb.TenantManagement.createTenant(database.delegate, tenantName)
            .thenApply { Unit }
            .toFdbFuture()
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
            .thenApply { Unit }
            .toFdbFuture()
    }

    actual fun deleteTenant(database: Database, tenantName: Tuple): FdbFuture<Unit> =
        deleteTenant(database, tenantName.pack())

    actual fun listTenants(database: Database, begin: ByteArray, end: ByteArray, limit: Int): FdbFuture<List<KeyValue>> {
        val tenantIterator = com.apple.foundationdb.TenantManagement.listTenants(database.delegate, begin, end, limit)
        val result = ArrayList<com.apple.foundationdb.KeyValue>()

        fun fetch(): CompletableFuture<List<com.apple.foundationdb.KeyValue>> {
            return tenantIterator.onHasNext().thenCompose { hasNext ->
                if (!hasNext) {
                    CompletableFuture.completedFuture(result)
                } else {
                    result.add(tenantIterator.next())
                    fetch()
                }
            }
        }

        val future = fetch()
        future.whenComplete { _, _ -> tenantIterator.close() }
        return future.thenApply { list -> list.map { KeyValue.fromDelegate(it) } }.toFdbFuture()
    }

    actual fun listTenants(database: Database, begin: Tuple, end: Tuple, limit: Int): FdbFuture<List<KeyValue>> =
        listTenants(database, begin.pack(), end.pack(), limit)
}
