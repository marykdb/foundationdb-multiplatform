package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple

expect class Database : TransactionContext {
    fun options(): DatabaseOptions
    fun createTransaction(): Transaction
    fun openTenant(tenantName: Tuple): Tenant
    fun close()
}
