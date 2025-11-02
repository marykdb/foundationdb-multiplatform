package maryk.foundationdb

expect class Tenant : TransactionContext {
    fun close()
}
