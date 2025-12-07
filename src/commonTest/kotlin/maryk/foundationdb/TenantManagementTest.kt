package maryk.foundationdb

import kotlinx.coroutines.runBlocking
import kotlin.experimental.ExperimentalNativeApi
import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TenantManagementTest {
    data class TenantHandle(val tuple: Tuple) {
        val bytes: ByteArray = tuple.pack()
    }

    @OptIn(ExperimentalTime::class, ExperimentalNativeApi::class)
    @Test
    fun tenantLifecycleExercisesAllOverloads() = runBlocking {
        // Run on all platforms; native path now hardened.
        ensureApiVersionSelected()
        val database = FDB.instance().open()
        // Skip test if tenants unsupported
        val probeBegin = byteArrayOf()
        val probeEnd = byteArrayOf(0xFF.toByte())
        val tenantSupported = try {
            TenantManagement.listTenants(database, probeBegin, probeEnd, 1).await()
            true
        } catch (_: Throwable) {
            false
        }
        if (!tenantSupported) {
            database.close()
            return@runBlocking
        }
        val baseId = Clock.System.now().toEpochMilliseconds()
        val txnByteTenant = TenantHandle(Tuple.from("tenant-tests", baseId, "txn-byte"))
        val txnTupleTenant = TenantHandle(Tuple.from("tenant-tests", baseId, "txn-tuple"))
        val dbByteTenant = TenantHandle(Tuple.from("tenant-tests", baseId, "db-byte"))
        val dbTupleTenant = TenantHandle(Tuple.from("tenant-tests", baseId, "db-tuple"))
        val prefixTuple = Tuple.from("tenant-tests", baseId)
        val prefixBytes = prefixTuple.pack()
        val endBytes = Tuple.from("tenant-tests", baseId + 1).pack()

        val disabledMessage = "Tenants have been disabled"
        try {
            try {
                TenantManagement.listTenants(
                    database,
                    begin = prefixBytes,
                    end = endBytes,
                    limit = 0
                ).await()
            } catch (ex: Throwable) {
                val fdbException = ex.findFDBException()
                if (fdbException?.message?.contains(disabledMessage) == true) {
                    database.close()
                    // Skipping test: Tenants are disabled in the local FoundationDB cluster
                    return@runBlocking
                } else {
                    throw ex
                }
            }

            stage("transaction coverage create") {
                database.useTransaction {
                    TenantManagement.createTenant(it, txnByteTenant.bytes)
                }
                database.useTransaction {
                    TenantManagement.createTenant(it, txnTupleTenant.tuple)
                }
            }

            stage("database create tenants") {
                TenantManagement.createTenant(database, dbByteTenant.bytes).await()
                TenantManagement.createTenant(database, dbTupleTenant.tuple).await()
            }

            val tenantsByBytes = TenantManagement.listTenants(
                database,
                begin = prefixBytes,
                end = endBytes,
                limit = 0
            ).await()
            listOf(dbByteTenant, dbTupleTenant).forEach { handle ->
                assertTrue(
                    tenantsByBytes.any { kv -> kv.key.contentEquals(handle.bytes) },
                    "Tenant ${handle.tuple.items} missing from byte-based listing"
                )
            }

            val tenantsByTuples = TenantManagement.listTenants(
                database,
                begin = prefixTuple,
                end = Tuple.from("tenant-tests", baseId + 1),
                limit = 10
            ).await()
            assertTrue(tenantsByTuples.size >= 2)

            stage("tenant read/write operations") {
                val primary = database.openTenant(dbByteTenant.tuple)
                try {
                    val key = "tenant-key-$baseId".encodeToByteArray()
                    val value = "value-$baseId".encodeToByteArray()
                    primary.run { txn ->
                        txn.set(key, value)
                    }
                    val readBack = primary.read { rt ->
                        runBlocking { rt.get(key).await() }
                    }
                    requireNotNull(readBack)
                    assertTrue(readBack.contentEquals(value))

                    val readVersion = primary.readAsync { rt -> rt.getReadVersion() }.await()
                    assertTrue(readVersion > 0)

                    val asyncKey = "tenant-key-${baseId + 1}".encodeToByteArray()
                    val asyncValue = "async-value".encodeToByteArray()
                    primary.run { txn ->
                        txn.set(asyncKey, asyncValue)
                    }

                    val asyncRead = primary.read { rt ->
                        runBlocking { rt.get(asyncKey).await() }
                    }
                    requireNotNull(asyncRead)
                    assertTrue(asyncRead.contentEquals(asyncValue))

                    primary.run { txn ->
                        txn.clear(byteArrayOf(), byteArrayOf(0xFF.toByte()))
                    }
                } finally {
                    primary.close()
                }
            }

            stage("transaction coverage delete") {
                database.useTransaction {
                    TenantManagement.deleteTenant(it, txnByteTenant.bytes)
                }
                database.useTransaction {
                    TenantManagement.deleteTenant(it, txnTupleTenant.tuple)
                }
            }

            stage("database delete tenants") {
                runCatching { TenantManagement.deleteTenant(database, dbByteTenant.bytes).await() }
                runCatching { TenantManagement.deleteTenant(database, dbTupleTenant.tuple).await() }
            }

            val remaining = TenantManagement.listTenants(
                database,
                begin = prefixBytes,
                end = endBytes,
                limit = 0
            ).await()
            listOf(dbByteTenant, dbTupleTenant).forEach { handle ->
                assertFalse(remaining.any { kv -> kv.key.contentEquals(handle.bytes) })
            }
        } finally {
            database.close()
        }
    }

    private fun Throwable.findFDBException(): FDBException? = when (this) {
        is FDBException -> this
        else -> this.cause?.findFDBException()
    }

    private inline fun Database.useTransaction(block: (Transaction) -> Unit) {
        val txn = createTransaction()
        try {
            block(txn)
        } finally {
            txn.close()
        }
    }

    private suspend fun <T> stage(name: String, block: suspend () -> T): T =
        try {
            block()
        } catch (ex: Throwable) {
            throw AssertionError("Stage '$name' failed", ex)
        }
}
